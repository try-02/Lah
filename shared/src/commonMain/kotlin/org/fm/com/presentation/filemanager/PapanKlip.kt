package org.fm.com.presentation.filemanager

import androidx.compose.ui.platform.Clipboard

/** Menyalin string teks ke papan klip sistem secara asinkron. */
expect suspend fun Clipboard.salinTeks(teks: String)