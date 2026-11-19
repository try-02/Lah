package org.fm.com.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val INTERVAL_CEK_BATAL = 4096

class StatistikTeksUseCase(
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(
        teks: String,
        apakahBiner: Boolean = false,
        encoding: EncodingTeks = EncodingTeks.UTF_8,
        akhirBaris: JenisAkhirBaris = JenisAkhirBaris.LF,
        ukuranByteAsli: Long? = null
    ): HasilStatistikTeks {
        if (apakahBiner) {
            return HasilStatistikTeks.DilewatiBiner(
                "Statistik teks tidak tersedia untuk berkas biner"
            )
        }

        return withContext(computeDispatcher) {
            currentCoroutineContext().ensureActive()
            if (teks.isEmpty()) {
                return@withContext HasilStatistikTeks.Sukses(
                    StatistikTeks(
                        ukuranByteUtf8 = 0L,
                        ukuranByteAsli = ukuranByteAsli,
                        jumlahBarisTotal = 0,
                        jumlahBarisKosong = 0,
                        jumlahBarisTidakKosong = 0,
                        jumlahKata = 0,
                        jumlahKarakter = 0,
                        jumlahKarakterTanpaAkhirBaris = 0,
                        panjangBarisTerpanjang = 0,
                        encoding = encoding,
                        akhirBaris = akhirBaris
                    )
                )
            }

            var ukuranUtf8 = 0L
            var jumlahBarisTotal = 1
            var jumlahBarisKosong = 0
            var jumlahBarisTidakKosong = 0
            var jumlahKata = 0
            var karakterTanpaAkhirBaris = 0
            var panjangBarisTerpanjang = 0

            var panjangBarisIni = 0
            var barisIniKosong = true
            var sedangDalamKata = false

            val n = teks.length
            var i = 0
            while (i < n) {
                if ((i % INTERVAL_CEK_BATAL) == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val c = teks[i]
                if (c == '\u0000') {
                    return@withContext HasilStatistikTeks.DilewatiBiner(
                        "Statistik teks dilewati karena konten mengandung byte NUL (biner)"
                    )
                }

                if (c.isHighSurrogate() && i + 1 < n && teks[i + 1].isLowSurrogate()) {
                    ukuranUtf8 += 4L
                    karakterTanpaAkhirBaris += 2
                    panjangBarisIni += 2
                    barisIniKosong = false
                    if (!sedangDalamKata) {
                        jumlahKata++
                        sedangDalamKata = true
                    }
                    i += 2
                    continue
                }

                ukuranUtf8 += when {
                    c.code <= 0x7F -> 1L
                    c.code <= 0x7FF -> 2L
                    else -> 3L
                }

                when (c) {
                    '\n' -> {
                        if (barisIniKosong) jumlahBarisKosong++ else jumlahBarisTidakKosong++
                        if (panjangBarisIni > panjangBarisTerpanjang) {
                            panjangBarisTerpanjang = panjangBarisIni
                        }
                        jumlahBarisTotal++
                        panjangBarisIni = 0
                        barisIniKosong = true
                        sedangDalamKata = false
                    }
                    '\r' -> {
                        sedangDalamKata = false
                    }
                    else -> {
                        karakterTanpaAkhirBaris++
                        panjangBarisIni++
                        if (!c.isWhitespace()) {
                            barisIniKosong = false
                            if (!sedangDalamKata) {
                                jumlahKata++
                                sedangDalamKata = true
                            }
                        } else {
                            sedangDalamKata = false
                        }
                    }
                }
                i++
            }

            currentCoroutineContext().ensureActive()
            if (barisIniKosong) jumlahBarisKosong++ else jumlahBarisTidakKosong++
            if (panjangBarisIni > panjangBarisTerpanjang) {
                panjangBarisTerpanjang = panjangBarisIni
            }

            HasilStatistikTeks.Sukses(
                StatistikTeks(
                    ukuranByteUtf8 = ukuranUtf8,
                    ukuranByteAsli = ukuranByteAsli,
                    jumlahBarisTotal = jumlahBarisTotal,
                    jumlahBarisKosong = jumlahBarisKosong,
                    jumlahBarisTidakKosong = jumlahBarisTidakKosong,
                    jumlahKata = jumlahKata,
                    jumlahKarakter = n,
                    jumlahKarakterTanpaAkhirBaris = karakterTanpaAkhirBaris,
                    panjangBarisTerpanjang = panjangBarisTerpanjang,
                    encoding = encoding,
                    akhirBaris = akhirBaris
                )
            )
        }
    }
}