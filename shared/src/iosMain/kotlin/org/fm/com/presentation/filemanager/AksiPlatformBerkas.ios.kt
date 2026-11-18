@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.fm.com.presentation.filemanager

import org.fm.com.domain.model.LokasiBerkas
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController

class AksiPlatformBerkasIos : AksiPlatformBerkas {

    override fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val url = if (lokasi.pengenal.startsWith("file://")) {
            NSURL.URLWithString(lokasi.pengenal)
        } else {
            NSURL.fileURLWithPath(lokasi.pengenal)
        } ?: return

        val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(activityVC, animated = true, completion = null)
    }

    override fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val url = if (lokasi.pengenal.startsWith("file://")) {
            NSURL.URLWithString(lokasi.pengenal)
        } else {
            NSURL.fileURLWithPath(lokasi.pengenal)
        } ?: return

        val docController = UIDocumentInteractionController.interactionControllerWithURL(url)
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        val view = rootVC?.view ?: return
        docController.presentOpenInMenuFromRect(view.bounds, inView = view, animated = true)
    }
}