@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingToURL
import platform.Foundation.fileURLWithPath
import platform.Foundation.writeData

/**
 * Sumber tulis streaming iOS dengan strategi Simpan Aman (K6) dan siklus hidup K10:
 * 1. Data dialirkan ke berkas temp di [NSTemporaryDirectory].
 * 2. Pada [selesai], verifikasi ukuran, lalu ganti berkas target secara atomik ([NSFileManager.replaceItemAtURL]).
 * 3. Jika dokumen baru dibuat dan dibatalkan via [batalkan], dokumen target dibersihkan.
 * 4. [batalkan] bersifat idempoten (K10).
 */
class DokumenAliranTulis(
    private val targetUrl: NSURL,
    private val apakahDokumenBaru: Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberTulisBerkas {

    private enum class Status { AKTIF, SELESAI, BATAL }

    private val fileManager = NSFileManager.defaultManager
    private var status = Status.AKTIF
    private var tempUrl: NSURL? = null
    private var tempHandle: NSFileHandle? = null
    private var totalByteDitulis: Long = 0L

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withContext HasilOperasi.Gagal("Sumber tulis iOS sudah tidak aktif", PenyebabGalat.VALIDASI)
        }

        try {
            val handle = tempHandle ?: run {
                val tempPath = NSTemporaryDirectory() + "fm_tulis_" + NSUUID.UUID().UUIDString + ".tmp"
                val url = NSURL.fileURLWithPath(tempPath)
                fileManager.createFileAtPath(tempPath, contents = null, attributes = null)
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val h = NSFileHandle.fileHandleForWritingToURL(url, errorPtr.ptr)
                        ?: return@withContext HasilOperasi.Gagal(
                            errorPtr.value?.localizedDescription ?: "Gagal membuka berkas temp iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    tempUrl = url
                    tempHandle = h
                    h
                }
            }

            val data = potongan.data
            if (data.isNotEmpty()) {
                val nsData = data.usePinned { pinned ->
                    NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
                }
                handle.writeData(nsData)
                totalByteDitulis += data.size
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: Exception) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) hapusTargetBaru()
            HasilOperasi.Gagal(e.message ?: "Kegagalan I/O saat menulis temp iOS", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withContext HasilOperasi.Gagal("Sumber tulis iOS sudah tidak aktif", PenyebabGalat.VALIDASI)
        }

        try {
            tempHandle?.closeFile()
            tempHandle = null

            val temp = tempUrl
            val tempPath = temp?.path
            if (tempPath != null) {
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val attrs = fileManager.attributesOfItemAtPath(tempPath, errorPtr.ptr)
                    val ukuranTemp = (attrs?.objectForKey(platform.Foundation.NSFileSize) as? platform.Foundation.NSNumber)?.longLongValue ?: 0L
                    if (ukuranTemp != totalByteDitulis) {
                        bersihkanTemp()
                        status = Status.BATAL
                        if (apakahDokumenBaru) hapusTargetBaru()
                        return@withContext HasilOperasi.Gagal(
                            "Verifikasi ukuran gagal: tertulis $totalByteDitulis, aktual $ukuranTemp",
                            PenyebabGalat.TIDAK_DIKETAHUI
                        )
                    }
                }
            }

            // Simpan aman atomik ke target
            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(targetUrl) {
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val targetPath = targetUrl.path
                    val targetExists = targetPath != null && fileManager.fileExistsAtPath(targetPath)

                    val sukses = if (targetExists && temp != null) {
                        fileManager.replaceItemAtURL(
                            originalItemURL = targetUrl,
                            withItemAtURL = temp,
                            backupItemName = null,
                            options = 0uL,
                            resultingURL = null,
                            error = errorPtr.ptr
                        )
                    } else if (temp != null) {
                        fileManager.moveItemAtURL(temp, toURL = targetUrl, error = errorPtr.ptr)
                    } else {
                        false
                    }

                    if (!sukses) {
                        val err = errorPtr.value
                        bersihkanTemp()
                        status = Status.BATAL
                        if (apakahDokumenBaru) hapusTargetBaru()
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            err?.localizedDescription ?: "Gagal memfinalisasi berkas target iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }
                    bersihkanTemp()
                    status = Status.SELESAI
                    HasilOperasi.Sukses(Unit)
                }
            }
        } catch (e: Exception) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) hapusTargetBaru()
            HasilOperasi.Gagal(e.message ?: "Gagal menyelesaikan penulisan iOS", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        if (status == Status.SELESAI) {
            return@withContext HasilOperasi.Sukses(Unit)
        }
        status = Status.BATAL
        bersihkanTemp()
        if (apakahDokumenBaru) {
            hapusTargetBaru()
        }
        HasilOperasi.Sukses(Unit)
    }

    private fun bersihkanTemp() {
        try {
            tempHandle?.closeFile()
        } catch (_: Exception) {
        } finally {
            tempHandle = null
        }
        val path = tempUrl?.path
        if (path != null) {
            try {
                fileManager.removeItemAtPath(path, null)
            } catch (_: Exception) {
            } finally {
                tempUrl = null
            }
        }
    }

    private fun hapusTargetBaru() {
        val path = targetUrl.path
        if (path != null) {
            try {
                fileManager.removeItemAtPath(path, null)
            } catch (_: Exception) {
            }
        }
    }
}