package org.fm.com.domain.model

enum class JenisTargetBookmark {
    FOLDER,
    BERKAS,
    ARSIP
}

sealed interface StatusKetersediaanBookmark {
    data object BelumDiperiksa : StatusKetersediaanBookmark
    data object Tersedia : StatusKetersediaanBookmark
    data class IzinDicabut(val pesan: String) : StatusKetersediaanBookmark
    data class LokasiHilang(val pesan: String) : StatusKetersediaanBookmark
    data class TidakTersedia(val pesan: String) : StatusKetersediaanBookmark

    val apakahTersedia: Boolean
        get() = this is Tersedia

    val apakahBermasalah: Boolean
        get() = this is IzinDicabut || this is LokasiHilang || this is TidakTersedia
}

data class EntitasBookmark(
    val lokasi: LokasiBerkas,
    val jenisTarget: JenisTargetBookmark,
    val waktuDibuatEpochMs: Long = 0L
) {
    val adalahFolder: Boolean
        get() = jenisTarget == JenisTargetBookmark.FOLDER

    val namaTampilan: String
        get() = lokasi.label?.takeIf { it.isNotBlank() }
            ?: lokasi.pengenal.trimEnd('/').substringAfterLast('/').ifBlank { "Lokasi" }
}

data class BookmarkDenganStatus(
    val bookmark: EntitasBookmark,
    val status: StatusKetersediaanBookmark = StatusKetersediaanBookmark.BelumDiperiksa
)