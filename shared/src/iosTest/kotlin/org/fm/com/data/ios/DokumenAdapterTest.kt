package org.fm.com.data.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.fileURLWithPath

class DokumenAdapterTest {

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
        val lokasi = LokasiBerkas("file:///tmp", JenisLokasi.IOS, "tmp")
        val cap = assertNotNull(adapter.kapabilitas(lokasi))

        assertFalse(cap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertFalse(cap.apakahDidukung(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK))
        assertTrue(cap.apakahDidukung(OperasiBerkas.MEMBACA))
        assertTrue(cap.apakahDidukung(OperasiBerkas.MENULIS))
        assertNull(adapter.kapabilitas(LokasiBerkas("/sdcard", JenisLokasi.POSIX)))
    }

    @Test
    fun siklusHidupTulisDanBatalkanIdempoten() = runTest {
        val path = NSTemporaryDirectory() + "tulis_uji_" + NSUUID.UUID().UUIDString + ".txt"
        val url = NSURL.fileURLWithPath(path)
        val penulis = DokumenAliranTulis(url, apakahDokumenBaru = true)

        assertIs<HasilOperasi.Sukses<Unit>>(penulis.tulis(PotonganIsi("halo iOS".encodeToByteArray())))
        assertIs<HasilOperasi.Sukses<Unit>>(penulis.batalkan())
        assertIs<HasilOperasi.Sukses<Unit>>(penulis.batalkan()) // Idempoten

        // Setelah BATAL, menulis lagi harus gagal
        val tulisPascaBatal = assertIs<HasilOperasi.Gagal>(penulis.tulis(PotonganIsi("data".encodeToByteArray())))
        assertEquals(PenyebabGalat.VALIDASI, tulisPascaBatal.penyebab)
    }

    @Test
    fun operasiBerkasMemvalidasiNamaDanKonfirmasi() = runTest {
        val op = DokumenOperasiBerkas()
        val root = LokasiBerkas("file:///tmp", JenisLokasi.IOS, "tmp")

        // Validasi nama kosong / path traversal
        val hasil = assertIs<HasilOperasi.Gagal>(
            op.buatFolder(PermintaanOperasi(sumber = root, namaBaru = "../bahaya"))
        )
        assertEquals(PenyebabGalat.VALIDASI, hasil.penyebab)

        // Hapus tanpa konfirmasi wajib gagal
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

        val lok = LokasiBerkas("file:///tmp", JenisLokasi.IOS, "tmp")
        val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(lok).first())
        assertEquals(1, daftar.nilai.size)
        assertEquals("catatan.txt", daftar.nilai.first().nama)
    }
}