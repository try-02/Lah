package org.fm.com.data.persistensi

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.EntitasRiwayatLokasi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriRiwayatLokasi

class SqlDelightRepositoriRiwayatLokasi(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriRiwayatLokasi {
    override suspend fun catatLokasi(
        lokasi: LokasiBerkas,
        adalahFolder: Boolean,
        waktuEpochMs: Long
    ): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatLokasiEntityQueries.tambahAtauPerbaruiRiwayat(
                pengenal = lokasi.pengenal,
                jenis_lokasi = lokasi.jenis.name,
                label = lokasi.label,
                adalah_folder = adalahFolder,
                waktu_terakhir_dibuka = waktuEpochMs
            )
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal mencatat riwayat ke basis data", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatLokasi>> =
        database.riwayatLokasiEntityQueries
            .semuaRiwayat(batas)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                rows.mapNotNull { row ->
                    val jenis = runCatching { JenisLokasi.valueOf(row.jenis_lokasi) }.getOrNull()
                        ?: return@mapNotNull null
                    EntitasRiwayatLokasi(
                        lokasi = LokasiBerkas(row.pengenal, jenis, row.label),
                        adalahFolder = row.adalah_folder,
                        waktuTerakhirDibukaEpochMs = row.waktu_terakhir_dibuka
                    )
                }
            }

    override suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatLokasiEntityQueries.hapusRiwayat(lokasi.pengenal, lokasi.jenis.name)
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus riwayat", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatLokasiEntityQueries.hapusSemuaRiwayat()
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membersihkan riwayat", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
}

class MemoriRepositoriRiwayatLokasi : RepositoriRiwayatLokasi {
    private val mutex = Mutex()
    private val status = MutableStateFlow<Map<Pair<String, JenisLokasi>, EntitasRiwayatLokasi>>(emptyMap())

    override suspend fun catatLokasi(
        lokasi: LokasiBerkas,
        adalahFolder: Boolean,
        waktuEpochMs: Long
    ): HasilOperasi<Unit> = mutex.withLock {
        val baru = status.value.toMutableMap()
        baru[lokasi.pengenal to lokasi.jenis] = EntitasRiwayatLokasi(lokasi, adalahFolder, waktuEpochMs)
        status.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatLokasi>> =
        status.map { map ->
            map.values
                .sortedByDescending { it.waktuTerakhirDibukaEpochMs }
                .take(batas.toInt().coerceAtLeast(0))
        }

    override suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit> = mutex.withLock {
        val baru = status.value.toMutableMap()
        baru.remove(lokasi.pengenal to lokasi.jenis)
        status.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = mutex.withLock {
        status.value = emptyMap()
        HasilOperasi.Sukses(Unit)
    }
}