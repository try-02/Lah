package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas

private const val UKURAN_BUFFER_BAWAAN = 8 * 1024

/**
 * Sumber baca streaming dari dokumen SAF via [ContentResolver.openInputStream].
 */
class SafAliranIsi(
    private val uri: Uri,
    override val ukuranByte: Long?,
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ukuranBuffer: Int = UKURAN_BUFFER_BAWAAN
) : SumberIsiBerkas {

    init {
        require(ukuranBuffer > 0) { "ukuranBuffer harus > 0" }
    }

    private var inputStream: InputStream? = null
    private var buffer: ByteArray? = null
    private var tertutup = false
    private var sudahEof = false

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (tertutup) {
            return@withContext SumberIsiBerkas.HasilPotongan.Gagal("Sumber baca SAF sudah ditutup")
        }
        if (sudahEof) {
            return@withContext SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
        }

        try {
            val stream = inputStream ?: contentResolver.openInputStream(uri)?.also {
                inputStream = it
            } ?: return@withContext SumberIsiBerkas.HasilPotongan.Gagal("Gagal membuka aliran baca SAF: $uri")

            val buf = buffer ?: ByteArray(ukuranBuffer).also { buffer = it }
            val dibaca = stream.read(buf)

            if (dibaca == -1) {
                sudahEof = true
                tutupInternal()
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
            } else {
                val data = buf.copyOf(dibaca)
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(data, selesai = false))
            }
        } catch (e: CancellationException) {
            tutupInternal()
            throw e
        } catch (e: Exception) {
            tutupInternal()
            SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Kegagalan I/O saat membaca SAF")
        }
    }

    override suspend fun tutup(): Unit = withContext(ioDispatcher) {
        tutupInternal()
    }

    private fun tutupInternal() {
        tertutup = true
        try {
            inputStream?.close()
        } catch (_: Exception) {
        } finally {
            inputStream = null
            buffer = null
        }
    }

    companion object {
        fun buka(
            context: Context,
            uri: Uri,
            ukuranByte: Long?,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): SafAliranIsi = SafAliranIsi(
            uri = uri,
            ukuranByte = ukuranByte,
            contentResolver = context.applicationContext.contentResolver,
            ioDispatcher = ioDispatcher
        )
    }
}