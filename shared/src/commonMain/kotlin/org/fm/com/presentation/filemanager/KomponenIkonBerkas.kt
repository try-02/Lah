package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.presentation.komponen.IkonAplikasi

@Composable
fun KomponenIkonBerkas(
    jenis: JenisBerkas,
    modifier: Modifier = Modifier,
    ukuran: Dp = 20.dp
) {
    val vector = when (jenis) {
        JenisBerkas.FOLDER -> IkonAplikasi.Folder
        JenisBerkas.TAUTAN -> IkonAplikasi.Tautan
        else -> IkonAplikasi.Berkas
    }
    Icon(
        imageVector = vector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(ukuran)
    )
}