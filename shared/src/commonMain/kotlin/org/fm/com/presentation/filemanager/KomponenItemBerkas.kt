package org.fm.com.presentation.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemBerkasBaris(
    item: ItemBerkasUi,
    terpilih: Boolean,
    modeSeleksi: Boolean,
    onKlik: () -> Unit,
    onTekanLama: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onKlik,
                onLongClick = onTekanLama
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (modeSeleksi) {
            Checkbox(
                checked = terpilih,
                onCheckedChange = { onKlik() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        KomponenIkonBerkas(jenis = item.jenis, ukuran = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.nama,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    text = item.ukuranFormat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.tanggalFormat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemBerkasKotak(
    item: ItemBerkasUi,
    terpilih: Boolean,
    modeSeleksi: Boolean,
    onKlik: () -> Unit,
    onTekanLama: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(6.dp)
            .combinedClickable(
                onClick = onKlik,
                onLongClick = onTekanLama
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (terpilih) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (modeSeleksi) {
                Checkbox(
                    checked = terpilih,
                    onCheckedChange = { onKlik() }
                )
            }
            KomponenIkonBerkas(jenis = item.jenis, ukuran = 48.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.nama,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.ukuranFormat,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}