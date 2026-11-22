package org.fm.com.data.persistensi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.EntitasRiwayatPencarian
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas

class SqlDelightPersistensiIntegrationTest {

    @Test
    fun sqldelightBookmarkBerandaPreferensiDanRiwayatPencarianPersisten() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DatabaseAplikasi.Schema.create(driver)
            val db = buatDatabaseAplikasi(driver)

            val repoBookmark = SqlDelightRepositoriBookmark(db)
            val repoBeranda = SqlDelightRepositoriBeranda(db)
            val repoPreferensi = SqlDelightRepositoriPreferensi(db)
            val repoRiwayatLokasi = SqlDelightRepositoriRiwayatLokasi(db)
            val repoRiwayatPencarian = SqlDelightRepositoriRiwayatPencarian(db)

            // 1. Bookmark SQLDelight (folder, berkas, arsip + ganti lokasi atomik)
            val lokFolder = LokasiBerkas("/proyek", JenisLokasi.POSIX, "proyek")
            val lokArsip = LokasiBerkas("content://doc/arsip.zip", JenisLokasi.SAF, "arsip.zip")
            assertIs<HasilOperasi.Sukses<Unit>>(
                repoBookmark.simpanBookmark(EntitasBookmark(lokFolder, JenisTargetBookmark.FOLDER, 100L))
            )
            assertIs<HasilOperasi.Sukses<Unit>>(
                repoBookmark.simpanBookmark(EntitasBookmark(lokArsip, JenisTargetBookmark.ARSIP, 200L))
            )
            val daftarBm = repoBookmark.daftarBookmark().first()
            assertEquals(2, daftarBm.size)
            assertEquals(JenisTargetBookmark.ARSIP, daftarBm.first().jenisTarget)

            val lokPengganti = LokasiBerkas("/proyek_baru", JenisLokasi.POSIX, "proyek_baru")
            assertIs<HasilOperasi.Sukses<Unit>>(
                repoBookmark.gantiLokasiBookmark(
                    lokFolder,
                    EntitasBookmark(lokPengganti, JenisTargetBookmark.FOLDER, 300L)
                )
            )
            assertNull(repoBookmark.cariBookmark(lokFolder))
            assertNotNull(repoBookmark.cariBookmark(lokPengganti))

            // 2. Beranda SQLDelight
            assertNull(repoBeranda.ambilBeranda().lokasiKhusus)
            val berandaSaf = LokasiBerkas("content://tree/home", JenisLokasi.SAF, "Home SAF")
            assertIs<HasilOperasi.Sukses<Unit>>(repoBeranda.simpanBeranda(berandaSaf, 400L))
            assertEquals(berandaSaf, repoBeranda.ambilBeranda().lokasiKhusus)
            assertIs<HasilOperasi.Sukses<Unit>>(repoBeranda.resetKeBawaan())
            assertNull(repoBeranda.ambilBeranda().lokasiKhusus)

            // 3. Preferensi Word Wrap SQLDelight
            assertTrue(repoPreferensi.ambilPreferensi().wordWrapUmum)
            assertIs<HasilOperasi.Sukses<Unit>>(repoPreferensi.simpanWordWrapUmum(false, 500L))
            assertFalse(repoPreferensi.amatiPreferensi().first().wordWrapUmum)

            // 4. Riwayat Lokasi & Riwayat Pencarian SQLDelight
            assertIs<HasilOperasi.Sukses<Unit>>(repoRiwayatLokasi.catatLokasi(lokPengganti, true, 600L))
            assertEquals(1, repoRiwayatLokasi.daftarRiwayat().first().size)

            assertIs<HasilOperasi.Sukses<Unit>>(
                repoRiwayatPencarian.catatPencarian(
                    EntitasRiwayatPencarian("TODO", pekaHuruf = true, kataUtuh = false, gunakanRegex = false, 700L)
                )
            )
            val riwayatCari = repoRiwayatPencarian.daftarRiwayat().first()
            assertEquals(1, riwayatCari.size)
            assertEquals("TODO", riwayatCari.single().kueri)
            assertTrue(riwayatCari.single().pekaHuruf)
            assertIs<HasilOperasi.Sukses<Unit>>(repoRiwayatPencarian.hapusSemua())
            assertTrue(repoRiwayatPencarian.daftarRiwayat().first().isEmpty())
        } finally {
            driver.close()
        }
    }
}