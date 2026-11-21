package org.fm.com.domain.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlgoritmaMyersTest {

    @Test
    fun fileKosongDanIdentikMenghasilkanStatistikTepat() {
        val keduanyaKosong = AlgoritmaMyers.bandingkanTeks("", "")
        assertTrue(keduanyaKosong.statistik.identik)
        assertEquals(0, keduanyaKosong.statistik.jumlahBarisA)
        assertEquals(0, keduanyaKosong.barisBerdampingan.size)

        val kosongVsIsi = AlgoritmaMyers.bandingkanTeks("", "satu\ndua")
        assertFalse(kosongVsIsi.statistik.identik)
        assertEquals(2, kosongVsIsi.statistik.jumlahBarisTambah)
        assertEquals(0, kosongVsIsi.statistik.jumlahBarisHapus)
        assertEquals(listOf(JenisBarisDiff.TAMBAH, JenisBarisDiff.TAMBAH), kosongVsIsi.barisUnified.map { it.jenis })

        val identik = AlgoritmaMyers.bandingkanTeks("a\nb\nc", "a\nb\nc")
        assertTrue(identik.statistik.identik)
        assertEquals(3, identik.statistik.jumlahBarisSama)
        assertEquals(0, identik.statistik.jumlahBlokPerubahan)
    }

    @Test
    fun diffBarisMenyelaraskanPasanganUbahHapusTambahDanDiffKarakter() {
        val a = "header\nval nilai = 10\nhapus saya\nfooter"
        val b = "header\nval nilai = 25\nfooter\ntambah akhir"

        val hasil = AlgoritmaMyers.bandingkanTeks(a, b)
        assertFalse(hasil.statistik.identik)
        assertEquals(2, hasil.statistik.jumlahBlokPerubahan)
        assertEquals(2, hasil.statistik.jumlahBarisHapus)
        assertEquals(2, hasil.statistik.jumlahBarisTambah)

        // Baris index 1 harus berupa pasangan UBAH dengan diff karakter pada angka 10 vs 25
        val barisUbah = hasil.barisBerdampingan[1]
        assertEquals(JenisBarisDiff.UBAH, barisUbah.kiri.jenis)
        assertEquals(JenisBarisDiff.UBAH, barisUbah.kanan.jenis)
        assertTrue(barisUbah.kiri.rentangKarakterBeda.isNotEmpty())
        assertTrue(barisUbah.kanan.rentangKarakterBeda.isNotEmpty())

        // Baris index 2 hanya ada di A -> kanan KOSONG penyeimbang
        val barisHapus = hasil.barisBerdampingan[2]
        assertEquals(JenisBarisDiff.HAPUS, barisHapus.kiri.jenis)
        assertEquals(JenisBarisDiff.KOSONG, barisHapus.kanan.jenis)
        assertEquals(null, barisHapus.kanan.nomorBaris)
    }

    @Test
    fun kasusMenengahA_B_C_vs_B_X_OptimalDanUnifiedSejajar() {
        val hasil = AlgoritmaMyers.bandingkanBaris(
            barisA = listOf("a", "b", "c"),
            barisB = listOf("b", "x")
        )
        assertEquals(1, hasil.statistik.jumlahBarisSama)
        assertEquals(2, hasil.statistik.jumlahBarisHapus)
        assertEquals(1, hasil.statistik.jumlahBarisTambah)
        assertEquals(
            listOf(JenisBarisDiff.HAPUS, JenisBarisDiff.SAMA, JenisBarisDiff.HAPUS, JenisBarisDiff.TAMBAH),
            hasil.barisUnified.map { it.jenis }
        )
    }

    @Test
    fun opsiAbaikanWhitespacePekaHurufDanKonteksBekerja() {
        val a = "1\n2\n3\n  VAL X = 1  \n5\n6\n7"
        val b = "1\n2\n3\nval x = 1\n5\n6\n7"

        val ketat = AlgoritmaMyers.bandingkanTeks(a, b, OpsiDiff(pekaHuruf = true, modeWhitespace = ModeWhitespaceDiff.PERSIS))
        assertFalse(ketat.statistik.identik)

        val longgar = AlgoritmaMyers.bandingkanTeks(
            a,
            b,
            OpsiDiff(pekaHuruf = false, modeWhitespace = ModeWhitespaceDiff.ABAIKAN_TEPI)
        )
        assertTrue(longgar.statistik.identik)

        val hanyaBedaKonteks = AlgoritmaMyers.bandingkanTeks(
            "1\n2\n3\n4\n5\nlama\n7\n8\n9\n10",
            "1\n2\n3\n4\n5\nbaru\n7\n8\n9\n10",
            OpsiDiff(tampilkanBarisSama = false, jumlahBarisKonteks = 1)
        )
        // 4 baris tersembunyi di awal + baris 5, baris 6 (ubah), baris 7 + 3 baris tersembunyi di akhir
        val barisIsi = hanyaBedaKonteks.barisBerdampingan.filterNot {
            it.kiri.jenis == JenisBarisDiff.KOSONG && it.kanan.jenis == JenisBarisDiff.KOSONG
        }
        assertEquals(3, barisIsi.size)
        assertEquals(listOf("5", "lama", "7"), barisIsi.map { it.kiri.teks })
        assertEquals("@@ 4 baris sama disembunyikan @@", hanyaBedaKonteks.barisBerdampingan.first().kiri.teks)
        assertEquals("@@ 3 baris sama disembunyikan @@", hanyaBedaKonteks.barisBerdampingan.last().kiri.teks)
    }

    @Test
    fun patchUnifiedDanBerkasBesarDuaPuluhRibuBarisBerjalanCepat() {
        val a = buildString {
            repeat(20_000) { i -> append("baris tetap nomor ").append(i).append('\n') }
        }
        val b = a.replace("baris tetap nomor 12345", "baris DIUBAH nomor 12345")
        val hasil = AlgoritmaMyers.bandingkanTeks(a, b)
        assertEquals(1, hasil.statistik.jumlahBlokPerubahan)
        assertEquals(1, hasil.statistik.jumlahBarisHapus)
        assertEquals(1, hasil.statistik.jumlahBarisTambah)

        val patch = AlgoritmaMyers.bentukUnifiedPatch("a.txt", "b.txt", a, b)
        assertTrue(patch.contains("--- a/a.txt"))
        assertTrue(patch.contains("+++ b/b.txt"))
        assertTrue(patch.contains("-baris tetap nomor 12345"))
        assertTrue(patch.contains("+baris DIUBAH nomor 12345"))
    }
}