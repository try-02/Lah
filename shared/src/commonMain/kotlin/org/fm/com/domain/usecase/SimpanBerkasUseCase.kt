@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.domain.usecase

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenghitungSidikIsi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.model.ValidasiNama

sealed interface HasilCekKonflik {
    data object TidakAdaKonflik : HasilCekKonflik
    data class BerubahDiLuarAplikasi(val ukuranAktualByte: Long?) : HasilCekKonflik
    data object BerkasHilangDiDisk : HasilCekKonflik
    data class GagalPeriksa(
        val pesan: String,
        val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
    ) : HasilCekKonflik
}

interface PemeriksaKonflikSimpan {
    suspend fun periksa(
        lokasi: LokasiBerkas,
        sidikDiharapkan: SidikIsiBerkas?
    ): HasilCekKonflik
}

sealed interface HasilTulisTransaksional {
    data class Sukses(
        val lokasiAkhir: LokasiBerkas,
        val sidikBaru: SidikIsiBerkas
    ) : HasilTulisTransaksional

    data class Gagal(
        val pesan: String,
        val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
    ) : HasilTulisTransaksional
}

interface PelaksanaSimpanTransaksional {
    suspend fun tulisDanVerifikasi(
        lokasi: LokasiBerkas,
        bytes: ByteArray,
        timpa: Boolean
    ): HasilTulisTransaksional

    suspend fun tulisBaruDiDanVerifikasi(
        induk: LokasiBerkas,
        namaBaru: String,
        bytes: ByteArray,
        timpa: Boolean
    ): HasilTulisTransaksional
}

sealed interface HasilSimpanBerkas {
    data class Sukses(
        val lokasiHasil: LokasiBerkas,
        val namaHasil: String,
        val sidikBaru: SidikIsiBerkas,
        val waktuSimpanEpochMs: Long
    ) : HasilSimpanBerkas

    data class KonflikEksternal(
        val pesan: String,
        val berkasHilangDiDisk: Boolean
    ) : HasilSimpanBerkas

    data class KonflikTujuan(
        val namaBerkas: String,
        val pesan: String
    ) : HasilSimpanBerkas

    data class Gagal(
        val pesan: String,
        val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
    ) : HasilSimpanBerkas
}

