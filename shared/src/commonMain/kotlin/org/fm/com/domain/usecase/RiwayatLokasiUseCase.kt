package org.fm.com.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import org.fm.com.data.persistensi.EntitasRiwayatLokasi
import org.fm.com.data.persistensi.RepositoriRiwayatLokasi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

class RiwayatLokasiUseCase(
    private val repositoriRiwayat: RepositoriRiwayatLokasi
) {
    suspend fun catatKunjungan(
        lokasi: LokasiBerkas,
        adalahFolder: Boolean = true,
        waktuEpochMs: Long = Clock.System.now().toEpochMilliseconds()
    ): HasilOperasi<Unit> {
        return repositoriRiwayat.catatLokasi(lokasi, adalahFolder, waktuEpochMs)
    }

    fun ambilRiwayat(batas: Long = 50): Flow<List<EntitasRiwayatLokasi>> {
        return repositoriRiwayat.daftarRiwayat(batas)
    }

    suspend fun hapusRiwayat(lokasi: LokasiBerkas): HasilOperasi<Unit> {
        return repositoriRiwayat.hapus(lokasi)
    }

    suspend fun bersihkanSemuaRiwayat(): HasilOperasi<Unit> {
        return repositoriRiwayat.hapusSemua()
    }
}