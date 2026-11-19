@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlin.coroutines.cancellation.CancellationException
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingToURL
import platform.Foundation.writeData

class DokumenAliranTulis(
    private val targetUrl: NSURL,
    private val apakahDokumenBaru: Boolean,
    override val lokasiTarget: LokasiBerkas? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberTulisBerkas {
    private enum class Status { AKTIF, SELESAI, BATAL }

    private val fileManager = NSFileManager.defaultManager
    private val mutex = Mutex()
    private var status = Status.AKTIF
    private var tempUrl: NSURL? = null
    private var tempHandle: NSFileHandle? = null
    private var totalByteDitulis: Long = 0L

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (status != Status.AKTIF) {
                return@withContext HasilOperasi.Gagal("Sumber tulis iOS sudah tidak aktif", PenyebabGalat.VALIDASI)
            }
            try {
                val handle = tempHandle ?: buatTempFileDanHandle().let { hasil ->
                    when (hasil) {
                        is HasilOperasi.Sukses -> hasil.nilai
                        is HasilOperasi.Gagal -> return@withContext hasil
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
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    bersihkanTemp()
                    status = Status.BATAL
                    if (apakahDokumenBaru) hapusTargetBaru()
                }
                throw e
            } catch (e: Exception) {
                bersihkanTemp()
                status = Status.BATAL
                if (apakahDokumenBaru) hapusTargetBaru()
                HasilOperasi.Gagal(e.message ?: "Kegagalan I/O saat menulis temp iOS", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (status != Status.AKTIF) {
                return@withContext HasilOperasi.Gagal("Sumber tulis iOS sudah tidak aktif", PenyebabGalat.VALIDASI)
            }
            try {
                tempHandle?.closeFile()
                tempHandle = null

                if (tempUrl == null) {
                    when (val buatKosong = buatTempFileDanHandle()) {
                        is HasilOperasi.Sukses -> {
                            tempHandle?.closeFile()
                            tempHandle = null
                        }
                        is HasilOperasi.Gagal -> return@withContext buatKosong
                    }
                }

                val temp = tempUrl
                    ?: return@withContext gagalkan("Berkas sementara iOS tidak tersedia", PenyebabGalat.TIDAK_DIKETAHUI)
                val tempPath = temp.path
                    ?: return@withContext gagalkan("Path sementara iOS tidak valid", PenyebabGalat.TIDAK_DIKETAHUI)

                val ukuranTemp = bacaUkuranFile(tempPath)
                if (ukuranTemp != totalByteDitulis) {
                    return@withContext gagalkan(
                        "Verifikasi ukuran gagal: tertulis $totalByteDitulis, aktual $ukuranTemp",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }

                val parentUrl = targetUrl.URLByDeletingLastPathComponent
                PenyimpanBookmarkKeamanan.denganAksesKeamananGandaSuspend(targetUrl, parentUrl) {
                    PenyimpanBookmarkKeamanan.koordinasiTulis(
                        url = targetUrl,
                        untukMengganti = !apakahDokumenBaru
                    ) { coordinatedTarget ->
                        memScoped {
                            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                            val targetPath = coordinatedTarget.path
                            val targetExists = targetPath != null && fileManager.fileExistsAtPath(targetPath)
                            val sukses = if (targetExists) {
                                fileManager.replaceItemAtURL(
                                    originalItemURL = coordinatedTarget,
                                    withItemAtURL = temp,
                                    backupItemName = null,
                                    options = 0uL,
                                    resultingItemURL = null,
                                    error = errorPtr.ptr
                                )
                            } else {
                                fileManager.moveItemAtURL(temp, toURL = coordinatedTarget, error = errorPtr.ptr)
                            }
                            if (!sukses) {
                                val err = errorPtr.value
                                return@koordinasiTulis gagalkan(
                                    err?.localizedDescription ?: "Gagal memfinalisasi berkas target iOS",
                                    PenyebabGalat.IZIN_DITOLAK
                                )
                            }
                            val ukuranAkhir = targetPath?.let { bacaUkuranFile(it) }
                            if (ukuranAkhir != null && ukuranAkhir != totalByteDitulis) {
                                return@koordinasiTulis gagalkan(
                                    "Verifikasi ukuran target gagal: tertulis $totalByteDitulis, aktual $ukuranAkhir",
                                    PenyebabGalat.TIDAK_DIKETAHUI
                                )
                            }
                            bersihkanTemp()
                            status = Status.SELESAI
                            HasilOperasi.Sukses(Unit)
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    bersihkanTemp()
                    status = Status.BATAL
                    if (apakahDokumenBaru) hapusTargetBaru()
                }
                throw e
            } catch (e: Exception) {
                gagalkan(e.message ?: "Gagal menyelesaikan penulisan iOS", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
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
    }

    private fun buatTempFileDanHandle(): HasilOperasi<NSFileHandle> {
        val tempPath = NSTemporaryDirectory() + "fm_tulis_" + NSUUID.UUID().UUIDString + ".tmp"
        val url = NSURL.fileURLWithPath(tempPath)
        if (!fileManager.createFileAtPath(tempPath, contents = null, attributes = null)) {
            return HasilOperasi.Gagal("Gagal membuat berkas sementara iOS", PenyebabGalat.TIDAK_DIKETAHUI)
        }
        return memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val h = NSFileHandle.fileHandleForWritingToURL(url, errorPtr.ptr)
            if (h == null) {
                runCatching { fileManager.removeItemAtPath(tempPath, null) }
                HasilOperasi.Gagal(
                    errorPtr.value?.localizedDescription ?: "Gagal membuka berkas temp iOS",
                    PenyebabGalat.IZIN_DITOLAK
                )
            } else {
                tempUrl = url
                tempHandle = h
                HasilOperasi.Sukses(h)
            }
        }
    }

    private fun bacaUkuranFile(path: String): Long = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val attrs = fileManager.attributesOfItemAtPath(path, errorPtr.ptr)
        (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
    }

    private fun gagalkan(pesan: String, penyebab: PenyebabGalat): HasilOperasi.Gagal {
        bersihkanTemp()
        status = Status.BATAL
        if (apakahDokumenBaru) hapusTargetBaru()
        return HasilOperasi.Gagal(pesan, penyebab)
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
        val path = targetUrl.path ?: return
        val parentUrl = targetUrl.URLByDeletingLastPathComponent
        val lepasTarget = targetUrl.startAccessingSecurityScopedResource()
        val lepasParent = parentUrl?.startAccessingSecurityScopedResource() ?: false
        try {
            fileManager.removeItemAtPath(path, null)
        } catch (_: Exception) {
        } finally {
            if (lepasParent) parentUrl.stopAccessingSecurityScopedResource()
            if (lepasTarget) targetUrl.stopAccessingSecurityScopedResource()
        }
    }
}