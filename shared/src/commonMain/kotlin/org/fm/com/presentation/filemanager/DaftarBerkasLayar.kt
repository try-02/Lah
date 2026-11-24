package org.fm.com.presentation.filemanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.presentation.arsip.DialogKompres
import org.fm.com.presentation.arsip.IntentArsip
import org.fm.com.presentation.arsip.OverlayDialogEkstrakArsip
import org.fm.com.presentation.arsip.ViewModelArsip
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.rename.DialogBulkRename
import org.fm.com.presentation.rename.IntentBulkRename
import org.fm.com.presentation.rename.ViewModelBulkRename
import org.fm.com.presentation.tema.LocalFontMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarBerkasLayar(
    viewModel: ViewModelFilemanager,
    onBukaBerkas: (ItemBerkasUi) -> Unit,
    modifier: Modifier = Modifier,
    viewModelOperasiMassal: ViewModelOperasiMassal? = null,
    viewModelBulkRename: ViewModelBulkRename? = null,
    viewModelArsip: ViewModelArsip? = null,
    aksiPlatform: AksiPlatformBerkas = remember { AksiPlatformBerkasBawaan() },
    onPilihFolder: (() -> Unit)? = null,
    lokasiPintasan: List<LokasiBerkas> = emptyList(),
    onPilihLokasiPintasan: ((LokasiBerkas) -> Unit)? = null,
    onBandingkanBerkas: ((ItemBerkasUi, ItemBerkasUi) -> Unit)? = null,
    onBukaArsip: ((ItemBerkasUi) -> Unit)? = null,
    onBookmarkItem: ((LokasiBerkas, JenisBerkas, Boolean) -> Unit)? = null,
    onJadikanBeranda: ((LokasiBerkas) -> Unit)? = null,
    izinMemoriInternalAktif: Boolean? = null,
    onMintaIzinMemoriInternal: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultStateMassal = remember { MutableStateFlow(StateOperasiMassal()) }
    val stateMassal by (viewModelOperasiMassal?.state ?: defaultStateMassal).collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var menuKonteksItem by remember { mutableStateOf<ItemBerkasUi?>(null) }
    var tampilSwitcherLokasi by remember { mutableStateOf(false) }
    var itemPembandingAwal by remember { mutableStateOf<ItemBerkasUi?>(null) }

    val duaBerkasTerpilih = remember(state.itemTerpilih, state.daftarItem) {
        if (state.itemTerpilih.size != 2) null
        else {
            val terpilih = state.daftarItem.filter { it.lokasi in state.itemTerpilih && !it.adalahFolder }
            if (terpilih.size == 2) terpilih[0] to terpilih[1] else null
        }
    }

    val onBukaBerkasTerbaru by rememberUpdatedState(onBukaBerkas)
    val onBukaArsipTerbaru by rememberUpdatedState(onBukaArsip)
    val sedangMultiSelectTerbaru by rememberUpdatedState(state.sedangMultiSelect)

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(null))
        }
    }

    val lokasiAktif = state.lokasiSaatIni
    val promptRingkas = remember(lokasiAktif) {
        lokasiAktif?.let { PemformatLokasiRamah.formatRingkas(it) } ?: "~"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.sedangMultiSelect) {
                BarAksiSeleksi(
                    jumlahTerpilih = state.itemTerpilih.size,
                    kemampuan = state.kemampuanUi,
                    onPilihSemua = { viewModel.prosesIntent(IntentFilemanager.PilihSemua) },
                    onBersihkanPilihan = { viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan) },
                    onAksiHapus = {
                        viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(state.itemTerpilih.toList()))
                    },
                    onAksiSalin = {
                        val sumber = state.itemTerpilih.toList()
                        viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                        viewModelOperasiMassal?.inisiasiSalin(sumber)
                    },
                    onAksiPindah = {
                        val sumber = state.itemTerpilih.toList()
                        viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                        viewModelOperasiMassal?.inisiasiPindah(sumber)
                    },
                    onAksiBandingkan = if (duaBerkasTerpilih != null && onBandingkanBerkas != null) {
                        {
                            viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                            onBandingkanBerkas(duaBerkasTerpilih.first, duaBerkasTerpilih.second)
                        }
                    } else null,
                    onAksiRenameMassal = if (viewModelBulkRename != null && lokasiAktif != null) {
                        {
                            val entitasTerpilih = state.daftarItem
                                .filter { it.lokasi in state.itemTerpilih }
                                .map { item ->
                                    EntitasBerkas(
                                        lokasi = item.lokasi,
                                        nama = item.nama,
                                        jenis = item.jenis,
                                        ukuranByte = item.ukuranByte,
                                        waktuUbahEpochMs = item.waktuUbahEpochMs,
                                        tersembunyi = item.tersembunyi,
                                        dapatDibaca = item.dapatDibaca,
                                        dapatDitulis = item.dapatDitulis
                                    )
                                }
                            if (entitasTerpilih.isNotEmpty()) {
                                viewModelBulkRename.prosesIntent(
                                    IntentBulkRename.Buka(
                                        folderInduk = lokasiAktif,
                                        daftarItem = entitasTerpilih,
                                        kemampuanLokasi = state.kemampuanLokasi
                                    )
                                )
                            }
                        }
                    } else null,
                    onAksiKompres = if (viewModelArsip != null && lokasiAktif != null) {
                        {
                            val sumberKompres = state.daftarItem
                                .filter { it.lokasi in state.itemTerpilih }
                                .map { item ->
                                    ItemSumberKompresi(
                                        lokasi = item.lokasi,
                                        namaDalamArsip = item.nama,
                                        adalahFolder = item.adalahFolder
                                    )
                                }
                            if (sumberKompres.isNotEmpty()) {
                                viewModelArsip.prosesIntent(
                                    IntentArsip.BukaDialogKompres(
                                        daftarSumber = sumberKompres,
                                        tujuanInduk = lokasiAktif
                                    )
                                )
                            }
                        }
                    } else null
                )
            } else {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(enabled = lokasiPintasan.isNotEmpty() || onPilihFolder != null) {
                                    tampilSwitcherLokasi = true
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lokasiAktif?.label ?: "Berkas",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (lokasiPintasan.isNotEmpty() || onPilihFolder != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = IkonAplikasi.TukarLokasi,
                                        contentDescription = "Ganti lokasi penyimpanan",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$promptRingkas · ${state.jumlahItem} item",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = fontMono,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val modeBaru = if (state.modeTampilan == ModeTampilan.DAFTAR) {
                                ModeTampilan.KISI
                            } else {
                                ModeTampilan.DAFTAR
                            }
                            viewModel.prosesIntent(IntentFilemanager.UbahModeTampilan(modeBaru))
                        }) {
                            Icon(
                                imageVector = if (state.modeTampilan == ModeTampilan.DAFTAR) {
                                    IkonAplikasi.TampilanKisi
                                } else {
                                    IkonAplikasi.TampilanDaftar
                                },
                                contentDescription = "Ganti tampilan daftar atau kisi",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogUrutkan(true)) }) {
                            Icon(
                                imageVector = IkonAplikasi.Urutkan,
                                contentDescription = "Urutkan dan saring",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (onBookmarkItem != null && lokasiAktif != null && state.kemampuanUi.dapatBookmark) {
                            IconButton(onClick = {
                                onBookmarkItem(lokasiAktif, JenisBerkas.FOLDER, true)
                            }) {
                                Icon(
                                    imageVector = IkonAplikasi.Bookmark,
                                    contentDescription = "Bookmark folder ini",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }) {
                            Icon(
                                imageVector = IkonAplikasi.Segarkan,
                                contentDescription = "Segarkan daftar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.sedangMultiSelect && state.kemampuanUi.dapatMembuat) {
                FloatingActionButton(
                    onClick = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogBuat(true)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = IkonAplikasi.Tambah,
                        contentDescription = "Buat baru",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            if (state.segmenBreadcrumb.size > 1) {
                BreadcrumbLokasi(
                    segmen = state.segmenBreadcrumb,
                    onPilihSegmen = { segmen ->
                        viewModel.prosesIntent(IntentFilemanager.NavigasiKeSegmen(segmen))
                    }
                )
            }

            if (stateMassal.sedangMemilihTujuan) {
                BannerTempelBerkas(
                    apakahPindah = stateMassal.apakahPindah,
                    jumlahItem = stateMassal.daftarSumber.size,
                    dapatTempel = lokasiAktif != null && state.kemampuanUi.dapatMembuat,
                    onTempel = { viewModelOperasiMassal?.bukaKonfirmasiTempel() },
                    onBatal = { viewModelOperasiMassal?.batalModeTempel() }
                )
            }

            itemPembandingAwal?.let { pembandingAwal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pilih berkas kedua untuk dibandingkan dengan '${pembandingAwal.nama}'",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { itemPembandingAwal = null }) {
                        Text("Batal")
                    }
                }
            }

            KotakSaringCepat(
                kueri = state.kataKunciSaring,
                onUbahKueri = { viewModel.prosesIntent(IntentFilemanager.UbahKataKunciSaring(it)) }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f)) {
                if (state.sedangMemuat || state.pesanGalat != null || state.daftarItem.isEmpty()) {
                    LayarStatusFilemanager(
                        sedangMemuat = state.sedangMemuat,
                        pesanGalat = state.pesanGalat,
                        jumlahItem = state.jumlahItem,
                        promptLokasi = lokasiAktif?.let { PemformatLokasiRamah.formatPrompt(it) } ?: "~ $",
                        onMuatUlang = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }
                    )
                } else if (state.modeTampilan == ModeTampilan.DAFTAR) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.daftarItem,
                            key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                        ) { item ->
                            val menuTerbukaUntukItem = menuKonteksItem?.lokasi == item.lokasi
                            Box {
                                ItemBerkasBaris(
                                    item = item,
                                    terpilih = item.lokasi in state.itemTerpilih,
                                    modeSeleksi = state.sedangMultiSelect,
                                    onKlik = {
                                        val pembandingAwal = itemPembandingAwal
                                        if (pembandingAwal != null && !item.adalahFolder) {
                                            onBandingkanBerkas?.invoke(pembandingAwal, item)
                                            itemPembandingAwal = null
                                        } else if (sedangMultiSelectTerbaru) {
                                            viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                        } else if (item.adalahFolder) {
                                            viewModel.prosesIntent(IntentFilemanager.BukaLokasi(item.lokasi))
                                        } else if (
                                            onBukaArsipTerbaru != null &&
                                            (item.jenis == JenisBerkas.ARSIP || FormatArsip.dariNamaBerkas(item.nama) == FormatArsip.JAR)
                                        ) {
                                            onBukaArsipTerbaru?.invoke(item)
                                        } else {
                                            onBukaBerkasTerbaru(item)
                                        }
                                    },
                                    onTekanLama = {
                                        viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                    },
                                    onKlikMenu = { menuKonteksItem = item }
                                )
                                if (menuTerbukaUntukItem) {
                                    MenuKonteksItemBerkas(
                                        item = item,
                                        terbuka = true,
                                        kemampuan = state.kemampuanUi,
                                        onTutup = { menuKonteksItem = null },
                                        onPilih = { viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi)) },
                                        onSalin = { viewModelOperasiMassal?.inisiasiSalin(listOf(item.lokasi)) },
                                        onPindah = { viewModelOperasiMassal?.inisiasiPindah(listOf(item.lokasi)) },
                                        onBukaDengan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(item)) },
                                        onBandingkan = if (onBandingkanBerkas != null) {
                                            { itemPembandingAwal = item }
                                        } else {
                                            null
                                        },
                                        onBukaArsip = if (onBukaArsipTerbaru != null) {
                                            { onBukaArsipTerbaru?.invoke(item) }
                                        } else {
                                            null
                                        },
                                        onEkstrak = if (viewModelArsip != null && lokasiAktif != null) {
                                            {
                                                viewModelArsip.prosesIntent(
                                                    IntentArsip.EkstrakLangsungDariFileManager(
                                                        sumber = item.lokasi,
                                                        folderTujuan = lokasiAktif
                                                    )
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onKompres = if (viewModelArsip != null && lokasiAktif != null) {
                                            {
                                                viewModelArsip.prosesIntent(
                                                    IntentArsip.BukaDialogKompres(
                                                        daftarSumber = listOf(
                                                            ItemSumberKompresi(
                                                                lokasi = item.lokasi,
                                                                namaDalamArsip = item.nama,
                                                                adalahFolder = item.adalahFolder
                                                            )
                                                        ),
                                                        tujuanInduk = lokasiAktif
                                                    )
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onBookmark = if (onBookmarkItem != null) {
                                            { onBookmarkItem(item.lokasi, item.jenis, item.adalahFolder) }
                                        } else {
                                            null
                                        },
                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        items(
                            items = state.daftarItem,
                            key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                        ) { item ->
                            val menuTerbukaUntukItem = menuKonteksItem?.lokasi == item.lokasi
                            Box {
                                ItemBerkasKotak(
                                    item = item,
                                    terpilih = item.lokasi in state.itemTerpilih,
                                    modeSeleksi = state.sedangMultiSelect,
                                    onKlik = {
                                        val pembandingAwal = itemPembandingAwal
                                        if (pembandingAwal != null && !item.adalahFolder) {
                                            onBandingkanBerkas?.invoke(pembandingAwal, item)
                                            itemPembandingAwal = null
                                        } else if (sedangMultiSelectTerbaru) {
                                            viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                        } else if (item.adalahFolder) {
                                            viewModel.prosesIntent(IntentFilemanager.BukaLokasi(item.lokasi))
                                        } else if (
                                            onBukaArsipTerbaru != null &&
                                            (item.jenis == JenisBerkas.ARSIP || FormatArsip.dariNamaBerkas(item.nama) == FormatArsip.JAR)
                                        ) {
                                            onBukaArsipTerbaru?.invoke(item)
                                        } else {
                                            onBukaBerkasTerbaru(item)
                                        }
                                    },
                                    onTekanLama = {
                                        viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                    },
                                    onKlikMenu = { menuKonteksItem = item }
                                )
                                if (menuTerbukaUntukItem) {
                                    MenuKonteksItemBerkas(
                                        item = item,
                                        terbuka = true,
                                        kemampuan = state.kemampuanUi,
                                        onTutup = { menuKonteksItem = null },
                                        onPilih = { viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi)) },
                                        onSalin = { viewModelOperasiMassal?.inisiasiSalin(listOf(item.lokasi)) },
                                        onPindah = { viewModelOperasiMassal?.inisiasiPindah(listOf(item.lokasi)) },
                                        onBukaDengan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(item)) },
                                        onBandingkan = if (onBandingkanBerkas != null) {
                                            { itemPembandingAwal = item }
                                        } else {
                                            null
                                        },
                                        onBukaArsip = if (onBukaArsipTerbaru != null) {
                                            { onBukaArsipTerbaru?.invoke(item) }
                                        } else {
                                            null
                                        },
                                        onEkstrak = if (viewModelArsip != null && lokasiAktif != null) {
                                            {
                                                viewModelArsip.prosesIntent(
                                                    IntentArsip.EkstrakLangsungDariFileManager(
                                                        sumber = item.lokasi,
                                                        folderTujuan = lokasiAktif
                                                    )
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onKompres = if (viewModelArsip != null && lokasiAktif != null) {
                                            {
                                                viewModelArsip.prosesIntent(
                                                    IntentArsip.BukaDialogKompres(
                                                        daftarSumber = listOf(
                                                            ItemSumberKompresi(
                                                                lokasi = item.lokasi,
                                                                namaDalamArsip = item.nama,
                                                                adalahFolder = item.adalahFolder
                                                            )
                                                        ),
                                                        tujuanInduk = lokasiAktif
                                                    )
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onBookmark = if (onBookmarkItem != null) {
                                            { onBookmarkItem(item.lokasi, item.jenis, item.adalahFolder) }
                                        } else {
                                            null
                                        },
                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (tampilSwitcherLokasi) {
        ModalBottomSheet(
            onDismissRequest = { tampilSwitcherLokasi = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Lokasi Penyimpanan",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                lokasiPintasan.forEach { pintasan ->
                    val aktif = lokasiAktif != null &&
                        lokasiAktif.pengenal == pintasan.pengenal &&
                        lokasiAktif.jenis == pintasan.jenis
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (aktif) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                            .clickable {
                                tampilSwitcherLokasi = false
                                if (onPilihLokasiPintasan != null) {
                                    onPilihLokasiPintasan(pintasan)
                                } else {
                                    viewModel.prosesIntent(IntentFilemanager.BukaLokasi(pintasan))
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.Folder,
                            contentDescription = null,
                            tint = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pintasan.label ?: pintasan.pengenal.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(pintasan),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (aktif) {
                            Icon(
                                imageVector = IkonAplikasi.Centang,
                                contentDescription = "Aktif",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (izinMemoriInternalAktif == false && onMintaIzinMemoriInternal != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onMintaIzinMemoriInternal()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Izinkan Akses Memori Internal",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Buka /storage/emulated/0 langsung tanpa pilih folder SAF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (onJadikanBeranda != null && lokasiAktif != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onJadikanBeranda(lokasiAktif)
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.Beranda,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Jadikan Folder Ini sebagai Beranda",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onPilihFolder != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                tampilSwitcherLokasi = false
                                onPilihFolder()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.PilihFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Tambah / Pilih Folder Lain...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (state.dialogUrutkanTerbuka) {
        DialogUrutkanBerkas(
            terbuka = true,
            kriteriaSaatIni = state.kriteriaUrutan,
            arahSaatIni = state.arahUrutan,
            folderDiAtas = state.folderDiAtas,
            tampilkanTersembunyi = state.tampilkanTersembunyi,
            onPilihKriteria = { viewModel.prosesIntent(IntentFilemanager.UbahPengurutan(it, state.arahUrutan)) },
            onPilihArah = { viewModel.prosesIntent(IntentFilemanager.UbahPengurutan(state.kriteriaUrutan, it)) },
            onToggleFolderDiAtas = { viewModel.prosesIntent(IntentFilemanager.ToggleFolderDiAtas) },
            onToggleTersembunyi = { viewModel.prosesIntent(IntentFilemanager.ToggleFileTersembunyi) },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogUrutkan(false)) }
        )
    }

    if (state.dialogBuatTerbuka) {
        DialogBuatBerkas(
            terbuka = true,
            onBuatFolder = { viewModel.prosesIntent(IntentFilemanager.BuatFolderBaru(it)) },
            onBuatBerkas = { viewModel.prosesIntent(IntentFilemanager.BuatBerkasBaru(it)) },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogBuat(false)) }
        )
    }

    if (state.dialogPropertiItem != null) {
        DialogPropertiBerkas(
            item = state.dialogPropertiItem,
            kemampuan = state.kemampuanUi,
            onSalinNama = { nama ->
                clipboardManager.setText(AnnotatedString(nama))
                viewModel.prosesIntent(IntentFilemanager.SetPesanInfo("Nama disalin: $nama"))
            },
            onSalinPath = { path ->
                clipboardManager.setText(AnnotatedString(path))
                viewModel.prosesIntent(IntentFilemanager.SetPesanInfo("Lokasi disalin"))
            },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(null)) }
        )
    }

    if (state.dialogRenameItem != null) {
        DialogRenameBerkas(
            item = state.dialogRenameItem,
            onGantiNama = { namaBaru ->
                state.dialogRenameItem?.let {
                    viewModel.prosesIntent(IntentFilemanager.EksekusiRename(it.lokasi, namaBaru))
                }
            },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(null)) }
        )
    }

    if (state.konfirmasiTimpa != null) {
        DialogKonfirmasiTimpa(
            permintaan = state.konfirmasiTimpa,
            onKonfirmasi = { viewModel.prosesIntent(IntentFilemanager.KonfirmasiTimpaAksi(it)) },
            onBatal = { viewModel.prosesIntent(IntentFilemanager.TutupKonfirmasiTimpa) }
        )
    }

    if (!state.dialogHapusItems.isNullOrEmpty()) {
        DialogKonfirmasiHapus(
            daftarHapus = state.dialogHapusItems,
            onKonfirmasiHapus = { viewModel.prosesIntent(IntentFilemanager.EksekusiHapusTerpilih) },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(null)) }
        )
    }

    if (state.dialogBukaDenganItem != null) {
        DialogBukaDengan(
            item = state.dialogBukaDenganItem,
            onBukaEditorInternal = { onBukaBerkas(it) },
            onBukaAplikasiEksternal = { aksiPlatform.bukaDengan(it.lokasi, it.nama) },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(null)) }
        )
    }

    if (state.dialogBagikanItem != null) {
        DialogBagikan(
            item = state.dialogBagikanItem,
            onKonfirmasiBagikan = { aksiPlatform.bagikanBerkas(it.lokasi, it.nama) },
            onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(null)) }
        )
    }

    if (stateMassal.dialogPilihTujuanTerbuka) {
        DialogPindahSalin(
            terbuka = true,
            apakahPindah = stateMassal.apakahPindah,
            jumlahItem = stateMassal.daftarSumber.size,
            lokasiTujuan = lokasiAktif,
            onKonfirmasi = {
                lokasiAktif?.let { tujuan ->
                    viewModelOperasiMassal?.eksekusiKeTujuan(tujuan) { hasil ->
                        viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                        viewModel.prosesIntent(IntentFilemanager.Segarkan)
                        val adaKonflik = viewModelOperasiMassal.state.value.tampilkanDialogKonflikTimpa
                        if (!adaKonflik) {
                            val pesan = when {
                                hasil == null -> "Operasi dibatalkan"
                                hasil.adaGagal -> "Selesai: ${hasil.berhasil.size} berhasil, ${hasil.gagal.size} gagal"
                                else -> "Berhasil memproses ${hasil.berhasil.size} item"
                            }
                            viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
                        }
                    }
                }
            },
            onTutup = { viewModelOperasiMassal?.tutupDialogTujuan() }
        )
    }

    if (stateMassal.tampilkanDialogKonflikTimpa) {
        DialogKonfirmasiTimpaMassal(
            terbuka = true,
            apakahPindah = stateMassal.apakahPindah,
            daftarKonflik = stateMassal.daftarKonflik,
            onTimpa = {
                viewModelOperasiMassal?.eksekusiTimpaKonflik { hasil ->
                    viewModel.prosesIntent(IntentFilemanager.Segarkan)
                    val pesan = when {
                        hasil == null -> "Operasi dibatalkan"
                        hasil.adaGagal -> "Selesai: ${hasil.berhasil.size} berhasil, ${hasil.gagal.size} gagal"
                        else -> "Berhasil memproses ${hasil.berhasil.size} item"
                    }
                    viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
                }
            },
            onLewati = {
                viewModelOperasiMassal?.lewatiKonflik { hasil ->
                    val pesan = if (hasil.adaGagal) {
                        "${hasil.berhasil.size} berhasil, konflik dilewati, ${hasil.gagal.size} gagal"
                    } else {
                        "${hasil.berhasil.size} berhasil, item konflik dilewati"
                    }
                    viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
                }
            }
        )
    }

    if (stateMassal.tampilkanDialogHasil) {
        DialogHasilOperasiMassal(
            terbuka = true,
            hasil = stateMassal.hasilTerakhir,
            onTutup = { viewModelOperasiMassal?.tutupDialogHasil() }
        )
    }

    if (stateMassal.sedangBerjalan) {
        IndikatorProgresMassal(
            state = stateMassal,
            onBatal = { viewModelOperasiMassal?.batalkanOperasi() }
        )
    }

    if (viewModelBulkRename != null) {
        DialogBulkRename(
            viewModel = viewModelBulkRename,
            onSelesaiSukses = { jumlahBerhasil ->
                viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                viewModel.prosesIntent(IntentFilemanager.Segarkan)
                viewModel.prosesIntent(
                    IntentFilemanager.SetPesanInfo("Berhasil mengganti nama $jumlahBerhasil item")
                )
            },
            onTutupDenganPerubahan = {
                viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                viewModel.prosesIntent(IntentFilemanager.Segarkan)
            }
        )
    }

    if (viewModelArsip != null) {
        DialogKompres(
            viewModel = viewModelArsip,
            onSelesaiKompres = { entitasHasil ->
                viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                viewModel.prosesIntent(IntentFilemanager.Segarkan)
                viewModel.prosesIntent(
                    IntentFilemanager.SetPesanInfo("Arsip '${entitasHasil.nama}' berhasil dibuat dan diverifikasi")
                )
            }
        )

        OverlayDialogEkstrakArsip(
            viewModel = viewModelArsip,
            onPilihFolderSistem = onPilihFolder,
            onEkstrakSelesai = { hasil, _ ->
                viewModel.prosesIntent(IntentFilemanager.Segarkan)
                val info = if (!hasil.adaGagal && !hasil.adaKonflik) {
                    "Berhasil mengekstrak ${hasil.jumlahDiekstrak} berkas"
                } else {
                    "Ekstraksi selesai: ${hasil.jumlahDiekstrak} berhasil, ${hasil.daftarGagal.size + hasil.daftarKonflik.size} perlu perhatian"
                }
                viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(info))
            }
        )
    }
}

@Composable
private fun KotakSaringCepat(
    kueri: String,
    onUbahKueri: (String) -> Unit
) {
    val fontMono = LocalFontMono.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = IkonAplikasi.Cari,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (kueri.isEmpty()) {
                Text(
                    text = "saring di folder ini...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = kueri,
                onValueChange = onUbahKueri,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontMono,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (kueri.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = IkonAplikasi.Tutup,
                contentDescription = "Bersihkan saringan",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onUbahKueri("") }
            )
        }
    }
}

@Composable
private fun BannerTempelBerkas(
    apakahPindah: Boolean,
    jumlahItem: Int,
    dapatTempel: Boolean,
    onTempel: () -> Unit,
    onBatal: () -> Unit
) {
    val kata = if (apakahPindah) "Memindah" else "Menyalin"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$kata $jumlahItem item. Buka tujuan lalu tempel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onBatal) {
            Text("Batal")
        }
        Spacer(modifier = Modifier.width(6.dp))
        Button(onClick = onTempel, enabled = dapatTempel) {
            Text("Tempel")
        }
    }
}

@Composable
private fun DialogKonfirmasiTimpa(
    permintaan: PermintaanKonfirmasiTimpa?,
    onKonfirmasi: (PermintaanKonfirmasiTimpa) -> Unit,
    onBatal: () -> Unit
) {
    if (permintaan == null) return
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Konfirmasi Timpa") },
        text = {
            Text("Item bernama '${permintaan.nama}' sudah ada di folder ini. Timpa item yang sudah ada?")
        },
        confirmButton = {
            TextButton(onClick = { onKonfirmasi(permintaan) }) {
                Text("Timpa", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onBatal) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun MenuKonteksItemBerkas(
    item: ItemBerkasUi,
    terbuka: Boolean,
    kemampuan: ModelKemampuanUi,
    onTutup: () -> Unit,
    onPilih: () -> Unit,
    onSalin: () -> Unit,
    onPindah: () -> Unit,
    onBukaDengan: () -> Unit,
    onBagikan: () -> Unit,
    onGantiNama: () -> Unit,
    onProperti: () -> Unit,
    onHapus: () -> Unit,
    onBandingkan: (() -> Unit)? = null,
    onBukaArsip: (() -> Unit)? = null,
    onEkstrak: (() -> Unit)? = null,
    onKompres: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null
) {
    val adalahItemArsip = !item.adalahFolder &&
        (item.jenis == JenisBerkas.ARSIP || FormatArsip.dariNamaBerkas(item.nama) != null)

    DropdownMenu(
        expanded = terbuka,
        onDismissRequest = onTutup,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        DropdownMenuItem(
            text = { Text("Pilih") },
            leadingIcon = { Icon(IkonAplikasi.Centang, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = { onTutup(); onPilih() }
        )
        MenuItemDenganAlasan(
            judul = "Salin",
            ikon = IkonAplikasi.Salin,
            didukung = kemampuan.dapatSalin,
            alasan = kemampuan.alasan(OperasiBerkas.MENYALIN),
            onKlik = { onTutup(); onSalin() }
        )
        MenuItemDenganAlasan(
            judul = "Pindah",
            ikon = IkonAplikasi.Pindah,
            didukung = kemampuan.dapatPindah,
            alasan = kemampuan.alasan(OperasiBerkas.MEMINDAHKAN),
            onKlik = { onTutup(); onPindah() }
        )
        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
        if (adalahItemArsip) {
            if (onBukaArsip != null) {
                MenuItemDenganAlasan(
                    judul = "Buka sebagai Arsip",
                    ikon = IkonAplikasi.Arsip,
                    didukung = true,
                    alasan = null,
                    onKlik = { onTutup(); onBukaArsip() }
                )
            }
            if (onEkstrak != null) {
                MenuItemDenganAlasan(
                    judul = "Ekstrak ke Sini...",
                    ikon = IkonAplikasi.Ekstrak,
                    didukung = kemampuan.dapatEkstrak,
                    alasan = kemampuan.alasan(OperasiBerkas.MENGEKSTRAK),
                    onKlik = { onTutup(); onEkstrak() }
                )
            }
        }
        if (onKompres != null) {
            MenuItemDenganAlasan(
                judul = "Kompres...",
                ikon = IkonAplikasi.Arsip,
                didukung = kemampuan.dapatKompres,
                alasan = kemampuan.alasan(OperasiBerkas.MENGKOMPRES),
                onKlik = { onTutup(); onKompres() }
            )
        }
        if (onBookmark != null) {
            MenuItemDenganAlasan(
                judul = "Bookmark",
                ikon = IkonAplikasi.Bookmark,
                didukung = kemampuan.dapatBookmark,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBOOKMARK),
                onKlik = { onTutup(); onBookmark() }
            )
        }
        if (!item.adalahFolder) {
            if (onBandingkan != null) {
                MenuItemDenganAlasan(
                    judul = "Bandingkan dengan...",
                    ikon = IkonAplikasi.Bandingkan,
                    didukung = true,
                    alasan = null,
                    onKlik = { onTutup(); onBandingkan() }
                )
            }
            MenuItemDenganAlasan(
                judul = "Buka Dengan...",
                ikon = IkonAplikasi.BukaDengan,
                didukung = kemampuan.dapatBukaDengan,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBUKA_DENGAN),
                onKlik = { onTutup(); onBukaDengan() }
            )
            MenuItemDenganAlasan(
                judul = "Bagikan",
                ikon = IkonAplikasi.Bagikan,
                didukung = kemampuan.dapatBagikan,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBAGIKAN),
                onKlik = { onTutup(); onBagikan() }
            )
        }
        MenuItemDenganAlasan(
            judul = "Ganti Nama",
            ikon = IkonAplikasi.GantiNama,
            didukung = kemampuan.dapatGantiNama,
            alasan = kemampuan.alasan(OperasiBerkas.MENGGANTI_NAMA),
            onKlik = { onTutup(); onGantiNama() }
        )
        if (!kemampuan.dapatBuatTautanSimbolik) {
            MenuItemDenganAlasan(
                judul = "Tautan Simbolik",
                ikon = IkonAplikasi.Tautan,
                didukung = false,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK),
                onKlik = {}
            )
        }
        DropdownMenuItem(
            text = { Text("Properti") },
            leadingIcon = { Icon(IkonAplikasi.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = { onTutup(); onProperti() }
        )
        HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
        MenuItemDenganAlasan(
            judul = "Hapus",
            ikon = IkonAplikasi.Hapus,
            didukung = kemampuan.dapatHapus,
            alasan = kemampuan.alasan(OperasiBerkas.MENGHAPUS),
            warnaKhusus = MaterialTheme.colorScheme.error,
            onKlik = { onTutup(); onHapus() }
        )
    }
}

@Composable
private fun MenuItemDenganAlasan(
    judul: String,
    ikon: ImageVector,
    didukung: Boolean,
    alasan: String?,
    warnaKhusus: Color? = null,
    onKlik: () -> Unit
) {
    val warnaTeks = when {
        !didukung -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        warnaKhusus != null -> warnaKhusus
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = warnaTeks,
                modifier = Modifier.size(18.dp)
            )
        },
        text = {
            Column {
                Text(judul, color = warnaTeks)
                if (!didukung && !alasan.isNullOrBlank()) {
                    Text(
                        text = alasan,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        enabled = didukung,
        onClick = onKlik
    )
}