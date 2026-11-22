@file:OptIn(ExperimentalMaterial3Api::class)

package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.diff.ModeWhitespaceDiff
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PenanganKembaliSistem
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun LayarDiff(
    viewModel: ViewModelDiff,
    onKembali: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    val listStateBerdampingan = rememberLazyListState()
    val listStateUnified = rememberLazyListState()
    var menuTerbuka by remember { mutableStateOf(false) }

    PenanganKembaliSistem(aktif = true, onKembali = onKembali)

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentDiff.SetPesanInfo(null))
        }
    }

    LaunchedEffect(state.patchUntukDisalin) {
        val patch = state.patchUntukDisalin ?: return@LaunchedEffect
        clipboardManager.setText(AnnotatedString(patch))
        viewModel.prosesIntent(IntentDiff.HapusPatchSiapSalin)
    }

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Bandingkan Berkas",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val stat = state.statistik
                        val ringkasan = if (stat != null) {
                            "+${stat.jumlahBarisTambah}  -${stat.jumlahBarisHapus} · ${stat.jumlahBlokPerubahan} blok"
                        } else {
                            "${state.berkasA?.nama ?: "A"} ↔ ${state.berkasB?.nama ?: "B"}"
                        }
                        Text(
                            text = ringkasan,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onKembali) {
                        Icon(
                            imageVector = IkonAplikasi.Kembali,
                            contentDescription = "Kembali",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    if (state.adaPerubahan) {
                        IconButton(onClick = { viewModel.prosesIntent(IntentDiff.LompatPerubahanSebelumnya) }) {
                            Icon(
                                imageVector = IkonAplikasi.Naik,
                                contentDescription = "Perubahan sebelumnya",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "${(state.indeksPerubahanAktif + 1).coerceAtLeast(1)}/${state.daftarPerubahan.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = fontMono,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { viewModel.prosesIntent(IntentDiff.LompatPerubahanBerikutnya) }) {
                            Icon(
                                imageVector = IkonAplikasi.Turun,
                                contentDescription = "Perubahan berikutnya",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.prosesIntent(IntentDiff.SetBukaDialogOpsi(true)) }) {
                        Icon(
                            imageVector = IkonAplikasi.Filter,
                            contentDescription = "Opsi perbandingan",
                            tint = if (state.opsi.adaPengabaian || !state.opsi.tampilkanBarisSama) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuTerbuka = true }) {
                            Icon(
                                imageVector = IkonAplikasi.MenuLainnya,
                                contentDescription = "Menu diff lainnya",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuTerbuka,
                            onDismissRequest = { menuTerbuka = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Lompat ke Beda Pertama") },
                                enabled = state.adaPerubahan,
                                onClick = {
                                    menuTerbuka = false
                                    viewModel.prosesIntent(IntentDiff.LompatBarisBedaPertama)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Salin Hasil Patch (Unified)") },
                                enabled = state.statistik != null,
                                onClick = {
                                    menuTerbuka = false
                                    viewModel.prosesIntent(IntentDiff.SiapkanEksporPatch)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Segarkan dari Disk") },
                                onClick = {
                                    menuTerbuka = false
                                    viewModel.prosesIntent(IntentDiff.SegarkanDariDisk)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            // Segmented mode tampilan: Berdampingan vs Unified
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val daftarMode = ModeTampilanDiff.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    daftarMode.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = state.modeTampilan == mode,
                            onClick = { viewModel.prosesIntent(IntentDiff.UbahModeTampilan(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = daftarMode.size),
                            icon = {},
                            label = {
                                Text(if (mode == ModeTampilanDiff.BERDAMPINGAN) "Berdampingan" else "Unified")
                            }
                        )
                    }
                }
            }

            state.pesanPengabaianAktif?.let { infoAbaikan ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Catatan: $infoAbaikan",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (state.terpotongBatasKompleksitas) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Sebagian blok sangat besar diringkas otomatis demi menjaga responsivitas",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.sedangMemuat || state.sedangMenghitung -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (state.sedangMemuat) {
                                    "membaca kedua berkas..."
                                } else {
                                    "menghitung perbedaan baris..."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.prosesIntent(IntentDiff.BatalkanProses) }) {
                                Text("Batalkan")
                            }
                        }
                    }
                    state.pesanGalat != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = state.pesanGalat!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = { viewModel.prosesIntent(IntentDiff.SegarkanDariDisk) }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                    state.statistik?.identik == true && !state.opsi.tampilkanBarisSama -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "// kedua berkas identik (0 perbedaan)",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    state.modeTampilan == ModeTampilanDiff.BERDAMPINGAN -> {
                        PanelBerdampingan(
                            namaBerkasA = state.berkasA?.nama ?: "A",
                            namaBerkasB = state.berkasB?.nama ?: "B",
                            daftarBaris = state.barisBerdampingan,
                            indeksPerubahanAktif = state.indeksPerubahanAktif,
                            listState = listStateBerdampingan
                        )
                    }
                    else -> {
                        PanelUnified(
                            daftarBaris = state.barisUnified,
                            indeksPerubahanAktif = state.indeksPerubahanAktif,
                            listState = listStateUnified
                        )
                    }
                }
            }
        }
    }

    if (state.dialogOpsiTerbuka) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.prosesIntent(IntentDiff.SetBukaDialogOpsi(false)) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            LembarOpsiDiff(
                state = state,
                onIntent = viewModel::prosesIntent,
                onTutup = { viewModel.prosesIntent(IntentDiff.SetBukaDialogOpsi(false)) }
            )
        }
    }
}

@Composable
private fun LembarOpsiDiff(
    state: StateDiff,
    onIntent: (IntentDiff) -> Unit,
    onTutup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Opsi Perbandingan", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onTutup) {
                Text("Selesai")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIntent(IntentDiff.TogglePekaHuruf) }
        ) {
            Checkbox(
                checked = state.opsi.pekaHuruf,
                onCheckedChange = { onIntent(IntentDiff.TogglePekaHuruf) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Peka huruf besar/kecil (Aa)", style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onIntent(IntentDiff.ToggleTampilkanBarisSama) }
        ) {
            Checkbox(
                checked = state.opsi.tampilkanBarisSama,
                onCheckedChange = { onIntent(IntentDiff.ToggleTampilkanBarisSama) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tampilkan seluruh baris sama", style = MaterialTheme.typography.bodyMedium)
        }

        Text("Penanganan Whitespace", style = MaterialTheme.typography.labelLarge)
        val wsList = ModeWhitespaceDiff.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            wsList.forEachIndexed { idx, mode ->
                val label = when (mode) {
                    ModeWhitespaceDiff.PERSIS -> "Persis"
                    ModeWhitespaceDiff.ABAIKAN_TEPI -> "Tepi"
                    ModeWhitespaceDiff.ABAIKAN_SEMUA -> "Abaikan"
                }
                SegmentedButton(
                    selected = state.opsi.modeWhitespace == mode,
                    onClick = { onIntent(IntentDiff.UbahModeWhitespace(mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = wsList.size),
                    icon = {},
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        if (!state.opsi.tampilkanBarisSama) {
            Text("Baris Konteks Sekitar Perubahan", style = MaterialTheme.typography.labelLarge)
            val konteksOpsi = listOf(0, 3, 5, 10)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                konteksOpsi.forEachIndexed { idx, jml ->
                    SegmentedButton(
                        selected = state.opsi.jumlahBarisKonteks == jml,
                        onClick = { onIntent(IntentDiff.UbahJumlahBarisKonteks(jml)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = konteksOpsi.size),
                        icon = {},
                        label = { Text("$jml") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}