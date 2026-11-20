package org.fm.com.presentation.filemanager

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun BreadcrumbLokasi(
    segmen: List<SegmenLokasi>,
    onPilihSegmen: (SegmenLokasi) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val fontMono = LocalFontMono.current

    LaunchedEffect(segmen.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segmen.forEachIndexed { indeks, item ->
            val adalahTerakhir = indeks == segmen.lastIndex
            Text(
                text = item.namaTampilan,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontMono,
                    fontSize = 12.sp,
                    fontWeight = if (adalahTerakhir) FontWeight.Medium else FontWeight.Normal
                ),
                color = if (adalahTerakhir) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable(enabled = !adalahTerakhir) { onPilihSegmen(item) }
                    .padding(vertical = 3.dp)
            )
            if (!adalahTerakhir) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        Text(
            text = " $",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}