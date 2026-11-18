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
fun buatDatabaseAplikasi(driver: SqlDriver): DatabaseAplikasi =
    DatabaseAplikasi(driver)