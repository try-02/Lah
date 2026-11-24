diff --git a/shared/src/iosTest/kotlin/org/fm/com/data/ios/DokumenAdapterTest.kt b/shared/src/iosTest/kotlin/org/fm/com/data/ios/DokumenAdapterTest.kt
--- a/shared/src/iosTest/kotlin/org/fm/com/data/ios/DokumenAdapterTest.kt
+++ b/shared/src/iosTest/kotlin/org/fm/com/data/ios/DokumenAdapterTest.kt
@@ -1,5 +1,6 @@
 @file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
 
 package org.fm.com.data.ios
 
+import app.cash.sqldelight.driver.native.NativeSqliteDriver
 import kotlin.test.Test
 import kotlin.test.assertEquals
@@ -14,15 +15,24 @@
 import kotlinx.coroutines.flow.first
 import kotlinx.coroutines.test.runTest
 import okio.Buffer
+import okio.FileSystem
+import org.fm.com.data.RepositoriRouterBerkas
+import org.fm.com.data.persistensi.DatabaseAplikasi
 import org.fm.com.data.persistensi.MemoriRepositoriBeranda
 import org.fm.com.data.persistensi.MemoriRepositoriBookmark
+import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
+import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
+import org.fm.com.data.persistensi.buatDatabaseAplikasi
+import org.fm.com.data.posix.PosixSumberDataPenyimpanan
+import org.fm.com.di.KontainerIos
 import org.fm.com.domain.io.PotonganIsi
 import org.fm.com.domain.io.SumberIsiBerkas
 import org.fm.com.domain.model.EntitasBerkas
 import org.fm.com.domain.model.EntitasBookmark
 import org.fm.com.domain.model.HasilOperasi
 import org.fm.com.domain.model.JenisBerkas
 import org.fm.com.domain.model.JenisLokasi
 import org.fm.com.domain.model.LokasiBerkas
 import org.fm.com.domain.model.OperasiBerkas
 import org.fm.com.domain.model.PenyebabGalat
+import org.fm.com.domain.model.PlatformPenyimpanan
 import org.fm.com.domain.model.StatusKetersediaanBookmark
@@ -188,4 +198,87 @@
             fm.removeItemAtURL(rootUrl, null)
         }
     }
+
    @Test
    fun tulisBaruTanpaTimpaMenolakJikaBerkasDibuatPihakLainSebelumSelesai() = runTest {
        val fm = NSFileManager.defaultManager
        val rootPath = NSTemporaryDirectory() + "fm_ios_race_" + NSUUID.UUID().UUIDString
        val rootUrl = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            assertTrue(fm.createDirectoryAtURL(rootUrl, true, null, errPtr.ptr))
        }
        try {
            val adapter = IosSumberDataPenyimpanan()
            val lokasiRoot = DokumenPemetaanLokasi.keLokasi(rootUrl, "Race")
+
            val writerKita = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(lokasiRoot, "balapan.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            assertIs<HasilOperasi.Sukses<Unit>>(writerKita.tulis(PotonganIsi("MILIK_KITA".encodeToByteArray(), true)))
+
            // Pihak lain menulis file bernama sama sebelum sesi kita selesai()
            val writerLain = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(lokasiRoot, "balapan.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            assertIs<HasilOperasi.Sukses<Unit>>(writerLain.tulis(PotonganIsi("MILIK_LAIN".encodeToByteArray(), true)))
            assertIs<HasilOperasi.Sukses<Unit>>(writerLain.selesai())
+
            val hasilKita = assertIs<HasilOperasi.Gagal>(writerKita.selesai())
            assertEquals(PenyebabGalat.KONFLIK, hasilKita.penyebab)
+
            val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(adapter.daftarIsi(lokasiRoot).first()).nilai
            val reader = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(adapter.bukaUntukBaca(daftar.single().lokasi)).nilai
            val buf = Buffer()
            try {
                while (true) {
                    when (val pot = reader.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            if (pot.potongan.data.isNotEmpty()) buf.write(pot.potongan.data)
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> error(pot.pesan)
                    }
                }
            } finally {
                reader.tutup()
            }
            assertEquals("MILIK_LAIN", buf.readByteArray().decodeToString())
        } finally {
            fm.removeItemAtURL(rootUrl, null)
        }
    }
+
    @Test
    fun kontainerIosMenyimpanDanMenyiapkanAksesUntukSubfolderDariBookmarkInduk() = runTest {
        val fm = NSFileManager.defaultManager
        val rootPath = NSTemporaryDirectory() + "fm_ios_bm_" + NSUUID.UUID().UUIDString
        val rootUrl = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        val subUrl = rootUrl.URLByAppendingPathComponent("proyek/sub")!!
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            assertTrue(fm.createDirectoryAtURL(subUrl, true, null, errPtr.ptr))
        }
        val namaDb = "test_kontainer_" + NSUUID.UUID().UUIDString + ".db"
        val driver = NativeSqliteDriver(DatabaseAplikasi.Schema, namaDb)
        try {
            val db = buatDatabaseAplikasi(driver)
            val router = RepositoriRouterBerkas(
                mapOf(
                    JenisLokasi.IOS to IosSumberDataPenyimpanan(),
                    JenisLokasi.POSIX to PosixSumberDataPenyimpanan(FileSystem.SYSTEM, PlatformPenyimpanan.IOS)
                )
            )
            val lokasiRoot = DokumenPemetaanLokasi.keLokasi(rootUrl, "Akar")
            val kontainer = KontainerIos(
                database = db,
                routerBerkas = router,
                repositoriRiwayat = SqlDelightRepositoriRiwayatLokasi(db),
                repositoriRiwayatPencarian = SqlDelightRepositoriRiwayatPencarian(db),
                lokasiDokumenApp = lokasiRoot
            )
            val bmBase64 = (PenyimpanBookmarkKeamanan.buatBookmarkBase64(rootUrl) as? HasilOperasi.Sukses)?.nilai
            kontainer.simpanBookmarkLokasi(lokasiRoot, bmBase64)
+
            val lokasiSub = DokumenPemetaanLokasi.keLokasi(subUrl, "sub")
            val siap = kontainer.siapkanAksesLokasi(lokasiSub)
            assertEquals(lokasiSub, siap)
            assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(siap).first())
            kontainer.lepaskanSemuaAksesKeamanan()
        } finally {
            driver.close()
            fm.removeItemAtURL(rootUrl, null)
        }
    }
 }

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.MemoriRepositoriBeranda
import org.fm.com.data.persistensi.MemoriRepositoriBookmark
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
import org.fm.com.di.KontainerIos
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
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
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

