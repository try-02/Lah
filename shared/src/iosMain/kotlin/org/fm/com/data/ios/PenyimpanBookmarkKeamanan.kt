@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationWithSecurityScope
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create

/**
 * Pengelola security-scoped bookmark iOS untuk mempertahankan akses izin direktori/berkas
 * di luar sandbox aplikasi (PRD §5.1).
 */
object PenyimpanBookmarkKeamanan {

    /**
     * Membuat security-scoped bookmark dari [url] dan mengembalikannya sebagai string Base64.
     */
    fun buatBookmarkBase64(url: NSURL): HasilOperasi<String> = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val bookmarkData = url.bookmarkDataWithOptions(
            options = NSURLBookmarkCreationWithSecurityScope,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = errorPtr.ptr
        )
        if (bookmarkData == null) {
            val err = errorPtr.value
            return HasilOperasi.Gagal(
                err?.localizedDescription ?: "Gagal membuat bookmark keamanan iOS",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val base64 = bookmarkData.base64EncodedStringWithOptions(0uL)
        HasilOperasi.Sukses(base64)
    }

    /**
     * Menyelesaikan string Base64 bookmark menjadi [NSURL] yang siap diakses.
     */
    fun selesaikanBookmarkBase64(base64: String): HasilOperasi<NSURL> = memScoped {
        if (base64.isBlank()) {
            return HasilOperasi.Gagal("Data bookmark Base64 kosong", PenyebabGalat.VALIDASI)
        }
        val data = NSData.create(base64EncodedString = base64, options = 0uL)
            ?: return HasilOperasi.Gagal("Format data bookmark Base64 tidak valid", PenyebabGalat.VALIDASI)

        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val isStalePtr = alloc<BooleanVar>()

        val url = NSURL.URLByResolvingBookmarkData(
            bookmarkData = data,
            options = NSURLBookmarkResolutionWithSecurityScope,
            relativeToURL = null,
            bookmarkDataIsStale = isStalePtr.ptr,
            error = errorPtr.ptr
        )

        if (url == null) {
            val err = errorPtr.value
            return HasilOperasi.Gagal(
                err?.localizedDescription ?: "Gagal menyelesaikan bookmark keamanan",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        HasilOperasi.Sukses(url)
    }

    /**
     * Menjalankan blok operasi sinkron dengan jaminan `startAccessingSecurityScopedResource`
     * dan pelepasan `stopAccessingSecurityScopedResource` pada `finally`.
     */
    inline fun <T> denganAksesKeamanan(url: NSURL, blok: () -> HasilOperasi<T>): HasilOperasi<T> {
        val perluLepas = url.startAccessingSecurityScopedResource()
        return try {
            blok()
        } finally {
            if (perluLepas) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }

    /**
     * Menjalankan blok operasi suspend dengan jaminan pelepasan security scope.
     */
    suspend fun <T> denganAksesKeamananSuspend(
        url: NSURL,
        blok: suspend () -> HasilOperasi<T>
    ): HasilOperasi<T> {
        val perluLepas = url.startAccessingSecurityScopedResource()
        return try {
            blok()
        } finally {
            if (perluLepas) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}