class SimpanBerkasUseCase(
    private val pemeriksaKonflik: PemeriksaKonflikSimpan,
    private val pelaksanaSimpan: PelaksanaSimpanTransaksional,
    private val clock: Clock = Clock.System,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun simpan(
        lokasi: LokasiBerkas,
        isiTeks: String,
        encoding: EncodingTeks = EncodingTeks.UTF_8,
        akhirBaris: JenisAkhirBaris = JenisAkhirBaris.LF,
        sidikDiharapkan: SidikIsiBerkas? = null,
        paksaTimpaKonflik: Boolean = false
    ): HasilSimpanBerkas {
        if (!paksaTimpaKonflik && sidikDiharapkan != null) {
            when (val cek = pemeriksaKonflik.periksa(lokasi, sidikDiharapkan)) {
                is HasilCekKonflik.TidakAdaKonflik -> Unit
                is HasilCekKonflik.BerubahDiLuarAplikasi -> {
                    return HasilSimpanBerkas.KonflikEksternal(
                        pesan = "Berkas telah berubah di luar aplikasi sejak terakhir dimuat atau disimpan. Timpa dengan perubahan lokal Anda?",
                        berkasHilangDiDisk = false
                    )
                }
                is HasilCekKonflik.BerkasHilangDiDisk -> {
                    return HasilSimpanBerkas.KonflikEksternal(
                        pesan = "Berkas asli tidak lagi ditemukan di penyimpanan (mungkin telah dihapus atau dipindahkan). Buat ulang dengan isi editor saat ini?",
                        berkasHilangDiDisk = true
                    )
                }
                is HasilCekKonflik.GagalPeriksa -> {
                    return HasilSimpanBerkas.Gagal(cek.pesan, cek.penyebab)
                }
            }
        }

        val bytes = withContext(computeDispatcher) {
            currentCoroutineContext().ensureActive()
            encodeTeksUntukSimpan(isiTeks, encoding, akhirBaris)
        }

        return when (val tulis = pelaksanaSimpan.tulisDanVerifikasi(lokasi, bytes, timpa = true)) {
            is HasilTulisTransaksional.Sukses -> {
                val nama = tulis.lokasiAkhir.label
                    ?: tulis.lokasiAkhir.pengenal.trimEnd('/').substringAfterLast('/').ifBlank { "Berkas" }
                HasilSimpanBerkas.Sukses(
                    lokasiHasil = tulis.lokasiAkhir,
                    namaHasil = nama,
                    sidikBaru = tulis.sidikBaru,
                    waktuSimpanEpochMs = clock.now().toEpochMilliseconds()
                )
            }
            is HasilTulisTransaksional.Gagal -> HasilSimpanBerkas.Gagal(tulis.pesan, tulis.penyebab)
        }
    }

    suspend fun simpanSebagai(
        induk: LokasiBerkas,
        namaBaru: String,
        isiTeks: String,
        encoding: EncodingTeks = EncodingTeks.UTF_8,
        akhirBaris: JenisAkhirBaris = JenisAkhirBaris.LF,
        paksaTimpaKonflik: Boolean = false
    ): HasilSimpanBerkas {
        val namaBersih = namaBaru.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            return HasilSimpanBerkas.Gagal("Nama berkas tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
        }

        val bytes = withContext(computeDispatcher) {
            currentCoroutineContext().ensureActive()
            encodeTeksUntukSimpan(isiTeks, encoding, akhirBaris)
        }

        return when (
            val tulis = pelaksanaSimpan.tulisBaruDiDanVerifikasi(
                induk = induk,
                namaBaru = namaBersih,
                bytes = bytes,
                timpa = paksaTimpaKonflik
            )
        ) {
            is HasilTulisTransaksional.Sukses -> {
                HasilSimpanBerkas.Sukses(
                    lokasiHasil = tulis.lokasiAkhir,
                    namaHasil = tulis.lokasiAkhir.label ?: namaBersih,
                    sidikBaru = tulis.sidikBaru,
                    waktuSimpanEpochMs = clock.now().toEpochMilliseconds()
                )
            }
            is HasilTulisTransaksional.Gagal -> {
                if (tulis.penyebab == PenyebabGalat.KONFLIK && !paksaTimpaKonflik) {
                    HasilSimpanBerkas.KonflikTujuan(namaBersih, tulis.pesan)
                } else {
                    HasilSimpanBerkas.Gagal(tulis.pesan, tulis.penyebab)
                }
            }
        }
    }

    internal companion object {
        fun encodeTeksUntukSimpan(
            teks: String,
            encoding: EncodingTeks,
            akhirBaris: JenisAkhirBaris
        ): ByteArray {
            val teksAkhirBaris = terapkanAkhirBaris(teks, akhirBaris)
            return when (encoding) {
                EncodingTeks.UTF_8 -> teksAkhirBaris.encodeToByteArray()
                EncodingTeks.UTF_8_BOM -> {
                    val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                    bom + teksAkhirBaris.encodeToByteArray()
                }
                EncodingTeks.UTF_16_LE -> encodeUtf16DenganBom(teksAkhirBaris, littleEndian = true)
                EncodingTeks.UTF_16_BE -> encodeUtf16DenganBom(teksAkhirBaris, littleEndian = false)
            }
        }

        private fun terapkanAkhirBaris(teks: String, akhirBaris: JenisAkhirBaris): String {
            val pemisah = when (akhirBaris) {
                JenisAkhirBaris.LF, JenisAkhirBaris.CAMPURAN -> return teks
                JenisAkhirBaris.CRLF -> "\r\n"
                JenisAkhirBaris.CR -> "\r"
            }
            if ('\n' !in teks) return teks
            val sb = StringBuilder(teks.length + 16)
            for (c in teks) {
                if (c == '\n') sb.append(pemisah) else sb.append(c)
            }
            return sb.toString()
        }

        private fun encodeUtf16DenganBom(teks: String, littleEndian: Boolean): ByteArray {
            val hasil = ByteArray(2 + teks.length * 2)
            if (littleEndian) {
                hasil[0] = 0xFF.toByte()
                hasil[1] = 0xFE.toByte()
            } else {
                hasil[0] = 0xFE.toByte()
                hasil[1] = 0xFF.toByte()
            }
            var idx = 2
            for (c in teks) {
                val unit = c.code
                val lo = (unit and 0xFF).toByte()
                val hi = ((unit ushr 8) and 0xFF).toByte()
                if (littleEndian) {
                    hasil[idx++] = lo
                    hasil[idx++] = hi
                } else {
                    hasil[idx++] = hi
                    hasil[idx++] = lo
                }
            }
            return hasil
        }

        fun sidikDariBytes(bytes: ByteArray): SidikIsiBerkas = PenghitungSidikIsi.dariBytes(bytes)
    }
}