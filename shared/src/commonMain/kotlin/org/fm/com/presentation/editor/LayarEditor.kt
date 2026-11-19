package org.fm.com.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarEditor(
    viewModel: ViewModelEditor,
    onKembali: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuTerbuka by remember { mutableStateOf(false) }

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentEditor.SetPesanInfo(null))
        }
    }

    val tabAktif = state.tabAktif

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val judul = if (tabAktif != null) {
                        val tanda = if (tabAktif.adaPerubahanBelumTersimpan) " •" else ""
                        "${tabAktif.namaBerkas}$tanda"
                    } else {
                        "Editor Teks"
                    }
                    Text(judul, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (onKembali != null) {
                        TextButton(onClick = onKembali) {
                            Text("Tutup")
                        }
                    }
                },
                actions = {
                    if (tabAktif != null && tabAktif.statusMuat == StatusMuatTab.SIAP) {
                        TextButton(
                            onClick = { viewModel.prosesIntent(IntentEditor.Undo(tabAktif.idTab)) },
                            enabled = tabAktif.dapatUndo && tabAktif.dapatDiedit
                        ) {
                            Text("Undo")
                        }
                        TextButton(
                            onClick = { viewModel.prosesIntent(IntentEditor.Redo(tabAktif.idTab)) },
                            enabled = tabAktif.dapatRedo && tabAktif.dapatDiedit
                        ) {
                            Text("Redo")
                        }
                        Box {
                            IconButton(onClick = { menuTerbuka = true }) {
                                Text("⋮", style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(
                                expanded = menuTerbuka,
                                onDismissRequest = { menuTerbuka = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Lompat ke Baris...") },
                                    onClick = {
                                        menuTerbuka = false
                                        viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(true))
                                    }
                                )
                                val wrapAktif = tabAktif.wordWrapEfektif(state.wordWrapUmum)
                                DropdownMenuItem(
                                    text = { Text(if (wrapAktif) "Matikan Word Wrap" else "Aktifkan Word Wrap") },
                                    onClick = {
                                        menuTerbuka = false
                                        viewModel.prosesIntent(IntentEditor.ToggleWordWrapTab(tabAktif.idTab))
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                if (tabAktif.modeHanyaBaca) "Nonaktifkan Hanya Baca" else "Mode Hanya Baca"
                                            )
                                            if (tabAktif.hanyaBacaPermanen) {
                                                Text(
                                                    text = "Berkas atau lokasi tidak memiliki izin tulis",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    },
                                    enabled = !tabAktif.hanyaBacaPermanen,
                                    onClick = {
                                        menuTerbuka = false
                                        viewModel.prosesIntent(IntentEditor.ToggleModeHanyaBaca(tabAktif.idTab))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tutup Tab") },
                                    onClick = {
                                        menuTerbuka = false
                                        viewModel.prosesIntent(IntentEditor.MintaTutupTab(tabAktif.idTab))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Tab Kosong Baru") },
                                    onClick = {
                                        menuTerbuka = false
                                        viewModel.prosesIntent(IntentEditor.BuatTabKosong())
                                    }
                                )
                            }
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
            if (state.daftarTab.isNotEmpty()) {
                BarTabEditor(
                    daftarTab = state.daftarTab,
                    idTabAktif = state.idTabAktif,
                    onPilihTab = { viewModel.prosesIntent(IntentEditor.PilihTab(it)) },
                    onTutupTab = { viewModel.prosesIntent(IntentEditor.MintaTutupTab(it)) }
                )
                HorizontalDivider()
            }

            if (tabAktif == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Belum Ada Berkas Dibuka",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.prosesIntent(IntentEditor.BuatTabKosong()) }) {
                            Text("Buat Catatan Baru")
                        }
                    }
                }
            } else {
                when (tabAktif.statusMuat) {
                    StatusMuatTab.MEMUAT -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Memuat berkas...")
                            }
                        }
                    }
                    StatusMuatTab.BINER, StatusMuatTab.TERLALU_BESAR -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabAktif.pesanStatus ?: "Berkas tidak dapat dibuka di editor teks",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusMuatTab.GAGAL -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tabAktif.pesanStatus ?: "Gagal membuka berkas",
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.prosesIntent(IntentEditor.MuatUlangTab(tabAktif.idTab)) }) {
                                    Text("Coba Lagi")
                                }
                            }
                        }
                    }
                    StatusMuatTab.SIAP -> {
                        if (tabAktif.modeHanyaBaca) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (tabAktif.hanyaBacaPermanen) {
                                        "Mode Hanya Baca (berkas/penyimpanan tak dapat ditulis) — seleksi & salin tetap aktif"
                                    } else {
                                        "Mode Hanya Baca aktif — seleksi & salin tetap tersedia"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        AreaTeksEditor(
                            tab = tabAktif,
                            wordWrapAktif = tabAktif.wordWrapEfektif(state.wordWrapUmum),
                            onUbahTeks = { teksBaru, kursor ->
                                viewModel.prosesIntent(IntentEditor.UbahTeks(tabAktif.idTab, teksBaru, kursor))
                            },
                            onUbahKursor = { kursor ->
                                viewModel.prosesIntent(IntentEditor.UbahPosisiKursor(tabAktif.idTab, kursor))
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        HorizontalDivider()
                        StatusEditor(
                            tab = tabAktif,
                            wordWrapUmum = state.wordWrapUmum,
                            onKlikPosisiBaris = { viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(true)) },
                            onToggleWordWrap = { viewModel.prosesIntent(IntentEditor.ToggleWordWrapTab(tabAktif.idTab)) },
                            onToggleHanyaBaca = { viewModel.prosesIntent(IntentEditor.ToggleModeHanyaBaca(tabAktif.idTab)) }
                        )
                    }
                }
            }
        }
    }

    if (tabAktif != null) {
        DialogLompatBaris(
            terbuka = state.dialogLompatBarisTerbuka,
            jumlahBaris = tabAktif.jumlahBaris,
            barisSaatIni = tabAktif.posisiKursor.baris,
            onLompat = { nomor ->
                viewModel.prosesIntent(IntentEditor.LompatKeBaris(tabAktif.idTab, nomor))
            },
            onTutup = { viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(false)) }
        )
    }

    state.tabMenungguKonfirmasiTutup?.let { tabKotor ->
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentEditor.BatalTutupTab) },
            title = { Text("Perubahan Belum Tersimpan") },
            text = {
                Text("Berkas '${tabKotor.namaBerkas}' memiliki perubahan yang belum disimpan. Tutup tab dan buang perubahan?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentEditor.KonfirmasiBuangDanTutupTab) }) {
                    Text("Buang", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentEditor.BatalTutupTab) }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun BarTabEditor(
    daftarTab: List<ModelTabEditor>,
    idTabAktif: String?,
    onPilihTab: (String) -> Unit,
    onTutupTab: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = daftarTab, key = { it.idTab }) { tab ->
            val aktif = tab.idTab == idTabAktif
            Surface(
                color = if (aktif) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.clickable { onPilihTab(tab.idTab) }
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prefiks = if (tab.adaPerubahanBelumTersimpan) "• " else ""
                    Text(
                        text = "$prefiks${tab.namaBerkas}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .clickable { onTutupTab(tab.idTab) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AreaTeksEditor(
    tab: ModelTabEditor,
    wordWrapAktif: Boolean,
    onUbahTeks: (String, Int) -> Unit,
    onUbahKursor: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollVertikal = rememberScrollState()
    val scrollHorizontal = rememberScrollState()
    var tinggiViewportPx by remember { mutableIntStateOf(0) }
    var textLayoutResult by remember(tab.idTab) { mutableStateOf<TextLayoutResult?>(null) }

    var tfv by remember(tab.idTab) {
        mutableStateOf(
            TextFieldValue(
                text = tab.isiSaatIni,
                selection = TextRange(tab.posisiKursor.indeksKarakter.coerceIn(0, tab.isiSaatIni.length))
            )
        )
    }

    LaunchedEffect(tab.idTab, tab.isiSaatIni, tab.urutanNavigasiKursor) {
        val targetKursor = tab.posisiKursor.indeksKarakter.coerceIn(0, tab.isiSaatIni.length)
        if (tfv.text != tab.isiSaatIni) {
            tfv = TextFieldValue(
                text = tab.isiSaatIni,
                selection = TextRange(targetKursor)
            )
        } else if (tab.urutanNavigasiKursor > 0L && tfv.selection.start != targetKursor) {
            tfv = tfv.copy(selection = TextRange(targetKursor))
        }
    }

    LaunchedEffect(tab.idTab, tab.urutanNavigasiKursor, textLayoutResult) {
        if (tab.urutanNavigasiKursor <= 0L) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = tab.posisiKursor.indeksKarakter.coerceIn(0, layout.layoutInput.text.length)
        val barisVisual = layout.getLineForOffset(offset)
        val topY = layout.getLineTop(barisVisual).toInt()
        scrollVertikal.animateScrollTo((topY - 80).coerceAtLeast(0))
    }

    val paddingAtasDp = 10.dp
    val paddingAtasPx = with(LocalDensity.current) { paddingAtasDp.toPx() }
    val gayaTeks = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    Box(
        modifier = modifier
            .onSizeChanged { tinggiViewportPx = it.height }
            .verticalScroll(scrollVertikal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            KomponenNomorBaris(
                teks = tab.isiSaatIni,
                jumlahBaris = tab.jumlahBaris,
                barisAktif = tab.posisiKursor.baris,
                textLayoutResult = textLayoutResult,
                scrollY = scrollVertikal.value,
                tinggiViewportPx = tinggiViewportPx,
                paddingAtasPx = paddingAtasPx
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())

            val modifierTeks = if (wordWrapAktif) {
                Modifier.weight(1f)
            } else {
                Modifier.weight(1f).horizontalScroll(scrollHorizontal)
            }

            BasicTextField(
                value = tfv,
                onValueChange = { nilaiBaru ->
                    if (tab.modeHanyaBaca) {
                        val pilihanBaru = nilaiBaru.selection
                        if (tfv.selection != pilihanBaru) {
                            tfv = tfv.copy(selection = pilihanBaru)
                            onUbahKursor(pilihanBaru.start)
                        }
                    } else {
                        val teksBerubah = nilaiBaru.text != tfv.text
                        tfv = nilaiBaru
                        if (teksBerubah) {
                            onUbahTeks(nilaiBaru.text, nilaiBaru.selection.start)
                        } else {
                            onUbahKursor(nilaiBaru.selection.start)
                        }
                    }
                },
                readOnly = tab.modeHanyaBaca,
                textStyle = gayaTeks,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                onTextLayout = { textLayoutResult = it },
                modifier = modifierTeks
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = paddingAtasDp)
            )
        }
    }
}