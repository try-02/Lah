@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.domain.usecase

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.EntitasRiwayatPencarian
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

class RiwayatPencarianUseCase(
    private val repositoriRiwayat: RepositoriRiwayatPencarian,
    private val clock: Clock = Clock.System
) {
    suspend fun catat(
        kueri: String,
        pekaHuruf: Boolean = false,
        kataUtuh: Boolean = false,
        gunakanRegex: Boolean = false,
        waktuEpochMs: Long = clock.now().toEpochMilliseconds()
    ): HasilOperasi<Unit> {
        if (kueri.isEmpty()) {
            return HasilOperasi.Gagal("Kueri pencarian kosong tidak disimpan", PenyebabGalat.VALIDASI)
        }
        return repositoriRiwayat.catatPencarian(
            EntitasRiwayatPencarian(
                kueri = kueri,
                pekaHuruf = pekaHuruf,
                kataUtuh = kataUtuh,
                gunakanRegex = gunakanRegex,
                waktuTerakhirDipakaiEpochMs = waktuEpochMs
            )
        )
    }

    fun ambilRiwayat(batas: Long = 20): Flow<List<EntitasRiwayatPencarian>> =
        repositoriRiwayat.daftarRiwayat(batas)

    suspend fun hapusItem(kueri: String): HasilOperasi<Unit> =
        repositoriRiwayat.hapus(kueri)

    suspend fun bersihkanSemua(): HasilOperasi<Unit> =
        repositoriRiwayat.hapusSemua()
}