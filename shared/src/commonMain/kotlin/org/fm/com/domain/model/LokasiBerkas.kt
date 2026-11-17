package org.fm.com.domain.model

/**
 * Penanda lokasi penyimpanan. `jenis` menentukan cara menafsirkan `pengenal`:
 * path POSIX asli, URI/identifier SAF, atau lokasi sandbox iOS — dilarang
 * merekayasa path root palsu untuk URI dan sandbox.
 */
data class LokasiBerkas(
    val pengenal: String,
    val jenis: JenisLokasi,
    val label: String? = null
) {
    init {
        require(pengenal.isNotBlank()) { "Pengenal lokasi tidak boleh kosong" }
    }
}

enum class JenisLokasi {
    /** Path POSIX asli, hanya bila memang tersedia secara alami. */
    POSIX,

    /** URI atau tree URI Storage Access Framework. */
    SAF,

    /** Pengenal lokasi dalam sandbox iOS (Document Picker / security scope). */
    IOS
}
