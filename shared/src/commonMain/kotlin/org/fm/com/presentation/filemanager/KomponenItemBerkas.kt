package org.fm.com.presentation.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemBerkasBaris(
    item: ItemBerkasUi,
    terpilih: Boolean,
    modeSeleksi: Boolean,
    onKlik: () -> Unit,
    onTekanLama: () -> Unit,
    modifier: Modifier = Modifier,
    onKlikMenu: () -> Unit = onTekanLama
) {
    val fontMono = LocalFontMono.current
    val haptic = LocalHapticFeedback.current
    val latar = if (terpilih) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val badgeEkstensi = remember(item.nama, item.adalahFolder) {
        PemformatLokasiRamah.ekstensiPendek(item.nama, item.adalahFolder)
    }
    val metadataTeks = remember(item.ukuranFormat, item.tanggalFormat, item.adalahFolder, item.dapatDitulis) {
        PemformatLokasiRamah.formatMetadataBaris(
            ukuranFormat = item.ukuranFormat,
            tanggalPendek = item.tanggalFormat,
            adalahFolder = item.adalahFolder,
            dapatDitulis = item.dapatDitulis
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(latar)
            .alpha(if (item.tersembunyi) 0.65f else 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
                .combinedClickable(
                    onClick = onKlik,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTekanLama()
                    }
                )
                .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (modeSeleksi) {
                Checkbox(
                    checked = terpilih,
                    onCheckedChange = { onKlik() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            KomponenIkonBerkas(jenis = item.jenis, ukuran = 20.dp)
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nama,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = fontMono,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (terpilih) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeEkstensi != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeEkstensi,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = fontMono,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metadataTeks,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = fontMono,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!modeSeleksi) {
                IconButton(
                    onClick = onKlikMenu,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = IkonAplikasi.MenuLainnya,
                        contentDescription = "Aksi ${item.nama}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 50.dp)
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
    modifier: Modifier = Modifier,
    onKlikMenu: () -> Unit = onTekanLama
) {
    val fontMono = LocalFontMono.current
    val haptic = LocalHapticFeedback.current
    val latar = if (terpilih) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val warnaGaris = if (terpilih) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .alpha(if (item.tersembunyi) 0.65f else 1f)
            .clip(MaterialTheme.shapes.small)
            .background(latar)
            .border(width = 0.8.dp, color = warnaGaris, shape = MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onKlik,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTekanLama()
                }
            )
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KomponenIkonBerkas(jenis = item.jenis, ukuran = 20.dp)
                Spacer(modifier = Modifier.weight(1f))
                if (modeSeleksi) {
                    Checkbox(checked = terpilih, onCheckedChange = { onKlik() })
                } else {
                    IconButton(onClick = onKlikMenu, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = IkonAplikasi.MenuLainnya,
                            contentDescription = "Aksi ${item.nama}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.nama,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = fontMono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (item.adalahFolder) "dir" else item.ukuranFormat,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = fontMono,
                    fontSize = 11.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}