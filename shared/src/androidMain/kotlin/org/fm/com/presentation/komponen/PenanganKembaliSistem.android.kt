package org.fm.com.presentation.komponen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PenanganKembaliSistem(
    aktif: Boolean,
    onKembali: () -> Unit
) {
    BackHandler(enabled = aktif, onBack = onKembali)
}