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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

private const val AMBANG_SATU_BLOK_CHAR = 16_384
private const val AMBANG_SATU_BLOK_BARIS = 350
private const val MAKS_BARIS_PER_BLOK = 40
private const val MAKS_CHAR_PER_BLOK = 3_000
private const val BATAS_PECAH_ULANG_BARIS = 100
private const val BATAS_PECAH_ULANG_CHAR = 6_000

@Immutable
private data class TabHeaderUi(
    val idTab: String,
    val namaBerkas: String,
    val kotor: Boolean
)

private data class BlokTeksEditor(
    val idLokal: Long,
    val offsetAwal: Int,
    val barisAwal: Int,
    val lanjutanBaris: Boolean,
    val teks: String,
    val jumlahLf: Int,
    val punyaPemisahAkhir: Boolean
) {
    val teksTampil: String
        get() = if (punyaPemisahAkhir && teks.endsWith('\n')) teks.dropLast(1) else teks
}

private data class PermintaanFokusBlok(
    val idBlok: Long,
    val awalLokal: Int,
    val akhirLokal: Int,
    val versi: Long
)

@Composable
fun LayarEditor(
    viewModel: ViewModelEditor,
    modifier: Modifier = Modifier,
    onKembali: (() -> Unit)? = null,
    onArsipDiperbarui: ((kembaliKeArsip: Boolean) -> Unit)? = null
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

    LaunchedEffect(state.acaraArsipDiperbarui?.id) {
        val acara = state.acaraArsipDiperbarui ?: return@LaunchedEffect
        onArsipDiperbarui?.invoke(acara.kembaliKeArsip)
        viewModel.prosesIntent(IntentEditor.KonsumsiAcaraArsipDiperbarui)
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
                            onUbahRentangTeks = remember(viewModel, idTabSaatIni) {
                                { awalGlobal, panjangDihapus, teksDisisip, kursorGlobal, akhirSeleksiGlobal ->
                                    viewModel.prosesIntent(
                                        IntentEditor.UbahRentangTeks(
                                            idTab = idTabSaatIni,
                                            awalGlobal = awalGlobal,
                                            panjangDihapus = panjangDihapus,
                                            teksDisisip = teksDisisip,
                                            indeksKursorGlobal = kursorGlobal,
                                            indeksAkhirSeleksiGlobal = akhirSeleksiGlobal
                                        )
                                    )
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

    if (state.konfirmasiPerbaruiArsip != null) {
        DialogKonfirmasiPerbaruiArsip(
            permintaan = state.konfirmasiPerbaruiArsip,
            sedangMenyimpan = state.sedangMenyimpan,
            onPerbaruiArsip = { viewModel.prosesIntent(IntentEditor.KonfirmasiPerbaruiArsip) },
            onBatalkanEdit = { viewModel.prosesIntent(IntentEditor.BatalkanEditDalamArsip) },
            onKembali = { viewModel.prosesIntent(IntentEditor.TutupDialogPerbaruiArsip) }
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
    onUbahRentangTeks: (awalGlobal: Int, panjangDihapus: Int, teksDisisip: String, kursorGlobal: Int, akhirSeleksiGlobal: Int) -> Unit,
    onUbahKursor: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val scrollHorizontal = rememberScrollState()
    val fontMono = LocalFontMono.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)

    val daftarBlok = remember(tab.idTab) { mutableStateListOf<BlokTeksEditor>() }
    var nextBlokId by remember(tab.idTab) { mutableLongStateOf(1L) }
    var urutanSinkronTerakhir by remember(tab.idTab) { mutableLongStateOf(-1L) }
    var panjangTeksLokal by remember(tab.idTab) { mutableIntStateOf(-1) }
    var permintaanFokus by remember(tab.idTab) { mutableStateOf<PermintaanFokusBlok?>(null) }
    var versiFokus by remember(tab.idTab) { mutableLongStateOf(0L) }

    fun isiUlangDariTeks(teks: String) {
        val hasilPecah = pecahMenjadiBlok(teks, nextBlokId)
        nextBlokId = hasilPecah.nextId
        daftarBlok.clear()
        daftarBlok.addAll(hasilPecah.blokList)
        panjangTeksLokal = teks.length
    }

    if (daftarBlok.isEmpty() && urutanSinkronTerakhir == -1L) {
        isiUlangDariTeks(tab.isiSaatIni)
        urutanSinkronTerakhir = tab.urutanSinkronTeks
    }

    LaunchedEffect(tab.idTab, tab.urutanSinkronTeks, tab.isiSaatIni.length) {
        if (tab.urutanSinkronTeks != urutanSinkronTerakhir || tab.isiSaatIni.length != panjangTeksLokal) {
            isiUlangDariTeks(tab.isiSaatIni)
            urutanSinkronTerakhir = tab.urutanSinkronTeks
        }
    }

    LaunchedEffect(tab.idTab, tab.urutanNavigasiKursor) {
        if (tab.urutanNavigasiKursor <= 0L || daftarBlok.isEmpty()) return@LaunchedEffect
        val awalGlobal = tab.posisiKursor.indeksKarakter.coerceIn(0, tab.isiSaatIni.length)
        val akhirGlobal = tab.posisiKursor.indeksAkhirSeleksi.coerceIn(0, tab.isiSaatIni.length)
        val idxBlok = cariIndeksBlokUntukOffset(daftarBlok, awalGlobal)
        val blok = daftarBlok[idxBlok]
        val maksLokal = blok.teksTampil.length
        val awalLokal = (awalGlobal - blok.offsetAwal).coerceIn(0, maksLokal)
        val akhirLokal = (akhirGlobal - blok.offsetAwal).coerceIn(0, maksLokal)

        lazyListState.scrollToItem(idxBlok)
        versiFokus++
        permintaanFokus = PermintaanFokusBlok(
            idBlok = blok.idLokal,
            awalLokal = awalLokal,
            akhirLokal = akhirLokal,
            versi = versiFokus
        )
    }

    val warnaLatarEditor = MaterialTheme.colorScheme.surfaceContainerLowest
    val warnaLatarGutter = MaterialTheme.colorScheme.surfaceContainerLow
    val warnaDivider = MaterialTheme.colorScheme.outlineVariant
    val warnaTeks = MaterialTheme.colorScheme.onSurface
    val warnaNomorNormal = MaterialTheme.colorScheme.onSurfaceVariant
    val warnaNomorAktif = MaterialTheme.colorScheme.primary
    val warnaSorotBarisAktif = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)

    val gayaTeks = remember(warnaTeks, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = warnaTeks
        )
    }
    val gayaNomorNormal = remember(warnaNomorNormal, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 12.sp,
            color = warnaNomorNormal
        )
    }
    val gayaNomorAktif = remember(warnaNomorAktif, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = warnaNomorAktif
        )
    }

    val jumlahDigit = remember(tab.jumlahBaris) {
        tab.jumlahBaris.coerceAtLeast(1).toString().length.coerceAtLeast(2)
    }
    val lebarTeksMaksPx = remember(jumlahDigit, gayaNomorNormal, textMeasurer) {
        textMeasurer.measure("8".repeat(jumlahDigit), style = gayaNomorNormal).size.width
    }
    val lebarGutterDp = with(density) { lebarTeksMaksPx.toDp() + 20.dp }
    val lebarGutterPx = with(density) { lebarGutterDp.toPx() }
    val paddingKananNomorPx = with(density) { 8.dp.toPx() }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.background(warnaLatarEditor)
    ) {
        itemsIndexed(
            items = daftarBlok,
            key = { _, blok -> blok.idLokal }
        ) { indeks, blok ->
            val adalahPertama = indeks == 0
            val adalahTerakhir = indeks == daftarBlok.lastIndex
            val paddingAtasDp = if (adalahPertama) 10.dp else 0.dp
            val paddingBawahDp = if (adalahTerakhir) 48.dp else 0.dp

            BarisBlokEditor(
                blok = blok,
                adalahTerakhir = adalahTerakhir,
                barisAktif = tab.posisiKursor.baris,
                modeHanyaBaca = tab.modeHanyaBaca,
                wordWrapAktif = wordWrapAktif,
                scrollHorizontal = scrollHorizontal,
                permintaanFokus = if (permintaanFokus?.idBlok == blok.idLokal) permintaanFokus else null,
                lebarGutterDp = lebarGutterDp,
                lebarGutterPx = lebarGutterPx,
                paddingKananNomorPx = paddingKananNomorPx,
                paddingAtasDp = paddingAtasDp,
                paddingBawahDp = paddingBawahDp,
                warnaLatarGutter = warnaLatarGutter,
                warnaDivider = warnaDivider,
                warnaCursor = warnaNomorAktif,
                warnaSorotBarisAktif = warnaSorotBarisAktif,
                gayaTeks = gayaTeks,
                gayaNomorNormal = gayaNomorNormal,
                gayaNomorAktif = gayaNomorAktif,
                textMeasurer = textMeasurer,
                onUbahBlok = { teksTampilBaru, kursorLokal, akhirSeleksiLokal ->
                    val idxSekarang = daftarBlok.indexOfFirst { it.idLokal == blok.idLokal }
                    if (idxSekarang < 0) return@BarisBlokEditor
                    val blokSaatIni = daftarBlok[idxSekarang]
                    val teksTampilLama = blokSaatIni.teksTampil
                    if (teksTampilBaru == teksTampilLama) return@BarisBlokEditor

                    val minLen = minOf(teksTampilLama.length, teksTampilBaru.length)
                    var prefix = 0
                    while (prefix < minLen && teksTampilLama[prefix] == teksTampilBaru[prefix]) {
                        prefix++
                    }
                    var suffix = 0
                    val sisa = minLen - prefix
                    while (
                        suffix < sisa &&
                        teksTampilLama[teksTampilLama.length - 1 - suffix] == teksTampilBaru[teksTampilBaru.length - 1 - suffix]
                    ) {
                        suffix++
                    }
                    val panjangDihapus = teksTampilLama.length - prefix - suffix
                    val teksDisisip = teksTampilBaru.substring(prefix, teksTampilBaru.length - suffix)
                    val awalGlobal = blokSaatIni.offsetAwal + prefix
                    val kursorGlobal = blokSaatIni.offsetAwal + kursorLokal
                    val akhirSeleksiGlobal = blokSaatIni.offsetAwal + akhirSeleksiLokal

                    val teksPenuhBlokBaru = if (blokSaatIni.punyaPemisahAkhir) {
                        "$teksTampilBaru\n"
                    } else {
                        teksTampilBaru
                    }
                    val deltaPanjang = teksPenuhBlokBaru.length - blokSaatIni.teks.length
                    val jumlahLfBaru = hitungLfCepat(teksPenuhBlokBaru)
                    val deltaLf = jumlahLfBaru - blokSaatIni.jumlahLf

                    panjangTeksLokal = (panjangTeksLokal + deltaPanjang).coerceAtLeast(0)

                    val perluPecahUlang =
                        jumlahLfBaru > BATAS_PECAH_ULANG_BARIS || teksPenuhBlokBaru.length > BATAS_PECAH_ULANG_CHAR

                    if (perluPecahUlang) {
                        val sub = pecahMenjadiBlok(
                            teks = teksPenuhBlokBaru,
                            idAwal = nextBlokId,
                            offsetDasar = blokSaatIni.offsetAwal,
                            barisDasar = blokSaatIni.barisAwal,
                            lanjutanAwal = blokSaatIni.lanjutanBaris
                        )
                        nextBlokId = sub.nextId
                        daftarBlok.removeAt(idxSekarang)
                        daftarBlok.addAll(idxSekarang, sub.blokList)
                        val awalSetelahnya = idxSekarang + sub.blokList.size
                        for (j in awalSetelahnya until daftarBlok.size) {
                            val item = daftarBlok[j]
                            daftarBlok[j] = item.copy(
                                offsetAwal = item.offsetAwal + deltaPanjang,
                                barisAwal = (item.barisAwal + deltaLf).coerceAtLeast(1)
                            )
                        }
                        val subIdx = cariIndeksBlokUntukOffset(sub.blokList, kursorGlobal)
                        val targetSub = sub.blokList[subIdx]
                        versiFokus++
                        permintaanFokus = PermintaanFokusBlok(
                            idBlok = targetSub.idLokal,
                            awalLokal = (kursorGlobal - targetSub.offsetAwal).coerceIn(0, targetSub.teksTampil.length),
                            akhirLokal = (akhirSeleksiGlobal - targetSub.offsetAwal).coerceIn(0, targetSub.teksTampil.length),
                            versi = versiFokus
                        )
                    } else {
                        daftarBlok[idxSekarang] = blokSaatIni.copy(
                            teks = teksPenuhBlokBaru,
                            jumlahLf = jumlahLfBaru
                        )
                        for (j in (idxSekarang + 1) until daftarBlok.size) {
                            val item = daftarBlok[j]
                            daftarBlok[j] = item.copy(
                                offsetAwal = item.offsetAwal + deltaPanjang,
                                barisAwal = (item.barisAwal + deltaLf).coerceAtLeast(1)
                            )
                        }
                    }

                    onUbahRentangTeks(
                        awalGlobal,
                        panjangDihapus,
                        teksDisisip,
                        kursorGlobal,
                        akhirSeleksiGlobal
                    )
                },
                onUbahKursorBlok = { awalLokal, akhirLokal ->
                    val idxSekarang = daftarBlok.indexOfFirst { it.idLokal == blok.idLokal }
                    if (idxSekarang >= 0) {
                        val b = daftarBlok[idxSekarang]
                        onUbahKursor(b.offsetAwal + awalLokal, b.offsetAwal + akhirLokal)
                    }
                },
                onBackspaceDiAwalBlok = {
                    val idxSekarang = daftarBlok.indexOfFirst { it.idLokal == blok.idLokal }
                    if (idxSekarang <= 0) return@BarisBlokEditor
                    val blokPrev = daftarBlok[idxSekarang - 1]
                    val blokIni = daftarBlok[idxSekarang]
                    if (!blokPrev.punyaPemisahAkhir || blokPrev.teks.isEmpty()) return@BarisBlokEditor

                    val posisiHapusGlobal = blokPrev.offsetAwal + blokPrev.teks.length - 1
                    val panjangPrevTampil = blokPrev.teksTampil.length
                    val gabungTeks = blokPrev.teksTampil + blokIni.teks
                    val jumlahLfGabung = hitungLfCepat(gabungTeks)

                    panjangTeksLokal = (panjangTeksLokal - 1).coerceAtLeast(0)
                    daftarBlok[idxSekarang - 1] = blokPrev.copy(
                        teks = gabungTeks,
                        jumlahLf = jumlahLfGabung,
                        punyaPemisahAkhir = blokIni.punyaPemisahAkhir
                    )
                    daftarBlok.removeAt(idxSekarang)
                    for (j in idxSekarang until daftarBlok.size) {
                        val item = daftarBlok[j]
                        daftarBlok[j] = item.copy(
                            offsetAwal = item.offsetAwal - 1,
                            barisAwal = (item.barisAwal - 1).coerceAtLeast(1)
                        )
                    }

                    versiFokus++
                    permintaanFokus = PermintaanFokusBlok(
                        idBlok = blokPrev.idLokal,
                        awalLokal = panjangPrevTampil,
                        akhirLokal = panjangPrevTampil,
                        versi = versiFokus
                    )
                    scope.launch {
                        lazyListState.scrollToItem(idxSekarang - 1)
                    }
                    onUbahRentangTeks(
                        posisiHapusGlobal,
                        1,
                        "",
                        posisiHapusGlobal,
                        posisiHapusGlobal
                    )
                }
            )
        }
    }
}

