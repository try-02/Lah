package org.fm.com.presentation.filemanager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class OperasiBerkasViewModelTest {

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
    fun renameDanHapusMemperbaruiState() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/draft.txt", "draft.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = op)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "root")
        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))

        val draftLokasi = vm.state.first { it.jumlahItem == 1 }.daftarItem.first().lokasi

        // 1. Rename
        vm.prosesIntent(IntentFilemanager.EksekusiRename(draftLokasi, "final.txt"))
        val stateRename = vm.state.first { !it.sedangMemuat && it.daftarItem.first().nama == "final.txt" }
        assertEquals("final.txt", stateRename.daftarItem.first().nama)

        // 2. Hapus
        val finalLokasi = stateRename.daftarItem.first().lokasi
        vm.prosesIntent(IntentFilemanager.TogglePilihItem(finalLokasi))
        vm.prosesIntent(IntentFilemanager.BukaDialogHapus(listOf(finalLokasi)))
        vm.prosesIntent(IntentFilemanager.EksekusiHapusTerpilih)

        val stateKosong = vm.state.first { !it.sedangMemuat && it.jumlahItem == 0 }
        assertEquals(0, stateKosong.jumlahItem)
        assertFalse(stateKosong.sedangMultiSelect)
    }
}