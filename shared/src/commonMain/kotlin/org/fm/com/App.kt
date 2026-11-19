package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.editor.IntentEditor
import org.fm.com.presentation.editor.LayarEditor
import org.fm.com.presentation.editor.ViewModelEditor
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal
import org.fm.com.presentation.pencarian.IntentPencarian
import org.fm.com.presentation.pencarian.LayarPencarian
import org.fm.com.presentation.pencarian.ViewModelPencarian
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
    MaterialTheme {
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

        val stateFm by vmFilemanager.state.collectAsState()
        val stateEditor by vmEditor.state.collectAsState()
        var tabTerpilih by remember { mutableIntStateOf(0) }
        var ruangEditorTerbuka by remember { mutableStateOf(false) }

        LaunchedEffect(kontainer) {
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(kontainer.lokasiAwal) ?: kontainer.lokasiAwal
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
            vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiSiap))
        }

        LaunchedEffect(stateFm.lokasiSaatIni) {
            val lokasiAktif = stateFm.lokasiSaatIni
            vmEditor.prosesIntent(IntentEditor.SetLokasiFolderSimpan(lokasiAktif))
            if (lokasiAktif != null) {
                vmPencarian.prosesIntent(IntentPencarian.SetFolderAkar(lokasiAktif))
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

        if (ruangEditorTerbuka) {
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
                    NavigationBar {
                        NavigationBarItem(
                            selected = tabTerpilih == 0,
                            onClick = { tabTerpilih = 0 },
                            label = { Text("Berkas") },
                            icon = { Text("📁") }
                        )
                        NavigationBarItem(
                            selected = tabTerpilih == 1,
                            onClick = { tabTerpilih = 1 },
                            label = { Text("Terbaru") },
                            icon = { Text("🕒") }
                        )
                        NavigationBarItem(
                            selected = tabTerpilih == 2,
                            onClick = { tabTerpilih = 2 },
                            label = { Text("Cari") },
                            icon = { Text("🔍") }
                        )
                        if (stateEditor.jumlahTab > 0) {
                            NavigationBarItem(
                                selected = false,
                                onClick = { ruangEditorTerbuka = true },
                                label = { Text("Editor (${stateEditor.jumlahTab})") },
                                icon = { Text("📝") }
                            )
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
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}