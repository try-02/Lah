package org.fm.com.presentation.filemanager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.SegmenLokasi

class NavigasiFilemanagerTest {

    @Test
    fun navigasiMasukDanKembaliMemperbaruiBreadcrumb() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/sub", "sub", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/sub/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repo)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "Root")
        val sub = LokasiBerkas("/root/sub", JenisLokasi.POSIX, "Sub")

        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))
        vm.state.first { it.lokasiSaatIni == root }

        vm.prosesIntent(IntentFilemanager.BukaLokasi(sub))
        val stateSub = vm.state.first { it.lokasiSaatIni == sub }
        assertEquals(true, stateSub.dapatNavigasiKembali)

        vm.prosesIntent(IntentFilemanager.NavigasiKembali)
        val stateKembali = vm.state.first { it.lokasiSaatIni == root }
        assertEquals(false, stateKembali.dapatNavigasiKembali)
    }

    @Test
    fun pencarianCepatMenyaringDaftarSecaraLokal() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/kucing.txt", "kucing.txt", JenisLokasi.POSIX, adalahFolder = false))
        repo.simpan(DataBerkas("/root/anjing.txt", "anjing.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repo)
        vm.prosesIntent(IntentFilemanager.BukaLokasi(LokasiBerkas("/root", JenisLokasi.POSIX)))

        vm.state.first { it.daftarItem.size == 2 }

        vm.prosesIntent(IntentFilemanager.UbahKataKunciSaring("kuc"))
        val stateSaring = vm.state.value
        assertEquals(1, stateSaring.daftarItem.size)
        assertEquals("kucing.txt", stateSaring.daftarItem.first().nama)
    }
}