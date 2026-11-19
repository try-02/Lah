package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.EntitasRiwayatLokasi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

interface RepositoriRiwayatLokasi {
    suspend fun catatLokasi(lokasi: LokasiBerkas, adalahFolder: Boolean, waktuEpochMs: Long): HasilOperasi<Unit>
    fun daftarRiwayat(batas: Long = 50): Flow<List<EntitasRiwayatLokasi>>
    suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit>
    suspend fun hapusSemua(): HasilOperasi<Unit>
}