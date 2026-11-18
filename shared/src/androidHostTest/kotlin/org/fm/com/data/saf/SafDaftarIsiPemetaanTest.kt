package org.fm.com.data.saf

import android.provider.DocumentsContract
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
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi

class SafDaftarIsiPemetaanTest {

    @Test
    fun petakanBerkasTeksDenganMetadataLengkap() {
        val data = SafPemetaanDokumen.keDataBerkas(
            docId = "primary:Dokumen/catatan.kt",
            uriDokumen = "content://com.android.externalstorage.documents/tree/primary%3ADokumen/document/primary%3ADokumen%2Fcatatan.kt",
            namaMentah = "catatan.kt",
            mimeType = "text/x-kotlin",
            ukuranMentah = 512L,
            waktuMentah = 1_700_000_000_000L,
            flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        )
        val domain = data.keDomain()
        assertEquals("catatan.kt", domain.nama)
        assertEquals(JenisBerkas.BERKAS_TEKS, domain.jenis)
        assertEquals(JenisLokasi.SAF, domain.lokasi.jenis)
        assertEquals(512L, domain.ukuranByte)
        assertEquals(1_700_000_000_000L, domain.waktuUbahEpochMs)
        assertFalse(domain.tersembunyi)
        assertTrue(domain.dapatDibaca)
        assertTrue(domain.dapatDitulis)
    }

    @Test
    fun petakanFolderDanFlagCreate() {
        val data = SafPemetaanDokumen.keDataBerkas(
            docId = "primary:Dokumen/Sub",
            uriDokumen = "content://com.android.externalstorage.documents/tree/primary%3ADokumen/document/primary%3ADokumen%2FSub",
            namaMentah = "Sub",
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            ukuranMentah = 4096L,
            waktuMentah = 1000L,
            flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        )
        val domain = data.keDomain()
        assertEquals(JenisBerkas.FOLDER, domain.jenis)
        assertNull(domain.ukuranByte)
        assertTrue(domain.dapatDitulis)
    }

    @Test
    fun dokumenVirtualTidakDapatDitulisDanNilaiNegatifAman() {
        val data = SafPemetaanDokumen.keDataBerkas(
            docId = "doc-virtual-1",
            uriDokumen = "content://contoh.provider/tree/root/document/doc-virtual-1",
            namaMentah = " ",
            mimeType = "application/vnd.google-apps.document",
            ukuranMentah = -1L,
            waktuMentah = -5L,
            flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT
        )
        val domain = data.keDomain()
        assertEquals("doc-virtual-1", domain.nama)
        assertNull(domain.ukuranByte)
        assertNull(domain.waktuUbahEpochMs)
        assertFalse(domain.dapatDitulis)
    }

    @Test
    fun berkasAwalanTitikDitandaiTersembunyi() {
        val data = SafPemetaanDokumen.keDataBerkas(
            docId = "primary:.gitignore",
            uriDokumen = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3A.gitignore",
            namaMentah = ".gitignore",
            mimeType = "application/octet-stream",
            ukuranMentah = 20L,
            waktuMentah = null,
            flags = 0
        )
        val domain = data.keDomain()
        assertTrue(domain.tersembunyi)
        assertFalse(domain.dapatDitulis)
    }

    @Test
    fun sumberSafTerdaftarDiRouterDanJujurUntukOperasiT10() = runTest {
        val lokSaf = LokasiBerkas(
            pengenal = "content://com.android.externalstorage.documents/tree/primary%3ADokumen/document/primary%3ADokumen",
            jenis = JenisLokasi.SAF,
            label = "Dokumen"
        )
        val contohEntitas = EntitasBerkas(
            lokasi = lokSaf.copy(pengenal = "${lokSaf.pengenal}%2Fa.txt", label = "a.txt"),
            nama = "a.txt",
            jenis = JenisBerkas.BERKAS_TEKS,
            ukuranByte = 10L
        )
        val saf = SafSumberDataPenyimpanan.untukUji { flowOf(HasilOperasi.Sukses(listOf(contohEntitas))) }
        val router = RepositoriRouterBerkas(mapOf(JenisLokasi.SAF to saf))

        val cap = assertNotNull(router.kapabilitas(lokSaf))
        assertFalse(cap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertTrue(cap.apakahDidukung(OperasiBerkas.MELIHAT_DAFTAR))
        assertNull(saf.kapabilitas(LokasiBerkas("/sdcard", JenisLokasi.POSIX)))

        val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(lokSaf).first())
        assertEquals(listOf("a.txt"), daftar.nilai.map { it.nama })

        val baca = assertIs<HasilOperasi.Gagal>(router.bukaUntukBaca(lokSaf))
        assertEquals(PenyebabGalat.TIDAK_DIDUKUNG, baca.penyebab)
        val buat = assertIs<HasilOperasi.Gagal>(
            router.buatFolder(PermintaanOperasi(sumber = lokSaf, namaBaru = "baru"))
        )
        assertEquals(PenyebabGalat.TIDAK_DIDUKUNG, buat.penyebab)
    }
}