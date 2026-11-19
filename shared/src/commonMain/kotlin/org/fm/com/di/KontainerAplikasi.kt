package org.fm.com.di

import kotlinx.coroutines.runBlocking
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian
import org.fm.com.domain.usecase.RiwayatLokasiUseCase
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.presentation.filemanager.AksiPlatformBerkas
import org.fm.com.presentation.filemanager.AksiPlatformBerkasBawaan
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

data class KontainerAplikasi(
    val repositoriBerkas: RepositoriBerkas,
    val repositoriOperasi: RepositoriOperasiBerkas,
    val repositoriRiwayat: RepositoriRiwayatLokasi,
    val riwayatLokasiUseCase: RiwayatLokasiUseCase,
    val repositoriRiwayatPencarian: RepositoriRiwayatPencarian,
    val riwayatPencarianUseCase: RiwayatPencarianUseCase,
    val lokasiAwal: LokasiBerkas,
    val lokasiPintasan: List<LokasiBerkas> = listOf(lokasiAwal),
    val aksiPlatform: AksiPlatformBerkas = AksiPlatformBerkasBawaan(),
    val apakahPreview: Boolean = false
)

fun buatKontainerAplikasi(
    lokasiAwal: LokasiBerkas,
    lokasiPintasan: List<LokasiBerkas> = listOf(lokasiAwal),
    aksiPlatform: AksiPlatformBerkas = AksiPlatformBerkasBawaan(),
    apakahPreview: Boolean = false,
    modulPlatform: Module
): KontainerAplikasi {
    val koinApp = koinApplication {
        modules(
            modulPlatform,
            module {
                single { RiwayatLokasiUseCase(get()) }
                single { RiwayatPencarianUseCase(get()) }
            }
        )
    }
    val koin = koinApp.koin
    return KontainerAplikasi(
        repositoriBerkas = koin.get(),
        repositoriOperasi = koin.get(),
        repositoriRiwayat = koin.get(),
        riwayatLokasiUseCase = koin.get(),
        repositoriRiwayatPencarian = koin.get(),
        riwayatPencarianUseCase = koin.get(),
        lokasiAwal = lokasiAwal,
        lokasiPintasan = lokasiPintasan,
        aksiPlatform = aksiPlatform,
        apakahPreview = apakahPreview
    )
}

fun buatKontainerPreview(): KontainerAplikasi {
    val storage = SumberMemoriPenyimpanan()
    runBlocking {
        storage.berkas.simpan(DataBerkas("/", "Penyimpanan", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/Dokumen", "Dokumen", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/Dokumen/Catatan.txt", "Catatan.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 45L),
            "Rencana pengembangan File Manager & Code Editor".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/README.md", "README.md", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 120L),
            "# File Manager & Code Editor".encodeToByteArray()
        )
    }
    val root = LokasiBerkas("/", JenisLokasi.POSIX, "Penyimpanan")
    return buatKontainerAplikasi(
        lokasiAwal = root,
        lokasiPintasan = listOf(root),
        apakahPreview = true,
        modulPlatform = module {
            single<RepositoriBerkas> { storage }
            single<RepositoriOperasiBerkas> { storage }
            single<RepositoriRiwayatLokasi> { MemoriRepositoriRiwayatLokasi() }
            single<RepositoriRiwayatPencarian> { MemoriRepositoriRiwayatPencarian() }
        }
    )
}