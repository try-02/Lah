package org.fm.com.di

import androidx.compose.runtime.Stable
import kotlinx.coroutines.runBlocking
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.pencarian.PencariIsiBerkas
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatLokasi
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian
import org.fm.com.data.arsip.EkstraktorArsip
import org.fm.com.data.arsip.PembuatArsip
import org.fm.com.data.arsip.ProviderGzip
import org.fm.com.data.arsip.ProviderTar
import org.fm.com.data.arsip.ProviderZip
import org.fm.com.data.arsip.ProviderZipBased
import org.fm.com.data.rename.PenerapkanRenameMassal
import org.fm.com.data.simpan.DeteksiKonflik
import org.fm.com.data.simpan.SimpanTransaksional
import org.fm.com.domain.arsip.RegistriArsipProvider
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.repository.RepositoriRiwayatLokasi
import org.fm.com.domain.repository.RepositoriRiwayatPencarian
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.CariIsiBerkasUseCase
import org.fm.com.domain.usecase.CariNamaBerkasUseCase
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.JalankanRenameMassalUseCase
import org.fm.com.domain.usecase.KompresArsipUseCase
import org.fm.com.domain.usecase.PelaksanaPembuatanArsip
import org.fm.com.domain.usecase.PelaksanaPenjelajahanArsip
import org.fm.com.domain.usecase.PelaksanaRenameMassal
import org.fm.com.domain.usecase.PelaksanaSimpanTransaksional
import org.fm.com.domain.usecase.PemeriksaKonflikSimpan
import org.fm.com.domain.usecase.PemindaiIsiBerkas
import org.fm.com.domain.usecase.RiwayatLokasiUseCase
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.domain.usecase.SimpanBerkasUseCase
import org.fm.com.presentation.filemanager.AksiPlatformBerkas
import org.fm.com.presentation.filemanager.AksiPlatformBerkasBawaan
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@Stable
data class KontainerAplikasi(
    val repositoriBerkas: RepositoriBerkas,
    val repositoriOperasi: RepositoriOperasiBerkas,
    val repositoriRiwayat: RepositoriRiwayatLokasi,
    val riwayatLokasiUseCase: RiwayatLokasiUseCase,
    val repositoriRiwayatPencarian: RepositoriRiwayatPencarian,
    val riwayatPencarianUseCase: RiwayatPencarianUseCase,
    val simpanBerkasUseCase: SimpanBerkasUseCase,
    val cariNamaBerkasUseCase: CariNamaBerkasUseCase,
    val cariIsiBerkasUseCase: CariIsiBerkasUseCase,
    val bandingkanBerkasUseCase: BandingkanBerkasUseCase,
    val jalankanRenameMassalUseCase: JalankanRenameMassalUseCase,
    val registriArsipProvider: RegistriArsipProvider,
    val ekstrakArsipUseCase: EkstrakArsipUseCase,
    val kompresArsipUseCase: KompresArsipUseCase,
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
                single<PemeriksaKonflikSimpan> { DeteksiKonflik(get()) }
                single<PelaksanaSimpanTransaksional> { SimpanTransaksional(get()) }
                single { SimpanBerkasUseCase(get(), get()) }
                single { BandingkanBerkasUseCase(get()) }
                single { CariNamaBerkasUseCase(get()) }
                single<PemindaiIsiBerkas> { PencariIsiBerkas(get()) }
                single { CariIsiBerkasUseCase(get()) }
                single<PelaksanaRenameMassal> { PenerapkanRenameMassal(get()) }
                single { JalankanRenameMassalUseCase(get()) }
                single { ProviderZip(get(), get()) }
                single { ProviderZipBased(get()) }
                single { ProviderTar(get(), get()) }
                single { ProviderGzip(get()) }
                single {
                    RegistriArsipProvider(
                        listOf(
                            get<ProviderZip>(),
                            get<ProviderZipBased>(),
                            get<ProviderTar>(),
                            get<ProviderGzip>()
                        )
                    )
                }
                single<PelaksanaPenjelajahanArsip> { EkstraktorArsip(get()) }
                single<PelaksanaPembuatanArsip> { PembuatArsip(get(), get()) }
                single { EkstrakArsipUseCase(get()) }
                single { KompresArsipUseCase(get()) }
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
        simpanBerkasUseCase = koin.get(),
        cariNamaBerkasUseCase = koin.get(),
        cariIsiBerkasUseCase = koin.get(),
        bandingkanBerkasUseCase = koin.get(),
        jalankanRenameMassalUseCase = koin.get(),
        registriArsipProvider = koin.get(),
        ekstrakArsipUseCase = koin.get(),
        kompresArsipUseCase = koin.get(),
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