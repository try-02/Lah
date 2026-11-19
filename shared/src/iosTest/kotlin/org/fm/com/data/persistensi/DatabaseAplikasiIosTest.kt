package org.fm.com.data.persistensi

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import platform.Foundation.NSUUID

class DatabaseAplikasiIosTest {

    @Test
    fun skemaDanOperasiBasisDataIosBekerja() {
        val namaDb = "test_" + NSUUID.UUID().UUIDString + ".db"
        val driver = NativeSqliteDriver(DatabaseAplikasi.Schema, namaDb)
        val db = DatabaseAplikasi(driver)

        // 1. Uji Bookmark
        db.bookmarkEntityQueries.tambahBookmark(
            pengenal = "file:///private/var/mobile/catatan.md",
            jenis_lokasi = "IOS",
            label = "Catatan",
            adalah_folder = false,
            waktu_dibuat = 1_700_000_000L
        )
        val bookmark = db.bookmarkEntityQueries.cariBerdasarkanPengenal("file:///private/var/mobile/catatan.md", "IOS").executeAsOneOrNull()
        assertNotNull(bookmark)
        assertEquals("Catatan", bookmark.label)

        // 2. Uji Riwayat Lokasi
        db.riwayatLokasiEntityQueries.tambahAtauPerbaruiRiwayat(
            pengenal = "file:///private/var/mobile/Dokumen",
            jenis_lokasi = "IOS",
            label = "Dokumen",
            adalah_folder = true,
            waktu_terakhir_dibuka = 1_700_000_100L
        )
        val riwayat = db.riwayatLokasiEntityQueries.semuaRiwayat(5L).executeAsList()
        assertEquals(1, riwayat.size)

        // 3. Uji Preferensi
        db.preferensiEntityQueries.simpanNilai("beranda", "file:///private/var/mobile/Dokumen", 1_700_000_200L)
        val nilai = db.preferensiEntityQueries.ambilNilai("beranda").executeAsOneOrNull()
        assertEquals("file:///private/var/mobile/Dokumen", nilai)
        
        // 4. Uji riwayatPencarianEntityQueries
        db.riwayatPencarianEntityQueries.tambahAtauPerbaruiPencarian(
            kueri = "TODO",
            peka_huruf = true,
            kata_utuh = true,
            regex = false,
            waktu_terakhir_dipakai = 1_700_000_300L
        )
        val riwayatCari = db.riwayatPencarianEntityQueries.semuaRiwayatPencarian(10L).executeAsList()
        assertEquals(1, riwayatCari.size)
        assertEquals("TODO", riwayatCari.first().kueri)
        db.riwayatPencarianEntityQueries.hapusRiwayatPencarian("TODO")
        assertEquals(0, db.riwayatPencarianEntityQueries.semuaRiwayatPencarian(10L).executeAsList().size)

        // 5. Uji Hapus
        db.preferensiEntityQueries.hapusKunci("beranda")
        assertNull(db.preferensiEntityQueries.ambilNilai("beranda").executeAsOneOrNull())

        driver.close()
    }
}