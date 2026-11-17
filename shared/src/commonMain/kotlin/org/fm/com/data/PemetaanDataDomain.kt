package org.fm.com.data

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

/**
 * Mapper Data → Domain. Menjaga aturan PRD §6.1: tiga model terpisah
 * (Data, Domain, UI) dengan pemetaan eksplisit, bukan satu model dipakai semua layer.
 */
object PemetaanDataDomain {

    /** Bangun model domain dari data mentah penyimpanan. */
    fun DataBerkas.keDomain(): EntitasBerkas {
        val lokasi = LokasiBerkas(pengenal, jenisLokasi, nama)
        return EntitasBerkas(
            lokasi = lokasi,
            nama = nama,
            jenis = jenisDomain(),
            ukuranByte = ukuranByte,
            waktuUbahEpochMs = waktuUbahEpochMs,
            tersembunyi = tersembunyi,
            dapatDibaca = dapatDibaca,
            dapatDitulis = dapatDitulis
        )
    }

    /** Bangun lokasi domain dari folder hasil penelusuran. */
    fun DataFolder.keDomain(): LokasiBerkas =
        LokasiBerkas(pengenal, jenisLokasi, nama)

    /** Jenis domain; tautan pada folder tetap folder, tautan pada berkas tetap tautan. */
    fun DataBerkas.jenisDomain(): JenisBerkas = when {
        adalahFolder -> JenisBerkas.FOLDER
        adalahTautan -> JenisBerkas.TAUTAN
        else -> jenisBerkasDariNama(nama)
    }

    /**
     * Jenis berkas dari nama. Ekstensi dibaca case-insensitive dan hanya
     * ekstensi terakhir yang dianggap; berkas tanpa ekstensi → [JenisBerkas.LAIN].
     */
    fun jenisBerkasDariNama(nama: String): JenisBerkas {
        val titik = nama.lastIndexOf('.')
        if (titik <= 0 || titik == nama.length - 1) return JenisBerkas.LAIN
        return when (nama.substring(titik + 1).lowercase()) {
            "txt", "md", "markdown", "log", "csv", "json", "xml", "kt", "kts",
            "java", "swift", "py", "js", "ts", "html", "htm", "css", "yml", "yaml",
            "toml", "properties", "c", "h", "cpp", "hpp", "sh", "ini", "gradle",
            "sql", "rs", "go", "rb", "php" -> JenisBerkas.BERKAS_TEKS

            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "ico",
            "tif", "tiff" -> JenisBerkas.BERKAS_GAMBAR

            "mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "wma" ->
                JenisBerkas.BERKAS_AUDIO

            "mp4", "mkv", "mov", "webm", "avi", "m4v", "3gp" ->
                JenisBerkas.BERKAS_VIDEO

            "zip", "gz", "gzip", "tar", "tgz", "bz2", "xz", "7z", "rar" ->
                JenisBerkas.ARSIP

            else -> JenisBerkas.LAIN
        }
    }
}