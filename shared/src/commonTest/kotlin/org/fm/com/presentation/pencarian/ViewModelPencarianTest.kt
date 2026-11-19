@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.pencarian

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.pencarian.PencariIsiBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.CariIsiBerkasUseCase
import org.fm.com.domain.usecase.CariNamaBerkasUseCase

class ViewModelPencarianTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun cariNamaDanCariIsiMemperbaruiStateDenganBenar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/proyek", "proyek", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/proyek/App.kt", "App.kt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 30L),
            "fun utama() {\n println(\"Halo\")\n}".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/proyek/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 15L),
            "tanpa fungsi".encodeToByteArray()
        )

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vm = ViewModelPencarian(
            cariNamaUseCase = CariNamaBerkasUseCase(repo, dispatcher),
            cariIsiUseCase = CariIsiBerkasUseCase(PencariIsiBerkas(repo, dispatcher))
        )

        vm.prosesIntent(IntentPencarian.SetFolderAkar(LokasiBerkas("/proyek", JenisLokasi.POSIX, "proyek")))
        vm.prosesIntent(IntentPencarian.UbahKueri("App"))
        vm.prosesIntent(IntentPencarian.MulaiPencarian)

        val stNama = vm.state.first { it.pernahMencari && !it.sedangMencari }
        assertNull(stNama.pesanGalat)
        assertEquals(1, stNama.hasilNama.size)
        assertEquals("App.kt", stNama.hasilNama.first().nama)

        // Ganti ke Cari Isi
        vm.prosesIntent(IntentPencarian.UbahModePencarian(ModePencarian.ISI))
        vm.prosesIntent(IntentPencarian.UbahKueri("println"))
        vm.prosesIntent(IntentPencarian.MulaiPencarian)

        val stIsi = vm.state.first { !it.sedangMencari && it.hasilIsi.isNotEmpty() }
        assertEquals(1, stIsi.hasilIsi.size)
        assertEquals("App.kt", stIsi.hasilIsi.first().nama)
        assertEquals(2, stIsi.hasilIsi.first().daftarBarisCocok.first().nomorBaris)

        // Validasi regex invalid di Cari Isi
        vm.prosesIntent(IntentPencarian.ToggleRegexIsi)
        vm.prosesIntent(IntentPencarian.UbahKueri("[invalid"))
        vm.prosesIntent(IntentPencarian.MulaiPencarian)
        assertFalse(vm.state.value.sedangMencari)
        assertNotNull(vm.state.value.pesanGalat)

        // Batalkan pencarian dan reset filter aman
        vm.prosesIntent(IntentPencarian.BatalkanPencarian)
        vm.prosesIntent(IntentPencarian.ResetFilter)
        assertTrue(vm.state.value.rekursif)
    }
}