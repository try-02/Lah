package org.fm.com.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataAndroid
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.buatDatabaseAplikasi
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
import org.fm.com.data.saf.SafPenyediaLokasi
import org.fm.com.data.saf.SafSumberDataPenyimpanan
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.presentation.filemanager.AksiPlatformBerkasAndroid
import org.koin.dsl.module

class KontainerAndroid internal constructor(
    private val appContext: Context,
    val database: DatabaseAplikasi,
    val safPenyediaLokasi: SafPenyediaLokasi,
    val routerBerkas: RepositoriRouterBerkas,
    val repositoriRiwayat: RepositoriRiwayatLokasi,
    val lokasiRuangKerja: LokasiBerkas
) {
    suspend fun muatLokasiAwal(): LokasiBerkas = withContext(Dispatchers.IO) {
        val treeTersimpan = (safPenyediaLokasi.daftarTreeTersimpan() as? HasilOperasi.Sukses)?.nilai.orEmpty()
        treeTersimpan.firstOrNull() ?: lokasiRuangKerja
    }

    suspend fun daftarLokasiPintasan(): List<LokasiBerkas> = withContext(Dispatchers.IO) {
        val treeTersimpan = (safPenyediaLokasi.daftarTreeTersimpan() as? HasilOperasi.Sukses)?.nilai.orEmpty()
        buildList {
            add(lokasiRuangKerja)
            for (tree in treeTersimpan) {
                if (none { it.pengenal == tree.pengenal && it.jenis == tree.jenis }) {
                    add(tree)
                }
            }
        }
    }

    fun keKontainerAplikasi(
        lokasiAwal: LokasiBerkas,
        pintasan: List<LokasiBerkas>
    ): KontainerAplikasi = buatKontainerAplikasi(
        lokasiAwal = lokasiAwal,
        lokasiPintasan = pintasan,
        aksiPlatform = AksiPlatformBerkasAndroid(appContext),
        apakahPreview = false,
        modulPlatform = module {
            single<RepositoriBerkas> { routerBerkas }
            single<RepositoriOperasiBerkas> { routerBerkas }
            single<RepositoriRiwayatLokasi> { repositoriRiwayat }
        }
    )
}

fun buatKontainerAndroid(context: Context): KontainerAndroid {
    val appContext = context.applicationContext
    val driver = PenyediaDriverBasisDataAndroid(appContext).buatDriver()
    val database = buatDatabaseAplikasi(driver)
    val repoRiwayat = SqlDelightRepositoriRiwayatLokasi(database)
    val safPenyediaLokasi = SafPenyediaLokasi(appContext)
    val router = RepositoriRouterBerkas(
        mapOf(
            JenisLokasi.POSIX to PosixSumberDataPenyimpanan(
                fileSystem = FileSystem.SYSTEM,
                platform = PlatformPenyimpanan.ANDROID
            ),
            JenisLokasi.SAF to SafSumberDataPenyimpanan(appContext)
        )
    )
    val dirRuangKerja = (appContext.getExternalFilesDir(null) ?: appContext.filesDir).apply {
        if (!exists()) mkdirs()
    }
    val lokasiRuangKerja = LokasiBerkas(
        pengenal = dirRuangKerja.absolutePath,
        jenis = JenisLokasi.POSIX,
        label = "Ruang Kerja App"
    )
    return KontainerAndroid(
        appContext = appContext,
        database = database,
        safPenyediaLokasi = safPenyediaLokasi,
        routerBerkas = router,
        repositoriRiwayat = repoRiwayat,
        lokasiRuangKerja = lokasiRuangKerja
    )
}