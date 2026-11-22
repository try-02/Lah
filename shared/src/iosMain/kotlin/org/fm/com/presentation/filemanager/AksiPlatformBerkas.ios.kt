@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.fm.com.presentation.filemanager

import org.fm.com.domain.model.LokasiBerkas
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

class AksiPlatformBerkasIos : AksiPlatformBerkas {

    override fun bagikanBerkas(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val url = keUrlAman(lokasi) ?: return
        val topVC = cariTopViewController() ?: return
        val view = topVC.view
        val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        activityVC.popoverPresentationController?.let { popover ->
            popover.sourceView = view
            popover.sourceRect = view.bounds
        }
        topVC.presentViewController(activityVC, animated = true, completion = null)
    }

    override fun bukaDengan(lokasi: LokasiBerkas, nama: String, mimeType: String?) {
        val url = keUrlAman(lokasi) ?: return
        val topVC = cariTopViewController() ?: return
        val view = topVC.view
        val docController = UIDocumentInteractionController.interactionControllerWithURL(url)
        docController.presentOpenInMenuFromRect(view.bounds, inView = view, animated = true)
    }

    private fun keUrlAman(lokasi: LokasiBerkas): NSURL? =
        if (lokasi.pengenal.startsWith("file://")) {
            NSURL.URLWithString(lokasi.pengenal)
        } else {
            NSURL.fileURLWithPath(lokasi.pengenal)
        }

    private fun cariTopViewController(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        val windowScene = scenes.firstOrNull {
            it is UIWindowScene && it.activationState == UISceneActivationStateForegroundActive
        } as? UIWindowScene ?: scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        val keyWindow = windowScene?.windows?.firstOrNull {
            (it as? UIWindow)?.isKeyWindow() == true
        } as? UIWindow ?: windowScene?.windows?.firstOrNull() as? UIWindow
        var top = keyWindow?.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}