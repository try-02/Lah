package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

data class KonfigurasiBeranda(
    val lokasiKhusus: LokasiBerkas? = null,
    val waktuDiubahEpochMs: Long = 0L
)

interface RepositoriBeranda {
    fun amatiBeranda(): Flow<KonfigurasiBeranda>
    suspend fun ambilBeranda(): KonfigurasiBeranda
    suspend fun simpanBeranda(lokasi: LokasiBerkas, waktuEpochMs: Long): HasilOperasi<Unit>
    suspend fun resetKeBawaan(): HasilOperasi<Unit>
}