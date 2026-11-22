package org.fm.com.presentation.komponen

import androidx.compose.runtime.Composable

@Composable
actual fun PenanganKembaliSistem(
    aktif: Boolean,
    onKembali: () -> Unit
) {
    // iOS tidak memiliki tombol kembali pada navigation bar sistem.
}