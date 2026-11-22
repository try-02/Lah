@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.pencarian

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.pencarian.PencariIsiBerkas
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.CariIsiBerkasUseCase
import org.fm.com.domain.usecase.CariNamaBerkasUseCase
import org.fm.com.domain.usecase.RiwayatPencarianUseCase

class RiwayatPencarianIntegrasiTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cariDiTabPencarianMencatatDanDapatMenghapusRiwayat() = runTest(testDispatcher) {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/Catatan.txt", "Catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "halo dunia pencarian".encodeToByteArray()
        )
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")
        val repoRiwayat = MemoriRepositoriRiwayatPencarian()
        val riwayatUseCase = RiwayatPencarianUseCase(repoRiwayat)

        val vm = ViewModelPencarian(
            cariNamaUseCase = CariNamaBerkasUseCase(storage, testDispatcher),
            cariIsiUseCase = CariIsiBerkasUseCase(PencariIsiBerkas(storage, testDispatcher)),
            riwayatPencarianUseCase = riwayatUseCase
        )

        vm.prosesIntent(IntentPencarian.SetFolderAkar(root))
        vm.prosesIntent(IntentPencarian.UbahKueri("Catatan"))
        vm.prosesIntent(IntentPencarian.MulaiPencarian)
        advanceUntilIdle()

        assertTrue(vm.state.value.daftarRiwayat.any { it.kueri == "Catatan" })

        vm.prosesIntent(IntentPencarian.UbahModePencarian(ModePencarian.ISI))
        vm.prosesIntent(IntentPencarian.UbahKueri("dunia"))
        vm.prosesIntent(IntentPencarian.MulaiPencarian)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.daftarRiwayat.size)

        vm.prosesIntent(IntentPencarian.BersihkanSemuaRiwayat)
        advanceUntilIdle()
        assertTrue(vm.state.value.daftarRiwayat.isEmpty())
    }
}