@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterCariNama
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.UtilitasFilterPencarian
import org.fm.com.domain.repository.RepositoriBerkas

class CariNamaBerkasUseCaseTest {

    private suspend fun siapkanPohon(repo: ImplementasiRepositoriBerkas) {
        repo.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/akar/src", "src", JenisLokasi.POSIX, adalahFolder = true, waktuUbahEpochMs = 2000L))
        repo.simpan(DataBerkas("/akar/src/Main.kt", "Main.kt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 500L, waktuUbahEpochMs = 5000L))
        repo.simpan(DataBerkas("/akar/src/Util.kt", "Util.kt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 2500L, waktuUbahEpochMs = 1000L))
        repo.simpan(DataBerkas("/akar/README.md", "README.md", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 150L, waktuUbahEpochMs = 4000L))
        repo.simpan(DataBerkas("/akar/.rahasia.txt", ".rahasia.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 50L, tersembunyi = true))
    }

    @Test
    fun modeFolderSaatIniVsRekursifDanFilterEkstensiUkuranTanggal() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        siapkanPohon(repo)
        val uc = CariNamaBerkasUseCase(repo, UnconfinedTestDispatcher(testScheduler))
        val akar = LokasiBerkas("/akar", JenisLokasi.POSIX, "akar")

        // Folder saat ini saja
        val nonRekursif = uc(akar, FilterCariNama(rekursif = false)).last()
        assertEquals(listOf("README.md", "src"), nonRekursif.daftarTemuan.map { it.lokasiRelatif })

        // Rekursif + ekstensi kt + ukuran 300..1000 + tanggal >= 3000
        val tersaring = uc(
            akar,
            FilterCariNama(
                rekursif = true,
                ekstensiDiizinkan = UtilitasFilterPencarian.normalisasiEkstensi(".kt, md"),
                ukuranMinByte = 300L,
                ukuranMaksByte = 1000L,
                waktuUbahMinEpochMs = 3000L
            )
        ).last()
        assertNull(tersaring.galatFatal)
        assertEquals(listOf("src/Main.kt"), tersaring.daftarTemuan.map { it.lokasiRelatif })

        // Hanya folder
        val hanyaFolder = uc(akar, FilterCariNama(filterJenis = FilterJenisEntitas.FOLDER_SAJA)).last()
        assertEquals(listOf("src"), hanyaFolder.daftarTemuan.map { it.lokasiRelatif })
    }

    @Test
    fun modeGlobDanRegexSertaPesanPolaInvalid() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        siapkanPohon(repo)
        val uc = CariNamaBerkasUseCase(repo, UnconfinedTestDispatcher(testScheduler))
        val akar = LokasiBerkas("/akar", JenisLokasi.POSIX, "akar")

        val glob = uc(akar, FilterCariNama(kueri = "*.kt", modeCocok = ModeCocokNama.GLOB)).last()
        assertEquals(listOf("src/Main.kt", "src/Util.kt"), glob.daftarTemuan.map { it.lokasiRelatif })

        val regex = uc(akar, FilterCariNama(kueri = "^[A-Z]+\\.md$", modeCocok = ModeCocokNama.REGEX)).last()
        assertEquals(listOf("README.md"), regex.daftarTemuan.map { it.lokasiRelatif })

        val invalid = uc(akar, FilterCariNama(kueri = "[rusak", modeCocok = ModeCocokNama.REGEX)).last()
        assertNotNull(invalid.galatFatal)
        assertTrue(invalid.selesai)
    }

    @Test
    fun errorSubfolderTidakMenghentikanPencarianRekursif() = runTest {
        val repoAsli = ImplementasiRepositoriBerkas()
        repoAsli.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        repoAsli.simpan(DataBerkas("/akar/terkunci", "terkunci", JenisLokasi.POSIX, adalahFolder = true))
        repoAsli.simpan(DataBerkas("/akar/terbuka", "terbuka", JenisLokasi.POSIX, adalahFolder = true))
        repoAsli.simpan(DataBerkas("/akar/terbuka/lolos.txt", "lolos.txt", JenisLokasi.POSIX, adalahFolder = false))

        val repoDenganSubfolderError = object : RepositoriBerkas by repoAsli {
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> {
                return if (lokasi.pengenal == "/akar/terkunci") {
                    flowOf(HasilOperasi.Gagal("Izin folder ditolak", PenyebabGalat.IZIN_DITOLAK))
                } else {
                    repoAsli.daftarIsi(lokasi)
                }
            }
        }

        val uc = CariNamaBerkasUseCase(repoDenganSubfolderError, UnconfinedTestDispatcher(testScheduler))
        val hasil = uc(LokasiBerkas("/akar", JenisLokasi.POSIX), FilterCariNama(kueri = "lolos")).last()
        assertNull(hasil.galatFatal)
        assertEquals(1, hasil.jumlahPeringatanFolder)
        assertEquals(listOf("terbuka/lolos.txt"), hasil.daftarTemuan.map { it.lokasiRelatif })
    }
}