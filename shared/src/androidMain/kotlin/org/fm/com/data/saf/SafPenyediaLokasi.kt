package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.data.DataFolder
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

/**
 * Penyedia lokasi SAF: membangun intent `ACTION_OPEN_DOCUMENT_TREE`, mempertahankan
 * izin persisten (`takePersistableUriPermission`), serta memeriksa dan melepas akses tree.
 */
class SafPenyediaLokasi(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    /**
     * Membuat intent sistem untuk memilih direktori pohon SAF (`ACTION_OPEN_DOCUMENT_TREE`).
     * Jika [lokasiAwal] sah, digunakan sebagai `EXTRA_INITIAL_URI`.
     */
    fun buatIntentPilihTree(lokasiAwal: LokasiBerkas? = null): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (lokasiAwal != null) {
                val uriAwal = SafPemetaanLokasi.keUriDokumen(appContext, lokasiAwal).nilaiAtauNull()
                if (uriAwal != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriAwal)
                }
            }
        }

    /**
     * Memproses [Intent] hasil `ACTION_OPEN_DOCUMENT_TREE`.
     * Mengembalikan `Sukses(null)` jika pengguna membatalkan pemilih tanpa memilih folder.
     */
    suspend fun prosesHasilIntent(data: Intent?): HasilOperasi<LokasiBerkas?> {
        val uri = data?.data ?: return HasilOperasi.Sukses(null)
        return when (val hasil = terimaTreeUri(uri, data.flags)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(hasil.nilai)
            is HasilOperasi.Gagal -> hasil
        }
    }

    /**
     * Mengambil izin persisten pada [uri] tree pilihan pengguna (dengan fallback
     * read-only bila provider menolak flag tulis), memverifikasi bahwa URI merujuk ke
     * folder yang dapat diakses, lalu mengembalikan [LokasiBerkas] yang sudah dinormalkan.
     */
    suspend fun terimaTreeUri(uri: Uri, flagIzin: Int? = null): HasilOperasi<LokasiBerkas> =
        withContext(ioDispatcher) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT ||
                uri.authority.isNullOrBlank() ||
                !DocumentsContract.isTreeUri(uri)
            ) {
                return@withContext HasilOperasi.Gagal(
                    "URI hasil pemilih bukan pohon direktori SAF",
                    PenyebabGalat.VALIDASI
                )
            }
            val uriPohon = SafPemetaanLokasi.keUriPohon(uri)
                ?: return@withContext HasilOperasi.Gagal(
                    "Gagal menentukan URI pohon SAF",
                    PenyebabGalat.VALIDASI
                )

            val hasilIzin = ambilIzinPersisten(uriPohon, flagIzin)
            if (hasilIzin is HasilOperasi.Gagal) return@withContext hasilIzin

            val docUri = when (val pemetaan = SafPemetaanLokasi.keUriDokumen(appContext, uriPohon.toString())) {
                is HasilOperasi.Sukses -> pemetaan.nilai
                is HasilOperasi.Gagal -> return@withContext pemetaan
            }

            when (val info = bacaMetadataFolder(docUri)) {
                is HasilOperasi.Sukses -> {
                    val nama = info.nilai
                    val lokasi = if (nama != null) {
                        DataFolder(
                            pengenal = docUri.toString(),
                            nama = nama,
                            jenisLokasi = JenisLokasi.SAF
                        ).keDomain()
                    } else {
                        LokasiBerkas(
                            pengenal = docUri.toString(),
                            jenis = JenisLokasi.SAF,
                            label = null
                        )
                    }
                    HasilOperasi.Sukses(lokasi)
                }
                is HasilOperasi.Gagal -> info
            }
        }

    /**
     * Mengembalikan daftar tree SAF yang saat ini masih memegang izin baca persisten.
     * Jika lokasi eksternal sedang tak tersedia, `label` dibiarkan `null` tanpa crash.
     */
    suspend fun daftarTreeTersimpan(): HasilOperasi<List<LokasiBerkas>> =
        withContext(ioDispatcher) {
            try {
                val daftar = buildList {
                    for (perm in contentResolver.persistedUriPermissions) {
                        if (!perm.isReadPermission || !DocumentsContract.isTreeUri(perm.uri)) continue
                        currentCoroutineContext().ensureActive()
                        val docUri = SafPemetaanLokasi
                            .keUriDokumen(appContext, perm.uri.toString())
                            .nilaiAtauNull() ?: continue
                        val nama = bacaMetadataFolder(docUri).nilaiAtauNull()
                        val lokasi = if (nama != null) {
                            DataFolder(docUri.toString(), nama, JenisLokasi.SAF).keDomain()
                        } else {
                            LokasiBerkas(docUri.toString(), JenisLokasi.SAF, label = null)
                        }
                        add(lokasi)
                    }
                }
                HasilOperasi.Sukses(daftar)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(
                    e.message ?: "Gagal membaca daftar izin SAF",
                    PenyebabGalat.IZIN_DITOLAK
                )
            } catch (e: Exception) {
                HasilOperasi.Gagal(
                    e.message ?: "Gagal memuat lokasi SAF tersimpan",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            }
        }

    /** Mengecek apakah tree untuk [lokasi] masih memiliki izin baca persisten. */
    suspend fun punyaIzinPersisten(lokasi: LokasiBerkas): Boolean =
        withContext(ioDispatcher) {
            val docUri = SafPemetaanLokasi.keUriDokumen(appContext, lokasi).nilaiAtauNull()
                ?: return@withContext false
            val uriPohon = SafPemetaanLokasi.keUriPohon(docUri) ?: return@withContext false
            contentResolver.persistedUriPermissions.any { it.uri == uriPohon && it.isReadPermission }
        }

    /**
     * Melepas izin persisten untuk seluruh pohon SAF milik [lokasi].
     * Catatan: izin di Android melekat pada root tree, sehingga seluruh subfolder di dalam
     * tree yang sama ikut kehilangan akses persisten.
     */
    suspend fun lepasIzinTree(lokasi: LokasiBerkas): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            val docUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val uriPohon = SafPemetaanLokasi.keUriPohon(docUri)
                ?: return@withContext HasilOperasi.Gagal(
                    "Lokasi tidak merujuk ke pohon SAF",
                    PenyebabGalat.VALIDASI
                )
            try {
                val izinSaatIni = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uriPohon }
                    ?: return@withContext HasilOperasi.Sukses(Unit)
                var flagsLepas = 0
                if (izinSaatIni.isReadPermission) {
                    flagsLepas = flagsLepas or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                if (izinSaatIni.isWritePermission) {
                    flagsLepas = flagsLepas or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
                if (flagsLepas != 0) {
                    contentResolver.releasePersistableUriPermission(uriPohon, flagsLepas)
                }
                HasilOperasi.Sukses(Unit)
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(
                    e.message ?: "Gagal melepas izin pohon SAF",
                    PenyebabGalat.IZIN_DITOLAK
                )
            } catch (e: IllegalArgumentException) {
                HasilOperasi.Gagal(
                    e.message ?: "URI pohon SAF tidak valid",
                    PenyebabGalat.VALIDASI
                )
            }
        }

    private fun ambilIzinPersisten(uriPohon: Uri, flagIzin: Int?): HasilOperasi<Unit> {
        val maskIzin = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val flagsDiminta = if (flagIzin != null) {
            val tersaring = flagIzin and maskIzin
            if ((tersaring and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                tersaring
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            maskIzin
        }

        return try {
            contentResolver.takePersistableUriPermission(uriPohon, flagsDiminta)
            HasilOperasi.Sukses(Unit)
        } catch (e: SecurityException) {
            // Sebagian penyedia / folder sistem hanya memberikan akses baca;
            // coba simpan izin baca saja jika flag tulis ikut diminta tanpa intent flags.
            val dapatFallbackBaca = flagIzin == null &&
                (flagsDiminta and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0
            if (dapatFallbackBaca) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uriPohon,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    HasilOperasi.Sukses(Unit)
                } catch (errBaca: SecurityException) {
                    HasilOperasi.Gagal(
                        errBaca.message ?: "Izin persisten SAF ditolak",
                        PenyebabGalat.IZIN_DITOLAK
                    )
                }
            } else {
                HasilOperasi.Gagal(
                    e.message ?: "Izin persisten SAF ditolak",
                    PenyebabGalat.IZIN_DITOLAK
                )
            }
        }
    }

    private suspend fun bacaMetadataFolder(docUri: Uri): HasilOperasi<String?> {
        val sinyalBatal = CancellationSignal()
        val pegangan = currentCoroutineContext()[Job]?.invokeOnCompletion { penyebab ->
            if (penyebab != null) sinyalBatal.cancel()
        }
        return try {
            val cursor = contentResolver.query(
                docUri,
                PROYEKSI_METADATA_FOLDER,
                null,
                null,
                null,
                sinyalBatal
            ) ?: return HasilOperasi.Gagal(
                "Folder SAF tidak ditemukan: $docUri",
                PenyebabGalat.TIDAK_DITEMUKAN
            )
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    HasilOperasi.Gagal(
                        "Folder SAF tidak ditemukan: $docUri",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                } else {
                    val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val idxNama = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mime = if (idxMime >= 0 && !c.isNull(idxMime)) c.getString(idxMime) else null
                    if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        HasilOperasi.Gagal(
                            "Lokasi SAF terpilih bukan folder",
                            PenyebabGalat.VALIDASI
                        )
                    } else {
                        val nama = if (idxNama >= 0 && !c.isNull(idxNama)) {
                            c.getString(idxNama)?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                        HasilOperasi.Sukses(nama)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OperationCanceledException) {
            currentCoroutineContext().ensureActive()
            HasilOperasi.Gagal("Pembacaan metadata folder dibatalkan", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(
                e.message ?: "Izin membaca folder SAF ditolak",
                PenyebabGalat.IZIN_DITOLAK
            )
        } catch (e: FileNotFoundException) {
            HasilOperasi.Gagal(
                e.message ?: "Folder SAF tidak ditemukan",
                PenyebabGalat.TIDAK_DITEMUKAN
            )
        } catch (e: IllegalArgumentException) {
            HasilOperasi.Gagal(
                e.message ?: "URI folder SAF tidak valid",
                PenyebabGalat.VALIDASI
            )
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal membaca metadata folder SAF",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            pegangan?.dispose()
        }
    }

    private fun <T> HasilOperasi<T>.nilaiAtauNull(): T? = when (this) {
        is HasilOperasi.Sukses -> nilai
        is HasilOperasi.Gagal -> null
    }

    private companion object {
        val PROYEKSI_METADATA_FOLDER = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
    }
}