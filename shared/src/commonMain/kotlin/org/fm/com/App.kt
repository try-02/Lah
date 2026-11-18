package org.fm.com

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.presentation.filemanager.DaftarBerkasLayar
import org.fm.com.presentation.filemanager.IntentFilemanager
import org.fm.com.presentation.filemanager.ViewModelFilemanager
import org.fm.com.presentation.filemanager.ViewModelOperasiMassal

@Composable
fun App(
    repositori: RepositoriBerkas? = null,
    repositoriOperasi: RepositoriOperasiBerkas? = null,
    lokasiAwal: LokasiBerkas? = null
) {
    MaterialTheme {
        val storageDefault = remember { SumberMemoriPenyimpanan() }
        val repoBerkas = repositori ?: storageDefault.berkas
        val repoOperasi = repositoriOperasi ?: (repositori as? RepositoriOperasiBerkas) ?: storageDefault.operasi

        val vmFilemanager = remember(repoBerkas, repoOperasi) {
            ViewModelFilemanager(repoBerkas, repoOperasi)
        }
        val vmOperasiMassal = remember(repoBerkas, repoOperasi) {
            ViewModelOperasiMassal(repoBerkas, repoOperasi)
        }

        LaunchedEffect(Unit) {
            if (repositori == null) {
                // Seed data awal agar aplikasi langsung memiliki folder dan berkas untuk dijelajahi
                seedDataAwal(storageDefault)
                val root = LokasiBerkas("/", JenisLokasi.POSIX, "Penyimpanan")
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(root))
            } else if (lokasiAwal != null) {
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiAwal))
            }
        }

        DaftarBerkasLayar(
            viewModel = vmFilemanager,
            viewModelOperasiMassal = vmOperasiMassal,
            onBukaBerkas = { item ->
                vmFilemanager.prosesIntent(
                    IntentFilemanager.SetPesanInfo("Membuka ${item.nama} (${item.jenis.name})")
                )
            }
        )
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