package org.fm.com.domain.usecase

data class StatistikTeks(
    val ukuranByteUtf8: Long,
    val ukuranByteAsli: Long?,
    val jumlahBarisTotal: Int,
    val jumlahBarisKosong: Int,
    val jumlahBarisTidakKosong: Int,
    val jumlahKata: Int,
    val jumlahKarakter: Int,
    val jumlahKarakterTanpaAkhirBaris: Int,
    val panjangBarisTerpanjang: Int,
    val encoding: EncodingTeks,
    val akhirBaris: JenisAkhirBaris
)

sealed interface HasilStatistikTeks {
    data class Sukses(val statistik: StatistikTeks) : HasilStatistikTeks
    data class DilewatiBiner(val alasan: String) : HasilStatistikTeks
    data class Gagal(val pesan: String) : HasilStatistikTeks
}