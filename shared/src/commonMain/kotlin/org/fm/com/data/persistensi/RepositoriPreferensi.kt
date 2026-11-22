package org.fm.com.data.persistensi

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
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
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.KonfigurasiPreferensi
import org.fm.com.domain.repository.RepositoriPreferensi

private const val KUNCI_WORD_WRAP_UMUM = "editor.word_wrap_umum"

class SqlDelightRepositoriPreferensi(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriPreferensi {

    override fun amatiPreferensi(): Flow<KonfigurasiPreferensi> =
        database.preferensiEntityQueries
            .ambilNilai(KUNCI_WORD_WRAP_UMUM)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { nilai ->
                KonfigurasiPreferensi(wordWrapUmum = nilai?.toBooleanStrictOrNull() ?: true)
            }

    override suspend fun ambilPreferensi(): KonfigurasiPreferensi = withContext(ioDispatcher) {
        val nilai = runCatching {
            database.preferensiEntityQueries.ambilNilai(KUNCI_WORD_WRAP_UMUM).executeAsOneOrNull()
        }.getOrNull()
        KonfigurasiPreferensi(wordWrapUmum = nilai?.toBooleanStrictOrNull() ?: true)
    }

    override suspend fun simpanWordWrapUmum(aktif: Boolean, waktuEpochMs: Long): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            try {
                database.preferensiEntityQueries.simpanNilai(
                    kunci = KUNCI_WORD_WRAP_UMUM,
                    nilai = aktif.toString(),
                    waktu_diubah = waktuEpochMs
                )
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menyimpan preferensi word wrap", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
}

class MemoriRepositoriPreferensi : RepositoriPreferensi {
    private val mutex = Mutex()
    private val status = MutableStateFlow(KonfigurasiPreferensi())

    override fun amatiPreferensi(): Flow<KonfigurasiPreferensi> = status.asStateFlow()

    override suspend fun ambilPreferensi(): KonfigurasiPreferensi = mutex.withLock { status.value }

    override suspend fun simpanWordWrapUmum(aktif: Boolean, waktuEpochMs: Long): HasilOperasi<Unit> =
        mutex.withLock {
            status.value = status.value.copy(wordWrapUmum = aktif)
            HasilOperasi.Sukses(Unit)
        }
}