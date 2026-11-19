package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

class BukaBerkasTeksUseCaseTest {
    @Test
    fun membacaUtf8DanNormalisasiCrlfDenganBenar() = runTest {
        val repo = ImplementasiRepositoriBerkas(ukuranPotongan = 4)
        val isi = "baris satu\r\nbaris dua — teks Indonesia 🚀\r\n"
        repo.simpan(
            DataBerkas("/d/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            isi.encodeToByteArray()
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher)

        val hasil = assertIs<HasilBukaBerkasTeks.Sukses>(uc(LokasiBerkas("/d/catatan.txt", JenisLokasi.POSIX)))
        assertEquals(EncodingTeks.UTF_8, hasil.data.encoding)
        assertEquals(JenisAkhirBaris.CRLF, hasil.data.akhirBaris)
        assertEquals("baris satu\nbaris dua — teks Indonesia 🚀\n", hasil.data.isi)
    }

    @Test
    fun membacaUtf8BomDanUtf16BomDenganBenar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "halo".encodeToByteArray()
        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'A'.code.toByte(), 0, 'B'.code.toByte(), 0)
        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0, 'X'.code.toByte(), 0, 'Y'.code.toByte())

        repo.simpan(DataBerkas("/u8.txt", "u8.txt", JenisLokasi.POSIX, adalahFolder = false), utf8Bom)
        repo.simpan(DataBerkas("/u16le.txt", "u16le.txt", JenisLokasi.POSIX, adalahFolder = false), utf16Le)
        repo.simpan(DataBerkas("/u16be.txt", "u16be.txt", JenisLokasi.POSIX, adalahFolder = false), utf16Be)

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher)

        val r8 = assertIs<HasilBukaBerkasTeks.Sukses>(uc(LokasiBerkas("/u8.txt", JenisLokasi.POSIX)))
        assertEquals(EncodingTeks.UTF_8_BOM, r8.data.encoding)
        assertEquals("halo", r8.data.isi)

        val r16le = assertIs<HasilBukaBerkasTeks.Sukses>(uc(LokasiBerkas("/u16le.txt", JenisLokasi.POSIX)))
        assertEquals(EncodingTeks.UTF_16_LE, r16le.data.encoding)
        assertEquals("AB", r16le.data.isi)

        val r16be = assertIs<HasilBukaBerkasTeks.Sukses>(uc(LokasiBerkas("/u16be.txt", JenisLokasi.POSIX)))
        assertEquals(EncodingTeks.UTF_16_BE, r16be.data.encoding)
        assertEquals("XY", r16be.data.isi)
    }

    @Test
    fun mendeteksiBinerDanMenolakBerkasTerlaluBesar() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(
            DataBerkas("/bin.dat", "bin.dat", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(0x41, 0x42, 0x00, 0x43)
        )
        repo.simpan(
            DataBerkas("/invalid_utf8.dat", "invalid_utf8.dat", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x80.toByte())
        )
        repo.simpan(
            DataBerkas("/besar.txt", "besar.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 100L),
            ByteArray(100) { 'a'.code.toByte() }
        )

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BukaBerkasTeksUseCase(
            repo,
            batasMaksimalByte = 50L,
            ioDispatcher = dispatcher,
            computeDispatcher = dispatcher
        )

        assertIs<HasilBukaBerkasTeks.Biner>(uc(LokasiBerkas("/bin.dat", JenisLokasi.POSIX)))
        assertIs<HasilBukaBerkasTeks.Biner>(uc(LokasiBerkas("/invalid_utf8.dat", JenisLokasi.POSIX)))

        val terlaluBesar = assertIs<HasilBukaBerkasTeks.TerlaluBesar>(uc(LokasiBerkas("/besar.txt", JenisLokasi.POSIX)))
        assertEquals(100L, terlaluBesar.ukuranByte)
        assertEquals(50L, terlaluBesar.batasMaksimalByte)
    }

    @Test
    fun memetakanBerkasHilangDanKegagalanBacaSecaraJujur() = runTest {
        val repo = ImplementasiRepositoriBerkas(ukuranPotongan = 4, gagalBacaSetelahPotongan = 1)
        repo.simpan(
            DataBerkas("/rusak.txt", "rusak.txt", JenisLokasi.POSIX, adalahFolder = false),
            "1234567890".encodeToByteArray()
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher)

        val hilang = assertIs<HasilBukaBerkasTeks.Gagal>(uc(LokasiBerkas("/tdk_ada.txt", JenisLokasi.POSIX)))
        assertEquals(PenyebabGalat.TIDAK_DITEMUKAN, hilang.penyebab)

        assertIs<HasilBukaBerkasTeks.Gagal>(uc(LokasiBerkas("/rusak.txt", JenisLokasi.POSIX)))
    }
}