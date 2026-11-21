### 1. Perbarui `LaunchedEffect` Lompat di `shared/src/commonMain/kotlin/org/fm/com/presentation/diff/LayarDiff.kt`
Ganti blok `LaunchedEffect(state.urutanNavigasi, state.modeTampilan)` (baris ~81–95) dengan:
```kotlin
    LaunchedEffect(state.urutanNavigasi, state.modeTampilan) {
        if (state.urutanNavigasi <= 0L) return@LaunchedEffect
        val aktif = state.perubahanAktif ?: return@LaunchedEffect
        when (state.modeTampilan) {
            ModeTampilanDiff.BERDAMPINGAN -> {
                val target = aktif.indeksBarisBerdampinganPertama
                if (target in state.barisBerdampingan.indices) {
                    val posisi = (target - 2).coerceAtLeast(0)
                    val jarak = kotlin.math.abs(posisi - listStateBerdampingan.firstVisibleItemIndex)
                    if (jarak <= 25) {
                        listStateBerdampingan.animateScrollToItem(posisi)
                    } else {
                        listStateBerdampingan.scrollToItem(posisi)
                    }
                }
            }
            ModeTampilanDiff.UNIFIED -> {
                val target = aktif.indeksBarisUnifiedPertama
                if (target in state.barisUnified.indices) {
                    val posisi = (target - 2).coerceAtLeast(0)
                    val jarak = kotlin.math.abs(posisi - listStateUnified.firstVisibleItemIndex)
                    if (jarak <= 25) {
                        listStateUnified.animateScrollToItem(posisi)
                    } else {
                        listStateUnified.scrollToItem(posisi)
                    }
                }
            }
        }
    }
```

---

### 2. Ringankan Node Gutter per Baris di `KomponenNomorBarisDiff.kt`, `PanelBerdampingan.kt`, & `PanelUnified.kt`

#### A. Ganti `GutterNomorBarisTunggal` dan `GutterNomorBarisUnified` di `KomponenNomorBarisDiff.kt`:
*(Hapus import `Arrangement`, `Row`, `TextAlign`, `Dp` jika tidak dipakai lagi di `KomponenNomorBarisDiff.kt`; tambahkan `import androidx.compose.foundation.layout.Box` dan `import androidx.compose.ui.unit.Dp`.)*
```kotlin
@Composable
fun GutterNomorBarisTunggal(
    nomorBaris: Int?,
    jenis: JenisBarisDiff,
    palet: PaletPanelDiff,
    jumlahDigit: Int,
    lebarGutter: Dp,
    modifier: Modifier = Modifier
) {
    val warnaSimbol = palet.warnaSimbol(jenis)
    val teksGutter = remember(nomorBaris, jenis, jumlahDigit, warnaSimbol) {
        val angka = nomorBaris?.toString()?.padStart(jumlahDigit, ' ') ?: " ".repeat(jumlahDigit)
        buildAnnotatedString {
            append(angka)
            append(' ')
            val awalSimbol = length
            append(jenis.simbol)
            addStyle(
                style = SpanStyle(color = warnaSimbol, fontWeight = FontWeight.Bold),
                start = awalSimbol,
                end = length
            )
        }
    }

    Box(
        modifier = modifier
            .width(lebarGutter)
            .fillMaxHeight()
            .background(palet.latarGutter)
            .padding(horizontal = 5.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} baris ${nomorBaris ?: "-"}"
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = teksGutter,
            style = palet.gayaNomor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun GutterNomorBarisUnified(
    nomorBarisA: Int?,
    nomorBarisB: Int?,
    jenis: JenisBarisDiff,
    palet: PaletPanelDiff,
    jumlahDigit: Int,
    lebarGutter: Dp,
    modifier: Modifier = Modifier
) {
    val warnaSimbol = palet.warnaSimbol(jenis)
    val teksGutter = remember(nomorBarisA, nomorBarisB, jenis, jumlahDigit, warnaSimbol) {
        val a = nomorBarisA?.toString()?.padStart(jumlahDigit, ' ') ?: " ".repeat(jumlahDigit)
        val b = nomorBarisB?.toString()?.padStart(jumlahDigit, ' ') ?: " ".repeat(jumlahDigit)
        buildAnnotatedString {
            append(a)
            append(' ')
            append(b)
            append(' ')
            val awalSimbol = length
            append(jenis.simbol)
            addStyle(
                style = SpanStyle(color = warnaSimbol, fontWeight = FontWeight.Bold),
                start = awalSimbol,
                end = length
            )
        }
    }

    Box(
        modifier = modifier
            .width(lebarGutter)
            .fillMaxHeight()
            .background(palet.latarGutter)
            .padding(horizontal = 6.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} A:${nomorBarisA ?: "-"} B:${nomorBarisB ?: "-"}"
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = teksGutter,
            style = palet.gayaNomor,
            maxLines = 1,
            softWrap = false
        )
    }
}
```

#### B. Sesuaikan Pemanggilan di `PanelBerdampingan.kt`:
Ubah `lebarAngkaDp` & `lebarGutterTotalDp` menjadi:
```kotlin
    val lebarGutterTotalDp = remember(jumlahDigit, palet.gayaNomor, density) {
        val contoh = "${"8".repeat(jumlahDigit)} +"
        val lebarPx = textMeasurer.measure(contoh, style = palet.gayaNomor, maxLines = 1).size.width
        with(density) { lebarPx.toDp() + 12.dp }
    }
```
Teruskan `jumlahDigit = jumlahDigit` & `lebarGutter = lebarGutterTotalDp` ke `SelBerdampinganPanel`, lalu di dalamnya panggil:
```kotlin
        GutterNomorBarisTunggal(
            nomorBaris = sel.nomorBaris,
            jenis = sel.jenis,
            palet = palet,
            jumlahDigit = jumlahDigit,
            lebarGutter = lebarGutter
        )
```

#### C. Sesuaikan Pemanggilan di `PanelUnified.kt`:
Ubah `lebarKolomNomorDp` & `lebarGutterTotalDp` menjadi:
```kotlin
    val lebarGutterTotalDp = remember(jumlahDigit, palet.gayaNomor, density) {
        val contoh = "${"8".repeat(jumlahDigit)} ${"8".repeat(jumlahDigit)} +"
        val lebarPx = textMeasurer.measure(contoh, style = palet.gayaNomor, maxLines = 1).size.width
        with(density) { lebarPx.toDp() + 14.dp }
    }
```
*(Pakai `lebarGutterTotalDp + 0.5.dp` saat menghitung `lebarAreaTeksDp`.)*
Lalu pada baris item di `PanelUnified.kt`:
```kotlin
                    GutterNomorBarisUnified(
                        nomorBarisA = baris.nomorBarisA,
                        nomorBarisB = baris.nomorBarisB,
                        jenis = baris.jenis,
                        palet = palet,
                        jumlahDigit = jumlahDigit,
                        lebarGutter = lebarGutterTotalDp
                    )
```

---