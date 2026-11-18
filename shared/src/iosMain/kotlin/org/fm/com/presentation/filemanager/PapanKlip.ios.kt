package org.fm.com.presentation.filemanager

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

actual suspend fun Clipboard.salinTeks(teks: String) {
    UIPasteboard.generalPasteboard.string = teks
}