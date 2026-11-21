package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val palet = ingatPaletPanelDiff(ukuranKodeSp = 12f)
    val fontMono = LocalFontMono.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)

    val nomorMaks = remember(daftarBaris) {
        var maks = 1
        for (b in daftarBaris) {
            b.kiri.nomorBaris?.let { if (it > maks) maks = it }
            b.kanan.nomorBaris?.let { if (it > maks) maks = it }
        }
        maks
    }
    val jumlahDigit = remember(nomorMaks) {
        nomorMaks.toString().length.coerceAtLeast(2)
    }

    val lebarTeksMaksPx = remember(daftarBaris, palet.gayaKode) {
        val kandidat = ArrayList<String>(4)
        var t1 = ""
        var t2 = ""
        var t3 = ""
        for (b in daftarBaris) {
            val k = b.kiri.teks
            if (k.length > t1.length) {
                t3 = t2; t2 = t1; t1 = k
            } else if (k.length > t2.length) {
                t3 = t2; t2 = k
            } else if (k.length > t3.length) {
                t3 = k
            }
            val n = b.kanan.teks
            if (n.length > t1.length) {
                t3 = t2; t2 = t1; t1 = n
            } else if (n.length > t2.length) {
                t3 = t2; t2 = n
            } else if (n.length > t3.length) {
                t3 = n
            }
        }
        kandidat.add(t1)
        kandidat.add(t2)
        kandidat.add(t3)
        hitungLebarMaksTeksPx(kandidat, palet.gayaKode, textMeasurer)
    }

    val tinggiBarisDp = remember(palet.gayaKode, density) {
        val tinggiTeksPx = textMeasurer.measure("Mg", style = palet.gayaKode, maxLines = 1).size.height
        with(density) { tinggiTeksPx.toDp() + 5.dp }
    }

    val lebarGutterTotalDp = remember(jumlahDigit, palet.gayaNomor, density) {
        val contoh = "${"8".repeat(jumlahDigit)} +"
        val lebarPx = textMeasurer.measure(contoh, style = palet.gayaNomor, maxLines = 1).size.width
        with(density) { lebarPx.toDp() + 12.dp }
    }

    var scrollHorizontalPx by remember(daftarBaris) { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val lebarPanelDp = ((maxWidth - 0.5.dp) / 2f).coerceAtLeast(1.dp)
        val lebarAreaTeksDp = (lebarPanelDp - lebarGutterTotalDp).coerceAtLeast(1.dp)
        val lebarAreaTeksPx = with(density) { lebarAreaTeksDp.toPx() }
        val paddingTeksPx = with(density) { 14.dp.toPx() }
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

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palet.latarHeader)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A: $namaBerkasA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = fontMono,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palet.teksUtama,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = palet.divider,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = "B: $namaBerkasB",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = fontMono,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = palet.teksUtama,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = palet.divider)

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
                    val adalahPemisahKonteks = baris.kiri.jenis == JenisBarisDiff.KOSONG &&
                        baris.kanan.jenis == JenisBarisDiff.KOSONG &&
                        baris.kiri.teks.startsWith("@@")

                    if (adalahPemisahKonteks) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palet.latarHeader)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = baris.kiri.teks,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = palet.teksSekunder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        val aktif = baris.indeksBlokPerubahan != null &&
                            baris.indeksBlokPerubahan == indeksPerubahanAktif
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(tinggiBarisDp)
                        ) {
                            SelBerdampinganPanel(
                                sel = baris.kiri,
                                disorotAktif = aktif,
                                palet = palet,
                                jumlahDigit = jumlahDigit,
                                lebarGutter = lebarGutterTotalDp,
                                bacaScrollHorizontalPx = { scrollHorizontalPx },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            VerticalDivider(
                                thickness = 0.5.dp,
                                color = palet.divider,
                                modifier = Modifier.fillMaxHeight()
                            )
                            SelBerdampinganPanel(
                                sel = baris.kanan,
                                disorotAktif = aktif,
                                palet = palet,
                                jumlahDigit = jumlahDigit,
                                lebarGutter = lebarGutterTotalDp,
                                bacaScrollHorizontalPx = { scrollHorizontalPx },
                                modifier = Modifier.weight(1f).fillMaxHeight()
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
    palet: PaletPanelDiff,
    jumlahDigit: Int,
    lebarGutter: Dp,
    bacaScrollHorizontalPx: () -> Float,
    modifier: Modifier = Modifier
) {
    val warnaLatar = palet.latarBaris(sel.jenis, disorotAktif)
    val warnaSorot = palet.sorotKarakter(sel.jenis)
    val annotated = remember(sel.teks, sel.rentangKarakterBeda, warnaSorot) {
        bangunTeksDenganSorotKarakter(sel.teks, sel.rentangKarakterBeda, warnaSorot)
    }

    Row(
        modifier = modifier.background(warnaLatar),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GutterNomorBarisTunggal(
            nomorBaris = sel.nomorBaris,
            jenis = sel.jenis,
            palet = palet,
            jumlahDigit = jumlahDigit,
            lebarGutter = lebarGutter
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = annotated,
                style = palet.gayaKode,
                softWrap = false,
                maxLines = 1,
                color = if (sel.jenis == JenisBarisDiff.KOSONG) palet.teksSekunder else palet.teksUtama,
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer {
                        translationX = -bacaScrollHorizontalPx()
                    }
            )
        }
    }
}