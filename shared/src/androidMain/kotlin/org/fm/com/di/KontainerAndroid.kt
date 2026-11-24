package org.fm.com.di

import android.content.Context
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataAndroid
import org.fm.com.data.persistensi.SqlDelightRepositoriBeranda
import org.fm.com.data.persistensi.SqlDelightRepositoriBookmark
import org.fm.com.data.persistensi.SqlDelightRepositoriPreferensi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
import org.fm.com.data.saf.SafPenyediaLokasi
import org.fm.com.data.saf.SafSumberDataPenyimpanan
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.repository.RepositoriBeranda
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriBookmark
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriPreferensi
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian
import org.fm.com.presentation.filemanager.AksiPlatformBerkasAndroid
import org.koin.dsl.module

private const val KUNCI_PROMPT_IZIN_MEMORI = "android.prompt_izin_memori_selesai"

class KontainerAndroid internal constructor(
    private val appContext: Context,
    val database: DatabaseAplikasi,
    val safPenyediaLokasi: SafPenyediaLokasi,
    val routerBerkas: RepositoriRouterBerkas,
    val repositoriRiwayat: RepositoriRiwayatLokasi,
    val repositoriRiwayatPencarian: RepositoriRiwayatPencarian,
    val lokasiRuangKerja: LokasiBerkas
) {
    val lokasiMemoriInternalSaatIni: LokasiBerkas?
        get() = PengelolaIzinPenyimpanan.buatLokasiMemoriInternalJikaDiizinkan(appContext)

    private suspend fun bacaTreeTersimpan(): List<LokasiBerkas> =
        (safPenyediaLokasi.daftarTreeTersimpan() as? HasilOperasi.Sukses)?.nilai.orEmpty()

    private fun susunPintasan(treeTersimpan: List<LokasiBerkas>): List<LokasiBerkas> = buildList {
        lokasiMemoriInternalSaatIni?.let { add(it) }
        add(lokasiRuangKerja)
        for (tree in treeTersimpan) {
            if (none { it.pengenal == tree.pengenal && it.jenis == tree.jenis }) {
                add(tree)
            }
        }
    }

    suspend fun muatLokasiAwal(): LokasiBerkas = withContext(Dispatchers.IO) {
        lokasiMemoriInternalSaatIni ?: bacaTreeTersimpan().firstOrNull() ?: lokasiRuangKerja
    }

    suspend fun daftarLokasiPintasan(): List<LokasiBerkas> = withContext(Dispatchers.IO) {
        susunPintasan(bacaTreeTersimpan())
    }

    suspend fun muatLokasiAwalDanPintasan(): Pair<LokasiBerkas, List<LokasiBerkas>> =
        withContext(Dispatchers.IO) {
            val treeTersimpan = bacaTreeTersimpan()
            val lokasiAwal = lokasiMemoriInternalSaatIni ?: treeTersimpan.firstOrNull() ?: lokasiRuangKerja
            lokasiAwal to susunPintasan(treeTersimpan)
        }

    suspend fun perluTampilkanPromptIzinMemori(): Boolean = withContext(Dispatchers.IO) {
        if (PengelolaIzinPenyimpanan.sudahPunyaIzinSemuaBerkas()) return@withContext false
        val sudahSelesai = runCatching {
            database.preferensiEntityQueries.ambilNilai(KUNCI_PROMPT_IZIN_MEMORI).executeAsOneOrNull()
        }.getOrNull()
        sudahSelesai != "true"
    }

    @OptIn(ExperimentalTime::class)
    suspend fun tandaiPromptIzinMemoriSelesai() = withContext(Dispatchers.IO) {
        runCatching {
            database.preferensiEntityQueries.simpanNilai(
                kunci = KUNCI_PROMPT_IZIN_MEMORI,
                nilai = "true",
                waktu_diubah = Clock.System.now().toEpochMilliseconds()
            )
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
            single<RepositoriRiwayatPencarian> { repositoriRiwayatPencarian }
            single<RepositoriBookmark> { SqlDelightRepositoriBookmark(database) }
            single<RepositoriBeranda> { SqlDelightRepositoriBeranda(database) }
            single<RepositoriPreferensi> { SqlDelightRepositoriPreferensi(database) }
        }
    )
}

fun buatKontainerAndroid(context: Context): KontainerAndroid {
    val appContext = context.applicationContext
    val driver = PenyediaDriverBasisDataAndroid(appContext).buatDriver()
    val database = buatDatabaseAplikasi(driver)
    val repoRiwayat = SqlDelightRepositoriRiwayatLokasi(database)
    val repoRiwayatPencarian = SqlDelightRepositoriRiwayatPencarian(database)
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
        repositoriRiwayatPencarian = repoRiwayatPencarian,
        lokasiRuangKerja = lokasiRuangKerja
    )
}