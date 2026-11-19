package org.fm.com.data.simpan

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenghitungSidikIsi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.HasilCekKonflik
import org.fm.com.domain.usecase.PemeriksaKonflikSimpan

class DeteksiKonflik(
    private val repositoriBerkas: RepositoriBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PemeriksaKonflikSimpan {
    override suspend fun periksa(
        lokasi: LokasiBerkas,
        sidikDiharapkan: SidikIsiBerkas?
    ): HasilCekKonflik {
        if (sidikDiharapkan == null) {
            return HasilCekKonflik.TidakAdaKonflik
        }

        return withContext(ioDispatcher) {
            val reader = when (val hasil = repositoriBerkas.bukaUntukBaca(lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> {
                    return@withContext when (hasil.penyebab) {
                        PenyebabGalat.TIDAK_DITEMUKAN -> HasilCekKonflik.BerkasHilangDiDisk
                        PenyebabGalat.TIDAK_DIDUKUNG -> HasilCekKonflik.TidakAdaKonflik
                        else -> HasilCekKonflik.GagalPeriksa(hasil.pesan, hasil.penyebab)
                    }
                }
            }

            try {
                val ukuranDilaporkan = reader.ukuranByte
                if (ukuranDilaporkan != null && ukuranDilaporkan != sidikDiharapkan.ukuranByte) {
                    return@withContext HasilCekKonflik.BerubahDiLuarAplikasi(ukuranDilaporkan)
                }

                val akumulator = PenghitungSidikIsi.Akumulator()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val potongan = reader.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val data = potongan.potongan.data
                            if (data.isNotEmpty()) {
                                akumulator.perbarui(data)
                                if (akumulator.ukuranSaatIni > sidikDiharapkan.ukuranByte) {
                                    return@withContext HasilCekKonflik.BerubahDiLuarAplikasi(
                                        reader.ukuranByte ?: akumulator.ukuranSaatIni
                                    )
                                }
                            }
                            if (potongan.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            return@withContext HasilCekKonflik.GagalPeriksa(
                                potongan.pesan,
                                PenyebabGalat.TIDAK_DIKETAHUI
                            )
                        }
                    }
                }

                val sidikAktual = akumulator.hasil()
                if (sidikAktual == sidikDiharapkan) {
                    HasilCekKonflik.TidakAdaKonflik
                } else {
                    HasilCekKonflik.BerubahDiLuarAplikasi(sidikAktual.ukuranByte)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilCekKonflik.GagalPeriksa(
                    e.message ?: "Gagal memeriksa konflik berkas",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            } finally {
                withContext(NonCancellable) {
                    reader.tutup()
                }
            }
        }
    }
}