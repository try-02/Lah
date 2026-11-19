package org.fm.com.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Buffer
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriBerkas

const val BATAS_UKURAN_EDITOR_BAWAAN_BYTE: Long = 5L * 1024L * 1024L
private const val UKURAN_SAMPEL_BINER = 8 * 1024

enum class EncodingTeks {
    UTF_8,
    UTF_8_BOM,
    UTF_16_LE,
    UTF_16_BE
}

enum class JenisAkhirBaris {
    LF,
    CRLF,
    CR,
    CAMPURAN
}

data class HasilMuatTeks(
    val isi: String,
    val encoding: EncodingTeks,
    val akhirBaris: JenisAkhirBaris,
    val ukuranByteAsli: Long
)

sealed interface HasilBukaBerkasTeks {
    data class Sukses(val data: HasilMuatTeks) : HasilBukaBerkasTeks
    data class Biner(val alasan: String) : HasilBukaBerkasTeks
    data class TerlaluBesar(val ukuranByte: Long?, val batasMaksimalByte: Long) : HasilBukaBerkasTeks
    data class Gagal(val pesan: String, val penyebab: PenyebabGalat) : HasilBukaBerkasTeks
}

class BukaBerkasTeksUseCase(
    private val repositoriBerkas: RepositoriBerkas,
    private val batasMaksimalByte: Long = BATAS_UKURAN_EDITOR_BAWAAN_BYTE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    init {
        require(batasMaksimalByte > 0L) { "batasMaksimalByte harus > 0" }
    }

    suspend operator fun invoke(lokasi: LokasiBerkas): HasilBukaBerkasTeks {
        val reader = withContext(ioDispatcher) {
            when (val hasil = repositoriBerkas.bukaUntukBaca(lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
        }
        if (reader is HasilOperasi.Gagal) {
            return HasilBukaBerkasTeks.Gagal(reader.pesan, reader.penyebab)
        }
        val sumber = reader as SumberIsiBerkas

        val ukuranDilaporkan = sumber.ukuranByte
        if (ukuranDilaporkan != null && ukuranDilaporkan > batasMaksimalByte) {
            withContext(ioDispatcher + NonCancellable) { sumber.tutup() }
            return HasilBukaBerkasTeks.TerlaluBesar(ukuranDilaporkan, batasMaksimalByte)
        }

        val buffer = Buffer()
        var totalByte = 0L
        try {
            withContext(ioDispatcher) {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val potonganHasil = sumber.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val p = potonganHasil.potongan
                            if (p.data.isNotEmpty()) {
                                totalByte += p.data.size
                                if (totalByte > batasMaksimalByte) {
                                    throw BerkasTerlaluBesarInternal(totalByte)
                                }
                                buffer.write(p.data)
                            }
                            if (p.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            throw KegagalanBacaInternal(potonganHasil.pesan)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BerkasTerlaluBesarInternal) {
            return HasilBukaBerkasTeks.TerlaluBesar(e.ukuranTerbaca, batasMaksimalByte)
        } catch (e: KegagalanBacaInternal) {
            return HasilBukaBerkasTeks.Gagal(e.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: Exception) {
            return HasilBukaBerkasTeks.Gagal(
                e.message ?: "Gagal membaca isi berkas",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(ioDispatcher + NonCancellable) {
                sumber.tutup()
            }
        }

        return withContext(computeDispatcher) {
            currentCoroutineContext().ensureActive()
            val bytes = buffer.readByteArray()
            uraiDanValidasiTeks(bytes)
        }
    }

    private fun uraiDanValidasiTeks(bytes: ByteArray): HasilBukaBerkasTeks {
        if (bytes.isEmpty()) {
            return HasilBukaBerkasTeks.Sukses(
                HasilMuatTeks(
                    isi = "",
                    encoding = EncodingTeks.UTF_8,
                    akhirBaris = JenisAkhirBaris.LF,
                    ukuranByteAsli = 0L
                )
            )
        }

        val encoding = deteksiBom(bytes)
        val teksMentah = when (encoding) {
            EncodingTeks.UTF_16_LE, EncodingTeks.UTF_16_BE -> {
                val panjangData = bytes.size - 2
                if (panjangData % 2 != 0) {
                    return HasilBukaBerkasTeks.Biner("Berkas memiliki penanda UTF-16 tetapi panjang byte tidak valid")
                }
                decodeUtf16(bytes, littleEndian = encoding == EncodingTeks.UTF_16_LE)
            }
            EncodingTeks.UTF_8_BOM -> {
                alasanBinerAtauNull(bytes, startOffset = 3)?.let {
                    return HasilBukaBerkasTeks.Biner(it)
                }
                try {
                    bytes.decodeToString(startIndex = 3, endIndex = bytes.size, throwOnInvalidSequence = true)
                } catch (_: CharacterCodingException) {
                    return HasilBukaBerkasTeks.Biner("Berkas mengandung urutan byte non-UTF-8 (kemungkinan biner)")
                }
            }
            EncodingTeks.UTF_8 -> {
                alasanBinerAtauNull(bytes, startOffset = 0)?.let {
                    return HasilBukaBerkasTeks.Biner(it)
                }
                try {
                    bytes.decodeToString(startIndex = 0, endIndex = bytes.size, throwOnInvalidSequence = true)
                } catch (_: CharacterCodingException) {
                    return HasilBukaBerkasTeks.Biner("Berkas mengandung urutan byte non-UTF-8 (kemungkinan biner)")
                }
            }
        }

        if (teksMentah.indexOf('\u0000') >= 0) {
            return HasilBukaBerkasTeks.Biner("Berkas mengandung karakter NUL (biner)")
        }

        val (isiTernormalisasi, akhirBaris) = normalisasiAkhirBaris(teksMentah)
        return HasilBukaBerkasTeks.Sukses(
            HasilMuatTeks(
                isi = isiTernormalisasi,
                encoding = encoding,
                akhirBaris = akhirBaris,
                ukuranByteAsli = bytes.size.toLong()
            )
        )
    }

    private fun deteksiBom(bytes: ByteArray): EncodingTeks {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return EncodingTeks.UTF_8_BOM
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return EncodingTeks.UTF_16_LE
            if (b0 == 0xFE && b1 == 0xFF) return EncodingTeks.UTF_16_BE
        }
        return EncodingTeks.UTF_8
    }

    private fun decodeUtf16(bytes: ByteArray, littleEndian: Boolean): String {
        val jumlahChar = (bytes.size - 2) / 2
        val chars = CharArray(jumlahChar)
        var idxByte = 2
        for (i in 0 until jumlahChar) {
            val b0 = bytes[idxByte].toInt() and 0xFF
            val b1 = bytes[idxByte + 1].toInt() and 0xFF
            val unit = if (littleEndian) {
                (b1 shl 8) or b0
            } else {
                (b0 shl 8) or b1
            }
            chars[i] = unit.toChar()
            idxByte += 2
        }
        return chars.concatToString()
    }

    private fun alasanBinerAtauNull(bytes: ByteArray, startOffset: Int): String? {
        val batas = minOf(bytes.size, startOffset + UKURAN_SAMPEL_BINER)
        val panjangSampel = batas - startOffset
        if (panjangSampel <= 0) return null
        var jumlahKontrolIlegal = 0
        for (i in startOffset until batas) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) {
                return "Berkas terdeteksi sebagai file biner (mengandung byte NUL)"
            }
            val kontrolIlegal = (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D && b != 0x0C) || b == 0x7F
            if (kontrolIlegal) {
                jumlahKontrolIlegal++
            }
        }
        if (jumlahKontrolIlegal * 20 > panjangSampel) {
            return "Berkas terdeteksi sebagai file biner (karakter kontrol berlebih)"
        }
        return null
    }

    private fun normalisasiAkhirBaris(teks: String): Pair<String, JenisAkhirBaris> {
        var adaLf = false
        var adaCrlf = false
        var adaCr = false
        var i = 0
        val n = teks.length
        while (i < n) {
            when (teks[i]) {
                '\r' -> {
                    if (i + 1 < n && teks[i + 1] == '\n') {
                        adaCrlf = true
                        i += 2
                    } else {
                        adaCr = true
                        i++
                    }
                }
                '\n' -> {
                    adaLf = true
                    i++
                }
                else -> i++
            }
        }

        val jenisAkhirBaris = when {
            (if (adaLf) 1 else 0) + (if (adaCrlf) 1 else 0) + (if (adaCr) 1 else 0) > 1 ->
                JenisAkhirBaris.CAMPURAN
            adaCrlf -> JenisAkhirBaris.CRLF
            adaCr -> JenisAkhirBaris.CR
            else -> JenisAkhirBaris.LF
        }

        if (!adaCrlf && !adaCr) {
            return teks to jenisAkhirBaris
        }

        val sb = StringBuilder(n)
        var j = 0
        while (j < n) {
            val c = teks[j]
            if (c == '\r') {
                sb.append('\n')
                if (j + 1 < n && teks[j + 1] == '\n') {
                    j += 2
                } else {
                    j++
                }
            } else {
                sb.append(c)
                j++
            }
        }
        return sb.toString() to jenisAkhirBaris
    }

    private class BerkasTerlaluBesarInternal(val ukuranTerbaca: Long) : RuntimeException()
    private class KegagalanBacaInternal(val pesan: String) : RuntimeException()
}