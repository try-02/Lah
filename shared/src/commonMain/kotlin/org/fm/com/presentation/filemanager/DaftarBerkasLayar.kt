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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarBerkasLayar(
    viewModel: ViewModelFilemanager,
    onBukaBerkas: (ItemBerkasUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (state.sedangMultiSelect) {
                BarAksiSeleksi(
                    jumlahTerpilih = state.itemTerpilih.size,
                    onPilihSemua = { viewModel.prosesIntent(IntentFilemanager.PilihSemua) },
                    onBersihkanPilihan = { viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan) },
                    onAksiHapus = { /* Menuju dialog konfirmasi T21 */ },
                    onAksiSalin = { /* Menuju use case massal T22 */ },
                    onAksiPindah = { /* Menuju use case massal T22 */ }
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
                                        viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                    }
                                )
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
                                        viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi))
                                    }
                                )
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
}