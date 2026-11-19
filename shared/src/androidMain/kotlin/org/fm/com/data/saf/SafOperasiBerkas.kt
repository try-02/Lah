package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
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
            buatEntitas(
                parentLokasi = permintaan.sumber,
                nama = nama,
                mimeType = tentukanMimeType(nama),
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
            val entitasLama = bacaSatuEntitas(docUri)
                ?: return@withContext HasilOperasi.Gagal("Dokumen asal tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            if (entitasLama.nama == namaBaru) {
                return@withContext HasilOperasi.Sukses(entitasLama)
            }
            val flags = bacaFlagsAtauNull(docUri)
            if (flags != null && (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) {
                return@withContext HasilOperasi.Gagal(
                    "Penyedia SAF tidak mendukung ganti nama",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }

            val parentUri = cariIndukLangsung(docUri)
            var konflikSementaraUri: Uri? = null
            val namaCadanganKonflik = ".tmp_timpa_${System.currentTimeMillis()}_$namaBaru"

            if (parentUri != null) {
                val konflikUri = cariAnakBernama(parentUri, namaBaru, ignoreCase = true)
                if (konflikUri != null && konflikUri != docUri) {
                    if (!permintaan.konfirmasi) {
                        return@withContext HasilOperasi.Gagal(
                            "Nama target sudah ada di folder: $namaBaru",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    konflikSementaraUri = try {
                        DocumentsContract.renameDocument(contentResolver, konflikUri, namaCadanganKonflik)
                            ?: return@withContext HasilOperasi.Gagal(
                                "Target dengan nama sama tidak dapat disiapkan untuk ditimpa",
                                PenyebabGalat.KONFLIK
                            )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@withContext HasilOperasi.Gagal(
                            e.message ?: "Gagal mengamankan target konflik sebelum timpa",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                }
            }

            try {
                val hasilUri = DocumentsContract.renameDocument(contentResolver, docUri, namaBaru)
                    ?: run {
                        pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                        return@withContext HasilOperasi.Gagal("Gagal mengganti nama dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                val entitasBaru = bacaSatuEntitas(hasilUri)
                if (entitasBaru == null) {
                    pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                    return@withContext HasilOperasi.Gagal("Gagal membaca dokumen setelah ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                if (entitasBaru.nama != namaBaru && !permintaan.konfirmasi) {
                    runCatching { DocumentsContract.renameDocument(contentResolver, hasilUri, entitasLama.nama) }
                    pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                    return@withContext HasilOperasi.Gagal(
                        "Nama target sudah ada di folder: $namaBaru",
                        PenyebabGalat.KONFLIK
                    )
                }
                if (konflikSementaraUri != null) {
                    runCatching { DocumentsContract.deleteDocument(contentResolver, konflikSementaraUri) }
                }
                HasilOperasi.Sukses(entitasBaru)
            } catch (e: CancellationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                throw e
            } catch (e: SecurityException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                HasilOperasi.Gagal(e.message ?: "Izin ganti nama SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                HasilOperasi.Gagal(e.message ?: "Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: UnsupportedOperationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung ganti nama", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
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
            permintaan.namaBaru?.let {
                if (!ValidasiNama.sah(it)) {
                    return@withContext HasilOperasi.Gagal("Nama baru tidak sah: $it", PenyebabGalat.VALIDASI)
                }
            }
            val sumberUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val targetParentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, tujuanInduk)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val flagsSumber = bacaFlagsAtauNull(sumberUri) ?: 0
            if ((flagsSumber and DocumentsContract.Document.FLAG_SUPPORTS_COPY) == 0) {
                return@withContext HasilOperasi.Gagal(
                    "Penyedia SAF tidak mendukung operasi salin native",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }
            val namaSumber = bacaSatuEntitas(sumberUri)?.nama
            val namaAkhir = permintaan.namaBaru ?: namaSumber
            if (namaAkhir != null) {
                val konflik = cariAnakBernama(targetParentUri, namaAkhir, ignoreCase = true)
                if (konflik != null && !permintaan.konfirmasi) {
                    return@withContext HasilOperasi.Gagal(
                        "Berkas dengan nama sama sudah ada di tujuan: $namaAkhir",
                        PenyebabGalat.KONFLIK
                    )
                }
            }

            try {
                val salinanUri = DocumentsContract.copyDocument(contentResolver, sumberUri, targetParentUri)
                    ?: return@withContext HasilOperasi.Gagal("Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                val uriAkhir = if (permintaan.namaBaru != null && permintaan.namaBaru != namaSumber) {
                    DocumentsContract.renameDocument(contentResolver, salinanUri, permintaan.namaBaru) ?: run {
                        runCatching { DocumentsContract.deleteDocument(contentResolver, salinanUri) }
                        return@withContext HasilOperasi.Gagal("Gagal menamai ulang hasil salin SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                } else {
                    salinanUri
                }
                val entitas = bacaSatuEntitas(uriAkhir)
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca hasil salin SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin salin SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                HasilOperasi.Gagal(e.message ?: "Dokumen asal/tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
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
            permintaan.namaBaru?.let {
                if (!ValidasiNama.sah(it)) {
                    return@withContext HasilOperasi.Gagal("Nama baru tidak sah: $it", PenyebabGalat.VALIDASI)
                }
            }
            val sumberUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val targetParentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, tujuanInduk)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val flagsSumber = bacaFlagsAtauNull(sumberUri) ?: 0
            if ((flagsSumber and DocumentsContract.Document.FLAG_SUPPORTS_MOVE) == 0) {
                return@withContext HasilOperasi.Gagal(
                    "Penyedia SAF tidak mendukung operasi pindah native",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }
            val sourceParentUri = cariIndukLangsung(sumberUri)
                ?: return@withContext HasilOperasi.Gagal(
                    "Induk langsung dokumen SAF tidak dapat ditentukan untuk pindah native",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            if (sourceParentUri == targetParentUri && permintaan.namaBaru == null) {
                val entitasSekarang = bacaSatuEntitas(sumberUri)
                    ?: return@withContext HasilOperasi.Gagal("Dokumen asal tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                return@withContext HasilOperasi.Sukses(entitasSekarang)
            }

            val namaSumber = bacaSatuEntitas(sumberUri)?.nama
            val namaAkhir = permintaan.namaBaru ?: namaSumber
            if (namaAkhir != null) {
                val konflik = cariAnakBernama(targetParentUri, namaAkhir, ignoreCase = true)
                if (konflik != null && konflik != sumberUri && !permintaan.konfirmasi) {
                    return@withContext HasilOperasi.Gagal(
                        "Berkas dengan nama sama sudah ada di tujuan: $namaAkhir",
                        PenyebabGalat.KONFLIK
                    )
                }
            }

            try {
                val hasilUri = DocumentsContract.moveDocument(
                    contentResolver,
                    sumberUri,
                    sourceParentUri,
                    targetParentUri
                ) ?: return@withContext HasilOperasi.Gagal("Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                val uriAkhir = if (permintaan.namaBaru != null && permintaan.namaBaru != namaSumber) {
                    DocumentsContract.renameDocument(contentResolver, hasilUri, permintaan.namaBaru) ?: hasilUri
                } else {
                    hasilUri
                }
                val entitas = bacaSatuEntitas(uriAkhir)
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca hasil pindah SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin pindah SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                HasilOperasi.Gagal(e.message ?: "Dokumen asal/tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: UnsupportedOperationException) {
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung pindah native", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    suspend fun cariAnakBernama(
        parentUri: Uri,
        nama: String,
        ignoreCase: Boolean = true
    ): Uri? {
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
                var kandidatInsensitive: Uri? = null
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (c.isNull(idxNama)) continue
                    val namaAnak = c.getString(idxNama)
                    val docId = c.getString(idxId)
                    if (namaAnak == nama) {
                        return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                    }
                    if (ignoreCase && kandidatInsensitive == null && namaAnak.equals(nama, ignoreCase = true)) {
                        kandidatInsensitive = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                    }
                }
                kandidatInsensitive
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            pegangan?.dispose()
        }
    }

    internal suspend fun bacaSatuEntitas(docUri: Uri): EntitasBerkas? {
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            pegangan?.dispose()
        }
    }

    private suspend fun cariIndukLangsung(docUri: Uri): Uri? {
        currentCoroutineContext().ensureActive()
        if (!DocumentsContract.isTreeUri(docUri)) return null
        return try {
            val docId = DocumentsContract.getDocumentId(docUri) ?: return null
            val pathObj = DocumentsContract.findDocumentPath(contentResolver, docUri) ?: return null
            val daftarId = pathObj.path
            val indeksTarget = daftarId.lastIndexOf(docId)
            val indeksInduk = when {
                indeksTarget > 0 -> indeksTarget - 1
                daftarId.size >= 2 -> daftarId.size - 2
                else -> -1
            }
            if (indeksInduk < 0) return null
            val parentDocId = daftarId[indeksInduk]
            if (parentDocId.isBlank() || parentDocId == docId) return null
            DocumentsContract.buildDocumentUriUsingTree(docUri, parentDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
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
        val flagsInduk = bacaFlagsAtauNull(parentDocUri)
        if (flagsInduk != null && (flagsInduk and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
            return HasilOperasi.Gagal(
                "Folder tujuan SAF tidak mendukung pembuatan item baru",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val existingDocUri = cariAnakBernama(parentDocUri, nama, ignoreCase = true)
        if (existingDocUri != null) {
            if (!konfirmasiTimpa) {
                return HasilOperasi.Gagal(
                    "Target sudah ada di folder induk: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            try {
                DocumentsContract.deleteDocument(contentResolver, existingDocUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                return HasilOperasi.Gagal("Izin menimpa entitas yang sudah ada ditolak: ${e.message}", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: Exception) {
                return HasilOperasi.Gagal("Gagal menimpa entitas yang sudah ada: ${e.message}", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
        return try {
            val createdUri = DocumentsContract.createDocument(contentResolver, parentDocUri, mimeType, nama)
                ?: return HasilOperasi.Gagal("Penyedia SAF gagal membuat dokumen baru", PenyebabGalat.TIDAK_DIKETAHUI)
            val entitas = bacaSatuEntitas(createdUri)
                ?: return HasilOperasi.Gagal("Gagal membaca dokumen yang baru dibuat", PenyebabGalat.TIDAK_DITEMUKAN)
            if (entitas.nama != nama && !konfirmasiTimpa) {
                runCatching { DocumentsContract.deleteDocument(contentResolver, createdUri) }
                return HasilOperasi.Gagal("Nama target bentrok dengan item yang sudah ada: $nama", PenyebabGalat.KONFLIK)
            }
            HasilOperasi.Sukses(entitas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin membuat dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            HasilOperasi.Gagal(e.message ?: "Folder induk SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: UnsupportedOperationException) {
            HasilOperasi.Gagal("Penyedia SAF tidak mendukung pembuatan dokumen ini", PenyebabGalat.TIDAK_DIDUKUNG)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membuat dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private fun pulihkanNamaCadangan(cadanganUri: Uri?, namaAsli: String) {
        if (cadanganUri == null) return
        runCatching { DocumentsContract.renameDocument(contentResolver, cadanganUri, namaAsli) }
    }

    private fun bacaFlagsAtauNull(docUri: Uri): Int? {
        return try {
            contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    internal companion object {
        val PROYEKSI_SATU_DOKUMEN = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )

        internal fun tentukanMimeType(nama: String): String {
            val titik = nama.lastIndexOf('.')
            if (titik <= 0 || titik == nama.length - 1) return "application/octet-stream"
            return when (nama.substring(titik + 1).lowercase()) {
                "txt", "md", "markdown", "csv", "log", "ini", "toml", "yaml", "yml", "properties",
                "kt", "kts", "java", "swift", "py", "js", "ts", "c", "cpp", "h", "hpp",
                "sh", "gradle", "sql", "rs", "go", "rb", "php", "dart", "cs" -> "text/plain"
                "json" -> "application/json"
                "xml" -> "text/xml"
                "html", "htm" -> "text/html"
                "css" -> "text/css"
                "zip" -> "application/zip"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "application/octet-stream"
            }
        }
    }
}