@Composable
private fun BarisBlokEditor(
    blok: BlokTeksEditor,
    adalahTerakhir: Boolean,
    barisAktif: Int,
    modeHanyaBaca: Boolean,
    wordWrapAktif: Boolean,
    scrollHorizontal: androidx.compose.foundation.ScrollState,
    permintaanFokus: PermintaanFokusBlok?,
    lebarGutterDp: Dp,
    lebarGutterPx: Float,
    paddingKananNomorPx: Float,
    paddingAtasDp: Dp,
    paddingBawahDp: Dp,
    warnaLatarGutter: Color,
    warnaDivider: Color,
    warnaCursor: Color,
    warnaSorotBarisAktif: Color,
    gayaTeks: TextStyle,
    gayaNomorNormal: TextStyle,
    gayaNomorAktif: TextStyle,
    textMeasurer: TextMeasurer,
    onUbahBlok: (teksTampilBaru: String, kursorLokal: Int, akhirSeleksiLokal: Int) -> Unit,
    onUbahKursorBlok: (awalLokal: Int, akhirLokal: Int) -> Unit,
    onBackspaceDiAwalBlok: () -> Unit
) {
    val density = LocalDensity.current
    val paddingAtasPx = with(density) { paddingAtasDp.toPx() }
    val garisPx = with(density) { 0.5.dp.toPx() }
    val focusRequester = remember(blok.idLokal) { FocusRequester() }
    var textLayoutResult by remember(blok.idLokal) { mutableStateOf<TextLayoutResult?>(null) }

    val teksTampil = blok.teksTampil
    var tfv by remember(blok.idLokal) {
        mutableStateOf(TextFieldValue(text = teksTampil, selection = TextRange(0)))
    }

    if (tfv.text != teksTampil) {
        val maks = teksTampil.length
        tfv = TextFieldValue(
            text = teksTampil,
            selection = TextRange(
                tfv.selection.start.coerceIn(0, maks),
                tfv.selection.end.coerceIn(0, maks)
            )
        )
    }

    LaunchedEffect(permintaanFokus?.versi) {
        val req = permintaanFokus ?: return@LaunchedEffect
        val maks = teksTampil.length
        tfv = tfv.copy(
            selection = TextRange(
                req.awalLokal.coerceIn(0, maks),
                req.akhirLokal.coerceIn(0, maks)
            )
        )
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (adalahTerakhir) Modifier.defaultMinSize(minHeight = 280.dp) else Modifier)
            .clipToBounds()
            .drawBehind {
                drawRect(
                    color = warnaLatarGutter,
                    topLeft = Offset.Zero,
                    size = Size(lebarGutterPx, size.height)
                )
                drawLine(
                    color = warnaDivider,
                    start = Offset(lebarGutterPx, 0f),
                    end = Offset(lebarGutterPx, size.height),
                    strokeWidth = garisPx
                )

                val layout = textLayoutResult ?: return@drawBehind
                val teksLayout = layout.layoutInput.text.text
                val totalBarisVisual = layout.lineCount
                if (totalBarisVisual <= 0) return@drawBehind

                var nomorLogis = blok.barisAwal
                var sudahBarisPertama = false

                for (barisVisual in 0 until totalBarisVisual) {
                    val startOffset = layout.getLineStart(barisVisual).coerceIn(0, teksLayout.length)
                    val adalahAwalBarisLogis = barisVisual == 0 ||
                        (startOffset > 0 && teksLayout[startOffset - 1] == '\n')
                    if (!adalahAwalBarisLogis) continue

                    val tampilkanNomor = if (!sudahBarisPertama) {
                        sudahBarisPertama = true
                        !blok.lanjutanBaris
                    } else {
                        nomorLogis++
                        true
                    }
                    if (!tampilkanNomor) continue

                    val topY = layout.getLineTop(barisVisual) + paddingAtasPx
                    val bottomY = layout.getLineBottom(barisVisual) + paddingAtasPx
                    val tinggiVisual = (bottomY - topY).coerceAtLeast(1f)
                    val adalahAktif = nomorLogis == barisAktif

                    if (adalahAktif) {
                        drawRect(
                            color = warnaSorotBarisAktif,
                            topLeft = Offset(0f, topY),
                            size = Size(size.width, tinggiVisual)
                        )
                    }

                    val hasilUkur = textMeasurer.measure(
                        text = nomorLogis.toString(),
                        style = if (adalahAktif) gayaNomorAktif else gayaNomorNormal
                    )
                    val posisiX = (lebarGutterPx - hasilUkur.size.width - paddingKananNomorPx).coerceAtLeast(0f)
                    val posisiY = topY + ((tinggiVisual - hasilUkur.size.height) / 2f).coerceAtLeast(0f)
                    drawText(
                        textLayoutResult = hasilUkur,
                        topLeft = Offset(posisiX, posisiY)
                    )
                }
            }
    ) {
        val modField = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onUbahKursorBlok(tfv.selection.start, tfv.selection.end)
                }
            }
            .onPreviewKeyEvent { event ->
                if (!modeHanyaBaca &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Backspace &&
                    tfv.selection.start == 0 &&
                    tfv.selection.end == 0
                ) {
                    onBackspaceDiAwalBlok()
                    true
                } else {
                    false
                }
            }

        if (wordWrapAktif) {
            BasicTextField(
                value = tfv,
                onValueChange = { nilaiBaru ->
                    if (modeHanyaBaca) {
                        val selBaru = nilaiBaru.selection
                        if (tfv.selection != selBaru) {
                            tfv = tfv.copy(selection = selBaru)
                            onUbahKursorBlok(selBaru.start, selBaru.end)
                        }
                    } else {
                        val teksBerubah = nilaiBaru.text != tfv.text
                        tfv = nilaiBaru
                        if (teksBerubah) {
                            onUbahBlok(nilaiBaru.text, nilaiBaru.selection.start, nilaiBaru.selection.end)
                        } else {
                            onUbahKursorBlok(nilaiBaru.selection.start, nilaiBaru.selection.end)
                        }
                    }
                },
                readOnly = modeHanyaBaca,
                textStyle = gayaTeks,
                cursorBrush = SolidColor(warnaCursor),
                onTextLayout = { textLayoutResult = it },
                modifier = modField
                    .fillMaxWidth()
                    .padding(
                        start = lebarGutterDp + 10.dp,
                        end = 10.dp,
                        top = paddingAtasDp,
                        bottom = paddingBawahDp
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = lebarGutterDp)
                    .horizontalScroll(scrollHorizontal)
            ) {
                BasicTextField(
                    value = tfv,
                    onValueChange = { nilaiBaru ->
                        if (modeHanyaBaca) {
                            val selBaru = nilaiBaru.selection
                            if (tfv.selection != selBaru) {
                                tfv = tfv.copy(selection = selBaru)
                                onUbahKursorBlok(selBaru.start, selBaru.end)
                            }
                        } else {
                            val teksBerubah = nilaiBaru.text != tfv.text
                            tfv = nilaiBaru
                            if (teksBerubah) {
                                onUbahBlok(nilaiBaru.text, nilaiBaru.selection.start, nilaiBaru.selection.end)
                            } else {
                                onUbahKursorBlok(nilaiBaru.selection.start, nilaiBaru.selection.end)
                            }
                        }
                    },
                    readOnly = modeHanyaBaca,
                    textStyle = gayaTeks,
                    cursorBrush = SolidColor(warnaCursor),
                    onTextLayout = { textLayoutResult = it },
                    modifier = modField.padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = paddingAtasDp,
                        bottom = paddingBawahDp
                    )
                )
            }
        }
    }
}

