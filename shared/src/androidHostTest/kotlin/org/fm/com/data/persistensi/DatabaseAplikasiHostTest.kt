package org.fm.com.data.persistensi

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseAplikasiHostTest {

    @Test
    fun skemaDanOperasiBasisDataHostBekerja() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DatabaseAplikasi.Schema.create(driver)
        val db = DatabaseAplikasi(driver)

        // 1. Uji Bookmark
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

        // 2. Uji Riwayat Lokasi
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

        // 3. Uji Preferensi
        db.preferensiEntityQueries.simpanNilai("word_wrap", "true", 1_700_000_200_000L)
        val nilai = db.preferensiEntityQueries.ambilNilai("word_wrap").executeAsOneOrNull()
        assertEquals("true", nilai)

        // 4. Uji Hapus
        db.bookmarkEntityQueries.hapusBookmark("/sdcard/catatan.txt", "POSIX")
        assertNull(db.bookmarkEntityQueries.cariBerdasarkanPengenal("/sdcard/catatan.txt", "POSIX").executeAsOneOrNull())

        driver.close()
    }
}