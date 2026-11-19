@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.editor

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian
import org.fm.com.data.simpan.DeteksiKonflik
import org.fm.com.data.simpan.SimpanTransaksional
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.domain.usecase.SimpanBerkasUseCase
import org.fm.com.domain.usecase.StatistikTeksUseCase

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
            ),
            computeDispatcher = dispatcher
        )
    }

    private fun buatVmLengkap(
        repo: ImplementasiRepositoriBerkas,
        dispatcher: CoroutineDispatcher
    ): ViewModelEditor = ViewModelEditor(
        repositoriBerkas = repo,
        bukaBerkasTeksUseCase = BukaBerkasTeksUseCase(
            repositoriBerkas = repo,
            ioDispatcher = dispatcher,
            computeDispatcher = dispatcher
        ),
        statistikTeksUseCase = StatistikTeksUseCase(computeDispatcher = dispatcher),
        simpanBerkasUseCase = SimpanBerkasUseCase(
            pemeriksaKonflik = DeteksiKonflik(repo, ioDispatcher = dispatcher),
            pelaksanaSimpan = SimpanTransaksional(repo, ioDispatcher = dispatcher),
            computeDispatcher = dispatcher
        ),
        computeDispatcher = dispatcher
    )

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

    @Test
    fun cariDanGantiKonfirmasiMassalSertaModeHanyaBacaBekerja() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val repoRiwayat = MemoriRepositoriRiwayatPencarian()
        val riwayatUc = RiwayatPencarianUseCase(repoRiwayat)
        repo.simpan(
            DataBerkas("/cari.txt", "cari.txt", JenisLokasi.POSIX, adalahFolder = false),
            "satu apel\ndua apel\ntiga apel".encodeToByteArray()
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val vm = ViewModelEditor(
            repositoriBerkas = repo,
            bukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher),
            riwayatPencarianUseCase = riwayatUc,
            computeDispatcher = dispatcher
        )

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/cari.txt", JenisLokasi.POSIX, "cari.txt")))
        vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }

        vm.prosesIntent(IntentEditor.SetBukaDialogCariGanti(true))
        vm.prosesIntent(IntentEditor.UbahKueriCari("apel"))
        vm.state.first { it.cariGanti.jumlahKecocokan == 3 }
        assertEquals(0, vm.state.value.cariGanti.indeksKecocokanAktif)
        assertEquals(5, vm.state.value.tabAktif!!.posisiKursor.awalRentang)
        assertEquals(9, vm.state.value.tabAktif!!.posisiKursor.akhirRentang)

        vm.prosesIntent(IntentEditor.CariBerikutnya)
        assertEquals(1, vm.state.value.cariGanti.indeksKecocokanAktif)
        assertEquals(2, vm.state.value.tabAktif!!.posisiKursor.baris)

        val riwayat = vm.state.first { it.riwayatPencarian.isNotEmpty() }.riwayatPencarian
        assertEquals(listOf("apel"), riwayat.map { it.kueri })

        vm.prosesIntent(IntentEditor.UbahTeksPengganti("jeruk"))
        vm.prosesIntent(IntentEditor.GantiSatu)
        assertEquals("satu apel\ndua jeruk\ntiga apel", vm.state.value.tabAktif!!.isiSaatIni)
        assertEquals(2, vm.state.value.cariGanti.jumlahKecocokan)

        vm.prosesIntent(IntentEditor.MintaGantiSemua)
        assertEquals(2, vm.state.value.cariGanti.konfirmasiGantiSemuaJumlah)
        vm.prosesIntent(IntentEditor.KonfirmasiGantiSemua)
        assertNull(vm.state.value.cariGanti.konfirmasiGantiSemuaJumlah)
        assertEquals("satu jeruk\ndua jeruk\ntiga jeruk", vm.state.value.tabAktif!!.isiSaatIni)
        assertEquals(0, vm.state.value.cariGanti.jumlahKecocokan)

        vm.prosesIntent(IntentEditor.Undo(vm.state.value.tabAktif!!.idTab))
        assertEquals("satu apel\ndua jeruk\ntiga apel", vm.state.value.tabAktif!!.isiSaatIni)

        vm.prosesIntent(IntentEditor.ToggleModeHanyaBaca(vm.state.value.tabAktif!!.idTab))
        vm.prosesIntent(IntentEditor.UbahKueriCari("jeruk"))
        vm.state.first { it.cariGanti.jumlahKecocokan == 1 }
        vm.prosesIntent(IntentEditor.UbahTeksPengganti("mangga"))
        vm.prosesIntent(IntentEditor.GantiSatu)
        assertEquals("satu apel\ndua jeruk\ntiga apel", vm.state.value.tabAktif!!.isiSaatIni)

        vm.prosesIntent(IntentEditor.ToggleRegexCari)
        vm.prosesIntent(IntentEditor.UbahKueriCari("[rusak"))
        assertNotNull(vm.state.value.cariGanti.galatPola)

        vm.prosesIntent(IntentEditor.BersihkanRiwayatPencarian)
        assertEquals(emptyList(), vm.state.value.riwayatPencarian)
    }

    @Test
    fun statistikTeksDihitungDiLatarBelakangDanDilewatiUntukBiner() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/teks.txt", "teks.txt", JenisLokasi.POSIX, adalahFolder = false),
            "baris satu\n\nbaris dua tiga".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/bin.dat", "bin.dat", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(0x00, 0x01, 0x02)
        )
        val vm = buatVmLengkap(repo, UnconfinedTestDispatcher(testScheduler))

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/teks.txt", JenisLokasi.POSIX, "teks.txt")))
        val stTeks = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }
        vm.prosesIntent(IntentEditor.HitungStatistikTab(stTeks.tabAktif!!.idTab))

        val stStat = vm.state.first { it.dialogStatistik.terbuka && !it.dialogStatistik.sedangMenghitung }
        val sukses = assertIs<HasilStatistikTeks.Sukses>(stStat.dialogStatistik.hasil)
        assertEquals(3, sukses.statistik.jumlahBarisTotal)
        assertEquals(1, sukses.statistik.jumlahBarisKosong)
        assertEquals(5, sukses.statistik.jumlahKata)
        assertEquals(14, sukses.statistik.panjangBarisTerpanjang)

        vm.prosesIntent(IntentEditor.TutupDialogStatistik)
        assertFalse(vm.state.value.dialogStatistik.terbuka)

        vm.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/bin.dat", JenisLokasi.POSIX, "bin.dat")))
        val stBin = vm.state.first { it.tabAktif?.namaBerkas == "bin.dat" && it.tabAktif?.statusMuat != StatusMuatTab.MEMUAT }
        vm.prosesIntent(IntentEditor.HitungStatistikTab(stBin.tabAktif!!.idTab))
        assertIs<HasilStatistikTeks.DilewatiBiner>(vm.state.value.dialogStatistik.hasil)
    }

    @Test
    fun simpanDeteksiKonflikEksternalDanDialogTutupTabSimpanBuangBatal() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/kerja", "kerja", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/kerja/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "awal".encodeToByteArray()
        )
        val vm = buatVmLengkap(repo, UnconfinedTestDispatcher(testScheduler))
        val lok = LokasiBerkas("/kerja/catatan.txt", JenisLokasi.POSIX, "catatan.txt")

        vm.prosesIntent(IntentEditor.BukaLokasi(lok))
        val idTab = vm.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }.tabAktif!!.idTab
        vm.prosesIntent(IntentEditor.UbahTeks(idTab, "edit lokal"))

        // Simulasikan perubahan eksternal
        repo.simpan(
            DataBerkas("/kerja/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "edit luar".encodeToByteArray()
        )

        // Tutup tab memicu DialogTutupTab -> Batal tidak menutup tab
        vm.prosesIntent(IntentEditor.MintaTutupTab(idTab))
        assertNotNull(vm.state.value.tabMenungguKonfirmasiTutup)
        vm.prosesIntent(IntentEditor.BatalTutupTab)
        assertEquals(1, vm.state.value.jumlahTab)
        assertEquals("edit lokal", vm.state.value.tabAktif?.isiSaatIni)

        // Simpan dan Tutup -> kena konflik eksternal -> editor tetap terbuka
        vm.prosesIntent(IntentEditor.MintaTutupTab(idTab))
        vm.prosesIntent(IntentEditor.KonfirmasiSimpanDanTutupTab)
        val konflik = assertNotNull(vm.state.value.konfirmasiKonflikSimpan)
        assertEquals(true, konflik.tutupSetelahSimpan)
        assertEquals(1, vm.state.value.jumlahTab)
        assertEquals("edit luar", repo.isiDari("/kerja/catatan.txt")?.decodeToString())

        // Konfirmasi timpa -> berhasil disimpan dan tab langsung ditutup
        vm.prosesIntent(IntentEditor.KonfirmasiTimpaKonflikSimpan)
        val stAkhir = vm.state.first { !it.sedangMenyimpan && it.jumlahTab == 0 }
        assertNull(stAkhir.konfirmasiKonflikSimpan)
        assertEquals("edit lokal", repo.isiDari("/kerja/catatan.txt")?.decodeToString())
    }

    @Test
    fun simpanGagalMempertahankanEditorTerbukaDanTabKosongSimpanSebagai() = runTest {
        val repoGagal = ImplementasiRepositoriBerkas(gagalTulisSetelahPotongan = 0)
        repoGagal.simpan(
            DataBerkas("/gagal.txt", "gagal.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi aman".encodeToByteArray()
        )
        val vmGagal = buatVmLengkap(repoGagal, UnconfinedTestDispatcher(testScheduler))
        vmGagal.prosesIntent(IntentEditor.BukaLokasi(LokasiBerkas("/gagal.txt", JenisLokasi.POSIX, "gagal.txt")))
        val idGagal = vmGagal.state.first { it.tabAktif?.statusMuat == StatusMuatTab.SIAP }.tabAktif!!.idTab

        vmGagal.prosesIntent(IntentEditor.UbahTeks(idGagal, "ubah belum tersimpan"))
        vmGagal.prosesIntent(IntentEditor.MintaTutupTab(idGagal))
        vmGagal.prosesIntent(IntentEditor.KonfirmasiSimpanDanTutupTab)

        assertEquals(1, vmGagal.state.value.jumlahTab)
        assertTrue(vmGagal.state.value.tabAktif!!.adaPerubahanBelumTersimpan)
        assertEquals("ubah belum tersimpan", vmGagal.state.value.tabAktif!!.isiSaatIni)
        assertEquals("isi aman", repoGagal.isiDari("/gagal.txt")?.decodeToString())

        // Tab kosong -> Simpan Sebagai ke folder aktif
        val repoBaru = ImplementasiRepositoriBerkas()
        repoBaru.simpan(DataBerkas("/dok", "dok", JenisLokasi.POSIX, adalahFolder = true))
        val vmBaru = buatVmLengkap(repoBaru, UnconfinedTestDispatcher(testScheduler))
        vmBaru.prosesIntent(IntentEditor.SetLokasiFolderSimpan(LokasiBerkas("/dok", JenisLokasi.POSIX, "dok")))
        vmBaru.prosesIntent(IntentEditor.BuatTabKosong("catatan_baru.txt"))
        val idKosong = vmBaru.state.value.tabAktif!!.idTab
        vmBaru.prosesIntent(IntentEditor.UbahTeks(idKosong, "konten baru"))
        vmBaru.prosesIntent(IntentEditor.SimpanTab(idKosong))

        assertNotNull(vmBaru.state.value.dialogSimpanSebagai)
        vmBaru.prosesIntent(IntentEditor.EksekusiSimpanSebagai("catatan_baru.txt"))
        val tabTersimpan = vmBaru.state.first { !it.sedangMenyimpan && it.tabAktif?.adaPerubahanBelumTersimpan == false }.tabAktif!!
        assertEquals("/dok/catatan_baru.txt", tabTersimpan.lokasi?.pengenal)
        assertEquals("konten baru", repoBaru.isiDari("/dok/catatan_baru.txt")?.decodeToString())
    }
}