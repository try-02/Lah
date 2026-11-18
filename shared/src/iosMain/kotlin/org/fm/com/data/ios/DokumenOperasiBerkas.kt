@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/**
 * Implementasi operasi dokumen sandbox iOS (buat folder, buat berkas, ganti nama, hapus, salin, pindah).
 */
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

                val targetPath = targetUrl.path
                if (targetPath != null && fileManager.fileExistsAtPath(targetPath)) {
                    if (!permintaan.konfirmasi) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            "Folder target sudah ada: $nama",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    fileManager.removeItemAtPath(targetPath, null)
                }

                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val sukses = fileManager.createDirectoryAtURL(
                        url = targetUrl,
                        withIntermediateDirectories = false,
                        attributes = null,
                        error = errorPtr.ptr
                    )
                    if (!sukses) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            errorPtr.value?.localizedDescription ?: "Gagal membuat folder iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                    HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(targetUrl))
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

                val targetPath = targetUrl.path
                if (targetPath != null && fileManager.fileExistsAtPath(targetPath)) {
                    if (!permintaan.konfirmasi) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            "Berkas target sudah ada: $nama",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    fileManager.removeItemAtPath(targetPath, null)
                }

                val targetPathSah = targetPath ?: return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                    "Path berkas kosong tidak valid",
                    PenyebabGalat.VALIDASI
                )

                val sukses = fileManager.createFileAtPath(targetPathSah, contents = NSData(), attributes = null)
                if (!sukses) {
                    return@denganAksesKeamananSuspend HasilOperasi.Gagal("Gagal membuat berkas kosong iOS", PenyebabGalat.IZIN_DITOLAK)
                }
                HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(targetUrl))
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

            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(sourceUrl) {
                val targetUrl = parentUrl.URLByAppendingPathComponent(namaBaru)
                    ?: return@denganAksesKeamananSuspend HasilOperasi.Gagal("Gagal menyusun URL tujuan", PenyebabGalat.VALIDASI)

                if (targetUrl.path == sourceUrl.path) {
                    return@denganAksesKeamananSuspend HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(sourceUrl))
                }

                val targetPath = targetUrl.path
                if (targetPath != null && fileManager.fileExistsAtPath(targetPath)) {
                    if (!permintaan.konfirmasi) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            "Nama target sudah ada di folder: $namaBaru",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    fileManager.removeItemAtPath(targetPath, null)
                }

                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val sukses = fileManager.moveItemAtURL(sourceUrl, toURL = targetUrl, error = errorPtr.ptr)
                    if (!sukses) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            errorPtr.value?.localizedDescription ?: "Gagal mengganti nama berkas iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                    HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(targetUrl))
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

            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(sourceUrl) {
                val path = sourceUrl.path
                if (path == null || !fileManager.fileExistsAtPath(path)) {
                    return@denganAksesKeamananSuspend HasilOperasi.Gagal("Berkas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                }

                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val sukses = fileManager.removeItemAtURL(sourceUrl, errorPtr.ptr)
                    if (!sukses) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            errorPtr.value?.localizedDescription ?: "Gagal menghapus entitas iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                    HasilOperasi.Sukses(Unit)
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

        PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(sourceUrl) {
            val targetUrl = targetParentUrl.URLByAppendingPathComponent(nama)
                ?: return@denganAksesKeamananSuspend HasilOperasi.Gagal("Gagal menyusun URL target", PenyebabGalat.VALIDASI)

            if (targetUrl.path == sourceUrl.path) {
                return@denganAksesKeamananSuspend HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(sourceUrl))
            }

            val targetPath = targetUrl.path
            if (targetPath != null && fileManager.fileExistsAtPath(targetPath)) {
                if (!permintaan.konfirmasi) {
                    return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                        "Target sudah ada di tujuan: $nama",
                        PenyebabGalat.KONFLIK
                    )
                }
                fileManager.removeItemAtPath(targetPath, null)
            }

            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val sukses = if (pindah) {
                    fileManager.moveItemAtURL(sourceUrl, toURL = targetUrl, error = errorPtr.ptr)
                } else {
                    fileManager.copyItemAtURL(sourceUrl, toURL = targetUrl, error = errorPtr.ptr)
                }

                if (!sukses) {
                    return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                        errorPtr.value?.localizedDescription ?: "Gagal menyalin/memindah entitas iOS",
                        PenyebabGalat.IZIN_DITOLAK
                    )
                }
                HasilOperasi.Sukses(DokumenPemetaanLokasi.keEntitasBerkas(targetUrl))
            }
        }
    }
}