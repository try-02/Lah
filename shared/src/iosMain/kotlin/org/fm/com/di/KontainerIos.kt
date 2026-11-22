@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package org.fm.com.di

import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.ios.DokumenPemetaanLokasi
import org.fm.com.data.ios.IosSumberDataPenyimpanan
import org.fm.com.data.ios.PenyimpanBookmarkKeamanan
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.PenyediaDriverBasisDataIos
import org.fm.com.data.persistensi.SqlDelightRepositoriBeranda
import org.fm.com.data.persistensi.SqlDelightRepositoriBookmark
import org.fm.com.data.persistensi.SqlDelightRepositoriPreferensi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.SqlDelightRepositoriRiwayatPencarian
import org.fm.com.data.persistensi.buatDatabaseAplikasi
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
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
import org.fm.com.presentation.filemanager.AksiPlatformBerkasIos
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private const val PREFIX_KUNCI_BOOKMARK = "ios_bm:"

class KontainerIos internal constructor(
    val database: DatabaseAplikasi,
    val routerBerkas: RepositoriRouterBerkas,
    val repositoriRiwayat: RepositoriRiwayatLokasi,
    val repositoriRiwayatPencarian: RepositoriRiwayatPencarian,
    val lokasiDokumenApp: LokasiBerkas
) {
    suspend fun simpanBookmarkLokasi(lokasi: LokasiBerkas, bookmarkBase64: String?) = withContext(Dispatchers.IO) {
        if (bookmarkBase64.isNullOrBlank()) return@withContext
        val kunci = "$PREFIX_KUNCI_BOOKMARK${lokasi.pengenal}"
        runCatching {
            database.preferensiEntityQueries.simpanNilai(
                kunci = kunci,
                nilai = bookmarkBase64,
                waktu_diubah = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    suspend fun siapkanAksesLokasi(lokasi: LokasiBerkas): LokasiBerkas = withContext(Dispatchers.IO) {
        if (lokasi.jenis != JenisLokasi.IOS) return@withContext lokasi
        val kunci = "$PREFIX_KUNCI_BOOKMARK${lokasi.pengenal}"
        val bookmarkBase64 = runCatching {
            database.preferensiEntityQueries.ambilNilai(kunci).executeAsOneOrNull()
        }.getOrNull() ?: return@withContext lokasi

        when (val hasil = PenyimpanBookmarkKeamanan.selesaikanDanSegarkanBookmark(bookmarkBase64)) {
            is HasilOperasi.Sukses -> {
                val url = hasil.nilai.url
                url.startAccessingSecurityScopedResource()
                val lokasiBaru = DokumenPemetaanLokasi.keLokasi(url, lokasi.label)
                hasil.nilai.bookmarkBaruBase64?.let { bmBaru ->
                    simpanBookmarkLokasi(lokasiBaru, bmBaru)
                }
                lokasiBaru
            }
            is HasilOperasi.Gagal -> lokasi
        }
    }

    fun keKontainerAplikasi(): KontainerAplikasi = buatKontainerAplikasi(
        lokasiAwal = lokasiDokumenApp,
        lokasiPintasan = listOf(lokasiDokumenApp),
        aksiPlatform = AksiPlatformBerkasIos(),
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

fun buatKontainerIos(): KontainerIos {
    val driver = PenyediaDriverBasisDataIos().buatDriver()
    val database = buatDatabaseAplikasi(driver)
    val repoRiwayat = SqlDelightRepositoriRiwayatLokasi(database)
    val repoRiwayatPencarian = SqlDelightRepositoriRiwayatPencarian(database)
    val router = RepositoriRouterBerkas(
        mapOf(
            JenisLokasi.IOS to IosSumberDataPenyimpanan(),
            JenisLokasi.POSIX to PosixSumberDataPenyimpanan(
                fileSystem = FileSystem.SYSTEM,
                platform = PlatformPenyimpanan.IOS
            )
        )
    )
    val docUrl = (NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        .firstOrNull() as? NSURL)
    val lokasiDokumen = if (docUrl != null) {
        DokumenPemetaanLokasi.keLokasi(docUrl, label = "Dokumen App")
    } else {
        LokasiBerkas("/tmp", JenisLokasi.POSIX, "Sementara")
    }
    return KontainerIos(
        database = database,
        routerBerkas = router,
        repositoriRiwayat = repoRiwayat,
        repositoriRiwayatPencarian = repoRiwayatPencarian,
        lokasiDokumenApp = lokasiDokumen
    )
}