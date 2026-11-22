package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterCariNama
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.UtilitasFilterPencarian
import org.fm.com.domain.repository.RepositoriBerkas

class FilterCariNamaTest {

    @Test
    fun normalisasiEkstensiDanKonversiGlobBekerjaBenar() {
        val eks = UtilitasFilterPencarian.normalisasiEkstensi(" .KT , py; .Md  ")
        assertEquals(setOf("kt", "py", "md"), eks)
        assertEquals("gz", UtilitasFilterPencarian.ambilEkstensi("arsip.tar.gz"))
        assertEquals("", UtilitasFilterPencarian.ambilEkstensi(".gitignore"))

        val regexGlob = UtilitasFilterPencarian.globKeRegex("App?.kt", pekaHuruf = true)
        assertTrue(regexGlob.matches("App1.kt"))
        assertTrue(!regexGlob.matches("App12.kt"))
        assertTrue(!regexGlob.matches("app1.kt"))
    }

    @Test
    fun pencarianNamaMendukungModeRekursifDanFilterLengkap() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/src", "src", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas(
                pengenal = "/src/Main.kt",
                nama = "Main.kt",
                jenisLokasi = JenisLokasi.POSIX,
                adalahFolder = false,
                ukuranByte = 500L,
                waktuUbahEpochMs = 2_000L
            )
        )
        storage.berkas.simpan(
            DataBerkas(
                pengenal = "/src/.rahasia.kt",
                nama = ".rahasia.kt",
                jenisLokasi = JenisLokasi.POSIX,
                adalahFolder = false,
                ukuranByte = 300L,
                waktuUbahEpochMs = 3_000L,
                tersembunyi = true
            )
        )
        storage.berkas.simpan(
            DataBerkas(
                pengenal = "/catatan.txt",
                nama = "catatan.txt",
                jenisLokasi = JenisLokasi.POSIX,
                adalahFolder = false,
                ukuranByte = 2_000L,
                waktuUbahEpochMs = 5_000L
            )
        )

        val useCase = CariNamaBerkasUseCase(storage)
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")

        // Non-rekursif: tidak menemukan Main.kt di /src
        val nonRekursif = useCase(
            root,
            FilterCariNama(kueri = "Main", rekursif = false)
        ).last()
        assertNull(nonRekursif.galatFatal)
        assertTrue(nonRekursif.daftarTemuan.isEmpty())

        // Rekursif + ekstensi + rentang ukuran + tanpa berkas tersembunyi
        val hasilKt = useCase(
            root,
            FilterCariNama(
                kueri = "*.kt",
                modeCocok = ModeCocokNama.GLOB,
                rekursif = true,
                sertakanTersembunyi = false,
                ekstensiDiizinkan = setOf("kt"),
                ukuranMinByte = 100L,
                ukuranMaksByte = 1_000L,
                filterJenis = FilterJenisEntitas.BERKAS_SAJA,
                waktuUbahMinEpochMs = 1_500L,
                waktuUbahMaksEpochMs = 4_000L
            )
        ).last()
        assertEquals(listOf("src/Main.kt"), hasilKt.daftarTemuan.map { it.lokasiRelatif })

        // Sertakan tersembunyi
        val denganTersembunyi = useCase(
            root,
            FilterCariNama(
                kueri = "^\\..*\\.kt$",
                modeCocok = ModeCocokNama.REGEX,
                sertakanTersembunyi = true
            )
        ).last()
        assertEquals(listOf("src/.rahasia.kt"), denganTersembunyi.daftarTemuan.map { it.lokasiRelatif })

        // Folder saja
        val folderSaja = useCase(
            root,
            FilterCariNama(kueri = "src", modeCocok = ModeCocokNama.LENGKAP, filterJenis = FilterJenisEntitas.FOLDER_SAJA)
        ).last()
        assertEquals(listOf("src"), folderSaja.daftarTemuan.map { it.lokasiRelatif })
    }

    @Test
    fun filterInvalidDanPeringatanSubfolderDitanganiTanpaMenghentikanPencarian() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/terkunci", "terkunci", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/aman", "aman", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/aman/target.txt", "target.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 10L)
        )

        val repoParsialGagal = object : RepositoriBerkas by storage {
            override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? = storage.kapabilitas(lokasi)
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> {
                return if (lokasi.pengenal == "/terkunci") {
                    flowOf(HasilOperasi.Gagal("Izin ditolak", PenyebabGalat.IZIN_DITOLAK))
                } else {
                    storage.daftarIsi(lokasi)
                }
            }
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
                storage.bukaUntukBaca(lokasi)
            override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
                storage.bukaUntukTulis(lokasi, timpa)
            override suspend fun bukaUntukTulisDi(induk: LokasiBerkas, nama: String, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
                storage.bukaUntukTulisDi(induk, nama, timpa)
        }

        val useCase = CariNamaBerkasUseCase(repoParsialGagal)
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")

        // Subfolder gagal tidak menggagalkan hasil di folder lain
        val hasilParsial = useCase(root, FilterCariNama(kueri = "target")).last()
        assertNull(hasilParsial.galatFatal)
        assertEquals(1, hasilParsial.jumlahPeringatanFolder)
        assertEquals(listOf("aman/target.txt"), hasilParsial.daftarTemuan.map { it.lokasiRelatif })

        // Regex invalid menghasilkan galatFatal jelas
        val regexInvalid = useCase(root, FilterCariNama(kueri = "[abc", modeCocok = ModeCocokNama.REGEX)).last()
        assertNotNull(regexInvalid.galatFatal)

        // Rentang ukuran terbalik menghasilkan galatFatal
        val ukuranTerbalik = useCase(
            root,
            FilterCariNama(ukuranMinByte = 500L, ukuranMaksByte = 100L)
        ).last()
        assertNotNull(ukuranTerbalik.galatFatal)
    }
}