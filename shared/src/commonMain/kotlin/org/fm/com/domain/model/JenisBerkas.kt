package org.fm.com.domain.model

/** Jenis entitas dalam daftar berkas. `LAIN` mencakup tipe yang belum dikenal. */
enum class JenisBerkas {
    FOLDER,
    BERKAS_TEKS,
    BERKAS_GAMBAR,
    BERKAS_AUDIO,
    BERKAS_VIDEO,
    ARSIP,
    TAUTAN,
    LAIN
}
