package org.fm.com.presentation.filemanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.domain.usecase.SegmenLokasi

@Composable
fun BreadcrumbLokasi(
    segmen: List<SegmenLokasi>,
    onPilihSegmen: (SegmenLokasi) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(segmen.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segmen.forEachIndexed { indeks, item ->
            val adalahTerakhir = indeks == segmen.lastIndex
            FilterChip(
                selected = adalahTerakhir,
                onClick = { onPilihSegmen(item) },
                label = { Text(item.namaTampilan) }
            )
            if (!adalahTerakhir) {
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}