class DokumenAdapterTest {

    @Test
    fun operasiBerkasStreamingDanDeteksiBookmarkHilangBerjalanDiIos() = runTest {
        val fm = NSFileManager.defaultManager
        val rootPath = NSTemporaryDirectory() + "fm_ios_uji_" + NSUUID.UUID().UUIDString
        val rootUrl = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            assertTrue(
                fm.createDirectoryAtURL(rootUrl, withIntermediateDirectories = true, attributes = null, error = errPtr.ptr)
            )
        }

        try {
            val adapter = IosSumberDataPenyimpanan()
            val lokasiRoot = DokumenPemetaanLokasi.keLokasi(rootUrl, label = "AkarUji")
            assertEquals(JenisLokasi.IOS, lokasiRoot.jenis)

            // Verifikasi matriks kapabilitas iOS jujur terhadap batasan sandbox
            val kap = adapter.kapabilitas(lokasiRoot)!!
            assertFalse(kap.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
            assertFalse(kap.apakahDidukung(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK))
            assertTrue(kap.apakahDidukung(OperasiBerkas.MEMBOOKMARK))
            assertTrue(kap.apakahDidukung(OperasiBerkas.MENULIS))

            // Buat folder & berkas via adapter
            val subFolder = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                adapter.buatFolder(PermintaanOperasi(sumber = lokasiRoot, namaBaru = "dokumen"))
            ).nilai
            val subCadangan = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                adapter.buatFolder(PermintaanOperasi(sumber = lokasiRoot, namaBaru = "cadangan"))
            ).nilai

