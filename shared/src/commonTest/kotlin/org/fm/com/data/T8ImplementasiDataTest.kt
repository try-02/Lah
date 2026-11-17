package org.fm.com.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.fm.com.domain.model.DataBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.repository.PermintaanOperasi

class T8ImplementasiDataTest {

    private val kaset = ImplementasiRepositoriBerkas()
    private val operasi = ImplementasiRepositoriOperasiBerkas(kaset)

    private fun lokasi(posenis: String = "/sdcard/Dokumen/berkas.txt", jenis: JenisLokasi = JenisLokasi.POSIX): LokasiBerkas =
        LokasiBerkas(pengenal = posenis, jenis = jenis, label = posenis.substringAfterLast("/"))

    private fun entitasBerkas(pengenal: String = "/sdcard/Dokumen/berkas.txt", isi: String = "halo"): DataBerkas =
        DataBerkas(
            pengenal = pengenal,
            nama = pengenal.substringAfterLast("/"),
            jenisLokasi = JenisLokasi.POSIX,
            adalahFolder = false,
            ukuranByte = isi.length.toLong()
        )

    @Test
    fun simpanDanBacaBerkas() = runBlocking {
        val e = entitasBerkas()
        kaset.simpan(e)
        val hasilBaca = kaset.bukaUntukBaca(lokasi(e.pengenal))
        assertTrue(hasilBaca is HasilOperasi.Sukses<*>) { "harus sukses: $hasilBaca" }
        val sumber = (hasilBaca as HasilOperasi.Sukses<*>).nilai
        assertEquals(e.ukuranByte, sumber.ukuranByte)
    }

    @Test
    fun bacaBerkasTidakDitemukan() = runBlocking {
        val hasil = kaset.bukaUntukBaca(lokasi("/tdak/ada.txt"))
        assertTrue(hasil is HasilOperasi.Gagal) { "harus gagal: $hasil" }
        assertEquals(PenyebabGalat.TIDAK_DITEMUKAN, (hasil as HasilOperasi.Gagal<*>).penyebab)
    }

    @Test
    fun timpaButuhKonfirmasi() = runBlocking {
        kaset.simpan(entitasBerkas())
        val lok = lokasi()
        val tanpaKonfirmasi = kaset.bukaUntukTulis(lok, timpa = false)
        assertTrue(tanpaKonfirmasi is HasilOperasi.Gagal) { "harus gagal: $tanpaKonfirmasi" }
        assertEquals(PenyebabGalat.KONFLIK, (tanpaKonfirmasi as HasilOperasi.Gagal<*>).penyebab)
        val denganKonfirmasi = kaset.bukaUntukTulis(lok, timpa = true)
        assertTrue(denganKonfirmasi is HasilOperasi.Sukses<*>) { "harus sukses: $denganKonfirmasi" }
    }

    @Test
    fun daftarIsiFolder() = runBlocking {
        kaset.simpan(entitasBerkas("/sdcard/Dokumen/a.txt"))
        kaset.simpan(entitasBerkas("/sdcard/Dokumen/b.txt"))
        kaset.simpan(DataBerkas("/sdcard/Dokumen/sub", "sub", JenisLokasi.POSIX, adalahFolder = true))
        val flow = kaset.daftarIsi(lokasi("/sdcard/Dokumen", JenisLokasi.POSIX))
            .first()
        assertTrue(flow is HasilOperasi.Sukses<*>) { "harus sukses: $flow" }
        val daftar = (flow as HasilOperasi.Sukses<*>).nilai
        assertEquals(2, daftar.size) { "hanya berkas langsung, tidak rekursif: $daftar" }
        assertTrue(daftar.all { it is EntitasBerkas })
    }

    @Test
    fun matriksSafTanpaPathPosix() {
        val cap = MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        assertNotNull(cap)
        assertFalse(cap.apakahDidukung(org.fm.com.domain.model.OperasiBerkas.MELIHAT_PATH_POSIX))
    }

    @Test
    fun buatFolderSukses() = runBlocking {
        val lok = lokasi("/sdcard/Dokumen/baru")
        val hasil = operasi.buatFolder(PermintaanOperasi(sumber = lok, konfirmasi = true))
        assertTrue(hasil is HasilOperasi.Sukses<*>) { "harus sukses: $hasil" }
    }

    @Test
    fun hapusTanpaKonfirmasiGagal() = runBlocking {
        val lok = lokasi()
        kaset.simpan(entitasBerkas(lok.pengenal))
        val hasil = operasi.hapus(PermintaanOperasi(sumber = lok, konfirmasi = false))
        assertTrue(hasil is HasilOperasi.Gagal) { "harus gagal: $hasil" }
        assertEquals(PenyebabGalat.VALIDASI, (hasil as HasilOperasi.Gagal<*>).penyebab)
    }

    @Test
    fun hapusDenganKonfirmasiSukses() = runBlocking {
        val lok = lokasi()
        kaset.simpan(entitasBerkas(lok.pengenal))
        val hasil = operasi.hapus(PermintaanOperasi(sumber = lok, konfirmasi = true))
        assertTrue(hasil is HasilOperasi.Sukses<*>) { "harus sukses: $hasil" }
        assertNull(kaset.kaset[lok.pengenal])
    }

    @Test
    fun salinDanPindah() = runBlocking {
        kaset.simpan(entitasBerkas("/sdcard/a.txt"))
        val sumber = lokasi("/sdcard/a.txt")
        val tujuan = lokasi("/sdcard/b.txt")
        val salin = operasi.salin(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
        assertTrue(salin is HasilOperasi.Sukses<*>) { "salin harus sukses: $salin" }
        assertNotNull(kaset.kaset["/sdcard/b.txt"])
        assertNotNull(kaset.kaset["/sdcard/a.txt"]) { "asal tetap ada setelah salin" }

        val pindah = operasi.pindah(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
        assertTrue(pindah is HasilOperasi.Sukses<*>) { "pindah harus sukses: $pindah" }
        assertNull(kaset.kaset["/sdcard/a.txt"]) { "asal hilang setelah pindah" }
        assertNotNull(kaset.kaset["/sdcard/b.txt"])
    }

    @Test
    fun gantiNama() = runBlocking {
        kaset.simpan(entitasBerkas("/sdcard/a.txt"))
        val sumber = lokasi("/sdcard/a.txt")
        val tujuan = lokasi("/sdcard/c.txt")
        val hasil = operasi.gantiNama(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
        assertTrue(hasil is HasilOperasi.Sukses<*>) { "harus sukses: $hasil" }
        assertNull(kaset.kaset["/sdcard/a.txt"])
        assertNotNull(kaset.kaset["/sdcard/c.txt"])
    }
}
