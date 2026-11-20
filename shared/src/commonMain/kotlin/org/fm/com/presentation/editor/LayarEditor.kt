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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

@Immutable
private data class TabHeaderUi(
    val idTab: String,
    val namaBerkas: String,
    val kotor: Boolean
)

@Composable
fun LayarEditor(
    viewModel: ViewModelEditor,
    onKembali: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentEditor.SetPesanInfo(null))
        }
    }

    val tabAktif = state.tabAktif
    val daftarHeaderTab by remember {
        derivedStateOf {
            state.daftarTab.map { TabHeaderUi(it.idTab, it.namaBerkas, it.adaPerubahanBelumTersimpan) }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopBarEditor(
                namaBerkas = tabAktif?.namaBerkas,
                kotor = tabAktif?.adaPerubahanBelumTersimpan == true,
                statusMuat = tabAktif?.statusMuat,
                dapatSimpan = tabAktif?.dapatDiedit == true && !state.sedangMenyimpan,
                dapatUndo = tabAktif?.dapatUndo == true && tabAktif.dapatDiedit,
                dapatRedo = tabAktif?.dapatRedo == true && tabAktif.dapatDiedit,
                hanyaBacaPermanen = tabAktif?.hanyaBacaPermanen == true,
                modeHanyaBaca = tabAktif?.modeHanyaBaca == true,
                wordWrapAktif = tabAktif?.wordWrapEfektif(state.wordWrapUmum) ?: state.wordWrapUmum,
                sedangMenyimpan = state.sedangMenyimpan,
                onKembali = onKembali,
                onSimpan = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.SimpanTab(it.idTab)) } },
                onSimpanSebagai = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.BukaDialogSimpanSebagai(it.idTab)) } },
                onCari = { viewModel.prosesIntent(IntentEditor.SetBukaDialogCariGanti(true)) },
                onUndo = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.Undo(it.idTab)) } },
                onRedo = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.Redo(it.idTab)) } },
                onLompatBaris = { viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(true)) },
                onStatistik = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.HitungStatistikTab(it.idTab)) } },
                onToggleWrap = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.ToggleWordWrapTab(it.idTab)) } },
                onToggleHanyaBaca = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.ToggleModeHanyaBaca(it.idTab)) } },
                onTutupTab = { tabAktif?.let { viewModel.prosesIntent(IntentEditor.MintaTutupTab(it.idTab)) } },
                onTabKosongBaru = { viewModel.prosesIntent(IntentEditor.BuatTabKosong()) }
            )
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            if (daftarHeaderTab.isNotEmpty()) {
                BarTabEditor(
                    daftarTab = daftarHeaderTab,
                    idTabAktif = state.idTabAktif,
                    onPilihTab = remember(viewModel) { { viewModel.prosesIntent(IntentEditor.PilihTab(it)) } },
                    onTutupTab = remember(viewModel) { { viewModel.prosesIntent(IntentEditor.MintaTutupTab(it)) } },
                    onTabBaru = remember(viewModel) { { viewModel.prosesIntent(IntentEditor.BuatTabKosong()) } }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (tabAktif == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "// belum ada berkas terbuka",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                                CircularProgressIndicator(strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "memuat berkas...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (tabAktif.hanyaBacaPermanen) {
                                        "[RO] penyimpanan terkunci — seleksi, salin & cari tetap aktif"
                                    } else {
                                        "[BACA] mode hanya baca aktif — seleksi, salin & cari tetap aktif"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        val wrapAktif = tabAktif.wordWrapEfektif(state.wordWrapUmum)
                        val idTabSaatIni = tabAktif.idTab

                        AreaTeksEditor(
                            tab = tabAktif,
                            wordWrapAktif = wrapAktif,
                            onUbahTeks = remember(viewModel, idTabSaatIni) {
                                { teksBaru, awal, akhir ->
                                    viewModel.prosesIntent(IntentEditor.UbahTeks(idTabSaatIni, teksBaru, awal, akhir))
                                }
                            },
                            onUbahKursor = remember(viewModel, idTabSaatIni) {
                                { awal, akhir ->
                                    viewModel.prosesIntent(IntentEditor.UbahPosisiKursor(idTabSaatIni, awal, akhir))
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        StatusEditor(
                            baris = tabAktif.posisiKursor.baris,
                            kolom = tabAktif.posisiKursor.kolom,
                            jumlahBaris = tabAktif.jumlahBaris,
                            adaPerubahanBelumTersimpan = tabAktif.adaPerubahanBelumTersimpan,
                            hanyaBacaPermanen = tabAktif.hanyaBacaPermanen,
                            modeHanyaBaca = tabAktif.modeHanyaBaca,
                            wordWrapAktif = wrapAktif,
                            encoding = tabAktif.encoding,
                            akhirBaris = tabAktif.akhirBaris,
                            siap = true,
                            onKlikPosisiBaris = remember(viewModel) {
                                { viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(true)) }
                            },
                            onToggleWordWrap = remember(viewModel, idTabSaatIni) {
                                { viewModel.prosesIntent(IntentEditor.ToggleWordWrapTab(idTabSaatIni)) }
                            },
                            onToggleHanyaBaca = remember(viewModel, idTabSaatIni) {
                                { viewModel.prosesIntent(IntentEditor.ToggleModeHanyaBaca(idTabSaatIni)) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (tabAktif != null && state.dialogLompatBarisTerbuka) {
        DialogLompatBaris(
            terbuka = true,
            jumlahBaris = tabAktif.jumlahBaris,
            barisSaatIni = tabAktif.posisiKursor.baris,
            onLompat = { nomor ->
                viewModel.prosesIntent(IntentEditor.LompatKeBaris(tabAktif.idTab, nomor))
            },
            onTutup = { viewModel.prosesIntent(IntentEditor.SetBukaDialogLompatBaris(false)) }
        )
    }

    if (tabAktif != null && state.cariGanti.terbuka) {
        DialogCariGanti(
            state = state.cariGanti,
            teksDokumen = tabAktif.isiSaatIni,
            modeHanyaBaca = tabAktif.modeHanyaBaca,
            riwayatPencarian = state.riwayatPencarian,
            onUbahKueriCari = { viewModel.prosesIntent(IntentEditor.UbahKueriCari(it)) },
            onUbahTeksPengganti = { viewModel.prosesIntent(IntentEditor.UbahTeksPengganti(it)) },
            onTogglePekaHuruf = { viewModel.prosesIntent(IntentEditor.TogglePekaHurufCari) },
            onToggleKataUtuh = { viewModel.prosesIntent(IntentEditor.ToggleKataUtuhCari) },
            onToggleRegex = { viewModel.prosesIntent(IntentEditor.ToggleRegexCari) },
            onCariSebelumnya = { viewModel.prosesIntent(IntentEditor.CariSebelumnya) },
            onCariBerikutnya = { viewModel.prosesIntent(IntentEditor.CariBerikutnya) },
            onGantiSatu = { viewModel.prosesIntent(IntentEditor.GantiSatu) },
            onMintaGantiSemua = { viewModel.prosesIntent(IntentEditor.MintaGantiSemua) },
            onKonfirmasiGantiSemua = { viewModel.prosesIntent(IntentEditor.KonfirmasiGantiSemua) },
            onBatalGantiSemua = { viewModel.prosesIntent(IntentEditor.BatalGantiSemua) },
            onPilihRiwayat = { viewModel.prosesIntent(IntentEditor.PilihRiwayatPencarian(it)) },
            onHapusItemRiwayat = { viewModel.prosesIntent(IntentEditor.HapusItemRiwayatPencarian(it)) },
            onHapusSemuaRiwayat = { viewModel.prosesIntent(IntentEditor.BersihkanRiwayatPencarian) },
            onTutup = { viewModel.prosesIntent(IntentEditor.SetBukaDialogCariGanti(false)) }
        )
    }

    if (tabAktif != null && state.dialogStatistik.terbuka) {
        DialogStatistik(
            state = state.dialogStatistik,
            namaBerkas = tabAktif.namaBerkas,
            onTutup = { viewModel.prosesIntent(IntentEditor.TutupDialogStatistik) }
        )
    }

    if (state.tabMenungguKonfirmasiTutup != null) {
        DialogTutupTab(
            tab = state.tabMenungguKonfirmasiTutup,
            sedangMenyimpan = state.sedangMenyimpan,
            onSimpanDanTutup = { viewModel.prosesIntent(IntentEditor.KonfirmasiSimpanDanTutupTab) },
            onBuangDanTutup = { viewModel.prosesIntent(IntentEditor.KonfirmasiBuangDanTutupTab) },
            onBatal = { viewModel.prosesIntent(IntentEditor.BatalTutupTab) }
        )
    }

    if (state.konfirmasiKonflikSimpan != null) {
        DialogKonflikSimpanEksternal(
            permintaan = state.konfirmasiKonflikSimpan,
            sedangMenyimpan = state.sedangMenyimpan,
            onTimpa = { viewModel.prosesIntent(IntentEditor.KonfirmasiTimpaKonflikSimpan) },
            onBatal = { viewModel.prosesIntent(IntentEditor.BatalKonflikSimpan) }
        )
    }

    if (state.dialogSimpanSebagai != null) {
        DialogSimpanSebagai(
            permintaan = state.dialogSimpanSebagai,
            sedangMenyimpan = state.sedangMenyimpan,
            onSimpan = { nama, timpa ->
                viewModel.prosesIntent(IntentEditor.EksekusiSimpanSebagai(nama, timpa))
            },
            onTutup = { viewModel.prosesIntent(IntentEditor.TutupDialogSimpanSebagai) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarEditor(
    namaBerkas: String?,
    kotor: Boolean,
    statusMuat: StatusMuatTab?,
    dapatSimpan: Boolean,
    dapatUndo: Boolean,
    dapatRedo: Boolean,
    hanyaBacaPermanen: Boolean,
    modeHanyaBaca: Boolean,
    wordWrapAktif: Boolean,
    sedangMenyimpan: Boolean,
    onKembali: (() -> Unit)?,
    onSimpan: () -> Unit,
    onSimpanSebagai: () -> Unit,
    onCari: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onLompatBaris: () -> Unit,
    onStatistik: () -> Unit,
    onToggleWrap: () -> Unit,
    onToggleHanyaBaca: () -> Unit,
    onTutupTab: () -> Unit,
    onTabKosongBaru: () -> Unit
) {
    val fontMono = LocalFontMono.current
    var menuTerbuka by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = namaBerkas ?: "Editor",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontMono),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (kotor) {
                    Text(
                        text = " •",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        navigationIcon = {
            if (onKembali != null) {
                IconButton(onClick = onKembali) {
                    Icon(
                        imageVector = IkonAplikasi.Kembali,
                        contentDescription = "Kembali ke daftar berkas",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        actions = {
            if (statusMuat == StatusMuatTab.SIAP) {
                IconButton(onClick = onSimpan, enabled = dapatSimpan) {
                    Icon(
                        imageVector = IkonAplikasi.Simpan,
                        contentDescription = if (sedangMenyimpan) "Menyimpan berkas" else "Simpan berkas",
                        tint = if (dapatSimpan && kotor) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onCari) {
                    Icon(
                        imageVector = IkonAplikasi.Cari,
                        contentDescription = "Cari dan ganti",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onUndo, enabled = dapatUndo) {
                    Icon(
                        imageVector = IkonAplikasi.Undo,
                        contentDescription = "Undo",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRedo, enabled = dapatRedo) {
                    Icon(
                        imageVector = IkonAplikasi.Redo,
                        contentDescription = "Redo",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(onClick = { menuTerbuka = true }) {
                        Icon(
                            imageVector = IkonAplikasi.MenuLainnya,
                            contentDescription = "Menu editor lainnya",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuTerbuka,
                        onDismissRequest = { menuTerbuka = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Simpan Sebagai...") },
                            leadingIcon = { Icon(IkonAplikasi.Simpan, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            enabled = !sedangMenyimpan,
                            onClick = { menuTerbuka = false; onSimpanSebagai() }
                        )
                        DropdownMenuItem(
                            text = { Text("Cari & Ganti...") },
                            leadingIcon = { Icon(IkonAplikasi.Cari, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuTerbuka = false; onCari() }
                        )
                        DropdownMenuItem(
                            text = { Text("Lompat ke Baris...") },
                            leadingIcon = { Icon(IkonAplikasi.Urutkan, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuTerbuka = false; onLompatBaris() }
                        )
                        DropdownMenuItem(
                            text = { Text("Statistik Teks...") },
                            leadingIcon = { Icon(IkonAplikasi.Statistik, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuTerbuka = false; onStatistik() }
                        )
                        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                        DropdownMenuItem(
                            text = { Text(if (wordWrapAktif) "Matikan Word Wrap" else "Aktifkan Word Wrap") },
                            onClick = { menuTerbuka = false; onToggleWrap() }
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(if (modeHanyaBaca) "Nonaktifkan Hanya Baca" else "Mode Hanya Baca")
                                    if (hanyaBacaPermanen) {
                                        Text(
                                            text = "Lokasi tidak memiliki izin tulis",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            enabled = !hanyaBacaPermanen,
                            onClick = { menuTerbuka = false; onToggleHanyaBaca() }
                        )
                        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                        DropdownMenuItem(
                            text = { Text("Tab Kosong Baru") },
                            leadingIcon = { Icon(IkonAplikasi.Tambah, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuTerbuka = false; onTabKosongBaru() }
                        )
                        DropdownMenuItem(
                            text = { Text("Tutup Tab") },
                            leadingIcon = { Icon(IkonAplikasi.Tutup, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuTerbuka = false; onTutupTab() }
                        )
                    }
                }
            } else if (statusMuat == StatusMuatTab.BINER) {
                TextButton(onClick = onStatistik) {
                    Text("Info")
                }
            }
        }
    )
}

@Composable
private fun BarTabEditor(
    daftarTab: List<TabHeaderUi>,
    idTabAktif: String?,
    onPilihTab: (String) -> Unit,
    onTutupTab: (String) -> Unit,
    onTabBaru: () -> Unit
) {
    val fontMono = LocalFontMono.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items = daftarTab, key = { it.idTab }) { tab ->
                val aktif = tab.idTab == idTabAktif
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .background(
                            if (aktif) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent
                        )
                        .clickable { onPilihTab(tab.idTab) }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tab.kotor) {
                            Text(
                                text = "● ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = tab.namaBerkas,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = fontMono,
                                fontSize = 12.sp
                            ),
                            fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (aktif) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = IkonAplikasi.Tutup,
                            contentDescription = "Tutup tab ${tab.namaBerkas}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onTutupTab(tab.idTab) }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (aktif) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
            }
        }
        IconButton(
            onClick = onTabBaru,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = IkonAplikasi.Tambah,
                contentDescription = "Tab kosong baru",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AreaTeksEditor(
    tab: ModelTabEditor,
    wordWrapAktif: Boolean,
    onUbahTeks: (String, Int, Int) -> Unit,
    onUbahKursor: (Int, Int) -> Unit,
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
                selection = TextRange(
                    tab.posisiKursor.indeksKarakter.coerceIn(0, tab.isiSaatIni.length),
                    tab.posisiKursor.indeksAkhirSeleksi.coerceIn(0, tab.isiSaatIni.length)
                )
            )
        )
    }

    LaunchedEffect(tab.idTab, tab.urutanNavigasiKursor) {
        val targetAwal = tab.posisiKursor.indeksKarakter.coerceIn(0, tab.isiSaatIni.length)
        val targetAkhir = tab.posisiKursor.indeksAkhirSeleksi.coerceIn(0, tab.isiSaatIni.length)
        val targetRange = TextRange(targetAwal, targetAkhir)
        if (tfv.text != tab.isiSaatIni) {
            tfv = TextFieldValue(text = tab.isiSaatIni, selection = targetRange)
        } else if (tab.urutanNavigasiKursor > 0L && tfv.selection != targetRange) {
            tfv = tfv.copy(selection = targetRange)
        }
    }

    LaunchedEffect(tab.idTab, tab.urutanNavigasiKursor, textLayoutResult) {
        if (tab.urutanNavigasiKursor <= 0L) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = tab.posisiKursor.indeksKarakter.coerceIn(0, layout.layoutInput.text.length)
        val barisVisual = layout.getLineForOffset(offset)
        val topY = layout.getLineTop(barisVisual).toInt()
        scrollVertikal.animateScrollTo((topY - 80).coerceAtLeast(0))

        if (!wordWrapAktif) {
            runCatching {
                val rect = layout.getCursorRect(offset)
                scrollHorizontal.animateScrollTo((rect.left - 80f).toInt().coerceAtLeast(0))
            }
        }
    }

    val paddingAtasDp = 10.dp
    val paddingAtasPx = with(LocalDensity.current) { paddingAtasDp.toPx() }
    val fontMono = LocalFontMono.current
    val warnaTeks = MaterialTheme.colorScheme.onSurface
    val gayaTeks = remember(warnaTeks, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = warnaTeks
        )
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged { tinggiViewportPx = it.height }
            .verticalScroll(scrollVertikal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            KomponenNomorBaris(
                jumlahBaris = tab.jumlahBaris,
                barisAktif = tab.posisiKursor.baris,
                textLayoutResult = textLayoutResult,
                scrollY = scrollVertikal.value,
                tinggiViewportPx = tinggiViewportPx,
                paddingAtasPx = paddingAtasPx
            )
            VerticalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxHeight()
            )

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
                            onUbahKursor(pilihanBaru.start, pilihanBaru.end)
                        }
                    } else {
                        val teksBerubah = nilaiBaru.text != tfv.text
                        tfv = nilaiBaru
                        if (teksBerubah) {
                            onUbahTeks(nilaiBaru.text, nilaiBaru.selection.start, nilaiBaru.selection.end)
                        } else {
                            onUbahKursor(nilaiBaru.selection.start, nilaiBaru.selection.end)
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