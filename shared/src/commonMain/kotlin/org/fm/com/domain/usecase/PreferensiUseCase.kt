@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.domain.usecase

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.repository.KonfigurasiPreferensi
import org.fm.com.domain.repository.RepositoriPreferensi

class PreferensiUseCase(
    private val repositoriPreferensi: RepositoriPreferensi,
    private val clock: Clock = Clock.System
) {
    fun amatiPreferensi(): Flow<KonfigurasiPreferensi> =
        repositoriPreferensi.amatiPreferensi()

    suspend fun ambilPreferensi(): KonfigurasiPreferensi =
        repositoriPreferensi.ambilPreferensi()

    suspend fun setWordWrapUmum(aktif: Boolean): HasilOperasi<Unit> =
        repositoriPreferensi.simpanWordWrapUmum(aktif, clock.now().toEpochMilliseconds())
}