package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.fm.com.di.KontainerAplikasi
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal
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

        var tabTerpilih by remember { mutableIntStateOf(0) }

        LaunchedEffect(kontainer) {
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(kontainer.lokasiAwal) ?: kontainer.lokasiAwal
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
        }

        LaunchedEffect(permintaanBukaEksternal?.id) {
            val lokasi = permintaanBukaEksternal?.lokasi ?: return@LaunchedEffect
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
            tabTerpilih = 0
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
        }

        LaunchedEffect(pesanEksternal?.first) {
            val pesan = pesanEksternal?.second ?: return@LaunchedEffect
            vmFilemanager.prosesIntent(IntentFilemanager.SetPesanInfo(pesan))
        }

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
                }
            }
        ) { bantalan ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bantalan)
            ) {
                if (tabTerpilih == 0) {
                    DaftarBerkasLayar(
                        viewModel = vmFilemanager,
                        viewModelOperasiMassal = vmOperasiMassal,
                        aksiPlatform = kontainer.aksiPlatform,
                        onBukaBerkas = { item ->
                            vmFilemanager.prosesIntent(
                                IntentFilemanager.SetPesanInfo("Membuka ${item.nama} (${item.jenis.name})")
                            )
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
                } else {
                    LayarTerbaru(
                        viewModel = vmTerbaru,
                        onBukaLokasi = { lokasi ->
                            scope.launch {
                                val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
                                tabTerpilih = 0
                                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
                            }
                        }
                    )
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