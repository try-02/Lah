package org.fm.com.domain.diff

enum class ModeWhitespaceDiff {
    PERSIS,
    ABAIKAN_TEPI,
    ABAIKAN_SEMUA
}

data class OpsiDiff(
    val pekaHuruf: Boolean = true,
    val modeWhitespace: ModeWhitespaceDiff = ModeWhitespaceDiff.PERSIS,
    val tampilkanBarisSama: Boolean = true,
    val jumlahBarisKonteks: Int = 3
) {
    init {
        require(jumlahBarisKonteks >= 0) { "jumlahBarisKonteks tidak boleh negatif" }
    }

    val adaPengabaian: Boolean
        get() = !pekaHuruf || modeWhitespace != ModeWhitespaceDiff.PERSIS
}

enum class JenisBarisDiff(val simbol: String, val labelAksesibilitas: String) {
    SAMA(" ", "Sama"),
    TAMBAH("+", "Ditambah"),
    HAPUS("-", "Dihapus"),
    UBAH("~", "Diubah"),
    KOSONG("·", "Baris penyeimbang")
}

data class RentangKarakterBeda(
    val awal: Int,
    val akhirEksklusif: Int
) {
    init {
        require(awal >= 0 && akhirEksklusif >= awal) {
            "Rentang karakter tidak valid: [$awal, $akhirEksklusif)"
        }
    }
}

data class SelBarisDiff(
    val nomorBaris: Int?,
    val teks: String,
    val jenis: JenisBarisDiff,
    val rentangKarakterBeda: List<RentangKarakterBeda> = emptyList()
)

data class BarisDiffBerdampingan(
    val indeks: Int,
    val kiri: SelBarisDiff,
    val kanan: SelBarisDiff,
    val indeksBlokPerubahan: Int? = null
)

data class BarisDiffUnified(
    val indeks: Int,
    val nomorBarisA: Int?,
    val nomorBarisB: Int?,
    val jenis: JenisBarisDiff,
    val teks: String,
    val rentangKarakterBeda: List<RentangKarakterBeda> = emptyList(),
    val indeksBlokPerubahan: Int? = null,
    val keteranganPemisahKonteks: String? = null
)

data class KelompokPerubahanDiff(
    val indeksPerubahan: Int,
    val indeksBarisBerdampinganPertama: Int,
    val indeksBarisUnifiedPertama: Int,
    val barisAwalA: Int,
    val jumlahBarisA: Int,
    val barisAwalB: Int,
    val jumlahBarisB: Int
)

data class StatistikDiff(
    val jumlahBarisA: Int,
    val jumlahBarisB: Int,
    val jumlahBarisSama: Int,
    val jumlahBarisTambah: Int,
    val jumlahBarisHapus: Int,
    val jumlahBlokPerubahan: Int
) {
    val identik: Boolean
        get() = jumlahBarisTambah == 0 && jumlahBarisHapus == 0
}

data class HasilDiffLengkap(
    val barisBerdampingan: List<BarisDiffBerdampingan>,
    val barisUnified: List<BarisDiffUnified>,
    val daftarPerubahan: List<KelompokPerubahanDiff>,
    val statistik: StatistikDiff,
    val terpotongBatasKompleksitas: Boolean = false
)