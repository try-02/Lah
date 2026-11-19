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
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForDeleting
import platform.Foundation.NSFileCoordinatorWritingForMoving
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationMinimalBookmark
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create

data class HasilBookmarkTerselesaikan(
    val url: NSURL,
    val bookmarkBaruBase64: String? = null
)

object PenyimpanBookmarkKeamanan {
    fun buatBookmarkBase64(url: NSURL): HasilOperasi<String> = denganAksesKeamanan(url) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val bookmarkData = url.bookmarkDataWithOptions(
                options = NSURLBookmarkCreationMinimalBookmark,
                includingResourceValuesForKeys = null,
                relativeToURL = null,
                error = errorPtr.ptr
            )
            if (bookmarkData == null) {
                val err = errorPtr.value
                return@denganAksesKeamanan HasilOperasi.Gagal(
                    err?.localizedDescription ?: "Gagal membuat bookmark keamanan iOS",
                    PenyebabGalat.IZIN_DITOLAK
                )
            }
            HasilOperasi.Sukses(bookmarkData.base64EncodedStringWithOptions(0uL))
        }
    }

    fun selesaikanBookmarkBase64(base64: String): HasilOperasi<NSURL> =
        when (val hasil = selesaikanDanSegarkanBookmark(base64)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(hasil.nilai.url)
            is HasilOperasi.Gagal -> hasil
        }

    fun selesaikanDanSegarkanBookmark(base64: String): HasilOperasi<HasilBookmarkTerselesaikan> = memScoped {
        if (base64.isBlank()) {
            return HasilOperasi.Gagal("Data bookmark Base64 kosong", PenyebabGalat.VALIDASI)
        }
        val data = NSData.create(base64EncodedString = base64, options = 0uL)
            ?: return HasilOperasi.Gagal("Format data bookmark Base64 tidak valid", PenyebabGalat.VALIDASI)
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val isStalePtr = alloc<BooleanVar>()
        val url = NSURL.URLByResolvingBookmarkData(
            bookmarkData = data,
            options = NSURLBookmarkResolutionWithoutUI,
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
        val bookmarkBaru = if (isStalePtr.value) {
            when (val segar = buatBookmarkBase64(url)) {
                is HasilOperasi.Sukses -> segar.nilai
                is HasilOperasi.Gagal -> null
            }
        } else {
            null
        }
        HasilOperasi.Sukses(HasilBookmarkTerselesaikan(url = url, bookmarkBaruBase64 = bookmarkBaru))
    }

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

    suspend fun <T> denganAksesKeamananGandaSuspend(
        urlPertama: NSURL,
        urlKedua: NSURL?,
        blok: suspend () -> HasilOperasi<T>
    ): HasilOperasi<T> {
        val lepasPertama = urlPertama.startAccessingSecurityScopedResource()
        val lepasKedua = if (urlKedua != null && urlKedua.path != urlPertama.path) {
            urlKedua.startAccessingSecurityScopedResource()
        } else {
            false
        }
        return try {
            blok()
        } finally {
            if (lepasKedua && urlKedua != null) {
                urlKedua.stopAccessingSecurityScopedResource()
            }
            if (lepasPertama) {
                urlPertama.stopAccessingSecurityScopedResource()
            }
        }
    }

    fun <T> koordinasiBaca(url: NSURL, aksi: (NSURL) -> HasilOperasi<T>): HasilOperasi<T> = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        var hasilAksi: HasilOperasi<T>? = null
        val coordinator = NSFileCoordinator(filePresenter = null)
        coordinator.coordinateReadingItemAtURL(
            url = url,
            options = 0uL,
            error = errorPtr.ptr
        ) { coordinatedUrl ->
            hasilAksi = aksi(coordinatedUrl ?: url)
        }
        hasilAksi ?: HasilOperasi.Gagal(
            errorPtr.value?.localizedDescription ?: "Gagal koordinasi baca berkas iOS",
            PenyebabGalat.IZIN_DITOLAK
        )
    }

    fun <T> koordinasiTulis(
        url: NSURL,
        untukMenghapus: Boolean = false,
        untukMengganti: Boolean = false,
        aksi: (NSURL) -> HasilOperasi<T>
    ): HasilOperasi<T> = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        var hasilAksi: HasilOperasi<T>? = null
        val opsi = when {
            untukMenghapus -> NSFileCoordinatorWritingForDeleting
            untukMengganti -> NSFileCoordinatorWritingForReplacing
            else -> 0uL
        }
        val coordinator = NSFileCoordinator(filePresenter = null)
        coordinator.coordinateWritingItemAtURL(
            url = url,
            options = opsi,
            error = errorPtr.ptr
        ) { coordinatedUrl ->
            hasilAksi = aksi(coordinatedUrl ?: url)
        }
        hasilAksi ?: HasilOperasi.Gagal(
            errorPtr.value?.localizedDescription ?: "Gagal koordinasi tulis berkas iOS",
            PenyebabGalat.IZIN_DITOLAK
        )
    }

    fun <T> koordinasiSalinAtauPindah(
        sumberUrl: NSURL,
        tujuanUrl: NSURL,
        pindah: Boolean,
        aksi: (NSURL, NSURL) -> HasilOperasi<T>
    ): HasilOperasi<T> = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        var hasilAksi: HasilOperasi<T>? = null
        val coordinator = NSFileCoordinator(filePresenter = null)
        if (pindah) {
            coordinator.coordinateWritingItemAtURL(
                url1 = sumberUrl,
                options1 = NSFileCoordinatorWritingForMoving,
                writingItemAtURL = tujuanUrl,
                options2 = NSFileCoordinatorWritingForReplacing,
                error = errorPtr.ptr
            ) { coordSumber, coordTujuan ->
                hasilAksi = aksi(coordSumber ?: sumberUrl, coordTujuan ?: tujuanUrl)
            }
        } else {
            coordinator.coordinateReadingItemAtURL(
                readingURL = sumberUrl,
                options = 0uL,
                writingItemAtURL = tujuanUrl,
                options = NSFileCoordinatorWritingForReplacing,
                error = errorPtr.ptr
            ) { coordSumber, coordTujuan ->
                hasilAksi = aksi(coordSumber ?: sumberUrl, coordTujuan ?: tujuanUrl)
            }
        }
        hasilAksi ?: HasilOperasi.Gagal(
            errorPtr.value?.localizedDescription ?: "Gagal koordinasi salin/pindah berkas iOS",
            PenyebabGalat.IZIN_DITOLAK
        )
    }
}