@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class StatistikTeksUseCaseTest {

    @Test
    fun menghitungBarisKataKarakterDanBarisTerpanjangDenganTepat() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = StatistikTeksUseCase(computeDispatcher = dispatcher)
        val teks = "satu dua\n   \ntiga empat lima\n"

        val hasil = assertIs<HasilStatistikTeks.Sukses>(
            uc(
                teks = teks,
                apakahBiner = false,
                encoding = EncodingTeks.UTF_8,
                akhirBaris = JenisAkhirBaris.CRLF,
                ukuranByteAsli = 32L
            )
        )
        val s = hasil.statistik
        assertEquals(4, s.jumlahBarisTotal)
        assertEquals(2, s.jumlahBarisKosong)
        assertEquals(2, s.jumlahBarisTidakKosong)
        assertEquals(5, s.jumlahKata)
        assertEquals(teks.length, s.jumlahKarakter)
        assertEquals(teks.length - 3, s.jumlahKarakterTanpaAkhirBaris)
        assertEquals(15, s.panjangBarisTerpanjang)
        assertEquals(32L, s.ukuranByteAsli)
        assertEquals(EncodingTeks.UTF_8, s.encoding)
        assertEquals(JenisAkhirBaris.CRLF, s.akhirBaris)
    }

    @Test
    fun teksKosongDanUnicodeEmojiDihitungAkurat() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = StatistikTeksUseCase(computeDispatcher = dispatcher)

        val kosong = assertIs<HasilStatistikTeks.Sukses>(uc(""))
        assertEquals(0, kosong.statistik.jumlahBarisTotal)
        assertEquals(0, kosong.statistik.jumlahKata)
        assertEquals(0, kosong.statistik.panjangBarisTerpanjang)

        val emoji = assertIs<HasilStatistikTeks.Sukses>(uc("halo 🚀"))
        assertEquals(9L, emoji.statistik.ukuranByteUtf8)
        assertEquals(2, emoji.statistik.jumlahKata)
        assertEquals(7, emoji.statistik.panjangBarisTerpanjang)
    }

    @Test
    fun fileBinerDanKontenNulDilewati() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = StatistikTeksUseCase(computeDispatcher = dispatcher)

        assertIs<HasilStatistikTeks.DilewatiBiner>(uc("abc", apakahBiner = true))
        assertIs<HasilStatistikTeks.DilewatiBiner>(uc("ab\u0000cd", apakahBiner = false))
    }

    @Test
    fun perhitunganDapatDibatalkanSaatSedangDijadwalkan() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val uc = StatistikTeksUseCase(computeDispatcher = dispatcher)
        val teksBesar = "kata ".repeat(20_000)

        var tertangkapBatal = false
        val deferred = async {
            try {
                uc(teksBesar)
            } catch (e: CancellationException) {
                tertangkapBatal = true
                throw e
            }
        }
        deferred.cancelAndJoin()
        assertEquals(true, deferred.isCancelled)
        assertEquals(false, deferred.isActive)
        assertEquals(false, tertangkapBatal && !deferred.isCancelled)
    }
}