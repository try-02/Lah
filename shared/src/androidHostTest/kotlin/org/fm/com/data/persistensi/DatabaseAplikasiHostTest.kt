@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.data.persistensi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.domain.model.EntitasRiwayatLokasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class DatabaseAplikasiHostTest {
    @Test
    fun skemaDanOperasiBasisDataHostBekerja() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DatabaseAplikasi.Schema.create(driver)
        val db = DatabaseAplikasi(driver)
        db.bookmarkEntityQueries.tambahBookmark(
            pengenal = "/sdcard/catatan.txt",
            jenis_lokasi = "POSIX",
            label = "Catatan Penting",
            adalah_folder = false,
            waktu_dibuat = 1_700_000_000_000L
        )
        val bookmark = db.bookmarkEntityQueries.cariBerdasarkanPengenal("/sdcard/catatan.txt", "POSIX").executeAsOneOrNull()
        assertNotNull(bookmark)
        assertEquals("Catatan Penting", bookmark.label)
        assertEquals(false, bookmark.adalah_folder)
        db.riwayatLokasiEntityQueries.tambahAtauPerbaruiRiwayat(
            pengenal = "/sdcard/Unduhan",
            jenis_lokasi = "POSIX",
            label = "Unduhan",
            adalah_folder = true,
            waktu_terakhir_dibuka = 1_700_000_100_000L
        )
        val riwayat = db.riwayatLokasiEntityQueries.semuaRiwayat(10L).executeAsList()
        assertEquals(1, riwayat.size)
        assertEquals("/sdcard/Unduhan", riwayat.first().pengenal)
        db.preferensiEntityQueries.simpanNilai("word_wrap", "true", 1_700_000_200_000L)
        val nilai = db.preferensiEntityQueries.ambilNilai("word_wrap").executeAsOneOrNull()
        assertEquals("true", nilai)
        db.bookmarkEntityQueries.hapusBookmark("/sdcard/catatan.txt", "POSIX")
        assertNull(db.bookmarkEntityQueries.cariBerdasarkanPengenal("/sdcard/catatan.txt", "POSIX").executeAsOneOrNull())
        driver.close()
    }

    @Test
    fun sqlDelightRepositoriRiwayatReaktifTerhadapPerubahan() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DatabaseAplikasi.Schema.create(driver)
        val db = DatabaseAplikasi(driver)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = SqlDelightRepositoriRiwayatLokasi(db, ioDispatcher = dispatcher)

        val emisi = mutableListOf<List<EntitasRiwayatLokasi>>()
        val job = backgroundScope.launch(dispatcher) {
            repo.daftarRiwayat(10).take(3).toList(emisi)
        }

        val lok1 = LokasiBerkas("/sdcard/Kerja", JenisLokasi.POSIX, "Kerja")
        repo.catatLokasi(lok1, adalahFolder = true, waktuEpochMs = 1_000L)
        repo.hapusSemua()

        job.join()
        assertEquals(3, emisi.size)
        assertEquals(0, emisi[0].size)
        assertEquals(listOf("/sdcard/Kerja"), emisi[1].map { it.lokasi.pengenal })
        assertEquals(0, emisi[2].size)
        assertEquals(0, repo.daftarRiwayat(10).first().size)
        driver.close()
    }
}