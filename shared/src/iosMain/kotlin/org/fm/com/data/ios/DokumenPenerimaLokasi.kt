package org.fm.com.data.ios

import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/**
 * Penerima hasil Document Picker iOS untuk memilih direktori pohon berkas.
 */
class DokumenPenerimaLokasi : NSObject(), UIDocumentPickerDelegateProtocol {

    var onLokasiDipilih: ((HasilOperasi<LokasiBerkas>) -> Unit)? = null

    /**
     * Membuat [UIDocumentPickerViewController] untuk membuka folder direktori.
     */
    fun buatPickerFolder(): UIDocumentPickerViewController {
        val tipeFolder = listOf("public.folder")
        val picker = UIDocumentPickerViewController(
            documentTypes = tipeFolder,
            inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen
        )
        picker.delegate = this
        picker.allowsMultipleSelection = false
        return picker
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onLokasiDipilih?.invoke(HasilOperasi.Gagal("Tidak ada dokumen yang dipilih", PenyebabGalat.VALIDASI))
            return
        }
        val bookmarkHasil = PenyimpanBookmarkKeamanan.buatBookmarkBase64(url)
        val lokasi = DokumenPemetaanLokasi.keLokasi(url)
        onLokasiDipilih?.invoke(HasilOperasi.Sukses(lokasi))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onLokasiDipilih?.invoke(HasilOperasi.Gagal("Pemilihan folder dibatalkan pengguna", PenyebabGalat.VALIDASI))
    }
}