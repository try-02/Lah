package org.fm.com

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.RepositoriRiwayatLokasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.usecase.RiwayatLokasiUseCase
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal
import org.fm.com.presentation.terbaru.LayarTerbaru
import org.fm.com.presentation.terbaru.ViewModelTerbaru

@Composable
fun App(
    repositori: RepositoriBerkas? = null,
    repositoriOperasi: RepositoriOperasiBerkas? = null,
    repositoriRiwayat: RepositoriRiwayatLokasi? = null,
    lokasiAwal: LokasiBerkas? = null
) {
    MaterialTheme {
        val storageDefault = remember { SumberMemoriPenyimpanan() }
        val repoBerkas = repositori ?: storageDefault.berkas
        val repoOperasi = repositoriOperasi ?: (repositori as? RepositoriOperasiBerkas) ?: storageDefault.operasi
        val repoRiwayat = remember(repositoriRiwayat) { repositoriRiwayat ?: MemoriRepositoriRiwayatLokasi() }

        val riwayatUseCase = remember(repoRiwayat) { RiwayatLokasiUseCase(repoRiwayat) }

        val vmFilemanager = remember(repoBerkas, repoOperasi, riwayatUseCase) {
            ViewModelFilemanager(
                repositoriBerkas = repoBerkas,
                repositoriOperasi = repoOperasi,
                riwayatLokasiUseCase = riwayatUseCase
            )
        }
        val vmOperasiMassal = remember(repoBerkas, repoOperasi) {
            ViewModelOperasiMassal(repoBerkas, repoOperasi)
        }
        val vmTerbaru = remember(riwayatUseCase) {
            ViewModelTerbaru(riwayatUseCase)
        }

        var tabTerpilih by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            if (repositori == null) {
                seedDataAwal(storageDefault)
                val root = LokasiBerkas("/", JenisLokasi.POSIX, "Penyimpanan")
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(root))
            } else if (lokasiAwal != null) {
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiAwal))
            }
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
                        onBukaBerkas = { item ->
                            vmFilemanager.prosesIntent(
                                IntentFilemanager.SetPesanInfo("Membuka ${item.nama} (${item.jenis.name})")
                            )
                        }
                    )
                } else {
                    LayarTerbaru(
                        viewModel = vmTerbaru,
                        onBukaLokasi = { lokasi ->
                            tabTerpilih = 0
                            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasi))
                        }
                    )
                }
            }
        }
    }
}

private suspend fun seedDataAwal(storage: SumberMemoriPenyimpanan) {
    storage.berkas.simpan(DataBerkas("/", "Penyimpanan", JenisLokasi.POSIX, adalahFolder = true))
    storage.berkas.simpan(DataBerkas("/Dokumen", "Dokumen", JenisLokasi.POSIX, adalahFolder = true))
    storage.berkas.simpan(
        DataBerkas("/Dokumen/Catatan.txt", "Catatan.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 45L),
        "Rencana pengembangan File Manager & Code Editor".encodeToByteArray()
    )
    storage.berkas.simpan(
        DataBerkas("/Dokumen/Kode.kt", "Kode.kt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 52L),
        "fun main() {\n    println(\"Halo Kotlin Multiplatform!\")\n}".encodeToByteArray()
    )
    storage.berkas.simpan(DataBerkas("/Foto", "Foto", JenisLokasi.POSIX, adalahFolder = true))
    storage.berkas.simpan(
        DataBerkas("/Foto/Liburan.png", "Liburan.png", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 204800L)
    )
    storage.berkas.simpan(DataBerkas("/Unduhan", "Unduhan", JenisLokasi.POSIX, adalahFolder = true))
    storage.berkas.simpan(
        DataBerkas("/Unduhan/Paket.zip", "Paket.zip", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 1048576L)
    )
    storage.berkas.simpan(
        DataBerkas("/README.md", "README.md", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 120L),
        "# File Manager & Code Editor\nAplikasi KMP Clean Architecture lintas platform.".encodeToByteArray()
    )
}