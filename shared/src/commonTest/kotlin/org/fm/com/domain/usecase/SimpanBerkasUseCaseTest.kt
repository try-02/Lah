@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.simpan.DeteksiKonflik
import org.fm.com.data.simpan.SimpanTransaksional
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class SimpanBerkasUseCaseTest {

    private fun buatUseCases(repo: ImplementasiRepositoriBerkas, testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler): Pair<BukaBerkasTeksUseCase, SimpanBerkasUseCase> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bukaUc = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher)
        val simpanUc = SimpanBerkasUseCase(
            pemeriksaKonflik = DeteksiKonflik(repo, ioDispatcher = dispatcher),
            pelaksanaSimpan = SimpanTransaksional(repo, ioDispatcher = dispatcher),
            computeDispatcher = dispatcher
        )
        return bukaUc to simpanUc
    }

    @Test
    fun roundTripSimpanDanBukaMempertahankanEncodingDanAkhirBaris() = runTest {
        val repo = ImplementasiRepositoriBerkas(ukuranPotongan = 4)
        val awalBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'A'.code.toByte(), 0, '\r'.code.toByte(), 0, '\n'.code.toByte(), 0, 'B'.code.toByte(), 0)
        repo.simpan(DataBerkas("/u16.txt", "u16.txt", JenisLokasi.POSIX, adalahFolder = false), awalBytes)

        val (bukaUc, simpanUc) = buatUseCases(repo, testScheduler)
        val lok = LokasiBerkas("/u16.txt", JenisLokasi.POSIX, "u16.txt")

        val muat = assertIs<HasilBukaBerkasTeks.Sukses>(bukaUc(lok)).data
        assertEquals(EncodingTeks.UTF_16_LE, muat.encoding)
        assertEquals(JenisAkhirBaris.CRLF, muat.akhirBaris)

        val hasilSimpan = assertIs<HasilSimpanBerkas.Sukses>(
            simpanUc.simpan(
                lokasi = lok,
                isiTeks = "A\nB\nC 🚀",
                encoding = muat.encoding,
                akhirBaris = muat.akhirBaris,
                sidikDiharapkan = muat.sidikIsiByte
            )
        )
        assertEquals(lok, hasilSimpan.lokasiHasil)

        val muatUlang = assertIs<HasilBukaBerkasTeks.Sukses>(bukaUc(lok)).data
        assertEquals(EncodingTeks.UTF_16_LE, muatUlang.encoding)
        assertEquals(JenisAkhirBaris.CRLF, muatUlang.akhirBaris)
        assertEquals("A\nB\nC 🚀", muatUlang.isi)
    }

    @Test
    fun mendeteksiPerubahanEksternalDanBerkasHilangSertaMendukungTimpa() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "versi satu".encodeToByteArray()
        )
        val (bukaUc, simpanUc) = buatUseCases(repo, testScheduler)
        val lok = LokasiBerkas("/catatan.txt", JenisLokasi.POSIX, "catatan.txt")

        val muat = assertIs<HasilBukaBerkasTeks.Sukses>(bukaUc(lok)).data

        // Perubahan eksternal di luar aplikasi
        repo.simpan(
            DataBerkas("/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "diubah pihak luar".encodeToByteArray()
        )

        val konflik = assertIs<HasilSimpanBerkas.KonflikEksternal>(
            simpanUc.simpan(
                lokasi = lok,
                isiTeks = "perubahan lokal",
                sidikDiharapkan = muat.sidikIsiByte,
                paksaTimpaKonflik = false
            )
        )
        assertEquals(false, konflik.berkasHilangDiDisk)
        assertEquals("diubah pihak luar", repo.isiDari("/catatan.txt")?.decodeToString())

        // Paksa timpa berhasil
        assertIs<HasilSimpanBerkas.Sukses>(
            simpanUc.simpan(
                lokasi = lok,
                isiTeks = "perubahan lokal",
                sidikDiharapkan = muat.sidikIsiByte,
                paksaTimpaKonflik = true
            )
        )
        assertEquals("perubahan lokal", repo.isiDari("/catatan.txt")?.decodeToString())

        // Hapus berkas di luar aplikasi -> konflik berkasHilangDiDisk
        repo.hapusPohon("/catatan.txt")
        val muatUlangHilang = assertIs<HasilSimpanBerkas.KonflikEksternal>(
            simpanUc.simpan(
                lokasi = lok,
                isiTeks = "perubahan baru",
                sidikDiharapkan = SimpanBerkasUseCase.sidikDariBytes("perubahan lokal".encodeToByteArray()),
                paksaTimpaKonflik = false
            )
        )
        assertEquals(true, muatUlangHilang.berkasHilangDiDisk)
    }

    @Test
    fun gagalTulisMidStreamMempertahankanIsiLamaDanMelaporkanGagal() = runTest {
        val repo = ImplementasiRepositoriBerkas(gagalTulisSetelahPotongan = 0)
        repo.simpan(
            DataBerkas("/aman.txt", "aman.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi asli aman".encodeToByteArray()
        )
        val (bukaUc, simpanUc) = buatUseCases(repo, testScheduler)
        val lok = LokasiBerkas("/aman.txt", JenisLokasi.POSIX, "aman.txt")
        val muat = assertIs<HasilBukaBerkasTeks.Sukses>(bukaUc(lok)).data

        assertIs<HasilSimpanBerkas.Gagal>(
            simpanUc.simpan(
                lokasi = lok,
                isiTeks = "isi gagal tulis",
                sidikDiharapkan = muat.sidikIsiByte
            )
        )
        assertEquals("isi asli aman", repo.isiDari("/aman.txt")?.decodeToString())
    }

    @Test
    fun simpanSebagaiMengecekKonflikNamaDanMembuatBerkasBaru() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/kerja", "kerja", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/kerja/sudah_ada.txt", "sudah_ada.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi lama".encodeToByteArray()
        )
        val (_, simpanUc) = buatUseCases(repo, testScheduler)
        val induk = LokasiBerkas("/kerja", JenisLokasi.POSIX, "kerja")

        assertIs<HasilSimpanBerkas.KonflikTujuan>(
            simpanUc.simpanSebagai(induk, "sudah_ada.txt", "isi baru", paksaTimpaKonflik = false)
        )
        assertEquals("isi lama", repo.isiDari("/kerja/sudah_ada.txt")?.decodeToString())

        val baru = assertIs<HasilSimpanBerkas.Sukses>(
            simpanUc.simpanSebagai(induk, "baru.txt", "catatan baru")
        )
        assertNotNull(repo.data("/kerja/baru.txt"))
        assertEquals("baru.txt", baru.namaHasil)
        assertEquals("catatan baru", repo.isiDari("/kerja/baru.txt")?.decodeToString())
    }
}