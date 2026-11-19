@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

class DokumenOperasiBerkas(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RepositoriOperasiBerkas {
    private val fileManager = NSFileManager.defaultManager

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val nama = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib untuk buat folder", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(nama)) {
                return@withContext HasilOperasi.Gagal("Nama folder tidak sah: $nama", PenyebabGalat.VALIDASI)
            }
            val parentUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(parentUrl) {
                val targetUrl = parentUrl.URLByAppendingPathComponent(nama)
                    ?: return@denganAksesKeamananSuspend HasilOperasi.Gagal("Gagal menyusun URL target", PenyebabGalat.VALIDASI)
                PenyimpanBookmarkKeamanan.koordinasiTulis(targetUrl) { coordTarget ->
                    val backupUrl = siapkanBackupJikaKonflik(coordTarget, parentUrl, permintaan.konfirmasi)
                        ?: if (coordTarget.path?.let { fileManager.fileExistsAtPath(it) } == true) {
                            return@koordinasiTulis HasilOperasi.Gagal(
                                "Folder target sudah ada: $nama",
                                PenyebabGalat.KONFLIK
                            )
                        } else {
                            null
                        }
                    memScoped {
                        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                        val sukses = fileManager.createDirectoryAtURL(
                            url = coordTarget,
                            withIntermediateDirectories = false,
                            attributes = null,
                            error = errorPtr.ptr
                        )
                        if (!sukses) {
                            pulihkanBackup(backupUrl, coordTarget)
                            return@koordinasiTulis HasilOperasi.Gagal(
                                errorPtr.value?.localizedDescription ?: "Gagal membuat folder iOS",
                                PenyebabGalat.IZIN_DITOLAK
                            )
                        }
                        hapusBackup(backupUrl)
                        HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(coordTarget))
                    }
                }
            }
        }

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val nama = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib untuk buat berkas", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(nama)) {
                return@withContext HasilOperasi.Gagal("Nama berkas tidak sah: $nama", PenyebabGalat.VALIDASI)
            }
            val parentUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(parentUrl) {
                val targetUrl = parentUrl.URLByAppendingPathComponent(nama)
                    ?: return@denganAksesKeamananSuspend HasilOperasi.Gagal("Gagal menyusun URL target", PenyebabGalat.VALIDASI)
                PenyimpanBookmarkKeamanan.koordinasiTulis(targetUrl) { coordTarget ->
                    val backupUrl = siapkanBackupJikaKonflik(coordTarget, parentUrl, permintaan.konfirmasi)
                        ?: if (coordTarget.path?.let { fileManager.fileExistsAtPath(it) } == true) {
                            return@koordinasiTulis HasilOperasi.Gagal(
                                "Berkas target sudah ada: $nama",
                                PenyebabGalat.KONFLIK
                            )
                        } else {
                            null
                        }
                    val targetPathSah = coordTarget.path ?: run {
                        pulihkanBackup(backupUrl, coordTarget)
                        return@koordinasiTulis HasilOperasi.Gagal("Path berkas kosong tidak valid", PenyebabGalat.VALIDASI)
                    }
                    val sukses = fileManager.createFileAtPath(targetPathSah, contents = NSData(), attributes = null)
                    if (!sukses) {
                        pulihkanBackup(backupUrl, coordTarget)
                        return@koordinasiTulis HasilOperasi.Gagal("Gagal membuat berkas kosong iOS", PenyebabGalat.IZIN_DITOLAK)
                    }
                    hapusBackup(backupUrl)
                    HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(coordTarget))
                }
            }
        }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            val namaBaru = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib untuk ganti nama", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(namaBaru)) {
                return@withContext HasilOperasi.Gagal("Nama baru tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
            }
            val sourceUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val parentUrl = sourceUrl.URLByDeletingLastPathComponent
                ?: return@withContext HasilOperasi.Gagal("Root tidak bisa diganti nama", PenyebabGalat.VALIDASI)
            PenyimpanBookmarkKeamanan.denganAksesKeamananGandaSuspend(sourceUrl, parentUrl) {
                val targetUrl = parentUrl.URLByAppendingPathComponent(namaBaru)
                    ?: return@denganAksesKeamananGandaSuspend HasilOperasi.Gagal("Gagal menyusun URL tujuan", PenyebabGalat.VALIDASI)
                if (targetUrl.URLByStandardizingPath?.path == sourceUrl.URLByStandardizingPath?.path) {
                    return@denganAksesKeamananGandaSuspend HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(sourceUrl))
                }
                PenyimpanBookmarkKeamanan.koordinasiSalinAtauPindah(sourceUrl, targetUrl, pindah = true) { coordSource, coordTarget ->
                    val sourcePath = coordSource.path
                    if (sourcePath == null || !fileManager.fileExistsAtPath(sourcePath)) {
                        return@koordinasiSalinAtauPindah HasilOperasi.Gagal("Berkas asal tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                    }
                    val backupUrl = siapkanBackupJikaKonflik(coordTarget, parentUrl, permintaan.konfirmasi)
                        ?: if (coordTarget.path?.let { fileManager.fileExistsAtPath(it) } == true) {
                            return@koordinasiSalinAtauPindah HasilOperasi.Gagal(
                                "Nama target sudah ada di folder: $namaBaru",
                                PenyebabGalat.KONFLIK
                            )
                        } else {
                            null
                        }
                    memScoped {
                        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                        val sukses = fileManager.moveItemAtURL(coordSource, toURL = coordTarget, error = errorPtr.ptr)
                        if (!sukses) {
                            pulihkanBackup(backupUrl, coordTarget)
                            return@koordinasiSalinAtauPindah HasilOperasi.Gagal(
                                errorPtr.value?.localizedDescription ?: "Gagal mengganti nama berkas iOS",
                                PenyebabGalat.IZIN_DITOLAK
                            )
                        }
                        hapusBackup(backupUrl)
                        HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(coordTarget))
                    }
                }
            }
        }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            if (!permintaan.konfirmasi) {
                return@withContext HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
            }
            val sourceUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(permintaan.sumber)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }
            val parentUrl = sourceUrl.URLByDeletingLastPathComponent
            PenyimpanBookmarkKeamanan.denganAksesKeamananGandaSuspend(sourceUrl, parentUrl) {
                PenyimpanBookmarkKeamanan.koordinasiTulis(sourceUrl, untukMenghapus = true) { coordSource ->
                    val path = coordSource.path
                    if (path == null || !fileManager.fileExistsAtPath(path)) {
                        return@koordinasiTulis HasilOperasi.Gagal("Berkas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                    }
                    memScoped {
                        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                        val sukses = fileManager.removeItemAtURL(coordSource, errorPtr.ptr)
                        if (!sukses) {
                            return@koordinasiTulis HasilOperasi.Gagal(
                                errorPtr.value?.localizedDescription ?: "Gagal menghapus entitas iOS",
                                PenyebabGalat.IZIN_DITOLAK
                            )
                        }
                        HasilOperasi.Sukses(Unit)
                    }
                }
            }
        }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = false)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = true)

    private suspend fun salinAtauPindah(
        permintaan: PermintaanOperasi,
        pindah: Boolean
    ): HasilOperasi<EntitasBerkas> = withContext(ioDispatcher) {
        val tujuanInduk = permintaan.tujuan
            ?: return@withContext HasilOperasi.Gagal("Tujuan (folder induk) wajib diisi", PenyebabGalat.VALIDASI)
        val sourceUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(permintaan.sumber)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return@withContext hasil
        }
        val targetParentUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(tujuanInduk)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return@withContext hasil
        }
        val nama = permintaan.namaBaru ?: sourceUrl.lastPathComponent ?: "berkas"
        if (!ValidasiNama.sah(nama)) {
            return@withContext HasilOperasi.Gagal("Nama tujuan tidak sah: $nama", PenyebabGalat.VALIDASI)
        }

        val normSource = sourceUrl.URLByStandardizingPath?.path?.trimEnd('/')
        val normTargetParent = targetParentUrl.URLByStandardizingPath?.path?.trimEnd('/')
        if (normSource != null && normTargetParent != null) {
            if (normTargetParent == normSource || normTargetParent.startsWith("$normSource/")) {
                return@withContext HasilOperasi.Gagal(
                    "Tidak bisa menyalin/memindah folder ke dalam dirinya sendiri atau turunannya",
                    PenyebabGalat.VALIDASI
                )
            }
        }

        PenyimpanBookmarkKeamanan.denganAksesKeamananGandaSuspend(sourceUrl, targetParentUrl) {
            val targetUrl = targetParentUrl.URLByAppendingPathComponent(nama)
                ?: return@denganAksesKeamananGandaSuspend HasilOperasi.Gagal("Gagal menyusun URL target", PenyebabGalat.VALIDASI)
            if (targetUrl.URLByStandardizingPath?.path == sourceUrl.URLByStandardizingPath?.path) {
                return@denganAksesKeamananGandaSuspend HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(sourceUrl))
            }
            PenyimpanBookmarkKeamanan.koordinasiSalinAtauPindah(sourceUrl, targetUrl, pindah = pindah) { coordSource, coordTarget ->
                val sourcePath = coordSource.path
                if (sourcePath == null || !fileManager.fileExistsAtPath(sourcePath)) {
                    return@koordinasiSalinAtauPindah HasilOperasi.Gagal("Berkas sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                val backupUrl = siapkanBackupJikaKonflik(coordTarget, targetParentUrl, permintaan.konfirmasi)
                    ?: if (coordTarget.path?.let { fileManager.fileExistsAtPath(it) } == true) {
                        return@koordinasiSalinAtauPindah HasilOperasi.Gagal(
                            "Target sudah ada di tujuan: $nama",
                            PenyebabGalat.KONFLIK
                        )
                    } else {
                        null
                    }
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val sukses = if (pindah) {
                        fileManager.moveItemAtURL(coordSource, toURL = coordTarget, error = errorPtr.ptr)
                    } else {
                        fileManager.copyItemAtURL(coordSource, toURL = coordTarget, error = errorPtr.ptr)
                    }
                    if (!sukses) {
                        pulihkanBackup(backupUrl, coordTarget)
                        return@koordinasiSalinAtauPindah HasilOperasi.Gagal(
                            errorPtr.value?.localizedDescription ?: "Gagal menyalin/memindah entitas iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                    hapusBackup(backupUrl)
                    HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(coordTarget))
                }
            }
        }
    }

    private fun siapkanBackupJikaKonflik(
        targetUrl: NSURL,
        parentUrl: NSURL,
        konfirmasi: Boolean
    ): NSURL? {
        val targetPath = targetUrl.path ?: return null
        if (!fileManager.fileExistsAtPath(targetPath)) return null
        if (!konfirmasi) return null
        val backupUrl = parentUrl.URLByAppendingPathComponent(".fm_bak_" + NSUUID.UUID().UUIDString)
            ?: return null
        return try {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                if (fileManager.moveItemAtURL(targetUrl, toURL = backupUrl, error = errorPtr.ptr)) {
                    backupUrl
                } else {
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun pulihkanBackup(backupUrl: NSURL?, targetUrl: NSURL) {
        if (backupUrl == null) return
        runCatching {
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                fileManager.moveItemAtURL(backupUrl, toURL = targetUrl, error = errorPtr.ptr)
            }
        }
    }

    private fun hapusBackup(backupUrl: NSURL?) {
        val path = backupUrl?.path ?: return
        runCatching { fileManager.removeItemAtPath(path, null) }
    }
}