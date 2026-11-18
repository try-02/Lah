package org.fm.com.domain.model

/**
 * Validasi nama satu segmen (bukan path) untuk operasi buat, gantiNama,
 * dan bukaUntukTulisDi. Menolak traversal dan karakter berbahaya lintas
 * POSIX/SAF/iOS.
 */
object ValidasiNama {
    fun sah(nama: String): Boolean =
        nama.isNotBlank() && nama != "." && nama != ".." &&
            '/' !in nama && '\\' !in nama && '\u0000' !in nama
}