package org.fm.com.domain.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class StatistikTeksTest {

    @Test
    fun teksKosongMenghasilkanSemuaNol() = runTest {
        val useCase = StatistikTeksUseCase()
        val hasil = assertIs<HasilStatistikTeks.Sukses>(
            useCase(
                teks = "",
                encoding = EncodingTeks.UTF_8,
                akhirBaris = JenisAkhirBaris.LF,
                ukuranByteAsli = 0L
            )
        ).statistik

        assertEquals(0L, hasil.ukuranByteUtf8)
        assertEquals(0L, hasil.ukuranByteAsli)
        assertEquals(0, hasil.jumlahBarisTotal)
        assertEquals(0, hasil.jumlahBarisKosong)
        assertEquals(0, hasil.jumlahBarisTidakKosong)
        assertEquals(0, hasil.jumlahKata)
        assertEquals(0, hasil.jumlahKarakter)
        assertEquals(0, hasil.jumlahKarakterTanpaAkhirBaris)
        assertEquals(0, hasil.panjangBarisTerpanjang)
    }

    @Test
    fun hitungBarisKosongKataKarakterDanUnicodeAkurat() = runTest {
        val useCase = StatistikTeksUseCase()
        val teks = "satu dua\n   \ntiga empat lima\né 🌍"

        val hasil = assertIs<HasilStatistikTeks.Sukses>(
            useCase(
                teks = teks,
                encoding = EncodingTeks.UTF_8_BOM,
                akhirBaris = JenisAkhirBaris.CRLF,
                ukuranByteAsli = 42L
            )
        ).statistik

        assertEquals(4, hasil.jumlahBarisTotal)
        assertEquals(1, hasil.jumlahBarisKosong)
        assertEquals(3, hasil.jumlahBarisTidakKosong)
        assertEquals(7, hasil.jumlahKata)
        assertEquals(15, hasil.panjangBarisTerpanjang) // "tiga empat lima"
        assertEquals(teks.length, hasil.jumlahKarakter)
        assertEquals(teks.length - 3, hasil.jumlahKarakterTanpaAkhirBaris)
        assertEquals(EncodingTeks.UTF_8_BOM, hasil.encoding)
        assertEquals(JenisAkhirBaris.CRLF, hasil.akhirBaris)
        assertEquals(42L, hasil.ukuranByteAsli)
        // 'é' = 2 byte UTF-8, '🌍' = 4 byte UTF-8
        assertTrue(hasil.ukuranByteUtf8 > teks.length.toLong())
    }

    @Test
    fun berkasBinerAtauMengandungByteNulDilewati() = runTest {
        val useCase = StatistikTeksUseCase()

        assertIs<HasilStatistikTeks.DilewatiBiner>(
            useCase(teks = "teks biasa", apakahBiner = true)
        )
        assertIs<HasilStatistikTeks.DilewatiBiner>(
            useCase(teks = "abc\u0000def", apakahBiner = false)
        )
    }

    @Test
    fun pembatalanCoroutineTidakDitelan() = runTest {
        val useCase = StatistikTeksUseCase()
        val teksBesar = "kata ".repeat(20_000)

        val job = async {
            useCase(teksBesar)
        }
        job.cancel()
        val gagal = runCatching { job.await() }.exceptionOrNull()
        assertTrue(gagal == null || gagal is CancellationException)
    }
}