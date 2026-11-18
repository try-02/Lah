package org.fm.com.data.persistensi

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class PenyediaDriverBasisDataAndroid(
    private val context: Context
) : PenyediaDriverBasisData {
    override fun buatDriver(namaBasisData: String): SqlDriver =
        AndroidSqliteDriver(
            schema = DatabaseAplikasi.Schema,
            context = context.applicationContext,
            name = namaBasisData
        )
}