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
import org.fm.com.data.DataBerkas
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.SumberDataPenyimpanan
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.persistensi.MemoriRepositoriBeranda
import org.fm.com.data.persistensi.MemoriRepositoriBookmark
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.StatusKetersediaanBookmark
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.usecase.BerandaUseCase
import org.fm.com.domain.usecase.BookmarkUseCase
import org.fm.com.domain.usecase.HasilResolusiBeranda
import org.fm.com.domain.usecase.SalinPindahkanBerkasUseCase

class SafAdapterTest {

    @Test
    fun pemetaanDokumenSafDanFlagTulisVirtualMematuhiKontrak() {
        // Dokumen virtual tidak pernah boleh ditandai dapat ditulis
        assertFalse(
            SafPemetaanDokumen.hitungDapatDitulis(
                adalahFolder = false,
                flags = DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT
            )
        )
        assertTrue(
            SafPemetaanDokumen.hitungDapatDitulis(
                adalahFolder = true,
                flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            )
        )
        assertFalse(
            SafPemetaanDokumen.hitungDapatDitulis(adalahFolder = true, flags = 0)
        )

        val folderData = SafPemetaanDokumen.keDataBerkas(
            docId = "primary:Proyek",
            uriDokumen = "content://com.android.externalstorage.documents/tree/primary%3AProyek/document/primary%3AProyek",
            namaMentah = ".git",
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            ukuranMentah = 4096L,
            waktuMentah = -1L,
            flags = DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        )
        assertTrue(folderData.adalahFolder)
        assertNull(folderData.ukuranByte)
        assertNull(folderData.waktuUbahEpochMs)
        assertTrue(folderData.tersembunyi)
        assertTrue(folderData.dapatDitulis)

        assertEquals("text/plain", SafOperasiBerkas.tentukanMimeType("Main.kt"))
        assertEquals("application/json", SafOperasiBerkas.tentukanMimeType("app.json"))
        assertEquals("application/zip", SafOperasiBerkas.tentukanMimeType("paket.zip"))
        assertEquals("application/octet-stream", SafOperasiBerkas.tentukanMimeType("tanpa_ekstensi"))
    }

