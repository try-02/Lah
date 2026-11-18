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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSDirectoryEnumerationSkipsSubdirectoryDescendants
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsDirectoryKey

/**
 * Pembaca daftar isi direktori sandbox iOS dalam SATU pemanggilan batch pre-fetch
 * (memenuhi target 1.000 item PRD §7.1).
 */
class DokumenDaftarIsi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val fileManager = NSFileManager.defaultManager

    fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
        emit(muatDaftarIsi(lokasi))
    }

    suspend fun muatDaftarIsi(lokasi: LokasiBerkas): HasilOperasi<List<EntitasBerkas>> =
        withContext(ioDispatcher) {
            val url = when (val hasil = DokumenPemetaanLokasi.keUrl(lokasi)) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return@withContext hasil
            }

            PenyimpanBookmarkKeamanan.denganAksesKeamananSuspend(url) {
                currentCoroutineContext().ensureActive()
                val path = url.path
                if (path == null || !fileManager.fileExistsAtPath(path)) {
                    return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                        "Direktori tidak ditemukan: ${lokasi.pengenal}",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                }

                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val checkDir = url.resourceValuesForKeys(listOf(NSURLIsDirectoryKey), errorPtr.ptr)
                    val isDir = (checkDir?.get(NSURLIsDirectoryKey) as? NSNumber)?.boolValue ?: false
                    if (!isDir) {
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            "Lokasi bukan direktori: ${lokasi.pengenal}",
                            PenyebabGalat.VALIDASI
                        )
                    }

                    val items = fileManager.contentsOfDirectoryAtURL(
                        url = url,
                        includingPropertiesForKeys = DokumenPemetaanLokasi.PROYEKSI_KUNCI_RESOURCE,
                        options = NSDirectoryEnumerationSkipsSubdirectoryDescendants,
                        error = errorPtr.ptr
                    )

                    if (items == null) {
                        val err = errorPtr.value
                        return@denganAksesKeamananSuspend HasilOperasi.Gagal(
                            err?.localizedDescription ?: "Gagal membaca isi direktori iOS",
                            PenyebabGalat.IZIN_DITOLAK
                        )
                    }

                    val hasil = ArrayList<EntitasBerkas>(items.size)
                    for (item in items) {
                        currentCoroutineContext().ensureActive()
                        val childUrl = item as? NSURL ?: continue
                        val attrs = childUrl.resourceValuesForKeys(
                            DokumenPemetaanLokasi.PROYEKSI_KUNCI_RESOURCE,
                            errorPtr.ptr
                        )
                        hasil.add(DokumenPemetaanLokasi.keEntitasBerkas(childUrl, attrs))
                    }
                    hasil.sortBy { it.nama }
                    HasilOperasi.Sukses(hasil)
                }
            }
        }
}