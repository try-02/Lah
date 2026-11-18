package org.fm.com.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.repository.PermintaanOperasi

class T8ImplementasiDataTest {
    private val kaset = ImplementasiRepositoriBerkas()
    private val operasi = ImplementasiRepositoriOperasiBerkas(kaset)

    private fun lokasi(
        pengenal: String = "/sdcard/Dokumen/berkas.txt",
        jenis: JenisLokasi = JenisLokasi.POSIX
    ): LokasiBerkas =
        LokasiBerkas(pengenal = pengenal, jenis = jenis, label = pengenal.substringAfterLast("/"))

    private suspend fun seedFolder(pengenal: String, jenis: JenisLokasi = JenisLokasi.POSIX) {
        kaset.simpan(DataBerkas(pengenal, pengenal.substringAfterLast("/"), jenis, adalahFolder = true))
    }

    private suspend fun seedBerkas(pengenal: String, isi: String = "halo", tautan: Boolean = false) {
        kaset.simpan(
            DataBerkas(
                pengenal, pengenal.substringAfterLast("/"), JenisLokasi.POSIX,
                adalahFolder = false, adalahTautan = tautan, ukuranByte = isi.length.toLong()
            ),
            isi.encodeToByteArray()
        )
    }

    private suspend fun tulisSemua(sumber: SumberTulisBerkas, vararg bagian: String) {
        for (b in bagian) {
            assertIs<HasilOperasi.Sukses<Unit>>(sumber.tulis(PotonganIsi(b.encodeToByteArray())))
        }
        assertIs<HasilOperasi.Sukses<Unit>>(sumber.selesai())
    }

    private suspend fun bacaSemua(sumber: SumberIsiBerkas): ByteArray {
        var gabungan = ByteArray(0)
        while (true) {
            val isi = assertIs<SumberIsiBerkas.HasilPotongan.Isi>(sumber.potongan())
            gabungan += isi.potongan.data
            if (isi.potongan.selesai) break
        }
        sumber.tutup()
        return gabungan
    }

    @Test
    fun roundTripTulisBacaChunked() = runTest {
        val lok = lokasi("/mem/a.txt", JenisLokasi.SAF)
        val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(kaset.bukaUntukTulis(lok, timpa = false))
        tulisSemua(tulis.nilai, "halo ", "dunia")
        val baca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(kaset.bukaUntukBaca(lok))
        assertEquals(10L, baca.nilai.ukuranByte)
        assertEquals("halo dunia", bacaSemua(baca.nilai).decodeToString())
        assertEquals(JenisLokasi.SAF, kaset.data(lok.pengenal)?.jenisLokasi, "jenis lokasi tidak boleh di-hardcode POSIX")
    }

    @Test
    fun bacaBerkasKosong() = runTest {
        seedBerkas("/sdcard/kosong.txt", isi = "")
        val baca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(kaset.bukaUntukBaca(lokasi("/sdcard/kosong.txt")))
        val pertama = assertIs<SumberIsiBerkas.HasilPotongan.Isi>(baca.nilai.potongan())
        assertTrue(pertama.potongan.selesai)
        assertEquals(0, pertama.potongan.data.size)
    }

    @Test
    fun tulisSetelahTerminalGagal() = runTest {
        val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(kaset.bukaUntukTulis(lokasi(), timpa = true))
        tulisSemua(tulis.nilai, "x")
        val gagal = assertIs<HasilOperasi.Gagal>(tulis.nilai.tulis(PotonganIsi("y".encodeToByteArray())))
        assertEquals(PenyebabGalat.VALIDASI, gagal.penyebab)
        assertIs<HasilOperasi.Gagal>(tulis.nilai.batalkan())
    }

