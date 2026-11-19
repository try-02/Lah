@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.terbaru

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.RiwayatLokasiUseCase

class ViewModelTerbaruTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun memuatMenambahReaktifDanMenghapusItemRiwayat() = runTest {
        val repo = MemoriRepositoriRiwayatLokasi()
        val useCase = RiwayatLokasiUseCase(repo)
        val lokasi1 = LokasiBerkas("/root/Dokumen", JenisLokasi.POSIX, "Dokumen")
        val lokasi2 = LokasiBerkas("/root/Unduhan", JenisLokasi.POSIX, "Unduhan")
        val lokasi3 = LokasiBerkas("/root/Proyek", JenisLokasi.POSIX, "Proyek")

        useCase.catatKunjungan(lokasi1, adalahFolder = true, waktuEpochMs = 1000L)
        useCase.catatKunjungan(lokasi2, adalahFolder = true, waktuEpochMs = 2000L)

        val vm = ViewModelTerbaru(useCase)
        val state = vm.state.first { !it.sedangMemuat && it.jumlahItem == 2 }
        assertEquals(2, state.jumlahItem)
        assertEquals("Unduhan", state.daftarItem[0].nama)
        assertEquals("Dokumen", state.daftarItem[1].nama)

        useCase.catatKunjungan(lokasi3, adalahFolder = true, waktuEpochMs = 3000L)
        val stateReaktif = vm.state.first { it.jumlahItem == 3 }
        assertEquals("Proyek", stateReaktif.daftarItem[0].nama)

        vm.prosesIntent(IntentTerbaru.HapusItem(lokasi2))
        val stateSetelahHapus = vm.state.first { it.jumlahItem == 2 }
        assertEquals(listOf("Proyek", "Dokumen"), stateSetelahHapus.daftarItem.map { it.nama })

        vm.prosesIntent(IntentTerbaru.KonfirmasiHapusSemua)
        val stateKosong = vm.state.first { it.jumlahItem == 0 }
        assertEquals(0, stateKosong.jumlahItem)
        assertFalse(stateKosong.dialogHapusSemuaTerbuka)
    }
}