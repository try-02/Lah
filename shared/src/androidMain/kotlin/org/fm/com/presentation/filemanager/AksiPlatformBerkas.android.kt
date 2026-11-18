package org.fm.com.presentation.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.fm.com.domain.model.LokasiBerkas

class AksiPlatformBerkasAndroid(
    private val context: Context
) : AksiPlatformBerkas {

    override fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val uri = Uri.parse(lokasi.pengenal)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, nama)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Bagikan $nama").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    override fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val uri = Uri.parse(lokasi.pengenal)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(viewIntent, "Buka dengan").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}