package org.fm.com.presentation.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segmen.forEachIndexed { indeks, item ->
            val adalahTerakhir = indeks == segmen.lastIndex
            Text(
                text = item.namaTampilan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (adalahTerakhir) FontWeight.SemiBold else FontWeight.Normal,
                color = if (adalahTerakhir) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (adalahTerakhir) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .clickable(enabled = !adalahTerakhir) { onPilihSegmen(item) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            if (!adalahTerakhir) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}