private data class HasilPecahBlok(
    val blokList: List<BlokTeksEditor>,
    val nextId: Long
)

private fun pecahMenjadiBlok(
    teks: String,
    idAwal: Long,
    offsetDasar: Int = 0,
    barisDasar: Int = 1,
    lanjutanAwal: Boolean = false
): HasilPecahBlok {
    var nextId = idAwal
    val lfTotal = hitungLfCepat(teks)
    if (teks.length <= AMBANG_SATU_BLOK_CHAR && lfTotal <= AMBANG_SATU_BLOK_BARIS) {
        return HasilPecahBlok(
            blokList = listOf(
                BlokTeksEditor(
                    idLokal = nextId++,
                    offsetAwal = offsetDasar,
                    barisAwal = barisDasar,
                    lanjutanBaris = lanjutanAwal,
                    teks = teks,
                    jumlahLf = lfTotal,
                    punyaPemisahAkhir = false
                )
            ),
            nextId = nextId
        )
    }

    val hasil = mutableListOf<BlokTeksEditor>()
    var pos = 0
    var barisSaatIni = barisDasar
    var sedangLanjutan = lanjutanAwal

    while (pos < teks.length) {
        var cur = pos
        var barisDiBlok = 0
        var lfDiBlok = 0
        val batasChar = minOf(teks.length, pos + MAKS_CHAR_PER_BLOK)

        while (barisDiBlok < MAKS_BARIS_PER_BLOK && cur < batasChar) {
            val idxLf = teks.indexOf('\n', startIndex = cur)
            if (idxLf < 0 || idxLf >= batasChar) {
                cur = batasChar
                break
            }
            cur = idxLf + 1
            barisDiBlok++
            lfDiBlok++
        }

        if (cur > pos && cur < teks.length && teks[cur - 1].isHighSurrogate()) {
            cur = (cur - 1).coerceAtLeast(pos + 1)
        }

        val isiBlok = teks.substring(pos, cur)
        val punyaPemisahAkhir = cur < teks.length && isiBlok.endsWith('\n')

        hasil.add(
            BlokTeksEditor(
                idLokal = nextId++,
                offsetAwal = offsetDasar + pos,
                barisAwal = barisSaatIni,
                lanjutanBaris = sedangLanjutan,
                teks = isiBlok,
                jumlahLf = lfDiBlok,
                punyaPemisahAkhir = punyaPemisahAkhir
            )
        )

        barisSaatIni += lfDiBlok
        sedangLanjutan = cur < teks.length && !isiBlok.endsWith('\n')
        pos = cur
    }

    return HasilPecahBlok(blokList = hasil, nextId = nextId)
}

private fun cariIndeksBlokUntukOffset(daftar: List<BlokTeksEditor>, offsetGlobal: Int): Int {
    if (daftar.isEmpty()) return 0
    var low = 0
    var high = daftar.lastIndex
    var ketemu = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        val b = daftar[mid]
        if (b.offsetAwal <= offsetGlobal) {
            ketemu = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return ketemu
}

private fun hitungLfCepat(teks: String): Int {
    var count = 0
    for (i in 0 until teks.length) {
        if (teks[i] == '\n') count++
    }
    return count
}