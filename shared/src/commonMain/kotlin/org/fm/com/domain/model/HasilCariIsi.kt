package org.fm.com.domain.model

const val BATAS_UKURAN_CARI_ISI_BAWAAN_BYTE: Long = 2L * 1024L * 1024L

data class FilterCariIsi(
    val kueri: String,
    val pekaHuruf: Boolean = false,
    val kataUtuh: Boolean = false,
    val gunakanRegex: Boolean = false,
    val rekursif: Boolean = true,
    val sertakanTersembunyi: Boolean = false,
    val ekstensiDiizinkan: Set<String> = emptySet(),
    val hanyaFormatTeksDikenal: Boolean = true,
    val ukuranMinByte: Long? = null,
    val ukuranMaksByte: Long? = null,
    val batasUkuranPerFileByte: Long = BATAS_UKURAN_CARI_ISI_BAWAAN_BYTE,
    val maksBarisCocokPerFile: Int = 50,
    val maksBerkasHasil: Int = 500,
    val batasKedalamanMaks: Int = 64
) {
    init {
        require(batasUkuranPerFileByte > 0L) { "batasUkuranPerFileByte harus > 0" }
        require(maksBarisCocokPerFile > 0) { "maksBarisCocokPerFile harus > 0" }
        require(maksBerkasHasil > 0) { "maksBerkasHasil harus > 0" }
        require(batasKedalamanMaks >= 0) { "batasKedalamanMaks tidak boleh negatif" }
    }
}

data class KecocokanBarisIsi(
    val nomorBaris: Int,
    val potonganTeks: String,
    val jumlahKecocokanDiBaris: Int,
    val kolomPertama: Int
)

data class HasilCariIsiBerkas(
    val entitas: EntitasBerkas,
    val lokasiRelatif: String,
    val jumlahKecocokanTotal: Int,
    val daftarBarisCocok: List<KecocokanBarisIsi>,
    val daftarTerpotongBatas: Boolean = false
)

data class GalatBacaCariIsi(
    val lokasiRelatif: String,
    val namaBerkas: String,
    val pesan: String
)

data class ProgresCariIsi(
    val daftarHasil: List<HasilCariIsiBerkas> = emptyList(),
    val jumlahBerkasDipindai: Int = 0,
    val jumlahFolderDipindai: Int = 0,
    val jumlahBinerDilewati: Int = 0,
    val jumlahTerlaluBesarDilewati: Int = 0,
    val daftarGalatBerkas: List<GalatBacaCariIsi> = emptyList(),
    val namaBerkasSaatIni: String = "",
    val selesai: Boolean = false,
    val galatFatal: String? = null,
    val terpotongBatasHasil: Boolean = false
)