    @Test
    fun kapabilitasSafDanPencabutanIzinDitanganiJujurTanpaAksesOtomatis() = runTest {
        val uriSaf = LokasiBerkas("content://com.android.externalstorage.documents/tree/primary%3ADok", JenisLokasi.SAF, "Dok")
        val uriBawaan = LokasiBerkas("/workspace", JenisLokasi.POSIX, "Workspace")
        var izinSafMasihAda = true

        val safAdapterUji = SafSumberDataPenyimpanan.untukUji { lokasi ->
            if (!izinSafMasihAda) {
                flowOf(HasilOperasi.Gagal("Izin SAF dicabut oleh sistem", PenyebabGalat.IZIN_DITOLAK))
            } else {
                flowOf(
                    HasilOperasi.Sukses(
                        listOf(
                            EntitasBerkas(
                                lokasi = LokasiBerkas("${lokasi.pengenal}/doc/catatan.txt", JenisLokasi.SAF, "catatan.txt"),
                                nama = "catatan.txt",
                                jenis = JenisBerkas.BERKAS_TEKS,
                                ukuranByte = 12L
                            )
                        )
                    )
                )
            }
        }

        val posixMem = SumberMemoriPenyimpanan()
        posixMem.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        posixMem.berkas.simpan(DataBerkas("/workspace", "Workspace", JenisLokasi.POSIX, adalahFolder = true))

        val router = RepositoriRouterBerkas(
            mapOf(
                JenisLokasi.SAF to safAdapterUji,
                JenisLokasi.POSIX to posixMem
            )
        )

        val kapSaf = assertNotNull(router.kapabilitas(uriSaf))
        assertFalse(kapSaf.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertFalse(kapSaf.apakahDidukung(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK))
        assertTrue(kapSaf.apakahDidukung(OperasiBerkas.MEMBOOKMARK))
        assertTrue(kapSaf.apakahDidukung(OperasiBerkas.MENGKOMPRES))

        // Tanpa Context di instance uji, operasi mutasi gagal jujur dengan TIDAK_DIDUKUNG
        val hapusGagal = router.hapus(PermintaanOperasi(sumber = uriSaf, konfirmasi = true))
        assertEquals(PenyebabGalat.TIDAK_DIDUKUNG, assertIs<HasilOperasi.Gagal>(hapusGagal).penyebab)

        val berandaUseCase = BerandaUseCase(MemoriRepositoriBeranda(), router, uriBawaan)
        val bookmarkUseCase = BookmarkUseCase(MemoriRepositoriBookmark(), router)

        assertIs<HasilOperasi.Sukses<LokasiBerkas>>(berandaUseCase.tetapkanBeranda(uriSaf))
        val bmSaf = assertIs<HasilOperasi.Sukses<EntitasBookmark>>(
            bookmarkUseCase.tambahBookmark(uriSaf, JenisBerkas.FOLDER, adalahFolder = true)
        ).nilai

        assertIs<HasilResolusiBeranda.GunakanKhusus>(berandaUseCase.selesaikanLokasiAwal())
        assertIs<StatusKetersediaanBookmark.Tersedia>(bookmarkUseCase.periksaKetersediaanSatu(bmSaf))

        // Simulasikan pencabutan izin SAF: homepage fallback ke bawaan dan bookmark ditandai IzinDicabut
        izinSafMasihAda = false
        val resolusiDicabut = assertIs<HasilResolusiBeranda.IzinAtauLokasiHilang>(
            berandaUseCase.selesaikanLokasiAwal()
        )
        assertEquals(uriSaf, resolusiDicabut.lokasiBermasalah)
        assertEquals(uriBawaan, resolusiDicabut.lokasiDigunakan)
        assertIs<StatusKetersediaanBookmark.IzinDicabut>(bookmarkUseCase.periksaKetersediaanSatu(bmSaf))
    }

    @Test
    fun routerMenolakOperasiLintasLokasiDanUseCaseMelakukanFallbackStreaming() = runTest {
        val sumberPosix = SumberMemoriPenyimpanan()
        sumberPosix.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        sumberPosix.berkas.simpan(
            DataBerkas("/asal.txt", "asal.txt", JenisLokasi.POSIX, adalahFolder = false),
            "DATA_LINTAS_BACKEND".encodeToByteArray()
        )

        val backendSafSimulasi = SumberMemoriPenyimpanan()
        backendSafSimulasi.berkas.simpan(DataBerkas("/saf_root", "SAF", JenisLokasi.SAF, adalahFolder = true))
        val safBackend = object : SumberDataPenyimpanan by backendSafSimulasi {
            override fun kapabilitas(lokasi: LokasiBerkas) = backendSafSimulasi.kapabilitas(lokasi)
            override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
                HasilOperasi.Gagal("FLAG_SUPPORTS_COPY absen", PenyebabGalat.TIDAK_DIDUKUNG)
            override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
                HasilOperasi.Gagal("FLAG_SUPPORTS_MOVE absen", PenyebabGalat.TIDAK_DIDUKUNG)
        }

        val router = RepositoriRouterBerkas(
            mapOf(
                JenisLokasi.POSIX to sumberPosix,
                JenisLokasi.SAF to safBackend
            )
        )

        val asal = LokasiBerkas("/asal.txt", JenisLokasi.POSIX, "asal.txt")
        val tujuanSaf = LokasiBerkas("/saf_root", JenisLokasi.SAF, "SAF")

        // Router menolak operasi lintas lokasi
        val langsungRouter = router.salin(PermintaanOperasi(sumber = asal, tujuan = tujuanSaf))
        assertEquals(PenyebabGalat.TIDAK_DIDUKUNG, assertIs<HasilOperasi.Gagal>(langsungRouter).penyebab)

        // Use case T22 menyelesaikan lintas jenis lokasi via streaming
        val useCaseSalin = SalinPindahkanBerkasUseCase(router, router)
        val hasilMassal = useCaseSalin(listOf(asal), tujuanSaf, apakahPindah = false)
        assertFalse(hasilMassal.adaGagal)
        assertEquals(1, hasilMassal.berhasil.size)
        assertEquals(
            "DATA_LINTAS_BACKEND",
            backendSafSimulasi.berkas.isiDari("/saf_root/asal.txt")?.decodeToString()
        )

        // Juga saat SAF sama-sama SAF namun FLAG_SUPPORTS_COPY absen -> fallback streaming bekerja
        val folderSafLain = LokasiBerkas("/saf_root/sub", JenisLokasi.SAF, "sub")
        backendSafSimulasi.berkas.simpan(DataBerkas("/saf_root/sub", "sub", JenisLokasi.SAF, adalahFolder = true))
        val hasilSesamaSaf = useCaseSalin(
            listOf(hasilMassal.berhasil.single().lokasi),
            folderSafLain,
            apakahPindah = false
        )
        assertFalse(hasilSesamaSaf.adaGagal)
        assertEquals(
            "DATA_LINTAS_BACKEND",
            backendSafSimulasi.berkas.isiDari("/saf_root/sub/asal.txt")?.decodeToString()
        )
    }
}