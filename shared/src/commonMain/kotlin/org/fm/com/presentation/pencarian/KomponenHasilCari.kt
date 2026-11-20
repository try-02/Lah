package org.fm.com.presentation.pencarian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun KartuHasilCariNama(
    item: ItemHasilCariNamaUi,
    onKlik: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    val badgeEkstensi = remember(item.nama, item.adalahFolder) {
        PemformatLokasiRamah.ekstensiPendek(item.nama, item.adalahFolder)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onKlik)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KomponenIkonBerkas(jenis = item.jenis, ukuran = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nama,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = fontMono,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.lokasiRelatif} · ${if (item.adalahFolder) "dir" else item.ukuranFormat}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 48.dp)
        )
    }
}

@Composable
fun KartuHasilCariIsi(
    item: ItemHasilCariIsiUi,
    onBukaPadaBaris: (EntitasBerkas, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val barisPertama = item.daftarBarisCocok.firstOrNull()?.nomorBaris ?: 1
                    onBukaPadaBaris(item.entitas, barisPertama)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KomponenIkonBerkas(jenis = item.entitas.jenis, ukuran = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nama,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = fontMono,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.lokasiRelatif,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${item.jumlahKecocokanTotal}×",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = fontMono,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item.daftarBarisCocok.forEach { baris ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBukaPadaBaris(item.entitas, baris.nomorBaris) }
                    .padding(start = 48.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${baris.nomorBaris}:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = fontMono,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = baris.potonganTeks,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = fontMono,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (item.daftarTerpotongBatas) {
            Text(
                text = "… sebagian hasil di berkas ini dipotong",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}