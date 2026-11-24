package org.fm.com.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

class SafAliranTulis(
    private val appContext: Context,
    private val targetDocUri: Uri,
    private val apakahDokumenBaru: Boolean,
    override val lokasiTarget: LokasiBerkas? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberTulisBerkas {
    private enum class Status { AKTIF, SELESAI, BATAL }

    private val contentResolver: ContentResolver = appContext.contentResolver
    private val mutex = Mutex()
    private var status: Status = Status.AKTIF
    private var berkasTemp: File? = null
    private var berkasBackup: File? = null
    private var tempOutputStream: FileOutputStream? = null
    private var totalByteDitulis: Long = 0L
    private val crcTemp = CRC32()

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
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
                if (potongan.data.isNotEmpty()) {
                    fos.write(potongan.data)
                    crcTemp.update(potongan.data)
                    totalByteDitulis += potongan.data.size
                }
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    status = Status.BATAL
                    bersihkanSemuaTemp()
                    if (apakahDokumenBaru) hapusDokumenSafAman()
                }
                throw e
            } catch (e: Exception) {
                status = Status.BATAL
                bersihkanSemuaTemp()
                if (apakahDokumenBaru) hapusDokumenSafAman()
                HasilOperasi.Gagal(e.message ?: "Kegagalan I/O saat menulis temp SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
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
                    return@withContext gagalkanTulis(
                        "Verifikasi ukuran temp gagal: tertulis $totalByteDitulis, terbaca $ukuranTemp",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }

                if (!apakahDokumenBaru) {
                    val backupBerhasil = buatCadanganTargetSebelumTimpa()
                    if (!backupBerhasil) {
                        return@withContext gagalkanTulis(
                            "Gagal membuat cadangan berkas lama sebelum menimpa target SAF",
                            PenyebabGalat.TIDAK_DIKETAHUI
                        )
                    }
                }

                val outStream = try {
                    bukaOutputStreamTarget()
                } catch (e: UnsupportedOperationException) {
                    pulihkanDariCadangan()
                    return@withContext gagalkanTulis(
                        e.message ?: "Penyedia SAF tidak mendukung mode penulisan target",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                } catch (e: IllegalArgumentException) {
                    pulihkanDariCadangan()
                    return@withContext gagalkanTulis(
                        e.message ?: "Mode penulisan SAF tidak didukung target",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                } ?: return@withContext gagalkanTulis(
                        "Gagal membuka aliran target SAF untuk penulisan",
                        PenyebabGalat.IZIN_DITOLAK
                    )

                outStream.use { os ->
                    if (temp != null && temp.exists()) {
                        temp.inputStream().use { input ->
                            input.copyTo(os)
                        }
                    }
                    os.flush()
                }

                val verifikasiError = verifikasiHasilBacaUlang()
                if (verifikasiError != null) {
                    pulihkanDariCadangan()
                    return@withContext gagalkanTulis(verifikasiError, PenyebabGalat.TIDAK_DIKETAHUI)
                }

                bersihkanSemuaTemp()
                status = Status.SELESAI
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    pulihkanDariCadangan()
                    bersihkanSemuaTemp()
                    status = Status.BATAL
                    if (apakahDokumenBaru) hapusDokumenSafAman()
                }
                throw e
            } catch (e: SecurityException) {
                pulihkanDariCadangan()
                gagalkanTulis(e.message ?: "Izin tulis target SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
            } catch (e: FileNotFoundException) {
                gagalkanTulis(e.message ?: "Berkas target SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            } catch (e: Exception) {
                pulihkanDariCadangan()
                gagalkanTulis(e.message ?: "Gagal menyelesaikan penulisan ke target SAF", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            if (status == Status.SELESAI) {
                return@withContext HasilOperasi.Sukses(Unit)
            }
            status = Status.BATAL
            bersihkanSemuaTemp()
            if (apakahDokumenBaru) {
                hapusDokumenSafAman()
            }
            HasilOperasi.Sukses(Unit)
        }
    }

    private fun buatCadanganTargetSebelumTimpa(): Boolean {
        return try {
            val streamAsli = contentResolver.openInputStream(targetDocUri) ?: return true
            val backup = File.createTempFile("saf_bak_", ".tmp", appContext.cacheDir)
            berkasBackup = backup
            streamAsli.use { input ->
                FileOutputStream(backup).use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            true
        } catch (_: FileNotFoundException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun pulihkanDariCadangan() {
        if (apakahDokumenBaru) return
        val backup = berkasBackup ?: return
        if (!backup.exists()) return
        try {
            bukaOutputStreamTarget()?.use { os ->
                backup.inputStream().use { input ->
                    input.copyTo(os)
                }
                os.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun bukaOutputStreamTarget(): OutputStream? {
        val daftarMode = arrayOf("wt", "rwt", "w")
        var galatModeTerakhir: Exception? = null
        for (mode in daftarMode) {
            try {
                return contentResolver.openOutputStream(targetDocUri, mode)
            } catch (e: UnsupportedOperationException) {
                galatModeTerakhir = e
            } catch (e: IllegalArgumentException) {
                galatModeTerakhir = e
            }
        }
        if (galatModeTerakhir != null) throw galatModeTerakhir
        return null
    }

    private fun verifikasiHasilBacaUlang(): String? {
        return try {
            val stream = contentResolver.openInputStream(targetDocUri)
                ?: return null
            val crcAktual = CRC32()
            var totalTerbaca = 0L
            val buffer = ByteArray(8 * 1024)
            stream.use { input ->
                while (true) {
                    val dibaca = input.read(buffer)
                    if (dibaca == -1) break
                    crcAktual.update(buffer, 0, dibaca)
                    totalTerbaca += dibaca
                }
            }
            when {
                totalTerbaca != totalByteDitulis ->
                    "Verifikasi baca ulang gagal: tertulis $totalByteDitulis byte, aktual $totalTerbaca byte"
                crcAktual.value != crcTemp.value ->
                    "Verifikasi checksum gagal setelah penulisan ke SAF"
                else -> null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } catch (e: Exception) {
            "Gagal memverifikasi hasil tulisan SAF: ${e.message}"
        }
    }

    private fun gagalkanTulis(pesan: String, penyebab: PenyebabGalat): HasilOperasi.Gagal {
        bersihkanSemuaTemp()
        status = Status.BATAL
        if (apakahDokumenBaru) {
            hapusDokumenSafAman()
        }
        return HasilOperasi.Gagal(pesan, penyebab)
    }

    private fun bersihkanSemuaTemp() {
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
        try {
            berkasBackup?.delete()
        } catch (_: Exception) {
        } finally {
            berkasBackup = null
        }
    }

    private fun hapusDokumenSafAman() {
        try {
            DocumentsContract.deleteDocument(contentResolver, targetDocUri)
        } catch (_: Exception) {
        }
    }
}