@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.filemanager

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class OperasiBerkasViewModelTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun buatFolderMenyegarkanDaftarOtomatis() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))

        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = op)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "root")
        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))
        vm.state.first { !it.sedangMemuat }
        assertEquals(0, vm.state.value.jumlahItem)

        vm.prosesIntent(IntentFilemanager.BuatFolderBaru("Dokumen"))
        vm.state.first { !it.sedangMemuat && it.jumlahItem == 1 }
        assertEquals("Dokumen", vm.state.value.daftarItem.first().nama)
        assertEquals(true, vm.state.value.daftarItem.first().adalahFolder)
    }

    @Test
    fun konflikBuatBerkasMemicuKonfirmasiTimpaTanpaMenghapusDaftar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/root/test.txt", "test.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi lama".encodeToByteArray()
        )

        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = op)
        vm.prosesIntent(IntentFilemanager.BukaLokasi(LokasiBerkas("/root", JenisLokasi.POSIX, "root")))
        vm.state.first { !it.sedangMemuat && it.jumlahItem == 1 }

        vm.prosesIntent(IntentFilemanager.BuatBerkasBaru("test.txt"))
        val stateKonflik = vm.state.first { !it.sedangMemuat && it.konfirmasiTimpa != null }
        assertNull(stateKonflik.pesanGalat)
        assertEquals(1, stateKonflik.jumlahItem)
        val konfirmasi = assertNotNull(stateKonflik.konfirmasiTimpa)
        assertEquals(JenisAksiTimpa.BUAT_BERKAS, konfirmasi.jenis)

        vm.prosesIntent(IntentFilemanager.KonfirmasiTimpaAksi(konfirmasi))
        val stateSelesai = vm.state.first { !it.sedangMemuat && it.konfirmasiTimpa == null }
        assertEquals(1, stateSelesai.jumlahItem)
        assertEquals("", repo.isiDari("/root/test.txt")?.decodeToString())
    }

    @Test
    fun konflikRenameMemicuKonfirmasiTimpaTanpaMenghapusDaftar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/root/asal.txt", "asal.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi baru".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/root/target.txt", "target.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi lama".encodeToByteArray()
        )

        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = op)
        vm.prosesIntent(IntentFilemanager.BukaLokasi(LokasiBerkas("/root", JenisLokasi.POSIX, "root")))
        val stateAwal = vm.state.first { !it.sedangMemuat && it.jumlahItem == 2 }
        val asal = stateAwal.daftarItem.first { it.nama == "asal.txt" }.lokasi

        vm.prosesIntent(IntentFilemanager.EksekusiRename(asal, "target.txt"))
        val stateKonflik = vm.state.first { !it.sedangMemuat && it.konfirmasiTimpa != null }
        assertNull(stateKonflik.pesanGalat)
        assertEquals(2, stateKonflik.jumlahItem)
        val konfirmasi = assertNotNull(stateKonflik.konfirmasiTimpa)
        assertEquals(JenisAksiTimpa.GANTI_NAMA, konfirmasi.jenis)

        vm.prosesIntent(IntentFilemanager.KonfirmasiTimpaAksi(konfirmasi))
        val stateSelesai = vm.state.first { !it.sedangMemuat && it.konfirmasiTimpa == null && it.jumlahItem == 1 }
        assertEquals(listOf("target.txt"), stateSelesai.daftarItem.map { it.nama })
        assertEquals("isi baru", repo.isiDari("/root/target.txt")?.decodeToString())
    }

    @Test
    fun renameDanHapusMemperbaruiState() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/draft.txt", "draft.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = op)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "root")
        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))
        val draftLokasi = vm.state.first { it.jumlahItem == 1 }.daftarItem.first().lokasi

        vm.prosesIntent(IntentFilemanager.EksekusiRename(draftLokasi, "final.txt"))
        val stateRename = vm.state.first { !it.sedangMemuat && it.daftarItem.first().nama == "final.txt" }
        assertEquals("final.txt", stateRename.daftarItem.first().nama)

        val finalLokasi = stateRename.daftarItem.first().lokasi
        vm.prosesIntent(IntentFilemanager.TogglePilihItem(finalLokasi))
        vm.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(finalLokasi)))
        vm.prosesIntent(IntentFilemanager.EksekusiHapusTerpilih)

        val stateKosong = vm.state.first { !it.sedangMemuat && it.jumlahItem == 0 }
        assertEquals(0, stateKosong.jumlahItem)
        assertFalse(stateKosong.sedangMultiSelect)
    }
}