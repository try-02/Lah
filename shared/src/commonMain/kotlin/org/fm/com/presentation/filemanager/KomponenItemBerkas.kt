package org.fm.com.presentation.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fm.com.presentation.komponen.IkonAplikasi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemBerkasBaris(
    item: ItemBerkasUi,
    terpilih: Boolean,
    modeSeleksi: Boolean,
    onKlik: () -> Unit,
    onTekanLama: () -> Unit,
    onKlikMenu: () -> Unit = onTekanLama,
    modifier: Modifier = Modifier
) {
    val latar = if (terpilih) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(latar)
            .alpha(if (item.tersembunyi) 0.72f else 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onKlik,
                    onLongClick = onTekanLama
                )
                .padding(start = 14.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (modeSeleksi) {
                Checkbox(
                    checked = terpilih,
                    onCheckedChange = { onKlik() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            KomponenIkonBerkas(jenis = item.jenis, ukuran = 34.dp)
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nama,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.adalahFolder) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.adalahFolder) "Folder" else item.ukuranFormat,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = if (item.adalahFolder) FontFamily.SansSerif else FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "  •  ${item.tanggalFormat}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (!item.dapatDitulis) {
                        Text(
                            text = "  • RO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            if (!modeSeleksi) {
                IconButton(
                    onClick = onKlikMenu,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = IkonAplikasi.MenuLainnya,
                        contentDescription = "Menu aksi ${item.nama}",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 62.dp)
        )
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
            .padding(5.dp)
            .alpha(if (item.tersembunyi) 0.72f else 1f)
            .combinedClickable(
                onClick = onKlik,
                onLongClick = onTekanLama
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (terpilih) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (modeSeleksi) {
                    Checkbox(
                        checked = terpilih,
                        onCheckedChange = { onKlik() }
                    )
                }
                KomponenIkonBerkas(jenis = item.jenis, ukuran = 42.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.nama,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.adalahFolder) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.adalahFolder) "Folder" else item.ukuranFormat,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (item.adalahFolder) FontFamily.SansSerif else FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}