            // Tulis streaming ke berkas baru lalu verifikasi baca ulang
            val writer = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(subFolder.lokasi, "catatan.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            val isiAsli = "Halo Sandbox iOS!".encodeToByteArray()
            assertIs<HasilOperasi.Sukses<Unit>>(writer.tulis(PotonganIsi(isiAsli, selesai = true)))
            assertIs<HasilOperasi.Sukses<Unit>>(writer.selesai())

            // Penulisan tanpa timpa pada nama sama ditolak KONFLIK
            val konflikTulis = adapter.bukaUntukTulisDi(subFolder.lokasi, "catatan.txt", timpa = false)
            assertEquals(PenyebabGalat.KONFLIK, assertIs<HasilOperasi.Gagal>(konflikTulis).penyebab)

            // Batalkan tulis pada berkas baru tidak meninggalkan file
            val writerBatal = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(subFolder.lokasi, "batal.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            assertIs<HasilOperasi.Sukses<Unit>>(writerBatal.tulis(PotonganIsi("sementara".encodeToByteArray(), true)))
            assertIs<HasilOperasi.Sukses<Unit>>(writerBatal.batalkan())

            val isiFolder = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(
                adapter.daftarIsi(subFolder.lokasi).first()
            ).nilai
            assertEquals(listOf("catatan.txt"), isiFolder.map { it.nama })

            val berkasCatatan = isiFolder.single()
            val reader = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(
                adapter.bukaUntukBaca(berkasCatatan.lokasi)
            ).nilai
            val buf = Buffer()
            try {
                while (true) {
                    when (val pot = reader.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val d = pot.potongan.data
                            if (d.isNotEmpty()) buf.write(d)
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> error(pot.pesan)
                    }
                }
            } finally {
                reader.tutup()
            }
            assertEquals("Halo Sandbox iOS!", buf.readByteArray().decodeToString())

            // Uji PenyimpanBookmarkKeamanan (valid & tidak valid)
            assertIs<HasilOperasi.Gagal>(PenyimpanBookmarkKeamanan.selesaikanDanSegarkanBookmark(""))
            assertIs<HasilOperasi.Gagal>(PenyimpanBookmarkKeamanan.selesaikanDanSegarkanBookmark("!!!"))
            val urlCatatan = assertIs<HasilOperasi.Sukses<NSURL>>(DokumenPemetaanLokasi.keUrl(berkasCatatan.lokasi)).nilai
            val bmRes = PenyimpanBookmarkKeamanan.buatBookmarkBase64(urlCatatan)
            if (bmRes is HasilOperasi.Sukses) {
                val resolved = assertIs<HasilOperasi.Sukses<HasilBookmarkTerselesaikan>>(
                    PenyimpanBookmarkKeamanan.selesaikanDanSegarkanBookmark(bmRes.nilai)
                ).nilai
                assertEquals(urlCatatan.path, resolved.url.path)
            }

            // Uji BookmarkUseCase & BerandaUseCase saat folder dihapus dari sandbox iOS
            val bookmarkUseCase = BookmarkUseCase(MemoriRepositoriBookmark(), adapter)
            val berandaUseCase = BerandaUseCase(MemoriRepositoriBeranda(), adapter, lokasiRoot)

            val bmFolder = assertIs<HasilOperasi.Sukses<EntitasBookmark>>(
                bookmarkUseCase.tambahBookmark(subFolder.lokasi, JenisBerkas.FOLDER, adalahFolder = true)
            ).nilai
            assertIs<HasilOperasi.Sukses<LokasiBerkas>>(berandaUseCase.tetapkanBeranda(subFolder.lokasi))
            assertIs<StatusKetersediaanBookmark.Tersedia>(bookmarkUseCase.periksaKetersediaanSatu(bmFolder))

            // Hapus memerlukan konfirmasi = true
            val hapusTanpaKonf = adapter.hapus(PermintaanOperasi(sumber = subFolder.lokasi, konfirmasi = false))
            assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(hapusTanpaKonf).penyebab)

            assertIs<HasilOperasi.Sukses<Unit>>(
                adapter.hapus(PermintaanOperasi(sumber = subFolder.lokasi, konfirmasi = true))
            )

            // Setelah dihapus -> Bookmark ditandai LokasiHilang & Beranda fallback ke root tanpa crash
            assertIs<StatusKetersediaanBookmark.LokasiHilang>(bookmarkUseCase.periksaKetersediaanSatu(bmFolder))
            val resBeranda = assertIs<HasilResolusiBeranda.IzinAtauLokasiHilang>(
                berandaUseCase.selesaikanLokasiAwal()
            )
            assertEquals(lokasiRoot, resBeranda.lokasiDigunakan)

            // Pilih ulang bookmark ke folder cadangan berhasil
            val terganti = assertIs<HasilOperasi.Sukses<EntitasBookmark>>(
                bookmarkUseCase.gantiLokasiBookmark(bmFolder, subCadangan.lokasi, JenisBerkas.FOLDER, true)
            ).nilai
            assertIs<StatusKetersediaanBookmark.Tersedia>(bookmarkUseCase.periksaKetersediaanSatu(terganti))
        } finally {
            fm.removeItemAtURL(rootUrl, null)
        }
    }

    @Test
    fun tulisBaruTanpaTimpaMenolakJikaBerkasDibuatPihakLainSebelumSelesai() = runTest {
        val fm = NSFileManager.defaultManager
        val rootPath = NSTemporaryDirectory() + "fm_ios_race_" + NSUUID.UUID().UUIDString
        val rootUrl = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            assertTrue(fm.createDirectoryAtURL(rootUrl, true, null, errPtr.ptr))
        }
        try {
            val adapter = IosSumberDataPenyimpanan()
            val lokasiRoot = DokumenPemetaanLokasi.keLokasi(rootUrl, "Race")

            val writerKita = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(lokasiRoot, "balapan.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            assertIs<HasilOperasi.Sukses<Unit>>(writerKita.tulis(PotonganIsi("MILIK_KITA".encodeToByteArray(), true)))

            // Pihak lain menulis file bernama sama sebelum sesi kita selesai()
            val writerLain = assertIs<HasilOperasi.Sukses<*>>(
                adapter.bukaUntukTulisDi(lokasiRoot, "balapan.txt", timpa = false)
            ).nilai as org.fm.com.domain.io.SumberTulisBerkas
            assertIs<HasilOperasi.Sukses<Unit>>(writerLain.tulis(PotonganIsi("MILIK_LAIN".encodeToByteArray(), true)))
            assertIs<HasilOperasi.Sukses<Unit>>(writerLain.selesai())

            val hasilKita = assertIs<HasilOperasi.Gagal>(writerKita.selesai())
            assertEquals(PenyebabGalat.KONFLIK, hasilKita.penyebab)

            val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(adapter.daftarIsi(lokasiRoot).first()).nilai
            val reader = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(adapter.bukaUntukBaca(daftar.single().lokasi)).nilai
            val buf = Buffer()
            try {
                while (true) {
                    when (val pot = reader.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            if (pot.potongan.data.isNotEmpty()) buf.write(pot.potongan.data)
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> error(pot.pesan)
                    }
                }
            } finally {
                reader.tutup()
            }
            assertEquals("MILIK_LAIN", buf.readByteArray().decodeToString())
        } finally {
            fm.removeItemAtURL(rootUrl, null)
        }
    }

    @Test
    fun kontainerIosMenyimpanDanMenyiapkanAksesUntukSubfolderDariBookmarkInduk() = runTest {
        val fm = NSFileManager.defaultManager
        val rootPath = NSTemporaryDirectory() + "fm_ios_bm_" + NSUUID.UUID().UUIDString
        val rootUrl = NSURL.fileURLWithPath(rootPath, isDirectory = true)
        val subUrl = rootUrl.URLByAppendingPathComponent("proyek/sub")!!
        memScoped {
            val errPtr = alloc<ObjCObjectVar<NSError?>>()
            assertTrue(fm.createDirectoryAtURL(subUrl, true, null, errPtr.ptr))
        }
        val namaDb = "test_kontainer_" + NSUUID.UUID().UUIDString + ".db"
        val driver = NativeSqliteDriver(DatabaseAplikasi.Schema, namaDb)
        try {
            val db = buatDatabaseAplikasi(driver)
            val router = RepositoriRouterBerkas(
                mapOf(
                    JenisLokasi.IOS to IosSumberDataPenyimpanan(),
                    JenisLokasi.POSIX to PosixSumberDataPenyimpanan(FileSystem.SYSTEM, PlatformPenyimpanan.IOS)
                )
            )
            val lokasiRoot = DokumenPemetaanLokasi.keLokasi(rootUrl, "Akar")
            val kontainer = KontainerIos(
                database = db,
                routerBerkas = router,
                repositoriRiwayat = SqlDelightRepositoriRiwayatLokasi(db),
                repositoriRiwayatPencarian = SqlDelightRepositoriRiwayatPencarian(db),
                lokasiDokumenApp = lokasiRoot
            )
            val bmBase64 = (PenyimpanBookmarkKeamanan.buatBookmarkBase64(rootUrl) as? HasilOperasi.Sukses)?.nilai
            kontainer.simpanBookmarkLokasi(lokasiRoot, bmBase64)

            val lokasiSub = DokumenPemetaanLokasi.keLokasi(subUrl, "sub")
            val siap = kontainer.siapkanAksesLokasi(lokasiSub)
            assertEquals(lokasiSub, siap)
            assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(router.daftarIsi(siap).first())
            kontainer.lepaskanSemuaAksesKeamanan()
        } finally {
            driver.close()
            fm.removeItemAtURL(rootUrl, null)
        }
    }
}