@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.fm.com.data.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

class DokumenAdapterTest {
    private fun buatDirektoriUji(): NSURL {
        val dirPath = NSTemporaryDirectory() + "fm_ios_uji_" + NSUUID.UUID().UUIDString
        val dirUrl = NSURL.fileURLWithPath(dirPath)
        NSFileManager.defaultManager.createDirectoryAtPath(dirPath, withIntermediateDirectories = true, attributes = null, error = null)
        return dirUrl
    }

    @Test
    fun pemetaanLokasiIosDuaArah() {
        val path = NSTemporaryDirectory() + "test_file.txt"
        val url = NSURL.fileURLWithPath(path)
        val lokasi = DokumenPemetaanLokasi.keLokasi(url, label = "test_file.txt")
        assertEquals(JenisLokasi.IOS, lokasi.jenis)
        assertEquals("test_file.txt", lokasi.label)
        val hasilUrl = assertIs<HasilOperasi.Sukses<NSURL>>(DokumenPemetaanLokasi.keUrl(lokasi))
        assertEquals(url.path, hasilUrl.nilai.path)
    }

    @Test
    fun matriksKapabilitasIosMenonaktifkanPathPosixDanTautanSimbolik() {
        val adapter = IosSumberDataPenyimpanan()
        val lokasi = LokasiBerkas("file:///tmp", JenisLokasi.IOS)
        val cap = assertNotNull(adapter.kapabilitas(lokasi))
        assertFalse(cap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertFalse(cap.apakahDidukung(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK))
        assertTrue(cap.apakahDidukung(OperasiBerkas.MEMBACA))
        assertTrue(cap.apakahDidukung(OperasiBerkas.MENULIS))
        assertEquals(null, adapter.kapabilitas(LokasiBerkas("/sdcard", JenisLokasi.POSIX)))
    }

    @Test
    fun roundTripTulisBacaDanBerkasNolByte() = runTest {
        val rootUrl = buatDirektoriUji()
        try {
            val adapter = IosSumberDataPenyimpanan()
            val rootLok = DokumenPemetaanLokasi.keLokasi(rootUrl)

            val tulisKosong = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(
                adapter.bukaUntukTulisDi(rootLok, "kosong.txt", timpa = false)
            )
            assertIs<HasilOperasi.Sukses<Unit>>(tulisKosong.nilai.selesai())
            val lokKosong = assertNotNull(tulisKosong.nilai.lokasiTarget)

            val bacaKosong = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(adapter.bukaUntukBaca(lokKosong))
            assertEquals(0L, bacaKosong.nilai.ukuranByte)
            val potKosong = assertIs<SumberIsiBerkas.HasilPotongan.Isi>(bacaKosong.nilai.potongan())
            assertTrue(potKosong.potongan.selesai)
            bacaKosong.nilai.tutup()

            val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(
                adapter.bukaUntukTulisDi(rootLok, "catatan.txt", timpa = false)
            )
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.tulis(PotonganIsi("Halo ".encodeToByteArray())))
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.tulis(PotonganIsi("iOS!".encodeToByteArray())))
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.nilai.selesai())

