package org.fm.com.presentation.terbaru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.RiwayatLokasiUseCase

class ViewModelTerbaruTest {

    @Test
    fun memuatDanMenghapusItemRiwayat() = runTest {
        val repo = MemoriRepositoriRiwayatLokasi()
        val useCase = RiwayatLokasiUseCase(repo)

        val lokasi1 = LokasiBerkas("/root/Dokumen", JenisLokasi.POSIX, "Dokumen")
        val lokasi2 = LokasiBerkas("/root/Unduhan", JenisLokasi.POSIX, "Unduhan")

        useCase.catatKunjungan(lokasi1, adalahFolder = true, waktuEpochMs = 1000L)
        useCase.catatKunjungan(lokasi2, adalahFolder = true, waktuEpochMs = 2000L)

        val vm = ViewModelTerbaru(useCase)
        val state = vm.state.first { !it.sedangMemuat && it.jumlahItem == 2 }

        assertEquals(2, state.jumlahItem)
        // Item dengan waktu terbaru berada paling atas
        assertEquals("Unduhan", state.daftarItem[0].nama)
        assertEquals("Dokumen", state.daftarItem[1].nama)

        // Hapus satu item
        vm.prosesIntent(IntentTerbaru.HapusItem(lokasi2))
        val stateSetelahHapus = vm.state.first { it.jumlahItem == 1 }
        assertEquals("Dokumen", stateSetelahHapus.daftarItem.first().nama)

        // Hapus semua
        vm.prosesIntent(IntentTerbaru.KonfirmasiHapusSemua)
        val stateKosong = vm.state.first { it.jumlahItem == 0 }
        assertEquals(0, stateKosong.jumlahItem)
        assertFalse(stateKosong.dialogHapusSemuaTerbuka)
    }
}