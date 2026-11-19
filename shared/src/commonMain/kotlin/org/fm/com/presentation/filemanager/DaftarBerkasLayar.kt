package org.fm.com.presentation.filemanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaftarBerkasLayar(
    viewModel: ViewModelFilemanager,
    viewModelOperasiMassal: ViewModelOperasiMassal? = null,
    aksiPlatform: AksiPlatformBerkas = remember { AksiPlatformBerkasBawaan() },
    onBukaBerkas: (ItemBerkasUi) -> Unit,
    onPilihFolder: (() -> Unit)? = null,
    lokasiPintasan: List<LokasiBerkas> = emptyList(),
    onPilihLokasiPintasan: ((LokasiBerkas) -> Unit)? = null,
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
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(state.lokasiSaatIni?.label ?: "Penjelajah Berkas") },
                    actions = {
                        if (onPilihFolder != null) {
                            TextButton(onClick = onPilihFolder) {
                                Text("Pilih")
                            }
                        }
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
            if (!state.sedangMultiSelect && state.kemampuanUi.dapatMembuat) {
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
            if (lokasiPintasan.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lokasiPintasan.forEach { pintasan ->
                        AssistChip(
                            onClick = {
                                if (onPilihLokasiPintasan != null) {
                                    onPilihLokasiPintasan(pintasan)
                                } else {
                                    viewModel.prosesIntent(IntentFilemanager.BukaLokasi(pintasan))
                                }
                            },
                            label = {
                                Text(pintasan.label ?: pintasan.pengenal.substringAfterLast('/'))
                            }
                        )
                    }
                }
            }

            if (state.segmenBreadcrumb.isNotEmpty()) {
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
                    dapatTempel = state.lokasiSaatIni != null && state.kemampuanUi.dapatMembuat,
                    onTempel = { viewModelOperasiMassal?.bukaKonfirmasiTempel() },
                    onBatal = { viewModelOperasiMassal?.batalModeTempel() }
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
                                key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
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
                                    MenuKonteksItemBerkas(
                                        item = item,
                                        terbuka = menuKonteksItem?.lokasi == item.lokasi,
                                        kemampuan = state.kemampuanUi,
                                        onTutup = { menuKonteksItem = null },
                                        onPilih = { viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi)) },
                                        onSalin = { viewModelOperasiMassal?.inisiasiSalin(listOf(item.lokasi)) },
                                        onPindah = { viewModelOperasiMassal?.inisiasiPindah(listOf(item.lokasi)) },
                                        onBukaDengan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(item)) },
                                        onBagikan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(item)) },
                                        onGantiNama = { viewModel.prosesIntent(IntentFilemanager.BukaDialogRename(item)) },
                                        onProperti = { viewModel.prosesIntent(IntentFilemanager.BukaDialogProperti(item)) },
                                        onHapus = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(item.lokasi))) }
                                    )
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
                                key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
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
                                    MenuKonteksItemBerkas(
                                        item = item,
                                        terbuka = menuKonteksItem?.lokasi == item.lokasi,
                                        kemampuan = state.kemampuanUi,
                                        onTutup = { menuKonteksItem = null },
                                        onPilih = { viewModel.prosesIntent(IntentFilemanager.TogglePilihItem(item.lokasi)) },
                                        onSalin = { viewModelOperasiMassal?.inisiasiSalin(listOf(item.lokasi)) },
                                        onPindah = { viewModelOperasiMassal?.inisiasiPindah(listOf(item.lokasi)) },
                                        onBukaDengan = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(item)) },
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
        kemampuan = state.kemampuanUi,
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

    DialogKonfirmasiTimpa(
        permintaan = state.konfirmasiTimpa,
        onKonfirmasi = { viewModel.prosesIntent(IntentFilemanager.KonfirmasiTimpaAksi(it)) },
        onBatal = { viewModel.prosesIntent(IntentFilemanager.TutupKonfirmasiTimpa) }
    )

    DialogKonfirmasiHapus(
        daftarHapus = state.dialogHapusItems,
        onKonfirmasiHapus = { viewModel.prosesIntent(IntentFilemanager.EksekusiHapusTerpilih) },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogHapus(null)) }
    )

    DialogBukaDengan(
        item = state.dialogBukaDenganItem,
        onBukaEditorInternal = { onBukaBerkas(it) },
        onBukaAplikasiEksternal = { aksiPlatform.bukaDengan(it.lokasi, it.nama) },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBukaDengan(null)) }
    )

    DialogBagikan(
        item = state.dialogBagikanItem,
        onKonfirmasiBagikan = { aksiPlatform.bagikanBerkas(it.lokasi, it.nama) },
        onTutup = { viewModel.prosesIntent(IntentFilemanager.BukaDialogBagikan(null)) }
    )

    DialogPindahSalin(
        terbuka = stateMassal.dialogPilihTujuanTerbuka,
        apakahPindah = stateMassal.apakahPindah,
        jumlahItem = stateMassal.daftarSumber.size,
        lokasiTujuan = state.lokasiSaatIni,
        onKonfirmasi = {
            state.lokasiSaatIni?.let { tujuan ->
                viewModelOperasiMassal?.eksekusiKeTujuan(tujuan) { hasil ->
                    viewModel.prosesIntent(IntentFilemanager.BersihkanPilihan)
                    viewModel.prosesIntent(IntentFilemanager.Segarkan)
                    val adaKonflik = viewModelOperasiMassal.state.value.tampilkanDialogKonflikTimpa
                    if (!adaKonflik) {
                        val pesan = when {
                            hasil == null -> "Operasi dibatalkan"
                            hasil.adaGagal -> "Selesai dengan ${hasil.berhasil.size} berhasil dan ${hasil.gagal.size} gagal"
                            else -> "Berhasil memproses ${hasil.berhasil.size} item"
                        }
                        viewModel.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
                    }
                }
            }
        },
        onTutup = { viewModelOperasiMassal?.tutupDialogTujuan() }
    )

    DialogKonfirmasiTimpaMassal(
        terbuka = stateMassal.tampilkanDialogKonflikTimpa,
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

    DialogHasilOperasiMassal(
        terbuka = stateMassal.tampilkanDialogHasil,
        hasil = stateMassal.hasilTerakhir,
        onTutup = { viewModelOperasiMassal?.tutupDialogHasil() }
    )

    IndikatorProgresMassal(
        state = stateMassal,
        onBatal = { viewModelOperasiMassal?.batalkanOperasi() }
    )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$kata $jumlahItem item. Buka folder tujuan lalu tempel.",
                style = MaterialTheme.typography.bodySmall,
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
    onHapus: () -> Unit
) {
    DropdownMenu(
        expanded = terbuka,
        onDismissRequest = onTutup
    ) {
        DropdownMenuItem(
            text = { Text("Pilih") },
            onClick = { onTutup(); onPilih() }
        )
        MenuItemDenganAlasan(
            judul = "Salin",
            didukung = kemampuan.dapatSalin,
            alasan = kemampuan.alasan(OperasiBerkas.MENYALIN),
            onKlik = { onTutup(); onSalin() }
        )
        MenuItemDenganAlasan(
            judul = "Pindah",
            didukung = kemampuan.dapatPindah,
            alasan = kemampuan.alasan(OperasiBerkas.MEMINDAHKAN),
            onKlik = { onTutup(); onPindah() }
        )
        if (!item.adalahFolder) {
            MenuItemDenganAlasan(
                judul = "Buka Dengan...",
                didukung = kemampuan.dapatBukaDengan,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBUKA_DENGAN),
                onKlik = { onTutup(); onBukaDengan() }
            )
            MenuItemDenganAlasan(
                judul = "Bagikan",
                didukung = kemampuan.dapatBagikan,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBAGIKAN),
                onKlik = { onTutup(); onBagikan() }
            )
        }
        MenuItemDenganAlasan(
            judul = "Ganti Nama",
            didukung = kemampuan.dapatGantiNama,
            alasan = kemampuan.alasan(OperasiBerkas.MENGGANTI_NAMA),
            onKlik = { onTutup(); onGantiNama() }
        )
        if (!kemampuan.dapatBuatTautanSimbolik) {
            MenuItemDenganAlasan(
                judul = "Buat Tautan Simbolik",
                didukung = false,
                alasan = kemampuan.alasan(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK),
                onKlik = {}
            )
        }
        DropdownMenuItem(
            text = { Text("Properti") },
            onClick = { onTutup(); onProperti() }
        )
        MenuItemDenganAlasan(
            judul = "Hapus",
            didukung = kemampuan.dapatHapus,
            alasan = kemampuan.alasan(OperasiBerkas.MENGHAPUS),
            onKlik = { onTutup(); onHapus() }
        )
    }
}

@Composable
private fun MenuItemDenganAlasan(
    judul: String,
    didukung: Boolean,
    alasan: String?,
    onKlik: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(judul)
                if (!didukung && !alasan.isNullOrBlank()) {
                    Text(
                        text = alasan,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        enabled = didukung,
        onClick = onKlik
    )
}