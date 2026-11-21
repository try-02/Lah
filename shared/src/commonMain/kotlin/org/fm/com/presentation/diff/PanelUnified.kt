package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fm.com.domain.diff.BarisDiffUnified
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun PanelUnified(
    daftarBaris: List<BarisDiffUnified>,
    indeksPerubahanAktif: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val palet = ingatPaletPanelDiff(ukuranKodeSp = 12.5f)
    val fontMono = LocalFontMono.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)

    val nomorMaks = remember(daftarBaris) {
        var maks = 1
        for (b in daftarBaris) {
            b.nomorBarisA?.let { if (it > maks) maks = it }
            b.nomorBarisB?.let { if (it > maks) maks = it }
        }
        maks
    }
    val jumlahDigit = remember(nomorMaks) {
        nomorMaks.toString().length.coerceAtLeast(2)
    }

    val lebarTeksMaksPx = remember(daftarBaris, palet.gayaKode) {
        var t1 = ""
        var t2 = ""
        var t3 = ""
        for (b in daftarBaris) {
            if (b.keteranganPemisahKonteks != null) continue
            val t = b.teks
            if (t.length > t1.length) {
                t3 = t2; t2 = t1; t1 = t
            } else if (t.length > t2.length) {
                t3 = t2; t2 = t
            } else if (t.length > t3.length) {
                t3 = t
            }
        }
        hitungLebarMaksTeksPx(listOf(t1, t2, t3), palet.gayaKode, textMeasurer)
    }

    val tinggiBarisDp = remember(palet.gayaKode, density) {
        val tinggiTeksPx = textMeasurer.measure("Mg", style = palet.gayaKode, maxLines = 1).size.height
        with(density) { tinggiTeksPx.toDp() + 5.dp }
    }

    val lebarGutterTotalDp = remember(jumlahDigit, palet.gayaNomor, density) {
        val contoh = "${"8".repeat(jumlahDigit)} ${"8".repeat(jumlahDigit)} +"
        val lebarPx = textMeasurer.measure(contoh, style = palet.gayaNomor, maxLines = 1).size.width
        with(density) { lebarPx.toDp() + 14.dp }
    }

    var scrollHorizontalPx by remember(daftarBaris) { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val lebarAreaTeksDp = (maxWidth - lebarGutterTotalDp - 0.5.dp).coerceAtLeast(1.dp)
        val lebarAreaTeksPx = with(density) { lebarAreaTeksDp.toPx() }
        val paddingTeksPx = with(density) { 18.dp.toPx() }
        val maksScrollPx = ((lebarTeksMaksPx + paddingTeksPx) - lebarAreaTeksPx).coerceAtLeast(0f)

        LaunchedEffect(maksScrollPx) {
            if (scrollHorizontalPx > maksScrollPx) {
                scrollHorizontalPx = maksScrollPx
            }
        }

        val scrollableState = rememberScrollableState { delta ->
            if (maksScrollPx <= 0f) {
                0f
            } else {
                val lama = scrollHorizontalPx
                val baru = (lama + delta).coerceIn(0f, maksScrollPx)
                scrollHorizontalPx = baru
                baru - lama
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(palet.latarEditor)
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Horizontal,
                    reverseDirection = true,
                    flingBehavior = ScrollableDefaults.flingBehavior()
                )
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
                            .background(palet.latarHeader)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = pemisah,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            color = palet.teksSekunder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    val aktif = baris.indeksBlokPerubahan != null &&
                        baris.indeksBlokPerubahan == indeksPerubahanAktif
                    val warnaLatar = palet.latarBaris(baris.jenis, aktif)
                    val warnaSorot = palet.sorotKarakter(baris.jenis)
                    val annotated = remember(baris.teks, baris.rentangKarakterBeda, warnaSorot) {
                        bangunTeksDenganSorotKarakter(baris.teks, baris.rentangKarakterBeda, warnaSorot)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tinggiBarisDp)
                            .background(warnaLatar),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GutterNomorBarisUnified(
                            nomorBarisA = baris.nomorBarisA,
                            nomorBarisB = baris.nomorBarisB,
                            jenis = baris.jenis,
                            palet = palet,
                            jumlahDigit = jumlahDigit,
                            lebarGutter = lebarGutterTotalDp
                        )
                        VerticalDivider(
                            thickness = 0.5.dp,
                            color = palet.divider,
                            modifier = Modifier.fillMaxHeight()
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clipToBounds()
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = annotated,
                                style = palet.gayaKode,
                                softWrap = false,
                                maxLines = 1,
                                color = palet.teksUtama,
                                modifier = Modifier
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                    .graphicsLayer {
                                        translationX = -scrollHorizontalPx
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}