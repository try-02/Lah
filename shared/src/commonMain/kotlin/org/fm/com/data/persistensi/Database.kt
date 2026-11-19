package org.fm.com.data.persistensi

import app.cash.sqldelight.db.SqlDriver

const val NAMA_BASIS_DATA_BAWAAN = "filemanager.db"

/**
 * Kontrak pembuat [SqlDriver] per platform (Android / iOS / Uji).
 */
interface PenyediaDriverBasisData {
    fun buatDriver(namaBasisData: String = NAMA_BASIS_DATA_BAWAAN): SqlDriver
}

/**
 * Membangun instance [DatabaseAplikasi] dari [SqlDriver].
 */
fun buatDatabaseAplikasi(driver: SqlDriver): DatabaseAplikasi {
    runCatching {
        driver.execute(
            identifier = null,
            sql = """
                CREATE TABLE IF NOT EXISTS riwayatPencarianEntity (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    kueri TEXT NOT NULL,
                    peka_huruf INTEGER NOT NULL DEFAULT 0,
                    kata_utuh INTEGER NOT NULL DEFAULT 0,
                    regex INTEGER NOT NULL DEFAULT 0,
                    waktu_terakhir_dipakai INTEGER NOT NULL,
                    CONSTRAINT unik_kueri_pencarian UNIQUE (kueri)
                )
            """.trimIndent(),
            parameters = 0
        ).value
    }
    return DatabaseAplikasi(driver)
}