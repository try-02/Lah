@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import org.fm.com.data.DataBerkas
import org.fm.com.data.DataFolder
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLIsHiddenKey
import platform.Foundation.NSURLIsSymbolicLinkKey
import platform.Foundation.NSURLNameKey
import platform.Foundation.timeIntervalSince1970

/**
 * Pemetaan antara [NSURL] sandbox iOS dan model domain [LokasiBerkas] / [EntitasBerkas].
 */
object DokumenPemetaanLokasi {

    val PROYEKSI_KUNCI_RESOURCE = listOf(
        NSURLNameKey,
        NSURLIsDirectoryKey,
        NSURLFileSizeKey,
        NSURLContentModificationDateKey,
        NSURLIsHiddenKey,
        NSURLIsSymbolicLinkKey
    )

    /** Mengonversi [LokasiBerkas] ke [NSURL]. */
    fun keUrl(lokasi: LokasiBerkas): HasilOperasi<NSURL> {
        if (lokasi.jenis != JenisLokasi.IOS) {
            return HasilOperasi.Gagal("Lokasi bukan bertipe IOS: ${lokasi.jenis}", PenyebabGalat.VALIDASI)
        }
        val pengenal = lokasi.pengenal
        if (pengenal.isBlank()) {
            return HasilOperasi.Gagal("Pengenal lokasi iOS kosong", PenyebabGalat.VALIDASI)
        }
        val url = if (pengenal.startsWith("file://")) {
            NSURL.URLWithString(pengenal)
        } else {
            NSURL.fileURLWithPath(pengenal)
        } ?: return HasilOperasi.Gagal("Gagal membangun NSURL dari pengenal: $pengenal", PenyebabGalat.VALIDASI)

        return HasilOperasi.Sukses(url)
    }

    /** Mengonversi [NSURL] ke [LokasiBerkas]. */
    fun keLokasi(url: NSURL, label: String? = null): LokasiBerkas {
        val pengenal = url.absoluteString ?: url.path ?: ""
        val namaTampilan = label ?: url.lastPathComponent ?: pengenal
        return LokasiBerkas(
            pengenal = pengenal,
            jenis = JenisLokasi.IOS,
            label = namaTampilan
        )
    }

    /** Membangun [DataFolder] untuk navigasi folder induk. */
    fun keDataFolder(url: NSURL): DataFolder {
        val lokasi = keLokasi(url)
        val parentUrl = url.URLByDeletingLastPathComponent
        val parentPengenal = parentUrl?.let { if (it.path != url.path) it.absoluteString ?: it.path else null }
        return DataFolder(
            pengenal = lokasi.pengenal,
            nama = lokasi.label ?: url.lastPathComponent ?: "",
            jenisLokasi = JenisLokasi.IOS,
            indukPengenal = parentPengenal
        )
    }

    /**
     * Memetakan [NSURL] beserta atribut hasil query ke [EntitasBerkas].
     */
    fun keEntitasBerkas(url: NSURL, atribut: Map<Any?, *>? = null): EntitasBerkas {
        val lokasi = keLokasi(url)
        val fileManager = NSFileManager.defaultManager
        val nama = (atribut?.get(NSURLNameKey) as? String)?.takeIf { it.isNotBlank() }
            ?: url.lastPathComponent?.takeIf { it.isNotBlank() }
            ?: "berkas"
        val isDir = (atribut?.get(NSURLIsDirectoryKey) as? NSNumber)?.boolValue
            ?: run {
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    val values = url.resourceValuesForKeys(listOf(NSURLIsDirectoryKey), errorPtr.ptr)
                    (values?.get(NSURLIsDirectoryKey) as? NSNumber)?.boolValue ?: false
                }
            }
        val isSymlink = (atribut?.get(NSURLIsSymbolicLinkKey) as? NSNumber)?.boolValue ?: false
        val ukuran = if (isDir) null else {
            (atribut?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue?.takeIf { it >= 0L }
        }
        val waktuUbahMs = (atribut?.get(NSURLContentModificationDateKey) as? NSDate)?.let {
            (it.timeIntervalSince1970 * 1000.0).toLong().takeIf { ms -> ms >= 0L }
        }
        val isHidden = (atribut?.get(NSURLIsHiddenKey) as? NSNumber)?.boolValue
            ?: nama.startsWith(".")
        val path = url.path
        val dapatDitulis = if (path != null) fileManager.isWritableFileAtPath(path) else true
        val dapatDibaca = if (path != null) fileManager.isReadableFileAtPath(path) else true
        val data = DataBerkas(
            pengenal = lokasi.pengenal,
            nama = nama,
            jenisLokasi = JenisLokasi.IOS,
            adalahFolder = isDir,
            adalahTautan = isSymlink,
            ukuranByte = ukuran,
            waktuUbahEpochMs = waktuUbahMs,
            tersembunyi = isHidden,
            dapatDibaca = dapatDibaca,
            dapatDitulis = dapatDitulis
        )
        return data.keDomain()
    }
}