    @Test
    fun batalkanTidakMenyimpan() = runTest {
        val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(kaset.bukaUntukTulis(lokasi(), timpa = false))
        assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.tulis(PotonganIsi("x".encodeToByteArray())))
        assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.batalkan())
        assertNull(kaset.data(lokasi().pengenal))
        assertIs<HasilOperasi.Gagal>(tulis.nilai.selesai())
    }

    @Test
    fun timpaButuhKonfirmasiSaatBukaDanSelesai() = runTest {
        seedBerkas(lokasi().pengenal, isi = "asli")
        val tanpa = assertIs<HasilOperasi.Gagal>(kaset.bukaUntukTulis(lokasi(), timpa = false))
        assertEquals(PenyebabGalat.KONFLIK, tanpa.penyebab)
        // Konflik muncul di antara buka dan selesai → diperiksa ulang atomik saat selesai.
        val lokBaru = lokasi("/sdcard/Dokumen/baru.txt")
        val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(kaset.bukaUntukTulis(lokBaru, timpa = false))
        seedBerkas(lokBaru.pengenal, isi = "milik orang lain")
        assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.tulis(PotonganIsi("x".encodeToByteArray())))
        val gagal = assertIs<HasilOperasi.Gagal>(tulis.nilai.selesai())
        assertEquals(PenyebabGalat.KONFLIK, gagal.penyebab)
        assertEquals("milik orang lain", kaset.isiDari(lokBaru.pengenal)?.decodeToString())
    }

    @Test
    fun bacaTidakDitemukanAtauFolder() = runTest {
        val hilang = assertIs<HasilOperasi.Gagal>(kaset.bukaUntukBaca(lokasi("/tdk/ada.txt")))
        assertEquals(PenyebabGalat.TIDAK_DITEMUKAN, hilang.penyebab)
        seedFolder("/sdcard")
        val folder = assertIs<HasilOperasi.Gagal>(kaset.bukaUntukBaca(lokasi("/sdcard")))
        assertEquals(PenyebabGalat.VALIDASI, folder.penyebab)
    }

    @Test
    fun daftarIsiSnapshotAnakLangsung() = runTest {
        seedFolder("/sdcard/Dokumen")
        seedBerkas("/sdcard/Dokumen/b.txt")
        seedBerkas("/sdcard/Dokumen/a.txt")
        seedFolder("/sdcard/Dokumen/sub")
        seedBerkas("/sdcard/Dokumen/sub/c.txt")
        val hasil = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(
            kaset.daftarIsi(lokasi("/sdcard/Dokumen")).first()
        )
        assertEquals(listOf("a.txt", "b.txt", "sub"), hasil.nilai.map { it.nama })
    }

    @Test
    fun daftarIsiRootDanError() = runTest {
        seedFolder("/")
        seedFolder("/sdcard")
        val root = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(kaset.daftarIsi(lokasi("/")).first())
        assertEquals(listOf("sdcard"), root.nilai.map { it.nama })
        val hilang = assertIs<HasilOperasi.Gagal>(kaset.daftarIsi(lokasi("/tdk")).first())
        assertEquals(PenyebabGalat.TIDAK_DITEMUKAN, hilang.penyebab)
        seedBerkas("/sdcard/a.txt")
        val bukanFolder = assertIs<HasilOperasi.Gagal>(kaset.daftarIsi(lokasi("/sdcard/a.txt")).first())
        assertEquals(PenyebabGalat.VALIDASI, bukanFolder.penyebab)
    }

    @Test
    fun matriksSafTanpaPathPosix() {
        val cap = MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        assertNotNull(cap)
        assertFalse(cap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
    }

    @Test
    fun buatFolderDenganNamaBaru() = runTest {
        seedFolder("/sdcard/Dokumen")
        val hasil = operasi.buatFolder(
            PermintaanOperasi(sumber = lokasi("/sdcard/Dokumen"), namaBaru = "baru")
        )
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "harus sukses: $hasil")
        assertTrue(kaset.data("/sdcard/Dokumen/baru")?.adalahFolder == true)
    }

    @Test
    fun buatBerkasKosongBisaDibaca() = runTest {
        seedFolder("/sdcard/Dokumen")
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
            operasi.buatBerkasKosong(PermintaanOperasi(sumber = lokasi("/sdcard/Dokumen"), namaBaru = "kosong.txt"))
        )
        val baca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(kaset.bukaUntukBaca(lokasi("/sdcard/Dokumen/kosong.txt")))
        assertEquals(0L, baca.nilai.ukuranByte)
    }

    @Test
    fun buatValidasiNamaDanKonfirmasi() = runTest {
        seedFolder("/sdcard/Dokumen")
        val tanpaNama = assertIs<HasilOperasi.Gagal>(operasi.buatFolder(PermintaanOperasi(sumber = lokasi("/sdcard/Dokumen"))))
        assertEquals(PenyebabGalat.VALIDASI, tanpaNama.penyebab)
        val namaSah = assertIs<HasilOperasi.Gagal>(
            operasi.buatFolder(PermintaanOperasi(sumber = lokasi("/sdcard/Dokumen"), namaBaru = "a/b"))
        )
        assertEquals(PenyebabGalat.VALIDASI, namaSah.penyebab)
        seedBerkas("/sdcard/Dokumen/ada.txt")
        // Dulu lolos karena fallback tujuan ?: sumber melewati cek timpa.
        val timpa = assertIs<HasilOperasi.Gagal>(
            operasi.buatBerkasKosong(PermintaanOperasi(sumber = lokasi("/sdcard/Dokumen"), namaBaru = "ada.txt"))
        )
        assertEquals(PenyebabGalat.KONFLIK, timpa.penyebab)
    }

    @Test
    fun gantiNamaBerkas() = runTest {
        seedBerkas("/sdcard/a.txt")
        val hasil = operasi.gantiNama(PermintaanOperasi(sumber = lokasi("/sdcard/a.txt"), namaBaru = "c.txt"))
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "harus sukses: $hasil")
        assertNull(kaset.data("/sdcard/a.txt"))
        assertNotNull(kaset.data("/sdcard/c.txt"))
        assertEquals("halo", kaset.isiDari("/sdcard/c.txt")?.decodeToString())
        val tanpaNama = assertIs<HasilOperasi.Gagal>(operasi.gantiNama(PermintaanOperasi(sumber = lokasi("/sdcard/c.txt"))))
        assertEquals(PenyebabGalat.VALIDASI, tanpaNama.penyebab)
    }

    @Test
    fun gantiNamaFolderRekursifDanTautanUtuh() = runTest {
        seedFolder("/d")
        seedFolder("/d/sub")
        seedBerkas("/d/sub/c.txt", isi = "isi", tautan = true)
        val hasil = operasi.gantiNama(PermintaanOperasi(sumber = lokasi("/d"), namaBaru = "e"))
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "harus sukses: $hasil")
        assertNull(kaset.data("/d/sub/c.txt"))
        assertNull(kaset.isiDari("/d/sub/c.txt"))
        assertEquals("isi", kaset.isiDari("/e/sub/c.txt")?.decodeToString())
        assertTrue(kaset.data("/e/sub/c.txt")?.adalahTautan == true, "flag tautan harus dipertahankan")
    }

    @Test
    fun hapusKonfirmasiDanRekursif() = runTest {
        seedFolder("/d")
        seedFolder("/d/sub")
        seedBerkas("/d/sub/c.txt")
        val tanpa = assertIs<HasilOperasi.Gagal>(operasi.hapus(PermintaanOperasi(sumber = lokasi("/d"))))
        assertEquals(PenyebabGalat.VALIDASI, tanpa.penyebab)
        assertIs<HasilOperasi.Sukses<Unit>>(operasi.hapus(PermintaanOperasi(sumber = lokasi("/d"), konfirmasi = true)))
        assertNull(kaset.data("/d"))
        assertNull(kaset.data("/d/sub/c.txt"))
        assertNull(kaset.isiDari("/d/sub/c.txt"))
    }

    @Test
    fun salinFolderRekursifAsalUtuh() = runTest {
        seedFolder("/d"); seedFolder("/t")
        seedBerkas("/d/a.txt", isi = "isi")
        val hasil = operasi.salin(PermintaanOperasi(sumber = lokasi("/d"), tujuan = lokasi("/t")))
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "salin harus sukses: $hasil")
        assertEquals("isi", kaset.isiDari("/t/d/a.txt")?.decodeToString())
        assertEquals("isi", kaset.isiDari("/d/a.txt")?.decodeToString(), "asal tetap ada setelah salin")
    }

    @Test
    fun pindahFolderBersihkanAsal() = runTest {
        seedFolder("/d"); seedFolder("/t")
        seedBerkas("/d/a.txt", isi = "isi")
        val hasil = operasi.pindah(PermintaanOperasi(sumber = lokasi("/d"), tujuan = lokasi("/t"), namaBaru = "e"))
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "pindah harus sukses: $hasil")
        assertNull(kaset.data("/d"))
        assertNull(kaset.data("/d/a.txt"))
        assertEquals("isi", kaset.isiDari("/t/e/a.txt")?.decodeToString())
    }

    @Test
    fun salinKeDescendanSendiriGagal() = runTest {
        seedFolder("/d"); seedFolder("/d/sub")
        val hasil = assertIs<HasilOperasi.Gagal>(
            operasi.salin(PermintaanOperasi(sumber = lokasi("/d"), tujuan = lokasi("/d/sub")))
        )
        assertEquals(PenyebabGalat.VALIDASI, hasil.penyebab)
    }

    @Test
    fun salinTimpaButuhKonfirmasiDanTujuanWajib() = runTest {
        seedFolder("/t")
        seedBerkas("/d.txt"); seedBerkas("/t/d.txt", isi = "lama")
        val tanpa = assertIs<HasilOperasi.Gagal>(
            operasi.salin(PermintaanOperasi(sumber = lokasi("/d.txt"), tujuan = lokasi("/t")))
        )
        assertEquals(PenyebabGalat.KONFLIK, tanpa.penyebab)
        assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
            operasi.salin(PermintaanOperasi(sumber = lokasi("/d.txt"), tujuan = lokasi("/t"), konfirmasi = true))
        )
        assertEquals("halo", kaset.isiDari("/t/d.txt")?.decodeToString())
        val tanpaTujuan = assertIs<HasilOperasi.Gagal>(operasi.pindah(PermintaanOperasi(sumber = lokasi("/d.txt"))))
        assertEquals(PenyebabGalat.VALIDASI, tanpaTujuan.penyebab)
    }

    @Test
    fun tulisKonkurenAman() = runTest {
        coroutineScope {
            repeat(50) { i -> launch { seedBerkas("/par/f$i.txt", isi = "x$i") } }
        }
        seedFolder("/par")
        val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(kaset.daftarIsi(lokasi("/par")).first())
        assertEquals(50, daftar.nilai.size)
    }

    @Test
    fun routerMerutekanDanMenolakJujur() = runTest {
        val posix = SumberMemoriPenyimpanan()
        val saf = SumberMemoriPenyimpanan()
        val router = RepositoriRouterBerkas(mapOf(JenisLokasi.POSIX to posix, JenisLokasi.SAF to saf))
        posix.berkas.simpan(DataBerkas("/p", "p", JenisLokasi.POSIX, adalahFolder = true))
        posix.berkas.simpan(DataBerkas("/p/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false))
        saf.berkas.simpan(DataBerkas("content://p", "p", JenisLokasi.SAF, adalahFolder = true))
        val daftarPosix = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(lokasi("/p")).first())
        assertEquals(1, daftarPosix.nilai.size)
        val daftarSaf = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(
            router.daftarIsi(lokasi("content://p", JenisLokasi.SAF)).first()
        )
        assertEquals(0, daftarSaf.nilai.size)
        val ios = assertIs<HasilOperasi.Gagal>(router.bukaUntukBaca(lokasi("/x", JenisLokasi.IOS)))
        assertEquals(PenyebabGalat.VALIDASI, ios.penyebab)
        val lintas = assertIs<HasilOperasi.Gagal>(
            router.salin(PermintaanOperasi(sumber = lokasi("/p/a.txt"), tujuan = lokasi("content://p", JenisLokasi.SAF)))
        )
        assertEquals(PenyebabGalat.VALIDASI, lintas.penyebab)
    }
}