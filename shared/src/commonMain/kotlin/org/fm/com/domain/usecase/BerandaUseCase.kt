@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.domain.usecase

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.KonfigurasiBeranda
import org.fm.com.domain.repository.RepositoriBeranda
import org.fm.com.domain.repository.RepositoriBerkas

sealed interface HasilResolusiBeranda {
    val lokasiDigunakan: LokasiBerkas

    data class GunakanBawaan(override val lokasiDigunakan: LokasiBerkas) : HasilResolusiBeranda
    data class GunakanKhusus(override val lokasiDigunakan: LokasiBerkas) : HasilResolusiBeranda
    data class IzinAtauLokasiHilang(
        val lokasiBermasalah: LokasiBerkas,
        override val lokasiDigunakan: LokasiBerkas,
        val pesan: String,
        val penyebab: PenyebabGalat
    ) : HasilResolusiBeranda
}

class BerandaUseCase(
    private val repositoriBeranda: RepositoriBeranda,
    private val repositoriBerkas: RepositoriBerkas,
    val lokasiBawaan: LokasiBerkas,
    private val clock: Clock = Clock.System
) {
    fun amatiKonfigurasi(): Flow<KonfigurasiBeranda> =
        repositoriBeranda.amatiBeranda()

    suspend fun ambilKonfigurasi(): KonfigurasiBeranda =
        repositoriBeranda.ambilBeranda()

    suspend fun tetapkanBeranda(lokasi: LokasiBerkas): HasilOperasi<LokasiBerkas> {
        return when (val cek = repositoriBerkas.daftarIsi(lokasi).first()) {
            is HasilOperasi.Sukses -> {
                when (val simpan = repositoriBeranda.simpanBeranda(lokasi, clock.now().toEpochMilliseconds())) {
                    is HasilOperasi.Sukses -> HasilOperasi.Sukses(lokasi)
                    is HasilOperasi.Gagal -> simpan
                }
            }
            is HasilOperasi.Gagal -> cek
        }
    }

    suspend fun resetKeBawaan(): HasilOperasi<LokasiBerkas> =
        when (val hapus = repositoriBeranda.resetKeBawaan()) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(lokasiBawaan)
            is HasilOperasi.Gagal -> hapus
        }

    suspend fun selesaikanLokasiAwal(
        siapkanAkses: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
    ): HasilResolusiBeranda {
        val konfig = repositoriBeranda.ambilBeranda()
        val khusus = konfig.lokasiKhusus ?: return HasilResolusiBeranda.GunakanBawaan(lokasiBawaan)

        val lokasiSiap = siapkanAkses?.invoke(khusus) ?: khusus
        return when (val cek = repositoriBerkas.daftarIsi(lokasiSiap).first()) {
            is HasilOperasi.Sukses -> HasilResolusiBeranda.GunakanKhusus(lokasiSiap)
            is HasilOperasi.Gagal -> HasilResolusiBeranda.IzinAtauLokasiHilang(
                lokasiBermasalah = khusus,
                lokasiDigunakan = lokasiBawaan,
                pesan = cek.pesan,
                penyebab = cek.penyebab
            )
        }
    }
}
