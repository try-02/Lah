package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

/**
 * Pemetaan dua arah antara [Uri] SAF dan [LokasiBerkas] murni melalui API
 * [DocumentsContract] tanpa manipulasi string URI dan tanpa merekayasa path POSIX.
 */
object SafPemetaanLokasi {

    /**
     * Mengubah [lokasi] menjadi [Uri] dokumen yang siap di-query via [ContentResolver].
     * Menerima baik raw tree URI (`/tree/<id>`) maupun document URI dalam tree
     * (`/tree/<treeId>/document/<docId>`).
     */
    fun keUriDokumen(context: Context, lokasi: LokasiBerkas): HasilOperasi<Uri> {
        if (lokasi.jenis != JenisLokasi.SAF) {
            return HasilOperasi.Gagal(
                "Lokasi bukan bertipe SAF: ${lokasi.jenis}",
                PenyebabGalat.VALIDASI
            )
        }
        return keUriDokumen(context, lokasi.pengenal)
    }

    /** Memvalidasi pengenal string URI SAF menjadi [Uri] dokumen. */
    fun keUriDokumen(context: Context, pengenalUri: String): HasilOperasi<Uri> {
        if (pengenalUri.isBlank()) {
            return HasilOperasi.Gagal("Pengenal URI SAF kosong", PenyebabGalat.VALIDASI)
        }
        return try {
            val uri = pengenalUri.toUri()
            if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank()) {
                return HasilOperasi.Gagal(
                    "Pengenal bukan URI content SAF yang sah",
                    PenyebabGalat.VALIDASI
                )
            }
            when {
                DocumentsContract.isDocumentUri(context, uri) -> HasilOperasi.Sukses(uri)
                DocumentsContract.isTreeUri(uri) -> {
                    // URI anak/dokumen yang sudah memiliki segmen /document/ tetap sah
                    // meski visibilitas paket DocumentsProvider pihak ketiga dibatasi.
                    val sudahDokumen = runCatching { DocumentsContract.getDocumentId(uri) }
                        .getOrNull()
                        ?.isNotBlank() == true
                    if (sudahDokumen) {
                        HasilOperasi.Sukses(uri)
                    } else {
                        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
                        if (treeDocId.isNullOrBlank()) {
                            HasilOperasi.Gagal("Tree document ID SAF kosong", PenyebabGalat.VALIDASI)
                        } else {
                            HasilOperasi.Sukses(
                                DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
                            )
                        }
                    }
                }
                else -> HasilOperasi.Gagal(
                    "URI bukan dokumen atau pohon SAF yang sah",
                    PenyebabGalat.VALIDASI
                )
            }
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin akses URI SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: IllegalArgumentException) {
            HasilOperasi.Gagal(e.message ?: "Format URI SAF tidak valid", PenyebabGalat.VALIDASI)
        }
    }

    /**
     * Membangun URI anak langsung (`buildChildDocumentsUriUsingTree`) untuk folder
     * dalam cakupan tree SAF. Menolak single document URI yang tidak memiliki akses pohon.
     */
    fun keUriAnak(context: Context, lokasi: LokasiBerkas): HasilOperasi<Uri> {
        val docUri = when (val hasil = keUriDokumen(context, lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        return try {
            if (!DocumentsContract.isTreeUri(docUri)) {
                return HasilOperasi.Gagal(
                    "Lokasi SAF tidak memiliki akses pohon direktori (tree URI)",
                    PenyebabGalat.VALIDASI
                )
            }
            val docId = DocumentsContract.getDocumentId(docUri)
            if (docId.isNullOrBlank()) {
                return HasilOperasi.Gagal("Document ID SAF kosong", PenyebabGalat.VALIDASI)
            }
            HasilOperasi.Sukses(DocumentsContract.buildChildDocumentsUriUsingTree(docUri, docId))
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin akses URI SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: IllegalArgumentException) {
            HasilOperasi.Gagal(e.message ?: "Format URI SAF tidak valid", PenyebabGalat.VALIDASI)
        }
    }

    /**
     * Mengembalikan raw tree URI (`/tree/<treeDocId>`) untuk pencocokan atau pelepasan
     * `persistedUriPermissions` pada [ContentResolver].
     */
    fun keUriPohon(uri: Uri): Uri? {
        if (!DocumentsContract.isTreeUri(uri)) return null
        val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
    }

    /** Memetakan [Uri] SAF yang sudah dinormalkan ke [LokasiBerkas]. */
    fun keLokasi(uriDokumen: Uri, label: String? = null): HasilOperasi<LokasiBerkas> {
        if (uriDokumen.scheme != ContentResolver.SCHEME_CONTENT || uriDokumen.authority.isNullOrBlank()) {
            return HasilOperasi.Gagal(
                "URI bukan content SAF yang sah",
                PenyebabGalat.VALIDASI
            )
        }
        return HasilOperasi.Sukses(
            LokasiBerkas(
                pengenal = uriDokumen.toString(),
                jenis = JenisLokasi.SAF,
                label = label?.takeIf { it.isNotBlank() }
            )
        )
    }
}