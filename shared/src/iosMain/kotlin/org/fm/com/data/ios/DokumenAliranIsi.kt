@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
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
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.HasilOperasi
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForReadingFromURL

private const val UKURAN_BUFFER_BAWAAN = 8 * 1024

class DokumenAliranIsi(
    private val url: NSURL,
    override val ukuranByte: Long?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ukuranBuffer: Int = UKURAN_BUFFER_BAWAAN
) : SumberIsiBerkas {
    init {
        require(ukuranBuffer > 0) { "ukuranBuffer harus > 0" }
    }

    private val mutex = Mutex()
    private var fileHandle: NSFileHandle? = null
    private var tertutup = false
    private var sudahEof = false
    private var securityScopeAktif = false

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (tertutup) {
                return@withContext SumberIsiBerkas.HasilPotongan.Gagal("Sumber baca iOS sudah ditutup")
            }
            if (sudahEof) {
                return@withContext SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
            }
            try {
                val handle = fileHandle ?: run {
                    securityScopeAktif = url.startAccessingSecurityScopedResource()
                    when (val hasilBuka = bukaHandleTerkordinasi()) {
                        is HasilOperasi.Sukses -> hasilBuka.nilai.also { fileHandle = it }
                        is HasilOperasi.Gagal -> {
                            tutupInternal()
                            return@withContext SumberIsiBerkas.HasilPotongan.Gagal(hasilBuka.pesan)
                        }
                    }
                }
                val nsData = memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val data = handle.readDataUpToLength(ukuranBuffer.toULong(), errorPtr.ptr)
                    if (data == null) {
                        val err = errorPtr.value
                        tutupInternal()
                        return@withContext SumberIsiBerkas.HasilPotongan.Gagal(
                            err?.localizedDescription ?: "Gagal membaca berkas iOS"
                        )
                    }
                    data
                }
                val panjang = nsData.length.toInt()
                if (panjang == 0) {
                    sudahEof = true
                    tutupInternal()
                    SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
                } else {
                    val bytes = nsData.bytes?.reinterpret<ByteVar>()?.readBytes(panjang) ?: ByteArray(0)
                    SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(bytes, selesai = false))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { tutupInternal() }
                throw e
            } catch (e: Exception) {
                tutupInternal()
                SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Kegagalan I/O saat membaca berkas iOS")
            }
        }
    }

    override suspend fun tutup(): Unit = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            tutupInternal()
        }
    }

    private fun bukaHandleTerkordinasi(): HasilOperasi<NSFileHandle> =
        PenyimpanBookmarkKeamanan.koordinasiBaca(url) { coordinatedUrl ->
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                val h = NSFileHandle.fileHandleForReadingFromURL(coordinatedUrl, errorPtr.ptr)
                if (h == null) {
                    HasilOperasi.Gagal(
                        errorPtr.value?.localizedDescription ?: "Gagal membuka aliran baca iOS: ${coordinatedUrl.path}"
                    )
                } else {
                    HasilOperasi.Sukses(h)
                }
            }
        }

    private fun tutupInternal() {
        tertutup = true
        try {
            fileHandle?.closeFile()
        } catch (_: Exception) {
        } finally {
            fileHandle = null
            if (securityScopeAktif) {
                url.stopAccessingSecurityScopedResource()
                securityScopeAktif = false
            }
        }
    }
}