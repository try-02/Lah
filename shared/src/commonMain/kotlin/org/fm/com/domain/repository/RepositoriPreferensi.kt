package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.HasilOperasi

data class KonfigurasiPreferensi(
    val wordWrapUmum: Boolean = true
)

interface RepositoriPreferensi {
    fun amatiPreferensi(): Flow<KonfigurasiPreferensi>
    suspend fun ambilPreferensi(): KonfigurasiPreferensi
    suspend fun simpanWordWrapUmum(aktif: Boolean, waktuEpochMs: Long): HasilOperasi<Unit>
}