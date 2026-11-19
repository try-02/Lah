@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.filemanager

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class NavigasiFilemanagerTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun navigasiMasukDanKembaliMemperbaruiBreadcrumb() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/sub", "sub", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/sub/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repo)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "Root")
        val sub = LokasiBerkas("/root/sub", JenisLokasi.POSIX, "Sub")

        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))
        val stateRoot = vm.state.first { it.lokasiSaatIni == root && !it.sedangMemuat }
        assertEquals(listOf("Root", "root"), stateRoot.segmenBreadcrumb.map { it.namaTampilan })
        assertFalse(stateRoot.dapatNavigasiKembali)

        vm.prosesIntent(IntentFilemanager.BukaLokasi(sub))
        val stateSub = vm.state.first { it.lokasiSaatIni == sub && !it.sedangMemuat }
        assertTrue(stateSub.dapatNavigasiKembali)
        assertEquals(listOf("Root", "root", "sub"), stateSub.segmenBreadcrumb.map { it.namaTampilan })

        vm.prosesIntent(IntentFilemanager.NavigasiKembali)
        val stateKembali = vm.state.first { it.lokasiSaatIni == root && !it.sedangMemuat }
        assertFalse(stateKembali.dapatNavigasiKembali)
        assertEquals(listOf("Root", "root"), stateKembali.segmenBreadcrumb.map { it.namaTampilan })
    }

    @Test
    fun lompatanKeLokasiBaruMeresetHierarkiSaf() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("content://treeA", "Tree A", JenisLokasi.SAF, adalahFolder = true))
        repo.simpan(DataBerkas("content://treeA/sub", "Sub A", JenisLokasi.SAF, adalahFolder = true))
        repo.simpan(DataBerkas("content://treeB", "Tree B", JenisLokasi.SAF, adalahFolder = true))

        val vm = ViewModelFilemanager(repo)
        val treeA = LokasiBerkas("content://treeA", JenisLokasi.SAF, "Tree A")
        val subA = LokasiBerkas("content://treeA/sub", JenisLokasi.SAF, "Sub A")
        val treeB = LokasiBerkas("content://treeB", JenisLokasi.SAF, "Tree B")

        vm.prosesIntent(IntentFilemanager.BukaLokasi(treeA))
        vm.state.first { it.lokasiSaatIni == treeA && !it.sedangMemuat }

        vm.prosesIntent(IntentFilemanager.BukaLokasi(subA))
        val stateSubA = vm.state.first { it.lokasiSaatIni == subA && !it.sedangMemuat }
        assertEquals(listOf("Tree A", "Sub A"), stateSubA.segmenBreadcrumb.map { it.namaTampilan })

        vm.prosesIntent(IntentFilemanager.BukaLokasi(treeB))
        val stateTreeB = vm.state.first { it.lokasiSaatIni == treeB && !it.sedangMemuat }
        assertEquals(listOf("Tree B"), stateTreeB.segmenBreadcrumb.map { it.namaTampilan })
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