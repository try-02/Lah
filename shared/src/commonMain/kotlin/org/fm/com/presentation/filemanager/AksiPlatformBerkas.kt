package org.fm.com.presentation.filemanager

import androidx.compose.runtime.Stable
import org.fm.com.domain.model.LokasiBerkas

/**
 * Kontrak pemicu aksi sistem operasi (share sheet & open with) dari modul bersama.
 */
@Stable
interface AksiPlatformBerkas {
    fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String? = null)
    fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String? = null)
}

/** Implementasi default tanpa efek samping untuk pengujian & pratinjau. */
class AksiPlatformBerkasBawaan : AksiPlatformBerkas {
    override fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String?) {}
    override fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String?) {}
}