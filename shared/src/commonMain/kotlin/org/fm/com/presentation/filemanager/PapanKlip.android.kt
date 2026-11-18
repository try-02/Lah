package org.fm.com.presentation.filemanager

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

actual suspend fun Clipboard.salinTeks(teks: String) {
    setClipEntry(ClipData.newPlainText("teks", teks).toClipEntry())
}