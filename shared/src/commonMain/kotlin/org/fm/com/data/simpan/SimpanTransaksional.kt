package org.fm.com.data.simpan

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenghitungSidikIsi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.HasilTulisTransaksional
import org.fm.com.domain.usecase.PelaksanaSimpanTransaksional

private const val UKURAN_POTONGAN_SIMPAN = 8 * 1024

class SimpanTransaksional(
    private val repositoriBerkas: RepositoriBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PelaksanaSimpanTransaksional {
    override suspend fun tulisDanVerifikasi(
        lokasi: LokasiBerkas,
        bytes: ByteArray,
        timpa: Boolean
    ): HasilTulisTransaksional = withContext(ioDispatcher) {
        val writer = when (val buka = repositoriBerkas.bukaUntukTulis(lokasi, timpa = timpa)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return@withContext HasilTulisTransaksional.Gagal(buka.pesan, buka.penyebab)
        }
        jalankanTulisDanVerifikasi(writer, lokasiFallback = lokasi, bytes = bytes)
    }

    override suspend fun tulisBaruDiDanVerifikasi(
        induk: LokasiBerkas,
        namaBaru: String,
        bytes: ByteArray,
        timpa: Boolean
    ): HasilTulisTransaksional = withContext(ioDispatcher) {
        val writer = when (val buka = repositoriBerkas.bukaUntukTulisDi(induk, namaBaru, timpa = timpa)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return@withContext HasilTulisTransaksional.Gagal(buka.pesan, buka.penyebab)
        }
        val fallback = LokasiBerkas(
            pengenal = if (induk.pengenal.endsWith("/")) "${induk.pengenal}$namaBaru" else "${induk.pengenal}/$namaBaru",
            jenis = induk.jenis,
            label = namaBaru
        )
        jalankanTulisDanVerifikasi(writer, lokasiFallback = fallback, bytes = bytes)
    }

    private suspend fun jalankanTulisDanVerifikasi(
        writer: SumberTulisBerkas,
        lokasiFallback: LokasiBerkas,
        bytes: ByteArray
    ): HasilTulisTransaksional {
        val sidikTarget = PenghitungSidikIsi.dariBytes(bytes)
        var selesaiSukses = false
        try {
            if (bytes.isNotEmpty()) {
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val akhir = minOf(offset + UKURAN_POTONGAN_SIMPAN, bytes.size)
                    val potongan = PotonganIsi(
                        data = bytes.copyOfRange(offset, akhir),
                        selesai = akhir >= bytes.size
                    )
                    when (val hasilTulis = writer.tulis(potongan)) {
                        is HasilOperasi.Sukses -> offset = akhir
                        is HasilOperasi.Gagal -> return HasilTulisTransaksional.Gagal(
                            hasilTulis.pesan,
                            hasilTulis.penyebab
                        )
                    }
                }
            } else {
                when (val hasilKosong = writer.tulis(PotonganIsi.KOSONG)) {
                    is HasilOperasi.Sukses -> Unit
                    is HasilOperasi.Gagal -> return HasilTulisTransaksional.Gagal(
                        hasilKosong.pesan,
                        hasilKosong.penyebab
                    )
                }
            }

            when (val hasilSelesai = writer.selesai()) {
                is HasilOperasi.Sukses -> selesaiSukses = true
                is HasilOperasi.Gagal -> return HasilTulisTransaksional.Gagal(
                    hasilSelesai.pesan,
                    hasilSelesai.penyebab
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilTulisTransaksional.Gagal(
                e.message ?: "Kegagalan I/O saat menulis berkas",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                if (!selesaiSukses) {
                    writer.batalkan()
                }
            }
        }

        val lokasiAkhir = writer.lokasiTarget ?: lokasiFallback
        val galatVerifikasi = verifikasiBacaUlang(lokasiAkhir, sidikTarget)
        if (galatVerifikasi != null) {
            return galatVerifikasi
        }

        return HasilTulisTransaksional.Sukses(
            lokasiAkhir = lokasiAkhir,
            sidikBaru = sidikTarget
        )
    }

    private suspend fun verifikasiBacaUlang(
        lokasi: LokasiBerkas,
        sidikDiharapkan: SidikIsiBerkas
    ): HasilTulisTransaksional.Gagal? {
        val reader = when (val buka = repositoriBerkas.bukaUntukBaca(lokasi)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> {
                return if (buka.penyebab == PenyebabGalat.TIDAK_DIDUKUNG) {
                    null
                } else {
                    HasilTulisTransaksional.Gagal(
                        "Verifikasi baca ulang gagal: ${buka.pesan}",
                        buka.penyebab
                    )
                }
            }
        }

        return try {
            val akumulator = PenghitungSidikIsi.Akumulator()
            while (true) {
                currentCoroutineContext().ensureActive()
                when (val potongan = reader.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        val data = potongan.potongan.data
                        if (data.isNotEmpty()) {
                            akumulator.perbarui(data)
                        }
                        if (potongan.potongan.selesai) break
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> {
                        return HasilTulisTransaksional.Gagal(
                            "Verifikasi baca ulang gagal: ${potongan.pesan}",
                            PenyebabGalat.TIDAK_DIKETAHUI
                        )
                    }
                }
            }
            val sidikAktual = akumulator.hasil()
            if (sidikAktual != sidikDiharapkan) {
                HasilTulisTransaksional.Gagal(
                    "Verifikasi integritas simpan gagal: ukuran/checksum tidak cocok (diharapkan ${sidikDiharapkan.ukuranByte} B, terbaca ${sidikAktual.ukuranByte} B)",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilTulisTransaksional.Gagal(
                e.message ?: "Gagal memverifikasi hasil simpan",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                reader.tutup()
            }
        }
    }
}