            val lokCatatan = assertNotNull(tulis.nilai.lokasiTarget)
            val baca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(adapter.bukaUntukBaca(lokCatatan))
            var data = ByteArray(0)
            while (true) {
                val p = assertIs<SumberIsiBerkas.HasilPotongan.Isi>(baca.nilai.potongan())
                data += p.potongan.data
                if (p.potongan.selesai) break
            }
            baca.nilai.tutup()
            assertEquals("Halo iOS!", data.decodeToString())
        } finally {
            rootUrl.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        }
    }

    @Test
    fun siklusHidupTulisDanBatalkanIdempoten() = runTest {
        val path = NSTemporaryDirectory() + "tulis_uji_" + NSUUID.UUID().UUIDString + ".txt"
        val url = NSURL.fileURLWithPath(path)
        val penulis = DokumenAliranTulis(url, apakahDokumenBaru = true)
        assertIs<HasilOperasi.Sukses<Unit>>(penulis.tulis(PotonganIsi("halo iOS".encodeToByteArray())))
        assertIs<HasilOperasi.Sukses<Unit>>(penulis.batalkan())
        assertIs<HasilOperasi.Sukses<Unit>>(penulis.batalkan())
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(path))
        val tulisPascaBatal = assertIs<HasilOperasi.Gagal>(penulis.tulis(PotonganIsi("data".encodeToByteArray())))
        assertEquals(PenyebabGalat.VALIDASI, tulisPascaBatal.penyebab)
    }

    @Test
    fun operasiBerkasDanDaftarIsiIosBerjalanNyata() = runTest {
        val rootUrl = buatDirektoriUji()
        try {
            val adapter = IosSumberDataPenyimpanan()
            val rootLok = DokumenPemetaanLokasi.keLokasi(rootUrl)

            val sub = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                adapter.buatFolder(PermintaanOperasi(sumber = rootLok, namaBaru = "Sub"))
            )
            val berkas = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                adapter.buatBerkasKosong(PermintaanOperasi(sumber = sub.nilai.lokasi, namaBaru = "a.txt"))
            )
            val renameKonflik = assertIs<HasilOperasi.Gagal>(
                adapter.buatBerkasKosong(PermintaanOperasi(sumber = sub.nilai.lokasi, namaBaru = "a.txt"))
            )
            assertEquals(PenyebabGalat.KONFLIK, renameKonflik.penyebab)

            val hasilRename = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                adapter.gantiNama(PermintaanOperasi(sumber = berkas.nilai.lokasi, namaBaru = "b.txt"))
            )
            assertEquals("b.txt", hasilRename.nilai.nama)

            val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(adapter.daftarIsi(sub.nilai.lokasi).first())
            assertEquals(listOf("b.txt"), daftar.nilai.map { it.nama })

            val salinDirKeDiri = assertIs<HasilOperasi.Gagal>(
                adapter.salin(PermintaanOperasi(sumber = sub.nilai.lokasi, tujuan = sub.nilai.lokasi))
            )
            assertEquals(PenyebabGalat.VALIDASI, salinDirKeDiri.penyebab)

            assertIs<HasilOperasi.Sukses<Unit>>(
                adapter.hapus(PermintaanOperasi(sumber = sub.nilai.lokasi, konfirmasi = true))
            )
        } finally {
            rootUrl.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        }
    }

    @Test
    fun operasiBerkasMemvalidasiNamaDanKonfirmasi() = runTest {
        val op = DokumenOperasiBerkas()
        val root = LokasiBerkas("file:///tmp", JenisLokasi.IOS)
        val hasil = assertIs<HasilOperasi.Gagal>(
            op.buatFolder(PermintaanOperasi(sumber = root, namaBaru = "../bahaya"))
        )
        assertEquals(PenyebabGalat.VALIDASI, hasil.penyebab)
        val hapusTanpaKonfirmasi = assertIs<HasilOperasi.Gagal>(
            op.hapus(PermintaanOperasi(sumber = root, konfirmasi = false))
        )
        assertEquals(PenyebabGalat.VALIDASI, hapusTanpaKonfirmasi.penyebab)
    }

    @Test
    fun routerMerutekanKeBackendIos() = runTest {
        val contohEntitas = EntitasBerkas(
            lokasi = LokasiBerkas("file:///tmp/catatan.txt", JenisLokasi.IOS, "catatan.txt"),
            nama = "catatan.txt",
            jenis = JenisBerkas.BERKAS_TEKS,
            ukuranByte = 12L
        )
        val adapterIos = IosSumberDataPenyimpanan.untukUji {
            flowOf(HasilOperasi.Sukses(listOf(contohEntitas)))
        }
        val router = RepositoriRouterBerkas(mapOf(JenisLokasi.IOS to adapterIos))
        val lok = LokasiBerkas("file:///tmp", JenisLokasi.IOS)
        val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(lok).first())
        assertEquals(1, daftar.nilai.size)
        assertEquals("catatan.txt", daftar.nilai.first().nama)
    }
}