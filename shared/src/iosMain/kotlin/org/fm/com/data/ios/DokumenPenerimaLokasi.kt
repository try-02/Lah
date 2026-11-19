package org.fm.com.data.ios

import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

data class HasilPemilihanLokasiIos(
    val lokasi: LokasiBerkas,
    val bookmarkBase64: String?
)

class DokumenPenerimaLokasi : NSObject(), UIDocumentPickerDelegateProtocol {
    var onLokasiDipilih: ((HasilOperasi<LokasiBerkas>) -> Unit)? = null
    var onLokasiDenganBookmarkDipilih: ((HasilOperasi<HasilPemilihanLokasiIos>) -> Unit)? = null

    fun buatPickerFolder(direktoriAwal: NSURL? = null): UIDocumentPickerViewController {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
        picker.delegate = this
        picker.allowsMultipleSelection = false
        if (direktoriAwal != null) {
            picker.directoryURL = direktoriAwal
        }
        return picker
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            val gagal = HasilOperasi.Gagal("Tidak ada folder yang dipilih", PenyebabGalat.VALIDASI)
            onLokasiDipilih?.invoke(gagal)
            onLokasiDenganBookmarkDipilih?.invoke(gagal)
            return
        }
        val bookmarkBase64 = when (val bm = PenyimpanBookmarkKeamanan.buatBookmarkBase64(url)) {
            is HasilOperasi.Sukses -> bm.nilai
            is HasilOperasi.Gagal -> null
        }
        val lokasi = DokumenPemetaanLokasi.keLokasi(url)
        onLokasiDipilih?.invoke(HasilOperasi.Sukses(lokasi))
        onLokasiDenganBookmarkDipilih?.invoke(
            HasilOperasi.Sukses(HasilPemilihanLokasiIos(lokasi = lokasi, bookmarkBase64 = bookmarkBase64))
        )
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        val batal = HasilOperasi.Gagal("Pemilihan folder dibatalkan pengguna", PenyebabGalat.VALIDASI)
        onLokasiDipilih?.invoke(batal)
        onLokasiDenganBookmarkDipilih?.invoke(batal)
    }
}