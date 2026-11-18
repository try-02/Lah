package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarBerkasLayar(
    viewModel: ViewModelFilemanager,
    viewModelOperasiMassal: ViewModelOperasiMassal? = null,
    onBukaBerkas: (ItemBerkasUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val stateMassal by viewModelOperasiMassal?.state?.collectAsState() ?: remember { mutableStateOf(StateOperasiMassal()) }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var menuKonteksItem by remember { mutableStateOf<ItemBerkasUi?>(null) }

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(null))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.sedangMultiSelect) {
                BarAksiSeleksi(
                    jumlahTerpilih = state.itemTerpilih.size,
                    onPilihSemua = { viewModel.prosesIntent(IntentFilemanager.PilihSemua) },
                    onBersihkanPilihan = { viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan) },
                    onAksiHapus = {
                        viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(state.itemTerpilih.toList()))
                    },
                    onAksiSalin = {
                        viewModelOperasiMassal?.inisiasiSalin(state.itemTerpilih.toList())
                    },
                    onAksiPindah = {
                        viewModelOperasiMassal?.inisiasiPindah(state.itemTerpilih.toList())
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(state.lokasiSaatIni?.label ?: "Penjelajah Berkas") },
                    actions = {
                        IconButton(onClick = {
                            val modeBaru = if (state.modeTampilan == ModeTampilan.DAFTAR) {
                                ModeTampilan.KISI
                            } else {
                                ModeTampilan.DAFTAR
                            }
                            viewModel.prosesIntent(IntentFilemanager.UbahModeTampilan(modeBaru))
                        }) {
                            Text(if (state.modeTampilan == ModeTampilan.DAFTAR) "Kisi" else "Daftar")
                        }
                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogUrutkan(true)) }) {
                            Text("Urut")
                        }
                        IconButton(onClick = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }) {
                            Text("Segar")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.sedangMultiSelect) {
                FloatingActionButton(onClick = {
                    viewModel.prosesIntent(IntentFilemanager.SetBukaDialogBuat(true))
                }) {
                    Text("+")
                }
            }
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            if (state.segmenBreadcrumb.isNotEmpty()) {
                BreadcrumbLokasi(
                    segmen = state.segmenBreadcrumb,
                    onPilihSegmen = { segmen ->
                        viewModel.prosesIntent(IntentFilemanager.NavigasiKeSegmen(segmen))
                    }
                )
            }

            OutlinedTextField(
                value = state.kataKunciSaring,
                onValueChange = { viewModel.prosesIntent(IntentFilemanager.UbahKataKunciSaring(it)) },
                placeholder = { Text("Cari berkas di folder ini...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.sedangMemuat || state.pesanGalat != null || state.daftarItem.isEmpty()) {
                    LayarStatusFilemanager(
                        sedangMemuat = state.sedangMemuat,
                        pesanGalat = state.pesanGalat,
                        jumlahItem = state.jumlahItem,
                        onMuatUlang = { viewModel.prosesIntent(IntentFilemanager.Segarkan) }
                    )
                } else {
                    if (state.modeTampilan == ModeTampilan.DAFTAR) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = state.daftarItem,
                                key = { it.lokasi.pengenal }
                            ) { item ->
                                Box {
                                    ItemBerkasBaris(
                                        item = item,
                                        terpilih = item.lokasi in state.itemTerpilih,
                                        modeSeleksi = state.sedangMultiSelect,
                                        onKlik = {
                                            if (state.sedangMultiSelect) {
                                                viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                            } else if (item.adalahFolder) {
                                                viewModel.prosesIntent(IntentFilemanager.BukaLokasi(item.lokasi))
                                            } else {
                                                onBukaBerkas(item)
                                            }
                                        },
                                        onTekanLama = {
                                            menuKonteksItem = item
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = menuKonteksItem == item,
                                        onDismissRequest = { menuKonteksItem = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Pilih") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ganti Nama") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Properti") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Hapus") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi)))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = state.daftarItem,
                                key = { it.lokasi.pengenal }
                            ) { item ->
                                Box {
                                    ItemBerkasKotak(
                                        item = item,
                                        terpilih = item.lokasi in state.itemTerpilih,
                                        modeSeleksi = state.sedangMultiSelect,
                                        onKlik = {
                                            if (state.sedangMultiSelect) {
                                                viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                            } else if (item.adalahFolder) {
                                                viewModel.prosesIntent(IntentFilemanager.BukaLokasi(item.lokasi))
                                            } else {
                                                onBukaBerkas(item)
                                            }
                                        },
                                        onTekanLama = {
                                            menuKonteksItem = item
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = menuKonteksItem == item,
                                        onDismissRequest = { menuKonteksItem = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Pilih") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ganti Nama") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Properti") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Hapus") },
                                            onClick = {
                                                menuKonteksItem = null
                                                viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi)))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    DialogUrutkanBerkas(
        terbuka = state.dialogUrutkanTerbuka,
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

    DialogBuatBerkas(
        terbuka = state.dialogBuatTerbuka,
        onBuatFolder = { viewModel.prosesIntent(IntentFilemanager.BuatFolderBaru(it)) },
        onBuatBerkas = { viewModel.prosesIntent(IntentFilemanager.BuatBerkasBaru(it)) },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.SetBukaDialogBuat(false)) }
    )

    DialogPropertiBerkas(
        item = state.dialogPropertiItem,
        onSalinNama = { nama ->
            clipboardManager.setText(AnnotatedString(nama))
            viewModel.prosesIntent(IntentFilemanager.SetPesanInfo("Nama disalin: $nama"))
        },
        onSalinPath = { path ->
            clipboardManager.setText(AnnotatedString(path))
            viewModel.prosesIntent(IntentFilemanager.SetPesanInfo("Lokasi disalin: $path"))
        },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(null)) }
    )

    DialogRenameBerkas(
        item = state.dialogRenameItem,
        onGantiNama = { namaBaru ->
            state.dialogRenameItem?.let {
                viewModel.prosesIntent(IntentFilemanager.EksekusiRename(it.lokasi, namaBaru))
            }
        },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(null)) }
    )

    DialogKonfirmasiHapus(
        daftarHapus = state.dialogHapusItems,
        onKonfirmasiHapus = { viewModel.prosesIntent(IntentFilemanager.EksekusiHapusTerpilih) },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(null)) }
    )

    DialogPindahSalin(
        terbuka = stateMassal.dialogPilihTujuanTerbuka,
        apakahPindah = stateMassal.apakahPindah,
        jumlahItem = stateMassal.daftarSumber.size,
        lokasiTujuan = state.lokasiSaatIni,
        onKonfirmasi = {
            state.lokasiSaatIni?.let { tujuan ->
                viewModelOperasiMassal?.eksekusiKeTujuan(tujuan) {
                    viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                    viewModel.prosesIntent(IntentFilemanager.Segarkan)
                    viewModel.prosesIntent(IntentFilemanager.SetPesanInfo("Operasi massal selesai"))
                }
            }
        },
        onTutup = { viewModelOperasiMassal?.tutupDialogTujuan() }
    )

    IndikatorProgresMassal(
        state = stateMassal,
        onBatal = { viewModelOperasiMassal?.batalkanOperasi() }
    )
}