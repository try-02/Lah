package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.diff.IntentDiff
import org.fm.com.presentation.diff.LayarDiff
import org.fm.com.presentation.diff.ViewModelDiff
import org.fm.com.presentation.editor.IntentEditor
import org.fm.com.presentation.editor.LayarEditor
import org.fm.com.presentation.editor.ViewModelEditor
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.pencarian.IntentPencarian
import org.fm.com.presentation.pencarian.LayarPencarian
import org.fm.com.presentation.pencarian.ViewModelPencarian
import org.fm.com.presentation.rename.ViewModelBulkRename
import org.fm.com.presentation.tema.TemaAplikasi
import org.fm.com.presentation.terbaru.LayarTerbaru
import org.fm.com.presentation.terbaru.ViewModelTerbaru

data class PermintaanBukaLokasi(
    val lokasi: LokasiBerkas,
    val id: Long
)

@Composable
fun App(
    kontainer: KontainerAplikasi = remember { buatKontainerPreview() },
    permintaanBukaEksternal: PermintaanBukaLokasi? = null,
    pesanEksternal: Pair<Long, String>? = null,
    lokasiPintasan: List<LokasiBerkas> = kontainer.lokasiPintasan,
    onPilihFolder: (() -> Unit)? = null,
    onSiapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
) {
    TemaAplikasi {
        val scope = rememberCoroutineScope()
        val kunciKontainer = remember(kontainer) { kontainer.hashCode().toString() }

        val vmFilemanager: ViewModelFilemanager = viewModel(key = "fm_$kunciKontainer") {
            ViewModelFilemanager(
                repositoriBerkas = kontainer.repositoriBerkas,
                repositoriOperasi = kontainer.repositoriOperasi,
                riwayatLokasiUseCase = kontainer.riwayatLokasiUseCase
            )
        }
        val vmOperasiMassal: ViewModelOperasiMassal = viewModel(key = "massal_$kunciKontainer") {
            ViewModelOperasiMassal(kontainer.repositoriBerkas, kontainer.repositoriOperasi)
        }
        val vmTerbaru: ViewModelTerbaru = viewModel(key = "terbaru_$kunciKontainer") {
            ViewModelTerbaru(kontainer.riwayatLokasiUseCase)
        }
        val vmPencarian: ViewModelPencarian = viewModel(key = "cari_$kunciKontainer") {
            ViewModelPencarian(
                cariNamaUseCase = kontainer.cariNamaBerkasUseCase,
                cariIsiUseCase = kontainer.cariIsiBerkasUseCase
            )
        }
        val vmEditor: ViewModelEditor = viewModel(key = "editor_$kunciKontainer") {
            ViewModelEditor(
                repositoriBerkas = kontainer.repositoriBerkas,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
                simpanBerkasUseCase = kontainer.simpanBerkasUseCase
            )
        }
        val vmDiff: ViewModelDiff = viewModel(key = "diff_$kunciKontainer") {
            ViewModelDiff(kontainer.bandingkanBerkasUseCase)
        }
        val vmBulkRename: ViewModelBulkRename = viewModel(key = "rename_$kunciKontainer") {
            ViewModelBulkRename(
                repositoriBerkas = kontainer.repositoriBerkas,
                jalankanRenameMassalUseCase = kontainer.jalankanRenameMassalUseCase
            )
        }

        val lokasiAktifFm by remember(vmFilemanager) {
            vmFilemanager.state.map { it.lokasiSaatIni }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null)

        val jumlahTabEditor by remember(vmEditor) {
            vmEditor.state.map { it.jumlahTab }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = 0)

        var tabTerpilih by remember { mutableIntStateOf(0) }
        var ruangEditorTerbuka by remember { mutableStateOf(false) }
        var ruangDiffTerbuka by remember { mutableStateOf(false) }

        LaunchedEffect(kontainer) {
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(kontainer.lokasiAwal) ?: kontainer.lokasiAwal
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
            vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiSiap))
        }

        LaunchedEffect(lokasiAktifFm) {
            vmEditor.prosesIntent(IntentEditor.SetLokasiFolderSimpan(lokasiAktifFm))
            if (lokasiAktifFm != null) {
                vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiAktifFm))
            }
        }

        LaunchedEffect(permintaanBukaEksternal?.id) {
            val lokasi = permintaanBukaEksternal?.lokasi ?: return@LaunchedEffect
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
            ruangEditorTerbuka = false
            tabTerpilih = 0
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
        }

        LaunchedEffect(pesanEksternal?.first) {
            val pesan = pesanEksternal?.second ?: return@LaunchedEffect
            vmFilemanager.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
        }

        if (ruangDiffTerbuka) {
            LayarDiff(
                viewModel = vmDiff,
                onKembali = { ruangDiffTerbuka = false }
            )
        } else if (ruangEditorTerbuka) {
            LayarEditor(
                viewModel = vmEditor,
                onKembali = {
                    ruangEditorTerbuka = false
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                }
            )
        } else {
            Scaffold(
                bottomBar = {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp
                        ) {
                            val warnaItem = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            NavigationBarItem(
                                selected = tabTerpilih == 0,
                                onClick = { tabTerpilih = 0 },
                                colors = warnaItem,
                                label = { Text("Berkas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = {
                                    Icon(
                                        imageVector = IkonAplikasi.Folder,
                                        contentDescription = "Berkas",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = tabTerpilih == 1,
                                onClick = { tabTerpilih = 1 },
                                colors = warnaItem,
                                label = { Text("Terbaru", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = {
                                    Icon(
                                        imageVector = IkonAplikasi.Jam,
                                        contentDescription = "Terbaru",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            NavigationBarItem(
                                selected = tabTerpilih == 2,
                                onClick = { tabTerpilih = 2 },
                                colors = warnaItem,
                                label = { Text("Cari", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = {
                                    Icon(
                                        imageVector = IkonAplikasi.Cari,
                                        contentDescription = "Cari",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            if (jumlahTabEditor > 0) {
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { ruangEditorTerbuka = true },
                                    colors = warnaItem,
                                    label = { Text("Editor ($jumlahTabEditor)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    icon = {
                                        Icon(
                                            imageVector = IkonAplikasi.Editor,
                                            contentDescription = "Buka editor",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { bantalan ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bantalan)
                ) {
                    when (tabTerpilih) {
                        0 -> DaftarBerkasLayar(
                            viewModel = vmFilemanager,
                            viewModelOperasiMassal = vmOperasiMassal,
                            viewModelBulkRename = vmBulkRename,
                            aksiPlatform = kontainer.aksiPlatform,
                            onBukaBerkas = { item ->
                                val entitas = EntitasBerkas(
                                    lokasi = item.lokasi,
                                    nama = item.nama,
                                    jenis = item.jenis,
                                    ukuranByte = item.ukuranByte,
                                    waktuUbahEpochMs = item.waktuUbahEpochMs,
                                    tersembunyi = item.tersembunyi,
                                    dapatDibaca = item.dapatDibaca,
                                    dapatDitulis = item.dapatDitulis
                                )
                                vmEditor.prosesIntent(IntentEditor.BukaBerkas(entitas))
                                ruangEditorTerbuka = true
                            },
                            onBandingkanBerkas = { itemA, itemB ->
                                vmDiff.prosesIntent(
                                    IntentDiff.Bandingkan(
                                        lokasiA = itemA.lokasi,
                                        namaA = itemA.nama,
                                        lokasiB = itemB.lokasi,
                                        namaB = itemB.nama
                                    )
                                )
                                ruangDiffTerbuka = true
                            },
                            onPilihFolder = onPilihFolder,
                            lokasiPintasan = lokasiPintasan,
                            onPilihLokasiPintasan = { lokasi ->
                                scope.launch {
                                    val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                    vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                                }
                            }
                        )
                        1 -> LayarTerbaru(
                            viewModel = vmTerbaru,
                            onBukaLokasi = { lokasi ->
                                scope.launch {
                                    val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                    tabTerpilih = 0
                                    vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                                }
                            }
                        )
                        else -> LayarPencarian(
                            viewModel = vmPencarian,
                            onBukaFolder = { lokasi ->
                                scope.launch {
                                    val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                    tabTerpilih = 0
                                    vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                                }
                            },
                            onBukaBerkasDiEditor = { entitas, nomorBaris ->
                                vmEditor.prosesIntent(
                                    IntentEditor.BukaBerkas(entitas = entitas, barisTujuan = nomorBaris)
                                )
                                ruangEditorTerbuka = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LayarMemuatAplikasi() {
    TemaAplikasi {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}