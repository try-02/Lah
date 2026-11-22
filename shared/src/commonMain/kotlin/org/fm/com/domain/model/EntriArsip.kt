package org.fm.com.domain.model

const val BATAS_UKURAN_TOTAL_EKSTRAK_BAWAAN_BYTE: Long = 256L * 1024L * 1024L
const val BATAS_UKURAN_SATU_ENTRI_BAWAAN_BYTE: Long = 128L * 1024L * 1024L
const val BATAS_JUMLAH_ENTRI_ARSIP_BAWAAN: Int = 20_000
const val BATAS_KEDALAMAN_ARSIP_BAWAAN: Int = 5

enum class FormatArsip(
    val namaTampilan: String,
    val ekstensiUtama: String,
    val semuaEkstensi: Set<String>
) {
    ZIP("ZIP", "zip", setOf("zip")),
    TAR("TAR", "tar", setOf("tar")),
    GZIP("GZIP", "gz", setOf("gz", "gzip")),
    TGZ("TAR.GZ / TGZ", "tgz", setOf("tgz", "tar.gz")),
    JAR("JAR", "jar", setOf("jar")),
    ZIP_LAIN(
        "Berbasis ZIP",
        "zip",
        setOf("apk", "aar", "docx", "xlsx", "pptx", "epub", "cbz", "xpi", "war", "ear")
    );

    companion object {
        fun dariNamaBerkas(nama: String): FormatArsip? {
            val bersih = nama.trim().lowercase()
            if (bersih.endsWith(".tar.gz") && bersih.length > ".tar.gz".length) {
                return TGZ
            }
            val titik = bersih.lastIndexOf('.')
            if (titik <= 0 || titik >= bersih.length - 1) return null
            val ekstensi = bersih.substring(titik + 1)
            return entries.firstOrNull { ekstensi in it.semuaEkstensi }
        }
    }
}

data class EntriArsip(
    val jalurInternal: String,
    val nama: String,
    val adalahFolder: Boolean,
    val ukuranTidakTerkompresiByte: Long? = null,
    val ukuranTerkompresiByte: Long? = null,
    val waktuUbahEpochMs: Long? = null,
    val terenkripsi: Boolean = false,
    val adalahArsipBersarang: Boolean = !adalahFolder && FormatArsip.dariNamaBerkas(nama) != null,
    val metodeKompresi: String? = null,
    val adalahTautanSimbolik: Boolean = false
) {
    init {
        require(jalurInternal.isNotBlank()) { "jalurInternal tidak boleh kosong" }
        require(nama.isNotBlank()) { "nama entri arsip tidak boleh kosong" }
        require(ukuranTidakTerkompresiByte == null || ukuranTidakTerkompresiByte >= 0L) {
            "ukuranTidakTerkompresiByte tidak boleh negatif"
        }
        require(ukuranTerkompresiByte == null || ukuranTerkompresiByte >= 0L) {
            "ukuranTerkompresiByte tidak boleh negatif"
        }
    }
}

data class RingkasanIsiArsip(
    val format: FormatArsip,
    val daftarEntri: List<EntriArsip>,
    val totalUkuranTidakTerkompresiByte: Long,
    val adaUkuranTidakDiketahui: Boolean
)

data class ItemSumberKompresi(
    val lokasi: LokasiBerkas,
    val namaDalamArsip: String,
    val adalahFolder: Boolean
)

data class KebijakanBatasArsip(
    val batasUkuranTotalEkstrakByte: Long = BATAS_UKURAN_TOTAL_EKSTRAK_BAWAAN_BYTE,
    val batasUkuranSatuEntriByte: Long = BATAS_UKURAN_SATU_ENTRI_BAWAAN_BYTE,
    val batasJumlahEntriMaks: Int = BATAS_JUMLAH_ENTRI_ARSIP_BAWAAN,
    val batasKedalamanArsipMaks: Int = BATAS_KEDALAMAN_ARSIP_BAWAAN
) {
    init {
        require(batasUkuranTotalEkstrakByte > 0L) { "batasUkuranTotalEkstrakByte harus > 0" }
        require(batasUkuranSatuEntriByte > 0L) { "batasUkuranSatuEntriByte harus > 0" }
        require(batasJumlahEntriMaks > 0) { "batasJumlahEntriMaks harus > 0" }
        require(batasKedalamanArsipMaks >= 1) { "batasKedalamanArsipMaks minimal 1" }
    }
}

enum class KebijakanKonflikEkstrak {
    TOLAK_KONFLIK,
    TIMPA,
    LEWATI
}

data class OpsiEkstrakArsip(
    val jalurTerpilih: Set<String>? = null,
    val kebijakanKonflik: KebijakanKonflikEkstrak = KebijakanKonflikEkstrak.TOLAK_KONFLIK,
    val konfirmasiPengguna: Boolean = false,
    val kataSandi: String? = null,
    val batas: KebijakanBatasArsip = KebijakanBatasArsip()
)

data class ItemGagalArsip(
    val jalurInternal: String,
    val pesan: String,
    val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
)

data class HasilEkstrakArsip(
    val jumlahDiekstrak: Int,
    val jumlahDilewati: Int,
    val totalByteDiekstrak: Long,
    val daftarBerkasHasil: List<EntitasBerkas>,
    val daftarKonflik: List<String> = emptyList(),
    val daftarGagal: List<ItemGagalArsip> = emptyList()
) {
    val adaGagal: Boolean get() = daftarGagal.isNotEmpty()
    val adaKonflik: Boolean get() = daftarKonflik.isNotEmpty()
}