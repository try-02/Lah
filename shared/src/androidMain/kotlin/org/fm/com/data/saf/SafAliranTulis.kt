package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat

/**
 * Sumber tulis streaming SAF dengan strategi Simpan Aman (K6) dan siklus hidup K10:
 * 1. Data streaming ditulis ke berkas temp di cache privat aplikasi.
 * 2. Pada [selesai], verifikasi ukuran temp, lalu salin ke stream target SAF.
 * 3. Jika berkas baru dibuat dan [batalkan] dipanggil, dokumen baru dihapus dari SAF.
 * 4. [batalkan] bersifat idempoten dan aman dipanggil di finally block.
 */
class SafAliranTulis(
    private val appContext: Context,
    private val targetDocUri: Uri,
    private val apakahDokumenBaru: Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberTulisBerkas {

    private enum class Status { AKTIF, SELESAI, BATAL }

    private val contentResolver: ContentResolver = appContext.contentResolver
    private var status: Status = Status.AKTIF
    private var berkasTemp: File? = null
    private var tempOutputStream: FileOutputStream? = null
    private var totalByteDitulis: Long = 0L

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withContext HasilOperasi.Gagal("Sumber tulis SAF sudah tidak aktif", PenyebabGalat.VALIDASI)
        }

        try {
            val fos = tempOutputStream ?: run {
                val temp = File.createTempFile("saf_tulis_", ".tmp", appContext.cacheDir)
                berkasTemp = temp
                FileOutputStream(temp).also { tempOutputStream = it }
            }
            fos.write(potongan.data)
            totalByteDitulis += potongan.data.size
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            bersihkanTemp()
            status = Status.BATAL
            throw e
        } catch (e: Exception) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) {
                hapusDokumenSafAman()
            }
            HasilOperasi.Gagal(e.message ?: "Kegagalan I/O saat menulis temp SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withContext HasilOperasi.Gagal("Sumber tulis SAF sudah tidak aktif", PenyebabGalat.VALIDASI)
        }

        try {
            tempOutputStream?.flush()
            tempOutputStream?.close()
            tempOutputStream = null

            val temp = berkasTemp
            val ukuranTemp = temp?.length() ?: 0L
            if (ukuranTemp != totalByteDitulis) {
                bersihkanTemp()
                status = Status.BATAL
                if (apakahDokumenBaru) hapusDokumenSafAman()
                return@withContext HasilOperasi.Gagal(
                    "Verifikasi ukuran gagal: tertulis $totalByteDitulis, terbaca $ukuranTemp",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            }

            // Tulis dan verifikasi ke dokumen SAF target
            val outStream = contentResolver.openOutputStream(targetDocUri, "wt")
                ?: return@withContext run {
                    bersihkanTemp()
                    status = Status.BATAL
                    if (apakahDokumenBaru) hapusDokumenSafAman()
                    HasilOperasi.Gagal("Gagal membuka aliran target SAF untuk penulisan", PenyebabGalat.IZIN_DITOLAK)
                }

            outStream.use { os ->
                if (temp != null && temp.exists()) {
                    temp.inputStream().use { `is` ->
                        `is`.copyTo(os)
                    }
                }
                os.flush()
            }

            bersihkanTemp()
            status = Status.SELESAI
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) hapusDokumenSafAman()
            throw e
        } catch (e: SecurityException) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) hapusDokumenSafAman()
            HasilOperasi.Gagal(e.message ?: "Izin tulis target SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            bersihkanTemp()
            status = Status.BATAL
            if (apakahDokumenBaru) hapusDokumenSafAman()
            HasilOperasi.Gagal(e.message ?: "Gagal menyelesaikan penulisan ke target SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = withContext(ioDispatcher) {
        if (status == Status.SELESAI) {
            // Kontrak K10: setelah SELESAI, batalkan() idempoten dan tidak mengubah hasil
            return@withContext HasilOperasi.Sukses(Unit)
        }
        status = Status.BATAL
        bersihkanTemp()
        if (apakahDokumenBaru) {
            hapusDokumenSafAman()
        }
        HasilOperasi.Sukses(Unit)
    }

    private fun bersihkanTemp() {
        try {
            tempOutputStream?.close()
        } catch (_: IOException) {
        } finally {
            tempOutputStream = null
        }
        try {
            berkasTemp?.delete()
        } catch (_: Exception) {
        } finally {
            berkasTemp = null
        }
    }

    private fun hapusDokumenSafAman() {
        try {
            DocumentsContract.deleteDocument(contentResolver, targetDocUri)
        } catch (_: Exception) {
        }
    }
}