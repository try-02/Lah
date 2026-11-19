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
import org.fm.com.domain.model.EntitasRiwayatPencarian
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriRiwayatPencarian

class SqlDelightRepositoriRiwayatPencarian(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriRiwayatPencarian {
    override suspend fun catatPencarian(entitas: EntitasRiwayatPencarian): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            try {
                database.riwayatPencarianEntityQueries.tambahAtauPerbaruiPencarian(
                    kueri = entitas.kueri,
                    peka_huruf = entitas.pekaHuruf,
                    kata_utuh = entitas.kataUtuh,
                    regex = entitas.gunakanRegex,
                    waktu_terakhir_dipakai = entitas.waktuTerakhirDipakaiEpochMs
                )
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilOperasi.Gagal(
                    e.message ?: "Gagal menyimpan riwayat pencarian ke basis data",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            }
        }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatPencarian>> =
        database.riwayatPencarianEntityQueries
            .semuaRiwayatPencarian(batas)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                rows.mapNotNull { row ->
                    if (row.kueri.isEmpty()) return@mapNotNull null
                    EntitasRiwayatPencarian(
                        kueri = row.kueri,
                        pekaHuruf = row.peka_huruf,
                        kataUtuh = row.kata_utuh,
                        gunakanRegex = row.regex,
                        waktuTerakhirDipakaiEpochMs = row.waktu_terakhir_dipakai
                    )
                }
            }

    override suspend fun hapus(kueri: String): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatPencarianEntityQueries.hapusRiwayatPencarian(kueri)
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal menghapus riwayat pencarian",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        }
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatPencarianEntityQueries.hapusSemuaRiwayatPencarian()
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal membersihkan riwayat pencarian",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        }
    }
}

class MemoriRepositoriRiwayatPencarian : RepositoriRiwayatPencarian {
    private val mutex = Mutex()
    private val status = MutableStateFlow<Map<String, EntitasRiwayatPencarian>>(emptyMap())

    override suspend fun catatPencarian(entitas: EntitasRiwayatPencarian): HasilOperasi<Unit> =
        mutex.withLock {
            val baru = status.value.toMutableMap()
            baru[entitas.kueri] = entitas
            status.value = baru
            HasilOperasi.Sukses(Unit)
        }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatPencarian>> =
        status.map { map ->
            map.values
                .sortedByDescending { it.waktuTerakhirDipakaiEpochMs }
                .take(batas.toInt().coerceAtLeast(0))
        }

    override suspend fun hapus(kueri: String): HasilOperasi<Unit> = mutex.withLock {
        val baru = status.value.toMutableMap()
        baru.remove(kueri)
        status.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = mutex.withLock {
        status.value = emptyMap()
        HasilOperasi.Sukses(Unit)
    }
}