package org.fm.com.data

import kotlin.test.Test
import kotlin.test.assertEquals
import org.fm.com.data.PemetaanDataDomain.jenisBerkasDariNama
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi

class T8PemetaanTest {
    @Test
    fun ekstensiTeksCaseInsensitive() {
        assertEquals(JenisBerkas.BERKAS_TEKS, jenisBerkasDariNama("CATATAN.TXT"))
        assertEquals(JenisBerkas.BERKAS_TEKS, jenisBerkasDariNama("kode.kt"))
    }

    @Test
    fun hanyaEkstensiTerakhir() {
        assertEquals(JenisBerkas.ARSIP, jenisBerkasDariNama("arsip.tar.gz"))
        assertEquals(JenisBerkas.BERKAS_TEKS, jenisBerkasDariNama("foto.png.txt"))
    }

    @Test
    fun tanpaEkstensiDanDotfile() {
        assertEquals(JenisBerkas.LAIN, jenisBerkasDariNama("README"))
        assertEquals(JenisBerkas.LAIN, jenisBerkasDariNama("nama."))
        assertEquals(JenisBerkas.LAIN, jenisBerkasDariNama(".gitignore"))
    }

    @Test
    fun klasifikasiJenisLain() {
        assertEquals(JenisBerkas.BERKAS_GAMBAR, jenisBerkasDariNama("foto.HEIC"))
        assertEquals(JenisBerkas.BERKAS_AUDIO, jenisBerkasDariNama("lagu.flac"))
        assertEquals(JenisBerkas.BERKAS_VIDEO, jenisBerkasDariNama("film.mkv"))
        assertEquals(JenisBerkas.ARSIP, jenisBerkasDariNama("paket.zip"))
        assertEquals(JenisBerkas.LAIN, jenisBerkasDariNama("biner.exe"))
    }

    @Test
    fun tautanFolderTetapFolderBerkasTetapTautan() {
        val folderTautan = DataBerkas("/d", "d", JenisLokasi.POSIX, adalahFolder = true, adalahTautan = true)
        assertEquals(JenisBerkas.FOLDER, folderTautan.keDomain().jenis)
        val berkasTautan = DataBerkas("/t", "t", JenisLokasi.POSIX, adalahFolder = false, adalahTautan = true)
        assertEquals(JenisBerkas.TAUTAN, berkasTautan.keDomain().jenis)
    }

    @Test
    fun keDomainRoundTripField() {
        val data = DataBerkas(
            pengenal = "content://x/doc/1", nama = "a.txt", jenisLokasi = JenisLokasi.SAF,
            adalahFolder = false, ukuranByte = 42L, waktuUbahEpochMs = 123L,
            tersembunyi = true, dapatDibaca = true, dapatDitulis = false
        )
        val domain = data.keDomain()
        assertEquals("content://x/doc/1", domain.lokasi.pengenal)
        assertEquals(JenisLokasi.SAF, domain.lokasi.jenis)
        assertEquals("a.txt", domain.lokasi.label)
        assertEquals("a.txt", domain.nama)
        assertEquals(JenisBerkas.BERKAS_TEKS, domain.jenis)
        assertEquals(42L, domain.ukuranByte)
        assertEquals(123L, domain.waktuUbahEpochMs)
        assertEquals(true, domain.tersembunyi)
        assertEquals(false, domain.dapatDitulis)
    }

    @Test
    fun dataFolderKeDomain() {
        val lokasi = DataFolder("content://x/tree/1", "Dokumen", JenisLokasi.SAF, indukPengenal = "content://x").keDomain()
        assertEquals("content://x/tree/1", lokasi.pengenal)
        assertEquals(JenisLokasi.SAF, lokasi.jenis)
        assertEquals("Dokumen", lokasi.label)
    }
}