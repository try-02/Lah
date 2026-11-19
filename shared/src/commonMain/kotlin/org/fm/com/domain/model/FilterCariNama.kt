package org.fm.com.domain.model

enum class ModeCocokNama {
    SEBAGIAN,
    LENGKAP,
    GLOB,
    REGEX
}

enum class FilterJenisEntitas {
    SEMUA,
    BERKAS_SAJA,
    FOLDER_SAJA
}

data class FilterCariNama(
    val kueri: String = "",
    val modeCocok: ModeCocokNama = ModeCocokNama.SEBAGIAN,
    val pekaHuruf: Boolean = false,
    val rekursif: Boolean = true,
    val sertakanTersembunyi: Boolean = false,
    val ekstensiDiizinkan: Set<String> = emptySet(),
    val ukuranMinByte: Long? = null,
    val ukuranMaksByte: Long? = null,
    val filterJenis: FilterJenisEntitas = FilterJenisEntitas.SEMUA,
    val jenisKhusus: Set<JenisBerkas> = emptySet(),
    val waktuUbahMinEpochMs: Long? = null,
    val waktuUbahMaksEpochMs: Long? = null,
    val batasKedalamanMaks: Int = 64,
    val maksHasil: Int = 1000
) {
    init {
        require(batasKedalamanMaks >= 0) { "batasKedalamanMaks tidak boleh negatif" }
        require(maksHasil > 0) { "maksHasil harus > 0" }
    }
}

data class ItemHasilCariNama(
    val entitas: EntitasBerkas,
    val lokasiRelatif: String
)

data class ProgresCariNama(
    val daftarTemuan: List<ItemHasilCariNama> = emptyList(),
    val jumlahFolderDipindai: Int = 0,
    val jumlahItemDiperiksa: Int = 0,
    val namaItemSaatIni: String = "",
    val jumlahPeringatanFolder: Int = 0,
    val selesai: Boolean = false,
    val galatFatal: String? = null,
    val terpotongBatasHasil: Boolean = false
)

object UtilitasFilterPencarian {
    fun normalisasiEkstensi(input: String): Set<String> {
        if (input.isBlank()) return emptySet()
        return input.split(',', ';', ' ')
            .asSequence()
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun ambilEkstensi(nama: String): String {
        val titik = nama.lastIndexOf('.')
        if (titik <= 0 || titik >= nama.length - 1) return ""
        return nama.substring(titik + 1).lowercase()
    }

    fun globKeRegex(polaGlob: String, pekaHuruf: Boolean): Regex {
        val sb = StringBuilder(polaGlob.length * 2 + 2)
        sb.append('^')
        for (c in polaGlob) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                '\\', '.', '+', '^', '$', '(', ')', '[', ']', '{', '}', '|' -> {
                    sb.append('\\').append(c)
                }
                else -> sb.append(c)
            }
        }
        sb.append('$')
        val opsi = if (pekaHuruf) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex(sb.toString(), opsi)
    }
}