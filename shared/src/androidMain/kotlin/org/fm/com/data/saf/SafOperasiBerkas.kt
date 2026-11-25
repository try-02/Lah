package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PelaporProgresOperasi
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

private const val UKURAN_BUFFER_SALIN_SAF = 32 * 1024
private const val BATAS_BFS_FOLDER_SAF = 250
private const val BATAS_KEDALAMAN_REKURSIF_SAF = 32
private const val BATAS_ITEM_FALLBACK_SAF = 5_000
private const val BATAS_ITEM_HAPUS_SAF = 10_000

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
            if (apakahUriRootTree(docUri)) {
                return@withContext HasilOperasi.Gagal(
                    "Folder root penyimpanan SAF tidak dapat diganti nama",
                    PenyebabGalat.VALIDASI
                )
            }

            val flags = bacaFlagsAtauNull(docUri)
            if (flags != null && (flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) {
                return@withContext gantiNamaViaSalinDanHapus(
                    docUri = docUri,
                    entitasLama = entitasLama,
                    namaBaru = namaBaru,
                    konfirmasi = permintaan.konfirmasi,
                    hintInduk = permintaan.tujuan
                )
            }

            val parentUri = cariIndukLangsung(docUri, permintaan.tujuan)
            var konflikSementaraUri: Uri? = null
            val namaCadanganKonflik = ".tmp_timpa_${System.currentTimeMillis()}_$namaBaru"

            if (parentUri != null) {
                val konflikUri = cariAnakBernama(parentUri, namaBaru, ignoreCase = true)
                if (konflikUri != null && !apakahDokumenSama(konflikUri, docUri)) {
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
            } catch (_: UnsupportedOperationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                gantiNamaViaSalinDanHapus(
                    docUri = docUri,
                    entitasLama = entitasLama,
                    namaBaru = namaBaru,
                    konfirmasi = permintaan.konfirmasi,
                    hintInduk = permintaan.tujuan
                )
            } catch (e: SecurityException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                HasilOperasi.Gagal(e.message ?: "Izin ganti nama SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaBaru)
                HasilOperasi.Gagal(e.message ?: "Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
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
                    cobaHapusFolderRekursifJikaPerlu(
                        docUri = docUri,
                        gagalBawaan = HasilOperasi.Gagal("Gagal menghapus dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                HasilOperasi.Gagal(e.message ?: "Izin hapus SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                cobaHapusFolderRekursifJikaPerlu(
                    docUri = docUri,
                    gagalBawaan = HasilOperasi.Gagal("Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                )
            } catch (e: IllegalStateException) {
                cobaHapusFolderRekursifJikaPerlu(
                    docUri = docUri,
                    gagalBawaan = HasilOperasi.Gagal(e.message ?: "Gagal menghapus dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                )
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
            var konflikSementaraUri: Uri? = null
            if (namaAkhir != null) {
                val konflik = cariAnakBernama(targetParentUri, namaAkhir, ignoreCase = true)
                if (konflik != null) {
                    if (apakahDokumenSama(konflik, sumberUri)) {
                        return@withContext HasilOperasi.Gagal(
                            "Berkas sudah berada di folder yang sama: $namaAkhir",
                            PenyebabGalat.VALIDASI
                        )
                    }
                    if (!permintaan.konfirmasi) {
                        return@withContext HasilOperasi.Gagal(
                            "Berkas dengan nama sama sudah ada di tujuan: $namaAkhir",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    val namaCadangan = ".tmp_timpa_${System.currentTimeMillis()}_$namaAkhir"
                    konflikSementaraUri = try {
                        DocumentsContract.renameDocument(contentResolver, konflik, namaCadangan)
                            ?: return@withContext HasilOperasi.Gagal(
                                "Target dengan nama sama tidak dapat disiapkan untuk ditimpa",
                                PenyebabGalat.KONFLIK
                            )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@withContext HasilOperasi.Gagal(
                            e.message ?: "Gagal menyiapkan target untuk ditimpa",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                }
            }

            try {
                val salinanUri = DocumentsContract.copyDocument(contentResolver, sumberUri, targetParentUri)
                    ?: run {
                        pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                        return@withContext HasilOperasi.Gagal("Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                val uriAkhir = if (permintaan.namaBaru != null && permintaan.namaBaru != namaSumber) {
                    DocumentsContract.renameDocument(contentResolver, salinanUri, permintaan.namaBaru) ?: run {
                        runCatching { DocumentsContract.deleteDocument(contentResolver, salinanUri) }
                        pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                        return@withContext HasilOperasi.Gagal("Gagal menamai ulang hasil salin SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                } else {
                    salinanUri
                }
                val entitas = bacaSatuEntitas(uriAkhir) ?: run {
                    pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                    return@withContext HasilOperasi.Gagal("Gagal membaca hasil salin SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                if (konflikSementaraUri != null) {
                    runCatching { DocumentsContract.deleteDocument(contentResolver, konflikSementaraUri) }
                }
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                throw e
            } catch (e: SecurityException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal(e.message ?: "Izin salin SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal(e.message ?: "Dokumen asal/tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: UnsupportedOperationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung salin dokumen", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
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
            if (apakahDokumenSama(sourceParentUri, targetParentUri) && permintaan.namaBaru == null) {
                val entitasSekarang = bacaSatuEntitas(sumberUri)
                    ?: return@withContext HasilOperasi.Gagal("Dokumen asal tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                return@withContext HasilOperasi.Sukses(entitasSekarang)
            }

            val namaSumber = bacaSatuEntitas(sumberUri)?.nama
            val namaAkhir = permintaan.namaBaru ?: namaSumber
            var konflikSementaraUri: Uri? = null
            if (namaAkhir != null) {
                val konflik = cariAnakBernama(targetParentUri, namaAkhir, ignoreCase = true)
                if (konflik != null && !apakahDokumenSama(konflik, sumberUri)) {
                    if (!permintaan.konfirmasi) {
                        return@withContext HasilOperasi.Gagal(
                            "Berkas dengan nama sama sudah ada di tujuan: $namaAkhir",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    val namaCadangan = ".tmp_timpa_${System.currentTimeMillis()}_$namaAkhir"
                    konflikSementaraUri = try {
                        DocumentsContract.renameDocument(contentResolver, konflik, namaCadangan)
                            ?: return@withContext HasilOperasi.Gagal(
                                "Target dengan nama sama tidak dapat disiapkan untuk ditimpa",
                                PenyebabGalat.KONFLIK
                            )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        return@withContext HasilOperasi.Gagal(
                            e.message ?: "Gagal menyiapkan target untuk ditimpa",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                }
            }

            try {
                val hasilUri = DocumentsContract.moveDocument(
                    contentResolver,
                    sumberUri,
                    sourceParentUri,
                    targetParentUri
                ) ?: run {
                    pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                    return@withContext HasilOperasi.Gagal("Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                }
                val uriAkhir = if (permintaan.namaBaru != null && permintaan.namaBaru != namaSumber) {
                    DocumentsContract.renameDocument(contentResolver, hasilUri, permintaan.namaBaru) ?: run {
                        pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                        return@withContext HasilOperasi.Gagal("Gagal menamai ulang hasil pindah SAF", PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                } else {
                    hasilUri
                }
                val entitas = bacaSatuEntitas(uriAkhir) ?: run {
                    pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                    return@withContext HasilOperasi.Gagal("Gagal membaca hasil pindah SAF", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                if (konflikSementaraUri != null) {
                    runCatching { DocumentsContract.deleteDocument(contentResolver, konflikSementaraUri) }
                }
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                throw e
            } catch (e: SecurityException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal(e.message ?: "Izin pindah SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal(e.message ?: "Dokumen asal/tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: UnsupportedOperationException) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal("Penyedia SAF tidak mendukung pindah native", PenyebabGalat.TIDAK_DIDUKUNG)
            } catch (e: Exception) {
                pulihkanNamaCadangan(konflikSementaraUri, namaAkhir ?: "")
                HasilOperasi.Gagal(e.message ?: "Gagal memindahkan dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    // ── Fallback Aman untuk Provider SAF Minimalis (mis. TermuxDocumentsProvider) ──

    private suspend fun gantiNamaViaSalinDanHapus(
        docUri: Uri,
        entitasLama: EntitasBerkas,
        namaBaru: String,
        konfirmasi: Boolean,
        hintInduk: LokasiBerkas?
    ): HasilOperasi<EntitasBerkas> {
        val parentUri = cariIndukLangsung(docUri, hintInduk)
            ?: return HasilOperasi.Gagal(
                "Folder induk dokumen tidak dapat ditentukan untuk ganti nama",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val flagsInduk = bacaFlagsAtauNull(parentUri)
        if (flagsInduk != null && (flagsInduk and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
            return HasilOperasi.Gagal(
                "Folder induk tidak mengizinkan pembuatan berkas untuk ganti nama",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val flagsDoc = bacaFlagsAtauNull(docUri)
        if (flagsDoc != null && (flagsDoc and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == 0) {
            return HasilOperasi.Gagal(
                "Dokumen tidak mengizinkan penghapusan setelah disalin untuk ganti nama",
                PenyebabGalat.IZIN_DITOLAK
            )
        }

        val pelapor = currentCoroutineContext()[PelaporProgresOperasi]
        val adalahFolder = entitasLama.jenis == JenisBerkas.FOLDER
        val konflikUri = cariAnakBernama(parentUri, namaBaru, ignoreCase = true)
        val caseOnly = konflikUri != null && apakahDokumenSama(konflikUri, docUri)

        if (konflikUri != null && !caseOnly) {
            if (!konfirmasi) {
                return HasilOperasi.Gagal(
                    "Nama target sudah ada di folder: $namaBaru",
                    PenyebabGalat.KONFLIK
                )
            }
            val entitasKonflik = bacaSatuEntitas(konflikUri)
            if (adalahFolder || entitasKonflik?.jenis == JenisBerkas.FOLDER) {
                return HasilOperasi.Gagal(
                    "Penimpaan folder saat ganti nama tidak didukung di penyedia SAF ini",
                    PenyebabGalat.KONFLIK
                )
            }
            return gantiNamaBerkasDenganTimpaKonflik(
                docUri = docUri,
                parentUri = parentUri,
                konflikUri = konflikUri,
                namaBaru = namaBaru
            )
        }

        if (adalahFolder) {
            val pohon = when (val pindai = kumpulkanPohonSaf(docUri, namaBaru, BATAS_ITEM_FALLBACK_SAF, pelapor)) {
                is HasilOperasi.Sukses -> pindai.nilai
                is HasilOperasi.Gagal -> return pindai
            }
            if (caseOnly) {
                val namaTemp = ".fm_rn_${Random.nextInt(0x100000, 0xFFFFFF).toString(16)}_$namaBaru"
                val tempResult = when (val salin = salinPohonSafTerpindai(pohon, parentUri, namaTemp, pelapor)) {
                    is HasilOperasi.Sukses -> salin.nilai
                    is HasilOperasi.Gagal -> return salin
                }
                val hapusAsal = hapusPohonDariDaftar(pohon)
                if (hapusAsal is HasilOperasi.Gagal) {
                    withContext(NonCancellable) { hapusHasilSalinPohon(tempResult) }
                    return hapusAsal
                }
                val renameNativeTemp = runCatching {
                    DocumentsContract.renameDocument(contentResolver, tempResult.rootTujuanUri, namaBaru)
                }.getOrNull()
                val uriFinal = if (renameNativeTemp != null) {
                    renameNativeTemp
                } else {
                    val pohonTemp = PohonSaf(
                        daftarFolder = pohon.daftarFolder.mapIndexed { idx, f ->
                            f.copy(uri = tempResult.folderTujuanUris[idx])
                        },
                        daftarBerkas = pohon.daftarBerkas.mapIndexed { idx, b ->
                            b.copy(uri = tempResult.berkasTujuanUris[idx])
                        }
                    )
                    when (val salinFinal = salinPohonSafTerpindai(pohonTemp, parentUri, namaBaru, pelapor)) {
                        is HasilOperasi.Sukses -> {
                            withContext(NonCancellable) { hapusHasilSalinPohon(tempResult) }
                            salinFinal.nilai.rootTujuanUri
                        }
                        is HasilOperasi.Gagal -> {
                            withContext(NonCancellable) {
                                val pulih = salinPohonSafTerpindai(pohonTemp, parentUri, entitasLama.nama, pelapor = null)
                                if (pulih is HasilOperasi.Sukses) hapusHasilSalinPohon(tempResult)
                            }
                            return salinFinal
                        }
                    }
                }
                val entitasBaru = bacaSatuEntitas(uriFinal)
                    ?: return HasilOperasi.Gagal("Gagal membaca folder hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                return HasilOperasi.Sukses(entitasBaru)
            }

            val hasilSalin = when (val salin = salinPohonSafTerpindai(pohon, parentUri, namaBaru, pelapor)) {
                is HasilOperasi.Sukses -> salin.nilai
                is HasilOperasi.Gagal -> return salin
            }
            val hapusAsal = hapusPohonDariDaftar(pohon)
            if (hapusAsal is HasilOperasi.Gagal) {
                return HasilOperasi.Gagal(
                    "Folder '$namaBaru' sudah disalin, tetapi gagal menghapus folder asal: ${hapusAsal.pesan}",
                    hapusAsal.penyebab
                )
            }
            val entitasBaru = bacaSatuEntitas(hasilSalin.rootTujuanUri)
                ?: return HasilOperasi.Gagal("Gagal membaca folder hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
            return HasilOperasi.Sukses(entitasBaru)
        }

        if (caseOnly) {
            val namaTemp = ".fm_rn_${Random.nextInt(0x100000, 0xFFFFFF).toString(16)}_$namaBaru"
            val tempUri = when (val buatTemp = salinBerkasTunggalKeNama(docUri, parentUri, namaTemp)) {
                is HasilOperasi.Sukses -> buatTemp.nilai
                is HasilOperasi.Gagal -> return buatTemp
            }
            val hapusAsal = runCatching { DocumentsContract.deleteDocument(contentResolver, docUri) }.getOrDefault(false)
            if (!hapusAsal) {
                withContext(NonCancellable) { hapusAman(tempUri) }
                return HasilOperasi.Gagal("Gagal menghapus berkas asal saat ganti nama", PenyebabGalat.IZIN_DITOLAK)
            }
            val hasilFinal = salinBerkasTunggalKeNama(tempUri, parentUri, namaBaru)
            return when (hasilFinal) {
                is HasilOperasi.Sukses -> {
                    withContext(NonCancellable) { hapusAman(tempUri) }
                    val entitasBaru = bacaSatuEntitas(hasilFinal.nilai)
                        ?: return HasilOperasi.Gagal("Gagal membaca berkas hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                    HasilOperasi.Sukses(entitasBaru)
                }
                is HasilOperasi.Gagal -> {
                    withContext(NonCancellable) {
                        val pulih = salinBerkasTunggalKeNama(tempUri, parentUri, entitasLama.nama)
                        if (pulih is HasilOperasi.Sukses) hapusAman(tempUri)
                    }
                    hasilFinal
                }
            }
        }

        val targetBaruUri = when (val salin = salinBerkasTunggalKeNama(docUri, parentUri, namaBaru)) {
            is HasilOperasi.Sukses -> salin.nilai
            is HasilOperasi.Gagal -> return salin
        }
        val terhapus = runCatching { DocumentsContract.deleteDocument(contentResolver, docUri) }.getOrDefault(false)
        if (!terhapus) {
            return HasilOperasi.Gagal(
                "Salinan '$namaBaru' sudah dibuat, tetapi gagal menghapus berkas asal",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val entitasBaru = bacaSatuEntitas(targetBaruUri)
            ?: return HasilOperasi.Gagal("Gagal membaca berkas hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
        return HasilOperasi.Sukses(entitasBaru)
    }

    private suspend fun gantiNamaBerkasDenganTimpaKonflik(
        docUri: Uri,
        parentUri: Uri,
        konflikUri: Uri,
        namaBaru: String
    ): HasilOperasi<EntitasBerkas> {
        val namaTemp = ".fm_rn_${Random.nextInt(0x100000, 0xFFFFFF).toString(16)}_$namaBaru"
        val tempUri = when (val resTemp = salinBerkasTunggalKeNama(docUri, parentUri, namaTemp)) {
            is HasilOperasi.Sukses -> resTemp.nilai
            is HasilOperasi.Gagal -> return resTemp
        }

        val cadanganLokal = File.createTempFile("saf_konflik_", ".tmp", appContext.cacheDir)
        var cadanganSiap = false
        try {
            cadanganSiap = salinUriKeFileLokal(konflikUri, cadanganLokal)
            if (!cadanganSiap) {
                withContext(NonCancellable) { hapusAman(tempUri) }
                return HasilOperasi.Gagal("Gagal mencadangkan berkas target sebelum menimpa", PenyebabGalat.IZIN_DITOLAK)
            }

            val terhapusKonflik = runCatching {
                DocumentsContract.deleteDocument(contentResolver, konflikUri)
            }.getOrDefault(false)
            if (!terhapusKonflik) {
                withContext(NonCancellable) { hapusAman(tempUri) }
                return HasilOperasi.Gagal("Gagal menghapus berkas target konflik sebelum menimpa", PenyebabGalat.IZIN_DITOLAK)
            }

            val hasilFinal = salinBerkasTunggalKeNama(tempUri, parentUri, namaBaru)
            return when (hasilFinal) {
                is HasilOperasi.Sukses -> {
                    withContext(NonCancellable) {
                        hapusAman(tempUri)
                        runCatching { DocumentsContract.deleteDocument(contentResolver, docUri) }
                    }
                    val entitasBaru = bacaSatuEntitas(hasilFinal.nilai)
                        ?: return HasilOperasi.Gagal("Gagal membaca berkas hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                    HasilOperasi.Sukses(entitasBaru)
                }
                is HasilOperasi.Gagal -> {
                    withContext(NonCancellable) {
                        pulihkanFileLokalKeFolderSaf(cadanganLokal, parentUri, namaBaru)
                        hapusAman(tempUri)
                    }
                    hasilFinal
                }
            }
        } finally {
            runCatching { cadanganLokal.delete() }
        }
    }

    private suspend fun salinBerkasTunggalKeNama(
        sumberUri: Uri,
        parentUri: Uri,
        namaBaru: String
    ): HasilOperasi<Uri> {
        val mime = tentukanMimeType(namaBaru)
        val createdUri = try {
            DocumentsContract.createDocument(contentResolver, parentUri, mime, namaBaru)
                ?: return HasilOperasi.Gagal("Gagal membuat berkas '$namaBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin buat berkas ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat berkas '$namaBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        val buffer = ByteArray(UKURAN_BUFFER_SALIN_SAF)
        return when (val salin = salinDanVerifikasiStreamSaf(sumberUri, createdUri, buffer, CRC32(), CRC32())) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(createdUri)
            is HasilOperasi.Gagal -> {
                withContext(NonCancellable) { hapusAman(createdUri) }
                salin
            }
        }
    }

    private data class EntriFolderPohon(
        val uri: Uri,
        val docId: String,
        val nama: String,
        val indeksFolderInduk: Int
    )

    private data class EntriBerkasPohon(
        val uri: Uri,
        val docId: String,
        val nama: String,
        val indeksFolderInduk: Int
    )

    private data class PohonSaf(
        val daftarFolder: List<EntriFolderPohon>,
        val daftarBerkas: List<EntriBerkasPohon>
    ) {
        val totalItemOperasi: Int get() = daftarFolder.size + daftarBerkas.size
    }

    private data class HasilSalinPohon(
        val rootTujuanUri: Uri,
        val folderTujuanUris: List<Uri>,
        val berkasTujuanUris: List<Uri>
    )

    private suspend fun kumpulkanPohonSaf(
        rootFolderUri: Uri,
        namaRootTampilan: String,
        batasTotalItem: Int,
        pelapor: PelaporProgresOperasi?
    ): HasilOperasi<PohonSaf> {
        val rootDocId = runCatching { DocumentsContract.getDocumentId(rootFolderUri) }.getOrNull()
            ?: rootFolderUri.toString()
        val daftarFolder = ArrayList<EntriFolderPohon>(32)
        val daftarBerkas = ArrayList<EntriBerkasPohon>(64)
        val dikunjungi = HashSet<String>(128)
        val antrean = ArrayDeque<Pair<Int, Int>>()

        dikunjungi.add(rootDocId)
        daftarFolder.add(EntriFolderPohon(rootFolderUri, rootDocId, namaRootTampilan, indeksFolderInduk = -1))
        antrean.addLast(0 to 0)

        while (antrean.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (idxInduk, kedalaman) = antrean.removeFirst()
            if (kedalaman >= BATAS_KEDALAMAN_REKURSIF_SAF) {
                return HasilOperasi.Gagal(
                    "Kedalaman folder melebihi batas aman ($BATAS_KEDALAMAN_REKURSIF_SAF tingkat)",
                    PenyebabGalat.VALIDASI
                )
            }
            pelapor?.onProgres(
                0,
                0,
                "Memindai ${daftarFolder.size} folder, ${daftarBerkas.size} berkas..."
            )

            val anakList = when (val res = daftarAnakSingkat(daftarFolder[idxInduk].uri)) {
                is HasilOperasi.Sukses -> res.nilai
                is HasilOperasi.Gagal -> return res
            }

            for (anak in anakList) {
                currentCoroutineContext().ensureActive()
                if (!dikunjungi.add(anak.docId)) continue
                if (apakahJalurBerulang(anak.docId)) {
                    return HasilOperasi.Gagal(
                        "Terdeteksi potensi siklus tautan simbolik pada '${anak.nama}'",
                        PenyebabGalat.VALIDASI
                    )
                }
                val totalSekarang = daftarFolder.size + daftarBerkas.size
                if (totalSekarang >= batasTotalItem) {
                    return HasilOperasi.Gagal(
                        "Folder berisi lebih dari $batasTotalItem item; operasi fallback SAF dibatalkan agar tidak membebani sistem",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                }
                if (anak.adalahFolder) {
                    val idxBaru = daftarFolder.size
                    daftarFolder.add(EntriFolderPohon(anak.uri, anak.docId, anak.nama, indeksFolderInduk = idxInduk))
                    antrean.addLast(idxBaru to (kedalaman + 1))
                } else {
                    daftarBerkas.add(EntriBerkasPohon(anak.uri, anak.docId, anak.nama, indeksFolderInduk = idxInduk))
                }
            }
        }
        return HasilOperasi.Sukses(PohonSaf(daftarFolder, daftarBerkas))
    }

    private suspend fun salinPohonSafTerpindai(
        pohon: PohonSaf,
        parentTujuanUri: Uri,
        namaFolderBaru: String,
        pelapor: PelaporProgresOperasi?
    ): HasilOperasi<HasilSalinPohon> {
        val folderTujuanUris = ArrayList<Uri>(pohon.daftarFolder.size)
        val berkasTujuanUris = ArrayList<Uri>(pohon.daftarBerkas.size)
        val totalOps = pohon.totalItemOperasi
        var diproses = 0
        val buffer = ByteArray(UKURAN_BUFFER_SALIN_SAF)
        val crcSumber = CRC32()
        val crcAktual = CRC32()

        fun bersihkanSebagian() {
            for (b in berkasTujuanUris.asReversed()) hapusAman(b)
            for (f in folderTujuanUris.asReversed()) hapusAman(f)
        }

        try {
            for (idx in pohon.daftarFolder.indices) {
                currentCoroutineContext().ensureActive()
                val f = pohon.daftarFolder[idx]
                val parentTarget = if (f.indeksFolderInduk < 0) {
                    parentTujuanUri
                } else {
                    folderTujuanUris[f.indeksFolderInduk]
                }
                val namaBuat = if (idx == 0) namaFolderBaru else f.nama
                pelapor?.onProgres(++diproses, totalOps, namaBuat)

                val dirBaru = DocumentsContract.createDocument(
                    contentResolver,
                    parentTarget,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    namaBuat
                ) ?: run {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return HasilOperasi.Gagal("Gagal membuat folder '$namaBuat'", PenyebabGalat.TIDAK_DIKETAHUI)
                }
                folderTujuanUris.add(dirBaru)
            }

            for (berkas in pohon.daftarBerkas) {
                currentCoroutineContext().ensureActive()
                val parentTarget = folderTujuanUris[berkas.indeksFolderInduk]
                pelapor?.onProgres(++diproses, totalOps, berkas.nama)

                val berkasBaru = DocumentsContract.createDocument(
                    contentResolver,
                    parentTarget,
                    tentukanMimeType(berkas.nama),
                    berkas.nama
                ) ?: run {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return HasilOperasi.Gagal("Gagal membuat berkas '${berkas.nama}'", PenyebabGalat.TIDAK_DIKETAHUI)
                }
                berkasTujuanUris.add(berkasBaru)

                val salin = salinDanVerifikasiStreamSaf(berkas.uri, berkasBaru, buffer, crcSumber, crcAktual)
                if (salin is HasilOperasi.Gagal) {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return salin
                }
            }

            return HasilOperasi.Sukses(
                HasilSalinPohon(
                    rootTujuanUri = folderTujuanUris.first(),
                    folderTujuanUris = folderTujuanUris,
                    berkasTujuanUris = berkasTujuanUris
                )
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) { bersihkanSebagian() }
            throw e
        } catch (e: SecurityException) {
            withContext(NonCancellable) { bersihkanSebagian() }
            return HasilOperasi.Gagal(e.message ?: "Izin menyalin folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            withContext(NonCancellable) { bersihkanSebagian() }
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private fun hapusHasilSalinPohon(hasil: HasilSalinPohon) {
        for (b in hasil.berkasTujuanUris.asReversed()) hapusAman(b)
        for (f in hasil.folderTujuanUris.asReversed()) hapusAman(f)
    }

    private suspend fun hapusPohonDariDaftar(pohon: PohonSaf): HasilOperasi<Unit> {
        return try {
            for (b in pohon.daftarBerkas.asReversed()) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, b.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus berkas asal '${b.nama}'", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            for (f in pohon.daftarFolder.asReversed()) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, f.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus folder asal '${f.nama}'", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin hapus folder asal SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus folder asal SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private suspend fun salinDanVerifikasiStreamSaf(
        sumberUri: Uri,
        targetUri: Uri,
        buffer: ByteArray,
        crcSumber: CRC32,
        crcAktual: CRC32
    ): HasilOperasi<Unit> {
        crcSumber.reset()
        var totalByte = 0L
        try {
            val inStream = contentResolver.openInputStream(sumberUri)
                ?: return HasilOperasi.Gagal("Gagal membuka aliran baca sumber SAF", PenyebabGalat.IZIN_DITOLAK)
            val outStream = bukaOutputStreamTarget(targetUri, preferModeLangsung = true)
                ?: run {
                    runCatching { inStream.close() }
                    return HasilOperasi.Gagal("Gagal membuka aliran tulis target SAF", PenyebabGalat.IZIN_DITOLAK)
                }

            inStream.use { src ->
                outStream.use { dst ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            dst.write(buffer, 0, n)
                            crcSumber.update(buffer, 0, n)
                            totalByte += n
                        }
                    }
                    dst.flush()
                }
            }

            if (totalByte == 0L) {
                return HasilOperasi.Sukses(Unit)
            }

            val verifikasiIn = try {
                contentResolver.openInputStream(targetUri)
            } catch (_: Exception) {
                null
            }
            if (verifikasiIn != null) {
                crcAktual.reset()
                var byteAktual = 0L
                verifikasiIn.use { ver ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ver.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            crcAktual.update(buffer, 0, n)
                            byteAktual += n
                        }
                    }
                }
                if (byteAktual != totalByte || crcAktual.value != crcSumber.value) {
                    return HasilOperasi.Gagal(
                        "Verifikasi salinan gagal: diharapkan $totalByte byte, terbaca $byteAktual byte",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }
            }
            return HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin salin dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            return HasilOperasi.Gagal(e.message ?: "Berkas SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private suspend fun salinUriKeFileLokal(sumberUri: Uri, fileLokal: File): Boolean {
        return try {
            val src = contentResolver.openInputStream(sumberUri) ?: return false
            val buf = ByteArray(UKURAN_BUFFER_SALIN_SAF)
            src.use { input ->
                FileOutputStream(fileLokal).use { out ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n == -1) break
                        if (n > 0) out.write(buf, 0, n)
                    }
                    out.flush()
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun pulihkanFileLokalKeFolderSaf(fileLokal: File, parentUri: Uri, nama: String) {
        if (!fileLokal.exists()) return
        runCatching {
            val restoredUri = DocumentsContract.createDocument(
                contentResolver,
                parentUri,
                tentukanMimeType(nama),
                nama
            ) ?: return
            bukaOutputStreamTarget(restoredUri)?.use { out ->
                fileLokal.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
        }
    }

    private fun bukaOutputStreamTarget(
        targetUri: Uri,
        preferModeLangsung: Boolean = false
    ): OutputStream? {
        val daftarMode = if (preferModeLangsung) {
            arrayOf("w", "wt", "rwt")
        } else {
            arrayOf("wt", "rwt", "w")
        }
        var galatTerakhir: Exception? = null
        for (mode in daftarMode) {
            try {
                return contentResolver.openOutputStream(targetUri, mode)
            } catch (e: UnsupportedOperationException) {
                galatTerakhir = e
            } catch (e: IllegalArgumentException) {
                galatTerakhir = e
            }
        }
        if (galatTerakhir != null) throw galatTerakhir
        return null
    }

    private suspend fun cobaHapusFolderRekursifJikaPerlu(
        docUri: Uri,
        gagalBawaan: HasilOperasi.Gagal
    ): HasilOperasi<Unit> {
        val entitas = bacaSatuEntitas(docUri)
            ?: return HasilOperasi.Gagal("Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        if (entitas.jenis != JenisBerkas.FOLDER) return gagalBawaan
        return hapusFolderRekursifBottomUp(docUri)
    }

    private suspend fun hapusEntitasTunggalAtauRekursif(
        docUri: Uri,
        adalahFolder: Boolean
    ): HasilOperasi<Unit> {
        return try {
            if (DocumentsContract.deleteDocument(contentResolver, docUri)) {
                HasilOperasi.Sukses(Unit)
            } else if (adalahFolder) {
                hapusFolderRekursifBottomUp(docUri)
            } else {
                HasilOperasi.Gagal("Gagal menghapus berkas SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (adalahFolder && bacaSatuEntitas(docUri)?.jenis == JenisBerkas.FOLDER) {
                hapusFolderRekursifBottomUp(docUri)
            } else {
                HasilOperasi.Gagal(e.message ?: "Gagal menghapus dokumen SAF", PenyebabGalat.IZIN_DITOLAK)
            }
        }
    }

    private suspend fun hapusFolderRekursifBottomUp(rootFolderUri: Uri): HasilOperasi<Unit> {
        val pelapor = currentCoroutineContext()[PelaporProgresOperasi]
        val pohon = when (val res = kumpulkanPohonSaf(rootFolderUri, "", BATAS_ITEM_HAPUS_SAF, pelapor)) {
            is HasilOperasi.Sukses -> res.nilai
            is HasilOperasi.Gagal -> return res
        }
        val totalOps = pohon.totalItemOperasi
        var diproses = 0
        return try {
            for (b in pohon.daftarBerkas.asReversed()) {
                currentCoroutineContext().ensureActive()
                pelapor?.onProgres(++diproses, totalOps, b.nama)
                if (!DocumentsContract.deleteDocument(contentResolver, b.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus salah satu berkas di dalam folder", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            for (f in pohon.daftarFolder.asReversed()) {
                currentCoroutineContext().ensureActive()
                pelapor?.onProgres(++diproses, totalOps, f.nama.ifBlank { "folder" })
                if (!DocumentsContract.deleteDocument(contentResolver, f.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus folder SAF setelah dikosongkan", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin hapus isi folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private data class AnakSingkatSaf(
        val docId: String,
        val uri: Uri,
        val nama: String,
        val adalahFolder: Boolean
    )

    private suspend fun daftarAnakSingkat(parentUri: Uri): HasilOperasi<List<AnakSingkatSaf>> {
        val childrenUri = runCatching {
            val docId = DocumentsContract.getDocumentId(parentUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
        }.getOrNull() ?: return HasilOperasi.Gagal("URI folder SAF tidak valid", PenyebabGalat.VALIDASI)

        val sinyalBatal = CancellationSignal()
        val pegangan = currentCoroutineContext()[Job]?.invokeOnCompletion { sinyalBatal.cancel() }
        return try {
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null,
                sinyalBatal
            ) ?: return HasilOperasi.Sukses(emptyList())

            cursor.use { c ->
                val idxId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val idxNama = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idxId < 0) return HasilOperasi.Sukses(emptyList())

                val hasil = ArrayList<AnakSingkatSaf>(c.count.coerceAtLeast(8))
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = if (!c.isNull(idxId)) c.getString(idxId) else continue
                    if (id.isNullOrBlank()) continue
                    val nama = if (idxNama >= 0 && !c.isNull(idxNama)) c.getString(idxNama) else id
                    val mime = if (idxMime >= 0 && !c.isNull(idxMime)) c.getString(idxMime) else null
                    hasil.add(
                        AnakSingkatSaf(
                            docId = id,
                            uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, id),
                            nama = nama?.takeIf { it.isNotBlank() } ?: id,
                            adalahFolder = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        )
                    )
                }
                HasilOperasi.Sukses(hasil)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin membaca isi folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membaca isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            pegangan?.dispose()
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

    private suspend fun cariIndukLangsung(docUri: Uri, hintInduk: LokasiBerkas? = null): Uri? {
        currentCoroutineContext().ensureActive()
        if (!DocumentsContract.isTreeUri(docUri)) return null

        val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(docUri) }.getOrNull()
        if (apakahRootTreeSaf(treeDocId, docId)) return null

        // 1. Jika pemanggil memberikan hint lokasi folder induk, verifikasi langsung dalam 1 query O(1).
        if (hintInduk != null) {
            val hintUri = SafPemetaanLokasi.keUriDokumen(appContext, hintInduk).let {
                (it as? HasilOperasi.Sukses)?.nilai
            }
            if (hintUri != null && verifikasiIndukDariAnak(hintUri, docId)) {
                return hintUri
            }
        }

        // 2. Jalur cepat standar API 26+ (didukung oleh ExternalStorageProvider, dll.).
        val indukDariPathApi = try {
            val pathObj = DocumentsContract.findDocumentPath(contentResolver, docUri)
            val daftarId = pathObj?.path
            if (daftarId != null) {
                val indeksTarget = daftarId.lastIndexOf(docId)
                val indeksInduk = when {
                    indeksTarget > 0 -> indeksTarget - 1
                    daftarId.size >= 2 -> daftarId.size - 2
                    else -> -1
                }
                val parentDocId = if (indeksInduk >= 0) daftarId[indeksInduk] else null
                if (!parentDocId.isNullOrBlank() && parentDocId != docId) {
                    DocumentsContract.buildDocumentUriUsingTree(docUri, parentDocId)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (indukDariPathApi != null) return indukDariPathApi

        // 3. Kandidat induk berbasis docId (untuk TermuxDocumentsProvider dll.), wajib terverifikasi query ContentResolver.
        val kandidatId = kandidatIndukDocId(docId, treeDocId)
        if (kandidatId != null) {
            val kandidatUri = DocumentsContract.buildDocumentUriUsingTree(docUri, kandidatId)
            if (verifikasiIndukDariAnak(kandidatUri, docId)) {
                return kandidatUri
            }
        }

        // 4. Fallback BFS terbatas dari akar pohon SAF untuk provider ber-ID opaque tanpa findDocumentPath.
        if (treeDocId.isNullOrBlank()) return null
        val treeRootUri = DocumentsContract.buildDocumentUriUsingTree(docUri, treeDocId)
        val antrean = ArrayDeque<Uri>()
        val dikunjungi = HashSet<String>()
        antrean.addLast(treeRootUri)
        dikunjungi.add(treeDocId)
        var folderDiperiksa = 0

        while (antrean.isNotEmpty() && folderDiperiksa < BATAS_BFS_FOLDER_SAF) {
            currentCoroutineContext().ensureActive()
            val dirUri = antrean.removeFirst()
            folderDiperiksa++
            val anakList = (daftarAnakSingkat(dirUri) as? HasilOperasi.Sukses)?.nilai ?: continue
            for (anak in anakList) {
                if (anak.docId == docId) return dirUri
                if (anak.adalahFolder && dikunjungi.add(anak.docId)) {
                    antrean.addLast(anak.uri)
                }
            }
        }
        return null
    }

    private suspend fun verifikasiIndukDariAnak(parentUri: Uri, targetDocId: String): Boolean {
        val childrenUri = runCatching {
            val parentId = DocumentsContract.getDocumentId(parentUri)
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)
        }.getOrNull() ?: return false

        val sinyalBatal = CancellationSignal()
        val pegangan = currentCoroutineContext()[Job]?.invokeOnCompletion { sinyalBatal.cancel() }
        return try {
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null,
                sinyalBatal
            ) ?: return false
            cursor.use { c ->
                val idxId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                if (idxId < 0) return false
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (!c.isNull(idxId) && c.getString(idxId) == targetDocId) {
                        return true
                    }
                }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
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
            if (apakahKonflikDuplikatProvider(nama, entitas.nama) && !konfirmasiTimpa) {
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

    private fun hapusAman(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
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

    private fun apakahUriRootTree(docUri: Uri): Boolean {
        if (!DocumentsContract.isTreeUri(docUri)) return false
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(docUri) }.getOrNull() ?: return false
        val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return false
        return apakahRootTreeSaf(treeDocId, docId)
    }

    private fun apakahDokumenSama(uriA: Uri, uriB: Uri): Boolean {
        if (uriA == uriB) return true
        return runCatching {
            apakahIdDokumenSama(
                authorityA = uriA.authority,
                docIdA = DocumentsContract.getDocumentId(uriA),
                authorityB = uriB.authority,
                docIdB = DocumentsContract.getDocumentId(uriB)
            )
        }.getOrDefault(false)
    }

    internal companion object {
        internal fun apakahJalurBerulang(docId: String): Boolean {
            val segmen = docId.split('/').filter { it.isNotEmpty() }
            if (segmen.size < 4) return false
            for (i in 0 until segmen.size - 2) {
                if (segmen[i] == segmen[i + 1] && segmen[i + 1] == segmen[i + 2]) {
                    return true
                }
            }
            if (segmen.size >= 6) {
                val n = segmen.size
                if (segmen[n - 1] == segmen[n - 3] &&
                    segmen[n - 2] == segmen[n - 4] &&
                    segmen[n - 3] == segmen[n - 5] &&
                    segmen[n - 4] == segmen[n - 6]
                ) {
                    return true
                }
            }
            return false
        }

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

        internal fun apakahIdDokumenSama(
            authorityA: String?,
            docIdA: String?,
            authorityB: String?,
            docIdB: String?
        ): Boolean = authorityA == authorityB && !docIdA.isNullOrBlank() && docIdA == docIdB

        internal fun apakahKonflikDuplikatProvider(namaDiminta: String, namaAktual: String): Boolean {
            if (namaAktual == namaDiminta) return false
            if (namaAktual.startsWith("$namaDiminta.", ignoreCase = true)) return false
            return true
        }

        internal fun apakahRootTreeSaf(treeDocId: String?, docId: String?): Boolean {
            if (treeDocId.isNullOrBlank() || docId.isNullOrBlank()) return false
            return treeDocId.trimEnd('/') == docId.trimEnd('/')
        }

        internal fun kandidatIndukDocId(docId: String, treeDocId: String?): String? {
            if (apakahRootTreeSaf(treeDocId, docId)) return null
            val bersih = docId.trimEnd('/')
            val idxSlash = bersih.lastIndexOf('/')
            val idxTitikDua = bersih.lastIndexOf(':')
            val kandidat = when {
                idxSlash > idxTitikDua && idxSlash > 0 -> bersih.substring(0, idxSlash)
                idxSlash == 0 && bersih.length > 1 -> "/"
                idxTitikDua >= 0 && idxTitikDua < bersih.length - 1 -> bersih.substring(0, idxTitikDua + 1)
                else -> return null
            }
            if (!treeDocId.isNullOrBlank()) {
                val treeBersih = treeDocId.trimEnd('/')
                if (kandidat.length < treeBersih.length) return null
            }
            return kandidat
        }
    }
}