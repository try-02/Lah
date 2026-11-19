package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.EntitasRiwayatPencarian
import org.fm.com.domain.model.HasilOperasi

interface RepositoriRiwayatPencarian {
    suspend fun catatPencarian(entitas: EntitasRiwayatPencarian): HasilOperasi<Unit>
    fun daftarRiwayat(batas: Long = 20): Flow<List<EntitasRiwayatPencarian>>
    suspend fun hapus(kueri: String): HasilOperasi<Unit>
    suspend fun hapusSemua(): HasilOperasi<Unit>
}