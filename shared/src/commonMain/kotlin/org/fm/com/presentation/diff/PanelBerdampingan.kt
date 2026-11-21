package org.fm.com.presentation.diff

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.diff.BarisDiffBerdampingan
import org.fm.com.domain.diff.JenisBarisDiff
import org.fm.com.domain.diff.SelBarisDiff
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun PanelBerdampingan(
    namaBerkasA: String,
    namaBerkasB: String,
    daftarBaris: List<BarisDiffBerdampingan>,
    indeksPerubahanAktif: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollHorizontalBersama = rememberScrollState()
    val fontMono = LocalFontMono.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layarSempit = maxWidth < 600.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Header nama berkas kiri (A) & kanan (B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A: $namaBerkasA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = fontMono,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = "B: $namaBerkasB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = fontMono,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                items(
                    items = daftarBaris,
                    key = { it.indeks }
                ) { baris ->
                    val aktif = baris.indeksBlokPerubahan != null &&
                        baris.indeksBlokPerubahan == indeksPerubahanAktif
                    val adalahPemisahKonteks = baris.kiri.jenis == JenisBarisDiff.KOSONG &&
                        baris.kanan.jenis == JenisBarisDiff.KOSONG &&
                        baris.kiri.teks.startsWith("@@")

                    if (adalahPemisahKonteks) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = baris.kiri.teks,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            SelBerdampinganPanel(
                                sel = baris.kiri,
                                disorotAktif = aktif,
                                scrollHorizontal = scrollHorizontalBersama,
                                layarSempit = layarSempit,
                                modifier = Modifier.weight(1f)
                            )
                            VerticalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.fillMaxHeight()
                            )
                            SelBerdampinganPanel(
                                sel = baris.kanan,
                                disorotAktif = aktif,
                                scrollHorizontal = scrollHorizontalBersama,
                                layarSempit = layarSempit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelBerdampinganPanel(
    sel: SelBarisDiff,
    disorotAktif: Boolean,
    scrollHorizontal: ScrollState,
    layarSempit: Boolean,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    val warnaLatar = warnaLatarBarisDiff(sel.jenis, disorotAktif)
    val warnaSorot = warnaSorotKarakterDiff(sel.jenis)
    val annotated = remember(sel.teks, sel.rentangKarakterBeda, warnaSorot) {
        bangunTeksDenganSorotKarakter(sel.teks, sel.rentangKarakterBeda, warnaSorot)
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(warnaLatar),
        verticalAlignment = Alignment.Top
    ) {
        GutterNomorBarisTunggal(
            nomorBaris = sel.nomorBaris,
            jenis = sel.jenis,
            lebarNomor = if (layarSempit) 28.dp else 36.dp,
            modifier = Modifier.fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollHorizontal)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontMono,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                softWrap = false,
                color = if (sel.jenis == JenisBarisDiff.KOSONG) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}