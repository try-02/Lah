package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

interface RepositoriBookmark {
    fun daftarBookmark(): Flow<List<EntitasBookmark>>
    suspend fun cariBookmark(lokasi: LokasiBerkas): EntitasBookmark?
    suspend fun simpanBookmark(bookmark: EntitasBookmark): HasilOperasi<Unit>
    suspend fun gantiLokasiBookmark(lokasiLama: LokasiBerkas, bookmarkBaru: EntitasBookmark): HasilOperasi<Unit>
    suspend fun hapusBookmark(lokasi: LokasiBerkas): HasilOperasi<Unit>
    suspend fun hapusSemua(): HasilOperasi<Unit>
}