package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.fm.com.data.DataBerkas
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

/**
 * Pembaca isi folder SAF dalam SATU query `ContentResolver.query` per folder
 * memakai `DocumentsContract.buildChildDocumentsUriUsingTree` dan proyeksi 6 kolom
 * untuk memenuhi target performa 1.000 item (PRD §7.1 / T55).
 */
class SafDaftarIsi(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
        emit(muatDaftarIsi(lokasi))
    }

    suspend fun muatDaftarIsi(lokasi: LokasiBerkas): HasilOperasi<List<EntitasBerkas>> =
        withContext(ioDispatcher) {
            val docUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val childrenUri = when (val hasil = SafPemetaanLokasi.keUriAnak(appContext, lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            val sinyalBatal = CancellationSignal()
            val peganganBatal = currentCoroutineContext()[Job]?.invokeOnCompletion { penyebab ->
                if (penyebab != null) sinyalBatal.cancel()
            }

            try {
                val cursor = contentResolver.query(
                    childrenUri,
                    PROYEKSI_DOKUMEN,
                    null,
                    null,
                    null,
                    sinyalBatal
                ) ?: return@withContext petakanSaatCursorKosongAtauNull(
                    docUri = docUri,
                    pengenalAsli = lokasi.pengenal,
                    sinyalBatal = sinyalBatal,
                    cursorNull = true
                )

                val daftar = cursor.use { c ->
                    val idxId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    if (idxId < 0) {
                        return@withContext HasilOperasi.Gagal(
                            "Penyedia SAF tidak mengembalikan kolom DOCUMENT_ID",
                            PenyebabGalat.TIDAK_DIKETAHUI
                        )
                    }
                    val idxNama = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val idxUkuran = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val idxWaktu = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val idxFlags = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                    val hasil = ArrayList<EntitasBerkas>(c.count.coerceAtLeast(16))
                    while (c.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val docId = c.teksAtauNull(idxId)?.takeIf { it.isNotBlank() } ?: continue
                        val namaMentah = c.teksAtauNull(idxNama)
                        val mimeType = c.teksAtauNull(idxMime)
                        val ukuranMentah = c.longAtauNull(idxUkuran)
                        val waktuMentah = c.longAtauNull(idxWaktu)
                        val flags = c.intAtauNol(idxFlags)
                        val uriAnak = DocumentsContract.buildDocumentUriUsingTree(docUri, docId)

                        val data = SafPemetaanDokumen.keDataBerkas(
                            docId = docId,
                            uriDokumen = uriAnak.toString(),
                            namaMentah = namaMentah,
                            mimeType = mimeType,
                            ukuranMentah = ukuranMentah,
                            waktuMentah = waktuMentah,
                            flags = flags
                        )
                        hasil.add(data.keDomain())
                    }
                    hasil
                }

                if (daftar.isNotEmpty()) {
                    HasilOperasi.Sukses(daftar)
                } else {
                    petakanSaatCursorKosongAtauNull(
                        docUri = docUri,
                        pengenalAsli = lokasi.pengenal,
                        sinyalBatal = sinyalBatal,
                        cursorNull = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OperationCanceledException) {
                currentCoroutineContext().ensureActive()
                HasilOperasi.Gagal("Pembacaan folder SAF dibatalkan", PenyebabGalat.TIDAK_DIKETAHUI)
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(
                    e.message ?: "Izin akses folder SAF ditolak atau telah dicabut",
                    PenyebabGalat.IZIN_DITOLAK
                )
            } catch (e: FileNotFoundException) {
                HasilOperasi.Gagal(
                    e.message ?: "Folder tidak ditemukan: ${lokasi.pengenal}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            } catch (e: UnsupportedOperationException) {
                petakanSaatQueryAnakGagal(
                    docUri = docUri,
                    lokasi = lokasi,
                    sinyalBatal = sinyalBatal,
                    gagalBawaan = HasilOperasi.Gagal(
                        e.message ?: "Penyedia SAF tidak mendukung pencantuman isi folder",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                )
            } catch (e: IllegalArgumentException) {
                petakanSaatQueryAnakGagal(
                    docUri = docUri,
                    lokasi = lokasi,
                    sinyalBatal = sinyalBatal,
                    gagalBawaan = HasilOperasi.Gagal(
                        e.message ?: "Lokasi SAF tidak valid: ${lokasi.pengenal}",
                        PenyebabGalat.VALIDASI
                    )
                )
            } catch (e: Exception) {
                HasilOperasi.Gagal(
                    e.message ?: "Gagal membaca daftar isi SAF",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            } finally {
                peganganBatal?.dispose()
            }
        }

    private suspend fun petakanSaatCursorKosongAtauNull(
        docUri: Uri,
        pengenalAsli: String,
        sinyalBatal: CancellationSignal,
        cursorNull: Boolean
    ): HasilOperasi<List<EntitasBerkas>> = when (val status = cekStatusDokumen(docUri, sinyalBatal)) {
        StatusDokumen.TIDAK_ADA -> HasilOperasi.Gagal(
            "Folder tidak ditemukan: $pengenalAsli",
            PenyebabGalat.TIDAK_DITEMUKAN
        )
        StatusDokumen.BUKAN_FOLDER -> HasilOperasi.Gagal(
            "Bukan folder: $pengenalAsli",
            PenyebabGalat.VALIDASI
        )
        StatusDokumen.IZIN_DITOLAK -> HasilOperasi.Gagal(
            "Izin akses folder SAF ditolak atau telah dicabut",
            PenyebabGalat.IZIN_DITOLAK
        )
        StatusDokumen.FOLDER -> if (cursorNull) {
            HasilOperasi.Gagal(
                "Penyedia SAF gagal mengembalikan kursor daftar isi",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } else {
            HasilOperasi.Sukses(emptyList())
        }
    }

    private suspend fun petakanSaatQueryAnakGagal(
        docUri: Uri,
        lokasi: LokasiBerkas,
        sinyalBatal: CancellationSignal,
        gagalBawaan: HasilOperasi.Gagal
    ): HasilOperasi<List<EntitasBerkas>> = when (cekStatusDokumen(docUri, sinyalBatal)) {
        StatusDokumen.TIDAK_ADA -> HasilOperasi.Gagal(
            "Folder tidak ditemukan: ${lokasi.pengenal}",
            PenyebabGalat.TIDAK_DITEMUKAN
        )
        StatusDokumen.BUKAN_FOLDER -> HasilOperasi.Gagal(
            "Bukan folder: ${lokasi.pengenal}",
            PenyebabGalat.VALIDASI
        )
        StatusDokumen.IZIN_DITOLAK -> HasilOperasi.Gagal(
            "Izin akses folder SAF ditolak atau telah dicabut",
            PenyebabGalat.IZIN_DITOLAK
        )
        StatusDokumen.FOLDER -> gagalBawaan
    }

    private suspend fun cekStatusDokumen(
        docUri: Uri,
        sinyalBatal: CancellationSignal
    ): StatusDokumen {
        currentCoroutineContext().ensureActive()
        return try {
            val cursor = contentResolver.query(
                docUri,
                PROYEKSI_STATUS_FOLDER,
                null,
                null,
                null,
                sinyalBatal
            ) ?: return StatusDokumen.TIDAK_ADA
            cursor.use { c ->
                if (!c.moveToFirst()) return StatusDokumen.TIDAK_ADA
                val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val mime = c.teksAtauNull(idxMime)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    StatusDokumen.FOLDER
                } else {
                    StatusDokumen.BUKAN_FOLDER
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            StatusDokumen.IZIN_DITOLAK
        } catch (_: FileNotFoundException) {
            StatusDokumen.TIDAK_ADA
        } catch (_: IllegalArgumentException) {
            StatusDokumen.TIDAK_ADA
        }
    }

    private enum class StatusDokumen { FOLDER, BUKAN_FOLDER, TIDAK_ADA, IZIN_DITOLAK }

    private companion object {
        val PROYEKSI_DOKUMEN = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        val PROYEKSI_STATUS_FOLDER = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
    }
}

/** Pemetaan murni kolom dokumen SAF ke [DataBerkas] (dapat diuji di JVM host test). */
internal object SafPemetaanDokumen {
    fun hitungDapatDitulis(adalahFolder: Boolean, flags: Int): Boolean {
        if ((flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0) {
            return false
        }
        return if (adalahFolder) {
            (flags and (DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or
                DocumentsContract.Document.FLAG_SUPPORTS_WRITE)) != 0
        } else {
            (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
        }
    }

    fun keDataBerkas(
        docId: String,
        uriDokumen: String,
        namaMentah: String?,
        mimeType: String?,
        ukuranMentah: Long?,
        waktuMentah: Long?,
        flags: Int
    ): DataBerkas {
        val adalahFolder = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val nama = namaMentah?.takeIf { it.isNotBlank() } ?: docId
        val ukuran = if (adalahFolder) null else ukuranMentah?.takeIf { it >= 0L }
        val waktu = waktuMentah?.takeIf { it >= 0L }
        return DataBerkas(
            pengenal = uriDokumen,
            nama = nama,
            jenisLokasi = JenisLokasi.SAF,
            adalahFolder = adalahFolder,
            adalahTautan = false,
            ukuranByte = ukuran,
            waktuUbahEpochMs = waktu,
            tersembunyi = nama.startsWith("."),
            dapatDibaca = true,
            dapatDitulis = hitungDapatDitulis(adalahFolder, flags)
        )
    }
}

private fun Cursor.teksAtauNull(indeks: Int): String? =
    if (indeks >= 0 && !isNull(indeks)) getString(indeks) else null

private fun Cursor.longAtauNull(indeks: Int): Long? =
    if (indeks >= 0 && !isNull(indeks)) getLong(indeks) else null

private fun Cursor.intAtauNol(indeks: Int): Int =
    if (indeks >= 0 && !isNull(indeks)) getInt(indeks) else 0