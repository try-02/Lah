@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.editor

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
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase

class ViewModelEditorTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    private fun buatVm(repo: ImplementasiRepositoriBerkas): ViewModelEditor {
        val dispatcher = UnconfinedTestDispatcher()
        return ViewModelEditor(
            repositoriBerkas = repo,
            bukaBerkasTeksUseCase = BukaBerkasTeksUseCase(
                repositoriBerkas = repo,
                ioDispatcher = dispatcher,
                computeDispatcher = dispatcher
            )
        )
    }

    @Test
    fun multiTabFokusTanpaDuplikatDanIndikatorBelumTersimpan() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/a.kt", "a.kt", JenisLokasi.POSIX, adalahFolder = false), "fun a() {}".encodeToByteArray())
        repo.simpan(DataBerkas("/b.kt", "b.kt", JenisLokasi.POSIX, adalahFolder = false), "fun b() {}".encodeToByteArray())
        val vm = buatVm(repo)

        val lokA = LokasiBerkas("/a.kt", JenisLokasi.POSIX, "a.kt")
        val lokB = LokasiBerkas("/b.kt", JenisLokasi.POSIX, "b.kt")

        vm.prosesIntent(IntentEditor.BukaLokasi(lokA))
        vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP && it.tabAktif?.namaBerkas == "a.kt" }

        vm.prosesIntent(IntentEditor.BukaLokasi(lokB))
        val stDua = vm.state.first { it.jumlahTab == 2 && it.tabAktif?.statusMuat == StatusMuatTab.SIAP && it.tabAktif?.namaBerkas == "b.kt" }
        assertEquals(2, stDua.jumlahTab)
        assertFalse(stDua.adaTabBelumTersimpan)

        // Buka ulang berkas pertama memindahkan fokus tanpa membuat tab baru
        vm.prosesIntent(IntentEditor.BukaLokasi(lokA))
        assertEquals(2, vm.state.value.jumlahTab)
        val tabA = assertNotNull(vm.state.value.tabAktif)
        assertEquals("a.kt", tabA.namaBerkas)

        // Edit teks -> indikator berubah -> kembalikan ke teks asli -> kembali false
        vm.prosesIntent(IntentEditor.UbahTeks(tabA.idTab, "fun a() { println() }"))
        assertTrue(vm.state.value.tabAktif!!.adaPerubahanBelumTersimpan)
        assertTrue(vm.state.value.adaTabBelumTersimpan)

        vm.prosesIntent(IntentEditor.UbahTeks(tabA.idTab, "fun a() {}"))
        assertFalse(vm.state.value.tabAktif!!.adaPerubahanBelumTersimpan)

        // Edit lagi lalu tandai tersimpan
        vm.prosesIntent(IntentEditor.UbahTeks(tabA.idTab, "fun aBaru() {}"))
        assertTrue(vm.state.value.tabAktif!!.adaPerubahanBelumTersimpan)
        vm.prosesIntent(IntentEditor.TandaiTersimpan(tabA.idTab))
        assertFalse(vm.state.value.tabAktif!!.adaPerubahanBelumTersimpan)
        assertEquals("fun aBaru() {}", vm.state.value.tabAktif!!.isiTersimpan)
    }

    @Test
    fun tutupTabBelumTersimpanMemicuKonfirmasiDanBatalTidakMenutup() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false), "awal".encodeToByteArray())
        val vm = buatVm(repo)

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/catatan.txt", JenisLokasi.POSIX, "catatan.txt")))
        val stSiap = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }
        val idTab = stSiap.tabAktif!!.idTab

        vm.prosesIntent(IntentEditor.UbahTeks(idTab, "diubah"))
        vm.prosesIntent(IntentEditor.MintaTutupTab(idTab))

        val konfirmasi = assertNotNull(vm.state.value.tabMenungguKonfirmasiTutup)
        assertEquals(idTab, konfirmasi.idTab)
        assertEquals(1, vm.state.value.jumlahTab)

        // Batal tidak menutup tab & mempertahankan perubahan
        vm.prosesIntent(IntentEditor.BatalTutupTab)
        assertNull(vm.state.value.tabMenungguKonfirmasiTutup)
        assertEquals(1, vm.state.value.jumlahTab)
        assertEquals("diubah", vm.state.value.tabAktif?.isiSaatIni)

        // Konfirmasi buang menutup tab
        vm.prosesIntent(IntentEditor.MintaTutupTab(idTab))
        vm.prosesIntent(IntentEditor.KonfirmasiBuangDanTutupTab)
        assertEquals(0, vm.state.value.jumlahTab)
        assertNull(vm.state.value.idTabAktif)
    }

    @Test
    fun modeHanyaBacaDanFileBinerMenolakPengubahanTeks() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/ro.txt", "ro.txt", JenisLokasi.POSIX, adalahFolder = false, dapatDitulis = false),
            "isi tetap".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/gambar.bin", "gambar.bin", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(0x00, 0x01, 0x02)
        )
        val vm = buatVm(repo)

        val entitasRo = EntitasBerkas(
            lokasi = LokasiBerkas("/ro.txt", JenisLokasi.POSIX, "ro.txt"),
            nama = "ro.txt",
            jenis = JenisBerkas.BERKAS_TEKS,
            dapatDitulis = false
        )
        vm.prosesIntent(IntentEditor.BukaBerkas(entitasRo))
        val stRo = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }
        val tabRo = stRo.tabAktif!!
        assertTrue(tabRo.hanyaBacaPermanen)
        assertTrue(tabRo.modeHanyaBaca)

        vm.prosesIntent(IntentEditor.UbahTeks(tabRo.idTab, "coba ubah"))
        assertEquals("isi tetap", vm.state.value.tabAktif?.isiSaatIni)
        vm.prosesIntent(IntentEditor.ToggleModeHanyaBaca(tabRo.idTab))
        assertTrue(vm.state.value.tabAktif!!.modeHanyaBaca)

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/gambar.bin", JenisLokasi.POSIX, "gambar.bin")))
        val stBin = vm.state.first { it.tabAktif?.namaBerkas == "gambar.bin" && it.tabAktif?.statusMuat != StatusMuatTab.MEMUAT }
        assertEquals(StatusMuatTab.BINER, stBin.tabAktif?.statusMuat)
        assertTrue(stBin.tabAktif!!.modeHanyaBaca)
    }

    @Test
    fun bukaDenganBarisTujuanDanWordWrapPerTabBekerja() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/kode.kt", "kode.kt", JenisLokasi.POSIX, adalahFolder = false),
            "baris 1\nbaris 2\nbaris 3".encodeToByteArray()
        )
        val vm = buatVm(repo)

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/kode.kt", JenisLokasi.POSIX, "kode.kt"), barisTujuan = 3))
        val st = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }
        val tab = st.tabAktif!!
        assertEquals(3, tab.jumlahBaris)
        assertEquals(3, tab.posisiKursor.baris)
        assertEquals(1, tab.posisiKursor.kolom)
        assertEquals(16, tab.posisiKursor.indeksKarakter)

        assertTrue(tab.wordWrapEfektif(st.wordWrapUmum))
        vm.prosesIntent(IntentEditor.ToggleWordWrapTab(tab.idTab))
        assertFalse(vm.state.value.tabAktif!!.wordWrapEfektif(vm.state.value.wordWrapUmum))
    }

    @Test
    fun undoRedoPerTabDanLompatBarisValidasiBatas() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/satu.kt", "satu.kt", JenisLokasi.POSIX, adalahFolder = false),
            "baris 1\nbaris 2\nbaris 3".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/dua.kt", "dua.kt", JenisLokasi.POSIX, adalahFolder = false),
            "alpha".encodeToByteArray()
        )
        val vm = buatVm(repo)

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/satu.kt", JenisLokasi.POSIX, "satu.kt")))
        val st1 = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP && it.tabAktif?.namaBerkas == "satu.kt" }
        val id1 = st1.tabAktif!!.idTab

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/dua.kt", JenisLokasi.POSIX, "dua.kt")))
        val st2 = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP && it.tabAktif?.namaBerkas == "dua.kt" }
        val id2 = st2.tabAktif!!.idTab

        // Edit tab 2
        vm.prosesIntent(IntentEditor.UbahTeks(id2, "alpha beta", indeksKursor = 10))
        assertTrue(vm.state.value.tabAktif!!.dapatUndo)
        assertFalse(vm.state.value.tabAktif!!.dapatRedo)

        // Berpindah ke tab 1 -> tidak punya riwayat undo dari tab 2
        vm.prosesIntent(IntentEditor.PilihTab(id1))
        assertFalse(vm.state.value.tabAktif!!.dapatUndo)

        // Lompat baris invalid ditolak dengan pesanInfo
        vm.prosesIntent(IntentEditor.LompatKeBaris(id1, 99))
        assertNotNull(vm.state.value.pesanInfo)
        assertEquals(1, vm.state.value.tabAktif!!.posisiKursor.baris)

        // Lompat baris valid -> kursor berpindah tepat
        vm.prosesIntent(IntentEditor.LompatKeBaris(id1, 2))
        assertEquals(2, vm.state.value.tabAktif!!.posisiKursor.baris)
        assertEquals(1, vm.state.value.tabAktif!!.posisiKursor.kolom)

        // Kembali ke tab 2 -> undo mengembalikan kondisi bersih & redo mengembalikan editan
        vm.prosesIntent(IntentEditor.PilihTab(id2))
        vm.prosesIntent(IntentEditor.Undo(id2))
        val tab2PascaUndo = vm.state.value.tabAktif!!
        assertEquals("alpha", tab2PascaUndo.isiSaatIni)
        assertFalse(tab2PascaUndo.adaPerubahanBelumTersimpan)
        assertTrue(tab2PascaUndo.dapatRedo)

        vm.prosesIntent(IntentEditor.Redo(id2))
        val tab2PascaRedo = vm.state.value.tabAktif!!
        assertEquals("alpha beta", tab2PascaRedo.isiSaatIni)
        assertTrue(tab2PascaRedo.adaPerubahanBelumTersimpan)
    }

    @Test
    fun indeksBarisLogisKomponenNomorBarisAkurat() {
        val indeks = bangunIndeksAwalBaris("satu\ndua\ntiga")
        assertEquals(1, cariNomorBarisLogis(indeks, 0))
        assertEquals(1, cariNomorBarisLogis(indeks, 3))
        assertEquals(2, cariNomorBarisLogis(indeks, 5))
        assertEquals(3, cariNomorBarisLogis(indeks, 9))
    }
}