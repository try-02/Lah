package org.fm.com.presentation.navigasi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun NavigasiUtama(
    tabAktif: TabUtama,
    onPilihTab: (TabUtama) -> Unit,
    jumlahTabEditor: Int,
    onBukaEditor: () -> Unit,
    modifier: Modifier = Modifier,
    namaTabEditorAktif: String? = null,
    kontenTab: @Composable (TabUtama) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layarLebar = maxWidth >= 600.dp

        if (layarLebar) {
            Row(modifier = Modifier.fillMaxSize()) {
                val warnaRail = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    TabUtama.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tabAktif == tab,
                            onClick = { onPilihTab(tab) },
                            colors = warnaRail,
                            label = {
                                Text(
                                    text = tab.judul,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = ikonUntukTab(tab),
                                    contentDescription = tab.deskripsiAksesibilitas,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                    if (jumlahTabEditor > 0) {
                        Spacer(modifier = Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = onBukaEditor,
                            colors = warnaRail,
                            label = {
                                Text(
                                    text = "Editor ($jumlahTabEditor)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = IkonAplikasi.Editor,
                                    contentDescription = "Kembali ke ruang kerja Editor",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
                VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    kontenTab(tabAktif)
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (jumlahTabEditor > 0) {
                            BilahEditorTerbuka(
                                jumlahTab = jumlahTabEditor,
                                namaTabAktif = namaTabEditorAktif,
                                onBukaEditor = onBukaEditor
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        val warnaItem = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp
                        ) {
                            TabUtama.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = tabAktif == tab,
                                    onClick = { onPilihTab(tab) },
                                    colors = warnaItem,
                                    label = {
                                        Text(
                                            text = tab.judul,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 10.5.sp
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = ikonUntukTab(tab),
                                            contentDescription = tab.deskripsiAksesibilitas,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { bantalan ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bantalan)
                ) {
                    kontenTab(tabAktif)
                }
            }
        }
    }
}

@Composable
private fun BilahEditorTerbuka(
    jumlahTab: Int,
    namaTabAktif: String?,
    onBukaEditor: () -> Unit
) {
    val fontMono = LocalFontMono.current
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onBukaEditor)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = IkonAplikasi.Editor,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (!namaTabAktif.isNullOrBlank()) {
                    "Editor ($jumlahTab) · $namaTabAktif"
                } else {
                    "Buka Ruang Kerja Editor ($jumlahTab tab)"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = fontMono,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Buka →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun ikonUntukTab(tab: TabUtama): ImageVector = when (tab) {
    TabUtama.FILE -> IkonAplikasi.Folder
    TabUtama.TERBARU -> IkonAplikasi.Jam
    TabUtama.BOOKMARK -> IkonAplikasi.Bookmark
    TabUtama.PENCARIAN -> IkonAplikasi.Cari
    TabUtama.PENGATURAN -> IkonAplikasi.Pengaturan
}
