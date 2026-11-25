package org.fm.com.presentation.filemanager

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class AksiPlatformBerkasAndroid(
    private val context: Context
) : AksiPlatformBerkas {
    override fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val uri = resolusiUriAman(lokasi) ?: run {
            tampilkanInfo("Lokasi berkas tidak dapat dibagikan ke aplikasi lain")
            return
        }
        val mime = mimeType ?: tebakMimeType(nama)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, nama)
            clipData = ClipData.newRawUri(nama, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        luncurkanChooser(shareIntent, "Bagikan $nama")
    }

    override fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val uri = resolusiUriAman(lokasi) ?: run {
            tampilkanInfo("Lokasi berkas tidak dapat dibuka dengan aplikasi eksternal")
            return
        }
        val mime = mimeType ?: tebakMimeType(nama)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newRawUri(nama, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        luncurkanChooser(viewIntent, "Buka dengan")
    }

    private fun resolusiUriAman(lokasi: LokasiBerkas): Uri? {
        return when (lokasi.jenis) {
            JenisLokasi.SAF -> runCatching { lokasi.pengenal.toUri() }.getOrNull()
            JenisLokasi.POSIX -> runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(lokasi.pengenal)
                )
            }.getOrNull()
            JenisLokasi.IOS, JenisLokasi.SFTP -> null
        }
    }

    private fun luncurkanChooser(target: Intent, judul: String) {
        try {
            val chooser = Intent.createChooser(target, judul).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            tampilkanInfo("Tidak ada aplikasi yang dapat menangani berkas ini")
        } catch (_: SecurityException) {
            tampilkanInfo("Izin akses berkas ditolak oleh sistem")
        }
    }

    private fun tebakMimeType(nama: String): String {
        val ext = nama.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun tampilkanInfo(pesan: String) {
        Toast.makeText(context.applicationContext, pesan, Toast.LENGTH_SHORT).show()
    }
}