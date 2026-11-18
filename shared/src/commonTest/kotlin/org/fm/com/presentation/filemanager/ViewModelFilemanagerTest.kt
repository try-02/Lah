package org.fm.com.presentation.filemanager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.KriteriaUrutan

class ViewModelFilemanagerTest {

    @Test
    fun bukaLokasiMemuatDaftarDanKemampuan() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/b.txt", "b.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 2048L))
        repo.simpan(DataBerkas("/root/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 1024L))
        repo.simpan(DataBerkas("/root/.tersembunyi", ".tersembunyi", JenisLokasi.POSIX, adalahFolder = false, tersembunyi = true))

        val vm = ViewModelFilemanager(repo)
        val lokasi = LokasiBerkas("/root", JenisLokasi.POSIX, "root")

        vm.prosesIntent(IntentFilemanager.BukaLokasi(lokasi))

        val state = vm.state.first { !it.sedangMemuat && it.daftarItem.isNotEmpty() }
        assertEquals(2, state.jumlahItem) // .tersembunyi diabaikan secara default
        assertEquals(listOf("a.txt", "b.txt"), state.daftarItem.map { it.nama })
        assertEquals("1 KiB", state.daftarItem.first().ukuranFormat)
        assertNotNull(state.kemampuanLokasi)
    }

    @Test
    fun togglePengurutanMengubahDaftarTanpaIo() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false))
        repo.simpan(DataBerkas("/root/z.txt", "z.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repo)
        vm.prosesIntent(IntentFilemanager.BukaLokasi(LokasiBerkas("/root", JenisLokasi.POSIX)))

        vm.state.first { !it.sedangMemuat && it.daftarItem.size == 2 }

        vm.prosesIntent(IntentFilemanager.UbahPengurutan(KriteriaUrutan.NAMA, ArahUrutan.TURUN))
        val stateTurun = vm.state.value
        assertEquals(listOf("z.txt", "a.txt"), stateTurun.daftarItem.map { it.nama })
        assertEquals(ArahUrutan.TURUN, stateTurun.arahUrutan)
    }

    @Test
    fun multiSelectBekerjaBenar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/1.txt", "1.txt", JenisLokasi.POSIX, adalahFolder = false))
        repo.simpan(DataBerkas("/root/2.txt", "2.txt", JenisLokasi.POSIX, adalahFolder = false))

        val vm = ViewModelFilemanager(repo)
        val lokasiRoot = LokasiBerkas("/root", JenisLokasi.POSIX)
        vm.prosesIntent(IntentFilemanager.BukaLokasi(lokasiRoot))

        val state = vm.state.first { it.daftarItem.size == 2 }
        val item1 = state.daftarItem[0].lokasi
        val item2 = state.daftarItem[1].lokasi

        assertFalse(vm.state.value.sedangMultiSelect)

        vm.prosesIntent(IntentFilemanager.TogglePilihItem(item1))
        assertTrue(vm.state.value.sedangMultiSelect)
        assertEquals(setOf(item1), vm.state.value.itemTerpilih)

        vm.prosesIntent(IntentFilemanager.PilihSemua)
        assertEquals(setOf(item1, item2), vm.state.value.itemTerpilih)

        vm.prosesIntent(IntentFilemanager.BersihkanPilihan)
        assertFalse(vm.state.value.sedangMultiSelect)
        assertTrue(vm.state.value.itemTerpilih.isEmpty())
    }
}