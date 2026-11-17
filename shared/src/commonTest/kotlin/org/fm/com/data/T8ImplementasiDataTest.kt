package org.fm.com.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.fm.com.domain.io.SumberIsiBerkas
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
        posenis: String = "/sdcard/Dokumen/berkas.txt",
        jenis: JenisLokasi = JenisLokasi.POSIX
    ): LokasiBerkas =
        LokasiBerkas(pengenal = posenis, jenis = jenis, label = posenis.substringAfterLast("/"))

    private fun entitasBerkas(
        pengenal: String = "/sdcard/Dokumen/berkas.txt",
        isi: String = "halo"
    ): DataBerkas =
        DataBerkas(
            pengenal = pengenal,
            nama = pengenal.substringAfterLast("/"),
            jenisLokasi = JenisLokasi.POSIX,
            adalahFolder = false,
            ukuranByte = isi.length.toLong()
        )

    @Test
    fun simpanDanBacaBerkas() {
        runBlocking<Unit> {
            val e = entitasBerkas()
            kaset.simpan(e)
            val hasilBaca = kaset.bukaUntukBaca(lokasi(e.pengenal))
            val sukses = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(hasilBaca, "harus sukses: $hasilBaca")
            assertEquals(e.ukuranByte, sukses.nilai.ukuranByte)
        }
    }

    @Test
    fun bacaBerkasTidakDitemukan() {
        runBlocking<Unit> {
            val hasil = kaset.bukaUntukBaca(lokasi("/tdak/ada.txt"))
            val gagal = assertIs<HasilOperasi.Gagal>(hasil, "harus gagal: $hasil")
            assertEquals(PenyebabGalat.TIDAK_DITEMUKAN, gagal.penyebab)
        }
    }

    @Test
    fun timpaButuhKonfirmasi() {
        runBlocking<Unit> {
            kaset.simpan(entitasBerkas())
            val lok = lokasi()
            val tanpaKonfirmasi = kaset.bukaUntukTulis(lok, timpa = false)
            val gagal = assertIs<HasilOperasi.Gagal>(tanpaKonfirmasi, "harus gagal: $tanpaKonfirmasi")
            assertEquals(PenyebabGalat.KONFLIK, gagal.penyebab)
            val denganKonfirmasi = kaset.bukaUntukTulis(lok, timpa = true)
            assertIs<HasilOperasi.Sukses<*>>(denganKonfirmasi, "harus sukses: $denganKonfirmasi")
        }
    }

    @Test
    fun daftarIsiFolder() {
        runBlocking<Unit> {
            kaset.simpan(entitasBerkas("/sdcard/Dokumen/a.txt"))
            kaset.simpan(entitasBerkas("/sdcard/Dokumen/b.txt"))
            kaset.simpan(DataBerkas("/sdcard/Dokumen/sub", "sub", JenisLokasi.POSIX, adalahFolder = true))
            kaset.simpan(entitasBerkas("/sdcard/Dokumen/sub/c.txt"))

            val hasilDaftar = kaset.daftarIsi(lokasi("/sdcard/Dokumen", JenisLokasi.POSIX)).first()
            val sukses = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(hasilDaftar, "harus sukses: $hasilDaftar")
            val daftar = sukses.nilai
            assertEquals(3, daftar.size, "hanya anak langsung, tidak rekursif: $daftar")
            assertEquals(listOf("a.txt", "b.txt", "sub"), daftar.map { it.nama })
        }
    }

    @Test
    fun matriksSafTanpaPathPosix() {
        val cap = MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        assertNotNull(cap)
        assertFalse(cap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
    }

    @Test
    fun buatFolderSukses() {
        runBlocking<Unit> {
            val lok = lokasi("/sdcard/Dokumen/baru")
            val hasil = operasi.buatFolder(PermintaanOperasi(sumber = lok, konfirmasi = true))
            assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "harus sukses: $hasil")
        }
    }

    @Test
    fun hapusTanpaKonfirmasiGagal() {
        runBlocking<Unit> {
            val lok = lokasi()
            kaset.simpan(entitasBerkas(lok.pengenal))
            val hasil = operasi.hapus(PermintaanOperasi(sumber = lok, konfirmasi = false))
            val gagal = assertIs<HasilOperasi.Gagal>(hasil, "harus gagal: $hasil")
            assertEquals(PenyebabGalat.VALIDASI, gagal.penyebab)
        }
    }

    @Test
    fun hapusDenganKonfirmasiSukses() {
        runBlocking<Unit> {
            val lok = lokasi()
            kaset.simpan(entitasBerkas(lok.pengenal))
            val hasil = operasi.hapus(PermintaanOperasi(sumber = lok, konfirmasi = true))
            assertIs<HasilOperasi.Sukses<Unit>>(hasil, "harus sukses: $hasil")
            assertNull(kaset.kaset[lok.pengenal])
        }
    }

    @Test
    fun salinDanPindah() {
        runBlocking<Unit> {
            kaset.simpan(entitasBerkas("/sdcard/a.txt"))
            val sumber = lokasi("/sdcard/a.txt")
            val tujuan = lokasi("/sdcard/b.txt")
            val salin = operasi.salin(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
            assertIs<HasilOperasi.Sukses<EntitasBerkas>>(salin, "salin harus sukses: $salin")
            assertNotNull(kaset.kaset["/sdcard/b.txt"])
            assertNotNull(kaset.kaset["/sdcard/a.txt"], "asal tetap ada setelah salin")

            val pindah = operasi.pindah(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
            assertIs<HasilOperasi.Sukses<EntitasBerkas>>(pindah, "pindah harus sukses: $pindah")
            assertNull(kaset.kaset["/sdcard/a.txt"], "asal hilang setelah pindah")
            assertNotNull(kaset.kaset["/sdcard/b.txt"])
        }
    }

    @Test
    fun gantiNama() {
        runBlocking<Unit> {
            kaset.simpan(entitasBerkas("/sdcard/a.txt"))
            val sumber = lokasi("/sdcard/a.txt")
            val tujuan = lokasi("/sdcard/c.txt")
            val hasil = operasi.gantiNama(PermintaanOperasi(sumber = sumber, tujuan = tujuan, konfirmasi = true))
            assertIs<HasilOperasi.Sukses<EntitasBerkas>>(hasil, "harus sukses: $hasil")
            assertNull(kaset.kaset["/sdcard/a.txt"])
            assertNotNull(kaset.kaset["/sdcard/c.txt"])
        }
    }
}