package org.fm.com.presentation.navigasi

enum class TabUtama(
    val judul: String,
    val deskripsiAksesibilitas: String
) {
    FILE("File", "Tab File Manager"),
    TERBARU("Terbaru", "Tab Lokasi Terbaru"),
    BOOKMARK("Bookmark", "Tab Bookmark"),
    PENCARIAN("Pencarian", "Tab Pencarian Berkas"),
    PENGATURAN("Pengaturan", "Tab Pengaturan")
}