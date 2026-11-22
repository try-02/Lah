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
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriBookmark

class SqlDelightRepositoriBookmark(
    private val database: DatabaseAplikasi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriBookmark {

    override fun daftarBookmark(): Flow<List<EntitasBookmark>> =
        database.bookmarkEntityQueries
            .semuaBookmark()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows ->
                rows.mapNotNull { row ->
                    val jenis = runCatching { JenisLokasi.valueOf(row.jenis_lokasi) }.getOrNull()
                        ?: return@mapNotNull null
                    val lokasi = LokasiBerkas(row.pengenal, jenis, row.label)
                    val target = when {
                        row.adalah_folder -> JenisTargetBookmark.FOLDER
                        FormatArsip.dariNamaBerkas(row.label ?: row.pengenal.substringAfterLast('/')) != null ->
                            JenisTargetBookmark.ARSIP
                        else -> JenisTargetBookmark.BERKAS
                    }
                    EntitasBookmark(
                        lokasi = lokasi,
                        jenisTarget = target,
                        waktuDibuatEpochMs = row.waktu_dibuat
                    )
                }
            }

    override suspend fun cariBookmark(lokasi: LokasiBerkas): EntitasBookmark? =
        withContext(ioDispatcher) {
            runCatching {
                val row = database.bookmarkEntityQueries
                    .cariBerdasarkanPengenal(lokasi.pengenal, lokasi.jenis.name)
                    .executeAsOneOrNull() ?: return@withContext null
                val jenis = runCatching { JenisLokasi.valueOf(row.jenis_lokasi) }.getOrNull()
                    ?: return@withContext null
                val target = when {
                    row.adalah_folder -> JenisTargetBookmark.FOLDER
                    FormatArsip.dariNamaBerkas(row.label ?: row.pengenal.substringAfterLast('/')) != null ->
                        JenisTargetBookmark.ARSIP
                    else -> JenisTargetBookmark.BERKAS
                }
                EntitasBookmark(
                    lokasi = LokasiBerkas(row.pengenal, jenis, row.label),
                    jenisTarget = target,
                    waktuDibuatEpochMs = row.waktu_dibuat
                )
            }.getOrNull()
        }

    override suspend fun simpanBookmark(bookmark: EntitasBookmark): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            try {
                database.bookmarkEntityQueries.tambahBookmark(
                    pengenal = bookmark.lokasi.pengenal,
                    jenis_lokasi = bookmark.lokasi.jenis.name,
                    label = bookmark.lokasi.label ?: bookmark.namaTampilan,
                    adalah_folder = bookmark.adalahFolder,
                    waktu_dibuat = bookmark.waktuDibuatEpochMs
                )
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menyimpan bookmark", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun gantiLokasiBookmark(
        lokasiLama: LokasiBerkas,
        bookmarkBaru: EntitasBookmark
    ): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.transaction {
                database.bookmarkEntityQueries.hapusBookmark(
                    pengenal = lokasiLama.pengenal,
                    jenisLokasi = lokasiLama.jenis.name
                )
                database.bookmarkEntityQueries.tambahBookmark(
                    pengenal = bookmarkBaru.lokasi.pengenal,
                    jenis_lokasi = bookmarkBaru.lokasi.jenis.name,
                    label = bookmarkBaru.lokasi.label ?: bookmarkBaru.namaTampilan,
                    adalah_folder = bookmarkBaru.adalahFolder,
                    waktu_dibuat = bookmarkBaru.waktuDibuatEpochMs
                )
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal memperbarui lokasi bookmark", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun hapusBookmark(lokasi: LokasiBerkas): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            try {
                database.bookmarkEntityQueries.hapusBookmark(lokasi.pengenal, lokasi.jenis.name)
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menghapus bookmark", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        try {
            database.bookmarkEntityQueries.hapusSemuaBookmark()
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membersihkan semua bookmark", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
}

class MemoriRepositoriBookmark : RepositoriBookmark {
    private val mutex = Mutex()
    private val peta = MutableStateFlow<Map<Pair<String, JenisLokasi>, EntitasBookmark>>(emptyMap())

    override fun daftarBookmark(): Flow<List<EntitasBookmark>> =
        peta.map { it.values.sortedByDescending { b -> b.waktuDibuatEpochMs } }

    override suspend fun cariBookmark(lokasi: LokasiBerkas): EntitasBookmark? =
        mutex.withLock { peta.value[lokasi.pengenal to lokasi.jenis] }

    override suspend fun simpanBookmark(bookmark: EntitasBookmark): HasilOperasi<Unit> = mutex.withLock {
        val baru = peta.value.toMutableMap()
        baru[bookmark.lokasi.pengenal to bookmark.lokasi.jenis] = bookmark
        peta.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun gantiLokasiBookmark(
        lokasiLama: LokasiBerkas,
        bookmarkBaru: EntitasBookmark
    ): HasilOperasi<Unit> = mutex.withLock {
        val baru = peta.value.toMutableMap()
        baru.remove(lokasiLama.pengenal to lokasiLama.jenis)
        baru[bookmarkBaru.lokasi.pengenal to bookmarkBaru.lokasi.jenis] = bookmarkBaru
        peta.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun hapusBookmark(lokasi: LokasiBerkas): HasilOperasi<Unit> = mutex.withLock {
        val baru = peta.value.toMutableMap()
        baru.remove(lokasi.pengenal to lokasi.jenis)
        peta.value = baru
        HasilOperasi.Sukses(Unit)
    }

    override suspend fun hapusSemua(): HasilOperasi<Unit> = mutex.withLock {
        peta.value = emptyMap()
        HasilOperasi.Sukses(Unit)
    }
}