package org.fm.com.presentation.komponen

import androidx.compose.runtime.Composable

@Composable
expect fun PenanganKembaliSistem(
    aktif: Boolean,
    onKembali: () -> Unit
)