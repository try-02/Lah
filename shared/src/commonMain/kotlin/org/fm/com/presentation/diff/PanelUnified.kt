package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.diff.BarisDiffUnified
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun PanelUnified(
    daftarBaris: List<BarisDiffUnified>,
    indeksPerubahanAktif: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollHorizontal = rememberScrollState()
    val fontMono = LocalFontMono.current

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        items(
            items = daftarBaris,
            key = { it.indeks }
        ) { baris ->
            val pemisah = baris.keteranganPemisahKonteks
            if (pemisah != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = pemisah,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val aktif = baris.indeksBlokPerubahan != null &&
                    baris.indeksBlokPerubahan == indeksPerubahanAktif
                val warnaLatar = warnaLatarBarisDiff(baris.jenis, aktif)
                val warnaSorot = warnaSorotKarakterDiff(baris.jenis)
                val annotated = remember(baris.teks, baris.rentangKarakterBeda, warnaSorot) {
                    bangunTeksDenganSorotKarakter(baris.teks, baris.rentangKarakterBeda, warnaSorot)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(warnaLatar),
                    verticalAlignment = Alignment.Top
                ) {
                    GutterNomorBarisUnified(
                        nomorBarisA = baris.nomorBarisA,
                        nomorBarisB = baris.nomorBarisB,
                        jenis = baris.jenis,
                        modifier = Modifier.fillMaxHeight()
                    )
                    VerticalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollHorizontal)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = fontMono,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            softWrap = false,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}