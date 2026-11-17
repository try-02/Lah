package org.fm.com.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ModelDomainTest {

    private val lokasiPosix = LokasiBerkas("/home/user/catatan.txt", JenisLokasi.POSIX)

    @Test
    fun `entitas menyimpan metadata dengan benar`() {
        const val waktu = 1_700_000_000_000L
        val entitas = EntitasBerkas(
            lokasi = lokasiPosix,
            nama = "catatan.txt",
            jenis = JenisBerkas.BERKAS_TEKS,
            ukuranByte = 42L,
            waktuUbahEpochMs = waktu,
            tersembunyi = true
        )

        assertEquals(lokasiPosix, entitas.lokasi)
        assertEquals("catatan.txt", entitas.nama)
        assertEquals(JenisBerkas.BERKAS_TEKS, entitas.jenis)
        assertEquals(42L, entitas.ukuranByte)
        assertEquals(waktu, entitas.waktuUbahEpochMs)
        assertEquals(true, entitas.tersembunyi)
    }

    @Test
    fun `entitas menolak nama kosong dan ukuran negatif`() {
        assertFailsWith<IllegalArgumentException> {
            EntitasBerkas(lokasiPosix, " ", JenisBerkas.BERKAS_TEKS)
        }
        assertFailsWith<IllegalArgumentException> {
            EntitasBerkas(lokasiPosix, "a.txt", JenisBerkas.BERKAS_TEKS, ukuranByte = -1)
        }
    }

    @Test
    fun `lokasi menolak pengenal kosong`() {
        assertFailsWith<IllegalArgumentException> {
            LokasiBerkas("", JenisLokasi.SAF)
        }
    }

    @Test
    fun `hasil operasi membungkus nilai dan galat validasi`() {
        val sukses = HasilOperasi.dari { 1 + 2 }
        val hasil = assertIs<HasilOperasi.Sukses<Int>>(sukses)
        assertEquals(3, hasil.nilai)

        val gagal = HasilOperasi.dari {
            require("".isNotBlank()) { "Nama kosong" }
        }
        val galat = assertIs<HasilOperasi.Gagal>(gagal)
        assertEquals("Nama kosong", galat.pesan)
        assertEquals(PenyebabGalat.VALIDASI, galat.penyebab)
    }
}
