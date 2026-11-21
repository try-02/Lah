package org.fm.com.domain.rename

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParserTokenPolaTest {

    @Test
    fun pisahNamaDanEkstensiMenanganiBerbagaiKasus() {
        assertEquals("laporan" to ".txt", ParserTokenPola.pisahNamaDanEkstensi("laporan.txt"))
        assertEquals("arsip.tar" to ".gz", ParserTokenPola.pisahNamaDanEkstensi("arsip.tar.gz"))
        assertEquals("README" to "", ParserTokenPola.pisahNamaDanEkstensi("README"))
        assertEquals(".gitignore" to "", ParserTokenPola.pisahNamaDanEkstensi(".gitignore"))
        assertEquals(".env" to ".local", ParserTokenPola.pisahNamaDanEkstensi(".env.local"))
        assertEquals("berkas." to "", ParserTokenPola.pisahNamaDanEkstensi("berkas."))
        assertEquals("v2.1.0" to "", ParserTokenPola.pisahNamaDanEkstensi("v2.1.0", adalahFolder = true))
    }

    @Test
    fun tokenPolaDasarDanNomorUrutDihitungBenar() {
        val parsed = ParserTokenPola.parse("file_{N}{S}")
        val sukses = assertIs<HasilParsePola.Sukses>(parsed)
        val konfig = KonfigurasiNomorUrut(angkaAwal = 1, langkah = 1)

        assertEquals(
            "file_1.txt",
            ParserTokenPola.terapkan(sukses.daftarToken, "laporan.txt", indeksUrut = 0, konfigurasiNomor = konfig)
        )
        assertEquals(
            "file_2.txt",
            ParserTokenPola.terapkan(sukses.daftarToken, "catatan.txt", indeksUrut = 1, konfigurasiNomor = konfig)
        )

        val awalSeratus = KonfigurasiNomorUrut(angkaAwal = 100, langkah = 5)
        assertEquals(
            "file_105.md",
            ParserTokenPola.terapkan(sukses.daftarToken, "dok.md", indeksUrut = 1, konfigurasiNomor = awalSeratus)
        )
    }

    @Test
    fun paddingNolDefaultDanLebarKustomTidakMemotongAngkaLebihPanjang() {
        val polaDefault = assertIs<HasilParsePola.Sukses>(ParserTokenPola.parse("gambar_{zN}.jpg"))
        val polaLebar3 = assertIs<HasilParsePola.Sukses>(ParserTokenPola.parse("gambar_{zN:3}.jpg"))
        val konfig = KonfigurasiNomorUrut(angkaAwal = 8, langkah = 1)

        assertEquals(
            "gambar_08.jpg",
            ParserTokenPola.terapkan(polaDefault.daftarToken, "a.png", indeksUrut = 0, konfigurasiNomor = konfig)
        )
        assertEquals(
            "gambar_10.jpg",
            ParserTokenPola.terapkan(polaDefault.daftarToken, "c.png", indeksUrut = 2, konfigurasiNomor = konfig)
        )
        assertEquals(
            "gambar_008.jpg",
            ParserTokenPola.terapkan(polaLebar3.daftarToken, "a.png", indeksUrut = 0, konfigurasiNomor = konfig)
        )

        // Angka melampaui lebar W tidak dipotong
        assertEquals(
            "gambar_1234.jpg",
            ParserTokenPola.terapkan(
                polaLebar3.daftarToken,
                "x.png",
                indeksUrut = 0,
                konfigurasiNomor = KonfigurasiNomorUrut(angkaAwal = 1234)
            )
        )
    }

    @Test
    fun angkaAwalInlineDidukungDanDapatDinonaktifkan() {
        val polaInline = assertIs<HasilParsePola.Sukses>(ParserTokenPola.parse("img_{z8}_{z8:3}{S}"))
        assertEquals(
            "img_08_008.jpg",
            ParserTokenPola.terapkan(polaInline.daftarToken, "foto.jpg", indeksUrut = 0)
        )
        assertEquals(
            "img_10_010.jpg",
            ParserTokenPola.terapkan(polaInline.daftarToken, "foto.jpg", indeksUrut = 2)
        )

        val ditolak = ParserTokenPola.parse(
            "img_{z8}{S}",
            opsi = OpsiParserPola(izinkanAngkaAwalInline = false)
        )
        assertIs<HasilParsePola.Gagal>(ditolak)
    }

    @Test
    fun polaEscapeDanPolaTidakValidDitanganiAman() {
        val escaped = assertIs<HasilParsePola.Sukses>(ParserTokenPola.parse("{{{P}}}{S}"))
        assertEquals("{uji}.txt", ParserTokenPola.terapkan(escaped.daftarToken, "uji.txt"))

        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse(""))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{P"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("nama}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{P{S}}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{X}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{zN:0}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{zN:abc}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{zN:25}"))
        assertIs<HasilParsePola.Gagal>(ParserTokenPola.parse("{z99999999999999}"))
    }
}