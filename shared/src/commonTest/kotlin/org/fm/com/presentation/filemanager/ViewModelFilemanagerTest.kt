@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.filemanager

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.HasilMassal
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.KriteriaUrutan

class ViewModelFilemanagerTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

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
        assertEquals(2, state.jumlahItem)
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

    @Test
    fun alurModeTempelSalinKeFolderBerbedaBekerja() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/asal", "asal", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/tujuan", "tujuan", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/root/asal/data.txt", "data.txt", JenisLokasi.POSIX, adalahFolder = false),
            "konten penting".encodeToByteArray()
        )

        val vmMassal = ViewModelOperasiMassal(repo, op)
        val sumber = LokasiBerkas("/root/asal/data.txt", JenisLokasi.POSIX, "data.txt")
        val tujuan = LokasiBerkas("/root/tujuan", JenisLokasi.POSIX, "tujuan")

        vmMassal.inisiasiSalin(listOf(sumber))
        assertTrue(vmMassal.state.value.sedangMemilihTujuan)
        assertFalse(vmMassal.state.value.dialogPilihTujuanTerbuka)

        vmMassal.bukaKonfirmasiTempel()
        assertTrue(vmMassal.state.value.dialogPilihTujuanTerbuka)

        var hasilSelesai: HasilMassal? = null
        vmMassal.eksekusiKeTujuan(tujuan) { hasilSelesai = it }

        vmMassal.state.first { !it.sedangBerjalan && it.hasilTerakhir != null }
        val hasil = assertNotNull(hasilSelesai)
        assertFalse(hasil.adaGagal)
        assertEquals(1, hasil.berhasil.size)
        assertFalse(vmMassal.state.value.sedangMemilihTujuan)
        assertEquals("konten penting", repo.isiDari("/root/tujuan/data.txt")?.decodeToString())
    }

    @Test
    fun formatTanggalValidDanAmanUntukNilaiNullAtauEkstrem() {
        assertEquals("-", PemetaanDomainUi.formatTanggal(null))
        assertEquals("-", PemetaanDomainUi.formatTanggal(0L))
        assertEquals("-", PemetaanDomainUi.formatTanggal(Long.MAX_VALUE))
        val format = PemetaanDomainUi.formatTanggal(1_700_000_000_000L)
        assertTrue(
            Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$""").matches(format),
            "Format tanggal tidak sesuai: $format"
        )
    }

    @Test
    fun konflikMassalMenawarkanTimpaDanLewatiSecaraTerpisah() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val op = ImplementasiRepositoriOperasiBerkas(repo)
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/asal", "asal", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/tujuan", "tujuan", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/root/asal/sama.txt", "sama.txt", JenisLokasi.POSIX, adalahFolder = false),
            "baru".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/root/asal/baru.txt", "baru.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi baru".encodeToByteArray()
        )
        repo.simpan(
            DataBerkas("/root/tujuan/sama.txt", "sama.txt", JenisLokasi.POSIX, adalahFolder = false),
            "lama".encodeToByteArray()
        )

        val vmMassal = ViewModelOperasiMassal(repo, op)
        val sumber = listOf(
            LokasiBerkas("/root/asal/sama.txt", JenisLokasi.POSIX, "sama.txt"),
            LokasiBerkas("/root/asal/baru.txt", JenisLokasi.POSIX, "baru.txt")
        )
        val tujuan = LokasiBerkas("/root/tujuan", JenisLokasi.POSIX, "tujuan")

        vmMassal.inisiasiSalin(sumber)
        vmMassal.bukaKonfirmasiTempel()
        vmMassal.eksekusiKeTujuan(tujuan) {}

        val stateKonflik = vmMassal.state.first { !it.sedangBerjalan && it.tampilkanDialogKonflikTimpa }
        assertEquals(1, stateKonflik.daftarKonflik.size)
        assertEquals("isi baru", repo.isiDari("/root/tujuan/baru.txt")?.decodeToString())
        assertEquals("lama", repo.isiDari("/root/tujuan/sama.txt")?.decodeToString())

        var hasilAkhir: HasilMassal? = null
        vmMassal.eksekusiTimpaKonflik { hasilAkhir = it }
        vmMassal.state.first { !it.sedangBerjalan && !it.tampilkanDialogKonflikTimpa }

        val finalHasil = assertNotNull(hasilAkhir)
        assertFalse(finalHasil.adaGagal)
        assertEquals(2, finalHasil.berhasil.size)
        assertEquals("baru", repo.isiDari("/root/tujuan/sama.txt")?.decodeToString())
    }
}