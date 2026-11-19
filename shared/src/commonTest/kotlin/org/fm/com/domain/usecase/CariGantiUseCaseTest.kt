package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CariGantiUseCaseTest {
    private val useCase = CariGantiUseCase()

    @Test
    fun cariBiasaPekaHurufDanKataUtuh() {
        val teks = "Kotlin kotlin kotlin_pro (kotlin)"

        val abaikanHuruf = assertIs<HasilCariEditor.Sukses>(
            useCase.cariSemua(teks, "kotlin", OpsiCariEditor(pekaHuruf = false))
        )
        assertEquals(4, abaikanHuruf.daftarKecocokan.size)

        val pekaHuruf = assertIs<HasilCariEditor.Sukses>(
            useCase.cariSemua(teks, "kotlin", OpsiCariEditor(pekaHuruf = true))
        )
        assertEquals(3, pekaHuruf.daftarKecocokan.size)

        val kataUtuh = assertIs<HasilCariEditor.Sukses>(
            useCase.cariSemua(teks, "kotlin", OpsiCariEditor(pekaHuruf = true, kataUtuh = true))
        )
        assertEquals(
            listOf(RentangKecocokan(7, 13), RentangKecocokan(26, 32)),
            kataUtuh.daftarKecocokan
        )
    }

    @Test
    fun cariRegexValidDanPolaTidakValidMenghasilkanPesanJelas() {
        val teks = "baris 10\nbaris 250\ntidak ada angka"
        val regexValid = assertIs<HasilCariEditor.Sukses>(
            useCase.cariSemua(teks, """^baris \d+""", OpsiCariEditor(gunakanRegex = true))
        )
        assertEquals(listOf("baris 10", "baris 250"), regexValid.daftarKecocokan.map {
            teks.substring(it.awal, it.akhirEksklusif)
        })

        val regexInvalid = assertIs<HasilCariEditor.PolaTidakValid>(
            useCase.cariSemua(teks, "[abc(", OpsiCariEditor(gunakanRegex = true))
        )
        assertTrue(regexInvalid.pesan.contains("Pola regex tidak valid"))
    }

    @Test
    fun navigasiBerikutnyaDanSebelumnyaMengikutiPosisiKursorDanWrap() {
        val daftar = listOf(
            RentangKecocokan(0, 3),
            RentangKecocokan(10, 13),
            RentangKecocokan(20, 23)
        )

        assertEquals(1, useCase.pilihKecocokanBerikutnya(daftar, 0, 3))
        assertEquals(0, useCase.pilihKecocokanBerikutnya(daftar, 20, 23))
        assertEquals(1, useCase.pilihKecocokanBerikutnya(daftar, 5, 5))

        assertEquals(0, useCase.pilihKecocokanSebelumnya(daftar, 10, 13))
        assertEquals(2, useCase.pilihKecocokanSebelumnya(daftar, 0, 3))
        assertEquals(1, useCase.pilihKecocokanSebelumnya(daftar, 15, 15))
    }

    @Test
    fun gantiSatuTidakMengulangTeksPenggantiYangMengandungKueri() {
        val teks = "foo"
        val hasil = assertIs<HasilOperasiGanti.Sukses>(
            useCase.gantiSatu(
                teks = teks,
                kueri = "foo",
                teksPengganti = "foo_baru",
                awalSeleksi = 0,
                akhirSeleksi = 3
            )
        )
        assertEquals("foo_baru", hasil.teksBaru)
        assertEquals(1, hasil.jumlahUbahan)
        assertNull(hasil.rentangBerikutnya)
    }

    @Test
    fun gantiSemuaMenggantiSeluruhKecocokanDanAmanTerhadapKarakterKhusus() {
        val teks = "nilai a, nilai a, nilai a"
        val hasil = assertIs<HasilOperasiGanti.Sukses>(
            useCase.gantiSemua(
                teks = teks,
                kueri = "nilai a",
                teksPengganti = "$1\\ok"
            )
        )
        assertEquals(3, hasil.jumlahUbahan)
        assertEquals("$1\\ok, $1\\ok, $1\\ok", hasil.teksBaru)
    }
}