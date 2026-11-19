package org.fm.com.domain.io

import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

interface SumberTulisBerkas {
    val lokasiTarget: LokasiBerkas?
        get() = null

    suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit>
    suspend fun selesai(): HasilOperasi<Unit>
    suspend fun batalkan(): HasilOperasi<Unit>
}