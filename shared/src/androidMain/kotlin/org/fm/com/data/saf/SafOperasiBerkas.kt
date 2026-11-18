package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

/**
 * Implementasi operasi dokumen SAF: buat folder/berkas, ganti nama, hapus, salin, dan pindah.
 */
class SafOperasiBerkas(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriOperasiBerkas {

    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val nama = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib untuk membuat folder", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(nama)) {
                return@withContext HasilOperasi.Gagal("Nama folder tidak sah: $nama", PenyebabGalat.VALIDASI)
            }

            buatEntitas(
                parentLokasi = permintaan.sumber,
                nama = nama,
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                konfirmasiTimpa = permintaan.konfirmasi
            )
        }

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val nama = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib untuk membuat berkas", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(nama)) {
                return@withContext HasilOperasi.Gagal("Nama berkas tidak sah: $nama", PenyebabGalat.VALIDASI)
            }

            val mimeType = tentukanMimeType(nama)
            buatEntitas(
                parentLokasi = permintaan.sumber,
                nama = nama,
                mimeType = mimeType,
                konfirmasiTimpa = permintaan.konfirmasi
            )
        }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val namaBaru = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib diisi untuk ganti nama", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(namaBaru)) {
                return@withContext HasilOperasi.Gagal("Nama tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
            }

            val docUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            try {
                val hasilUri = DocumentsContract.renameDocument(contentResolver, docUri, namaBaru)
                    ?: return@withContext HasilOperasi.Gagal("Gagal mengganti nama dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)

                val entitasBaru = bacaSatuEntitas(hasilUri)
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca dokumen setelah ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)

                // Jika provider diam-diam memberi akhiran (1) karena tabrakan nama dan tanpa konfirmasi
                if (entitasBaru.nama != namaBaru && !permintaan.konfirmasi) {
                    return@withContext HasilOperasi.Gagal(
                        "Nama target sudah ada di folder: $namaBaru (dihasilkan: ${entitasBaru.nama})",
                        PenyebabGalat.KONFLIK
                    )
                }

                HasilOperasi.Sukses(entitasBaru)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin ganti nama SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: UnsupportedOperationException) {
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung ganti nama", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal mengganti nama berkas SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            if (!permintaan.konfirmasi) {
                return@withContext HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
            }

            val docUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            try {
                val berhasil = DocumentsContract.deleteDocument(contentResolver, docUri)
                if (berhasil) {
                    HasilOperasi.Sukses(Unit)
                } else {
                    HasilOperasi.Gagal("Gagal menghapus dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin hapus SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                HasilOperasi.Gagal("Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: UnsupportedOperationException) {
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung hapus dokumen", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menghapus dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val tujuanInduk = permintaan.tujuan
                ?: return@withContext HasilOperasi.Gagal("Tujuan (folder induk) wajib diisi untuk salin", PenyebabGalat.VALIDASI)

            val sumberUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val targetParentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, tujuanInduk)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            // Validasi flag copy native
            val flagsSumber = bacaFlags(sumberUri)
            if ((flagsSumber and DocumentsContract.Document.FLAG_SUPPORTS_COPY) == 0) {
                return@withContext HasilOperasi.Gagal(
                    "Penyedia SAF tidak mendukung operasi salin native",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }

            try {
                val salinanUri = DocumentsContract.copyDocument(contentResolver, sumberUri, targetParentUri)
                    ?: return@withContext HasilOperasi.Gagal("Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)

                // Jika namaBaru ditentukan, ganti nama hasil salinan
                if (permintaan.namaBaru != null && ValidasiNama.sah(permintaan.namaBaru)) {
                    val renamedUri = DocumentsContract.renameDocument(contentResolver, salinanUri, permintaan.namaBaru)
                    val entitas = bacaSatuEntitas(renamedUri ?: salinanUri)
                    if (entitas != null) return@withContext HasilOperasi.Sukses(entitas)
                }

                val entitas = bacaSatuEntitas(salinanUri)
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca hasil salin SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin salin SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: UnsupportedOperationException) {
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung salin dokumen", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val tujuanInduk = permintaan.tujuan
                ?: return@withContext HasilOperasi.Gagal("Tujuan (folder induk) wajib diisi untuk pindah", PenyebabGalat.VALIDASI)

            val sumberUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val targetParentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, tujuanInduk)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            val flagsSumber = bacaFlags(sumberUri)
            if ((flagsSumber and DocumentsContract.Document.FLAG_SUPPORTS_MOVE) == 0) {
                return@withContext HasilOperasi.Gagal(
                    "Penyedia SAF tidak mendukung operasi pindah native",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }

            try {
                // DocumentsContract.moveDocument memerlukan sourceParentUri
                val sourceTreeUri = SafPemetaanLokasi.keUriPohon(sumberUri)
                    ?: return@withContext HasilOperasi.Gagal(
                        "Gagal menentukan pohon asal dokumen untuk pindah",
                        PenyebabGalat.VALIDASI
                    )
                val sourceParentUri = SafPemetaanLokasi.keUriDokumen(appContext, sourceTreeUri.toString()).let {
                    if (it is HasilOperasi.Sukses) it.nilai else targetParentUri
                }

                val hasilUri = DocumentsContract.moveDocument(
                    contentResolver,
                    sumberUri,
                    sourceParentUri,
                    targetParentUri
                ) ?: return@withContext HasilOperasi.Gagal("Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)

                if (permintaan.namaBaru != null && ValidasiNama.sah(permintaan.namaBaru)) {
                    val renamedUri = DocumentsContract.renameDocument(contentResolver, hasilUri, permintaan.namaBaru)
                    val entitas = bacaSatuEntitas(renamedUri ?: hasilUri)
                    if (entitas != null) return@withContext HasilOperasi.Sukses(entitas)
                }

                val entitas = bacaSatuEntitas(hasilUri)
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca hasil pindah SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin pindah SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: UnsupportedOperationException) {
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung pindah native", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    /**
     * Helper pemeriksaan eksistensi nama sebelum `createDocument` / `bukaUntukTulisDi`.
     */
    suspend fun cariAnakBernama(parentUri: Uri, nama: String): Uri? {
        val childrenUri = runCatching {
            val docId = DocumentsContract.getDocumentId(parentUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
        }.getOrNull() ?: return null

        val sinyalBatal = CancellationSignal()
        val pegangan = currentCoroutineContext()[Job]?.invokeOnCompletion { sinyalBatal.cancel() }
        return try {
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null,
                sinyalBatal
            ) ?: return null

            cursor.use { c ->
                val idxId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val idxNama = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idxId < 0 || idxNama < 0) return null

                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (!c.isNull(idxNama) && c.getString(idxNama) == nama) {
                        val docId = c.getString(idxId)
                        return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            pegangan?.dispose()
        }
    }

    private suspend fun buatEntitas(
        parentLokasi: LokasiBerkas,
        nama: String,
        mimeType: String,
        konfirmasiTimpa: Boolean
    ): HasilOperasi<EntitasBerkas> {
        val parentDocUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, parentLokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }

        // Cek tabrakan nama secara proaktif
        val existingDocUri = cariAnakBernama(parentDocUri, nama)
        if (existingDocUri != null) {
            if (!konfirmasiTimpa) {
                return HasilOperasi.Gagal(
                    "Target sudah ada di folder induk: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            // Hapus yang lama sebelum membuat baru jika konfirmasi diberikan
            try {
                DocumentsContract.deleteDocument(contentResolver, existingDocUri)
            } catch (e: Exception) {
                return HasilOperasi.Gagal("Gagal menimpa entitas yang sudah ada: ${e.message}", PenyebabGalat.IZIN_DITOLAK)
            }
        }

        return try {
            val createdUri = DocumentsContract.createDocument(contentResolver, parentDocUri, mimeType, nama)
                ?: return HasilOperasi.Gagal("Penyedia SAF gagal membuat dokumen baru", PenyebabGalat.TIDAK_DIKETAHUI)

            val entitas = bacaSatuEntitas(createdUri)
                ?: return HasilOperasi.Gagal("Gagal membaca dokumen yang baru dibuat", PenyebabGalat.TIDAK_DITEMUKAN)

            HasilOperasi.Sukses(entitas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin membuat dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: UnsupportedOperationException) {
            HasilOperasi.Gagal("Penyedia SAF tidak mendukung pembuatan dokumen ini", PenyebabGalat.TIDAK_DIDUKUNG)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membuat dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private suspend fun bacaSatuEntitas(docUri: Uri): EntitasBerkas? {
        val sinyalBatal = CancellationSignal()
        val pegangan = currentCoroutineContext()[Job]?.invokeOnCompletion { sinyalBatal.cancel() }
        return try {
            val cursor = contentResolver.query(
                docUri,
                PROYEKSI_SATU_DOKUMEN,
                null,
                null,
                null,
                sinyalBatal
            ) ?: return null

            cursor.use { c ->
                if (!c.moveToFirst()) return null
                val idxId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val idxNama = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val idxUkuran = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val idxWaktu = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val idxFlags = c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

                val docId = if (idxId >= 0 && !c.isNull(idxId)) c.getString(idxId) else docUri.toString()
                val nama = if (idxNama >= 0 && !c.isNull(idxNama)) c.getString(idxNama) else docId
                val mime = if (idxMime >= 0 && !c.isNull(idxMime)) c.getString(idxMime) else null
                val ukuran = if (idxUkuran >= 0 && !c.isNull(idxUkuran)) c.getLong(idxUkuran) else null
                val waktu = if (idxWaktu >= 0 && !c.isNull(idxWaktu)) c.getLong(idxWaktu) else null
                val flags = if (idxFlags >= 0 && !c.isNull(idxFlags)) c.getInt(idxFlags) else 0

                val data = SafPemetaanDokumen.keDataBerkas(
                    docId = docId,
                    uriDokumen = docUri.toString(),
                    namaMentah = nama,
                    mimeType = mime,
                    ukuranMentah = ukuran,
                    waktuMentah = waktu,
                    flags = flags
                )
                data.keDomain()
            }
        } catch (_: Exception) {
            null
        } finally {
            pegangan?.dispose()
        }
    }

    private fun bacaFlags(docUri: Uri): Int {
        return try {
            contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 0
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun tentukanMimeType(nama: String): String {
        val titik = nama.lastIndexOf('.')
        if (titik <= 0 || titik == nama.length - 1) return "application/octet-stream"
        return when (nama.substring(titik + 1).lowercase()) {
            "txt", "md", "csv", "log" -> "text/plain"
            "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html", "htm" -> "text/html"
            "zip" -> "application/zip"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        val PROYEKSI_SATU_DOKUMEN = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )
    }
}