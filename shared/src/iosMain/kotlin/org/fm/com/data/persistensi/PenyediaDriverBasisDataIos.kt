package org.fm.com.data.persistensi

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class PenyediaDriverBasisDataIos : PenyediaDriverBasisData {
    override fun buatDriver(namaBasisData: String): SqlDriver =
        NativeSqliteDriver(
            schema = DatabaseAplikasi.Schema,
            name = namaBasisData
        )
}