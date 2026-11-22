package org.fm.com.data.persistensi

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.KonfigurasiBeranda
import org.fm.com.domain.repository.RepositoriBeranda

private const val KUNCI_BERANDA_PENGENAL = "beranda.pengenal"
private const val KUNCI_BERANDA_JENIS = "beranda.jenis"
private const val KUNCI_BERANDA_LABEL = "beranda.label"

class SqlDelightRepositoriBeranda(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriBeranda {

    override fun amatiBeranda(): Flow<KonfigurasiBeranda> =
        database.preferensiEntityQueries
            .semuaPreferensi()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                val peta = rows.associateBy({ it.kunci }, { it })
                petakanDariPeta(peta)
            }

    override suspend fun ambilBeranda(): KonfigurasiBeranda = withContext(ioDispatcher) {
        runCatching {
            val rows = database.preferensiEntityQueries.semuaPreferensi().executeAsList()
            val peta = rows.associateBy({ it.kunci }, { it })
            petakanDariPeta(peta)
        }.getOrElse { KonfigurasiBeranda() }
    }

    override suspend fun simpanBeranda(lokasi: LokasiBerkas, waktuEpochMs: Long): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            try {
                database.transaction {
                    database.preferensiEntityQueries.simpanNilai(KUNCI_BERANDA_PENGENAL, lokasi.pengenal, waktuEpochMs)
                    database.preferensiEntityQueries.simpanNilai(KUNCI_BERANDA_JENIS, lokasi.jenis.name, waktuEpochMs)
                    val label = lokasi.label
                    if (label.isNullOrBlank()) {
                        database.preferensiEntityQueries.hapusKunci(KUNCI_BERANDA_LABEL)
                    } else {
                        database.preferensiEntityQueries.simpanNilai(KUNCI_BERANDA_LABEL, label, waktuEpochMs)
                    }
                }
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menyimpan folder beranda", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun resetKeBawaan(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.transaction {
                database.preferensiEntityQueries.hapusKunci(KUNCI_BERANDA_PENGENAL)
                database.preferensiEntityQueries.hapusKunci(KUNCI_BERANDA_JENIS)
                database.preferensiEntityQueries.hapusKunci(KUNCI_BERANDA_LABEL)
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal mereset folder beranda", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private fun petakanDariPeta(peta: Map<String, PreferensiEntity>): KonfigurasiBeranda {
        val pengenal = peta[KUNCI_BERANDA_PENGENAL]?.nilai?.takeIf { it.isNotBlank() }
            ?: return KonfigurasiBeranda()
        val jenisStr = peta[KUNCI_BERANDA_JENIS]?.nilai ?: return KonfigurasiBeranda()
        val jenis = runCatching { JenisLokasi.valueOf(jenisStr) }.getOrNull()
            ?: return KonfigurasiBeranda()
        val label = peta[KUNCI_BERANDA_LABEL]?.nilai
        val waktu = peta[KUNCI_BERANDA_PENGENAL]?.waktu_diubah ?: 0L
        return KonfigurasiBeranda(
            lokasiKhusus = LokasiBerkas(pengenal = pengenal, jenis = jenis, label = label),
            waktuDiubahEpochMs = waktu
        )
    }
}

class MemoriRepositoriBeranda : RepositoriBeranda {
    private val mutex = Mutex()
    private val status = MutableStateFlow(KonfigurasiBeranda())

    override fun amatiBeranda(): Flow<KonfigurasiBeranda> = status.asStateFlow()

    override suspend fun ambilBeranda(): KonfigurasiBeranda = mutex.withLock { status.value }

    override suspend fun simpanBeranda(lokasi: LokasiBerkas, waktuEpochMs: Long): HasilOperasi<Unit> =
        mutex.withLock {
            status.value = KonfigurasiBeranda(lokasiKhusus = lokasi, waktuDiubahEpochMs = waktuEpochMs)
            HasilOperasi.Sukses(Unit)
        }

    override suspend fun resetKeBawaan(): HasilOperasi<Unit> = mutex.withLock {
        status.value = KonfigurasiBeranda()
        HasilOperasi.Sukses(Unit)
    }
}