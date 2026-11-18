package org.fm.com.data.persistensi

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

/** Entitas riwayat lokasi di layer data. */
data class EntitasRiwayatLokasi(
    val lokasi: LokasiBerkas,
    val adalahFolder: Boolean,
    val waktuTerakhirDibukaEpochMs: Long
)

/** Kontrak repositori untuk persistensi riwayat lokasi yang dibuka pengguna (PRD §4.1). */
interface RepositoriRiwayatLokasi {
    suspend fun catatLokasi(lokasi: LokasiBerkas, adalahFolder: Boolean, waktuEpochMs: Long): HasilOperasi<Unit>
    fun daftarRiwayat(batas: Long = 50): Flow<List<EntitasRiwayatLokasi>>
    suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit>
    suspend fun hapusSemua(): HasilOperasi<Unit>
}

/** Implementasi riwayat lokasi menggunakan basis data SQLDelight. */
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
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal mencatat riwayat ke basis data", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatLokasi>> = flow {
        val list = withContext(ioDispatcher) {
            database.riwayatLokasiEntityQueries
                .semuaRiwayat(batas)
                .executeAsList()
                .mapNotNull { row ->
                    try {
                        val jenis = JenisLokasi.valueOf(row.jenis_lokasi)
                        EntitasRiwayatLokasi(
                            lokasi = LokasiBerkas(row.pengenal, jenis, row.label),
                            adalahFolder = row.adalah_folder,
                            waktuTerakhirDibukaEpochMs = row.waktu_terakhir_dibuka
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
        }
        emit(list)
    }

    override suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatLokasiEntityQueries.hapusRiwayat(lokasi.pengenal, lokasi.jenis.name)
            HasilOperasi.Sukses(Unit)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus riwayat", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.riwayatLokasiEntityQueries.hapusSemuaRiwayat()
            HasilOperasi.Sukses(Unit)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membersihkan riwayat", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
}

/** Implementasi fake in-memory untuk pengujian unit dan preview pra-DI. */
class MemoriRepositoriRiwayatLokasi : RepositoriRiwayatLokasi {
    private val mutex = Mutex()
    private val kaset = mutableMapOf<Pair<String, JenisLokasi>, EntitasRiwayatLokasi>()

    override suspend fun catatLokasi(
        lokasi: LokasiBerkas,
        adalahFolder: Boolean,
        waktuEpochMs: Long
    ): HasilOperasi<Unit> = mutex.withLock {
        kaset[lokasi.pengenal to lokasi.jenis] = EntitasRiwayatLokasi(lokasi, adalahFolder, waktuEpochMs)
        HasilOperasi.Sukses(Unit)
    }

    override fun daftarRiwayat(batas: Long): Flow<List<EntitasRiwayatLokasi>> = flow {
        val hasil = mutex.withLock {
            kaset.values
                .sortedByDescending { it.waktuTerakhirDibukaEpochMs }
                .take(batas.toInt())
        }
        emit(hasil)
    }

    override suspend fun hapus(lokasi: LokasiBerkas): HasilOperasi<Unit> = mutex.withLock {
        kaset.remove(lokasi.pengenal to lokasi.jenis)
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = mutex.withLock {
        kaset.clear()
        HasilOperasi.Sukses(Unit)
    }
}