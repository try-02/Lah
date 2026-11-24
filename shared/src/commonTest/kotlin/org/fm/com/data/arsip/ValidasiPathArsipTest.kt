package org.fm.com.data.arsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.buffer
import okio.use
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.arsip.MatriksArsip
import org.fm.com.domain.arsip.RegistriArsipProvider
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.KebijakanKonflikEkstrak
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat

class ValidasiPathArsipTest {

    @Test
    fun validasiJalurMenolakZipSlipDanMenormalisasiJalurSah() {
        val sah = assertIs<HasilValidasiPathArsip.Sah>(ValidasiPathArsip.validasi("./src\\main/App.kt"))
        assertEquals("src/main/App.kt", sah.jalurNormal)
        assertEquals(listOf("src", "main", "App.kt"), sah.daftarSegmen)
        assertEquals("App.kt", sah.namaSegmenAkhir)

        assertIs<HasilValidasiPathArsip.AbaikanRoot>(ValidasiPathArsip.validasi("./", adalahFolder = true))
        assertIs<HasilValidasiPathArsip.AbaikanRoot>(ValidasiPathArsip.validasi(".", adalahFolder = true))

        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("../rahasia.txt"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("sub/../../rahasia.txt"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("/etc/passwd"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("\\Windows\\System32"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("C:\\Windows\\win.ini"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("a/\u0000b.txt"))
        assertIs<HasilValidasiPathArsip.TidakSah>(ValidasiPathArsip.validasi("a//b.txt"))
        assertIs<HasilValidasiPathArsip.TidakSah>(
            ValidasiPathArsip.validasi("a/b/c/d.txt", batasSegmenMaks = 3)
        )
    }

    @Test
    fun matriksKapabilitasDanDeteksiFormatP0Konsisten() {
        assertEquals(FormatArsip.ZIP, FormatArsip.dariNamaBerkas("proyek.zip"))
        assertEquals(FormatArsip.TGZ, FormatArsip.dariNamaBerkas("backup.tar.gz"))
        assertEquals(FormatArsip.TGZ, FormatArsip.dariNamaBerkas("backup.tgz"))
        assertEquals(FormatArsip.GZIP, FormatArsip.dariNamaBerkas("log.txt.gz"))
        assertEquals(FormatArsip.TAR, FormatArsip.dariNamaBerkas("kode.tar"))
        assertEquals(FormatArsip.JAR, FormatArsip.dariNamaBerkas("library.jar"))
        assertEquals(FormatArsip.ZIP_LAIN, FormatArsip.dariNamaBerkas("app.apk"))
        assertEquals(FormatArsip.ZIP_LAIN, FormatArsip.dariNamaBerkas("dokumen.docx"))

        val zipKap = MatriksArsip.untuk(FormatArsip.ZIP)
        assertTrue(zipKap.dapatBaca && zipKap.dapatEkstrak && zipKap.dapatBuat)
        assertFalse(zipKap.dapatUbahIsi)
        assertFalse(zipKap.dapatEnkripsi)

        val zipLainKap = MatriksArsip.untuk(FormatArsip.ZIP_LAIN)
        assertTrue(zipLainKap.dapatBaca && zipLainKap.dapatEkstrak)
        assertFalse(zipLainKap.dapatBuat)

        val gzipKap = MatriksArsip.untuk(FormatArsip.GZIP)
        assertFalse(gzipKap.mendukungBanyakEntri)
    }

    @Test
    fun zipRoundTripKonflikZipSlipDanArsipRusakDitanganiAman() = runTest {
        val storage = siapkanPenyimpananContoh()
        val adapter = buatAdapterArsipPlatform()
        val providerZip = ProviderZip(storage, storage, adapter)
        val providerZipBased = ProviderZipBased(providerZip)
        val registri = RegistriArsipProvider(listOf(providerZip, providerZipBased))

        assertNotNull(registri.providerUntuk(FormatArsip.ZIP))
        assertNotNull(registri.providerUntuk(FormatArsip.JAR))
        assertNotNull(registri.providerUntuk(FormatArsip.ZIP_LAIN))

        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")
        val hasilBuat = providerZip.buatArsip(
            daftarSumber = listOf(
                ItemSumberKompresi(LokasiBerkas("/src", JenisLokasi.POSIX, "src"), "src", adalahFolder = true),
                ItemSumberKompresi(LokasiBerkas("/catatan.txt", JenisLokasi.POSIX, "catatan.txt"), "catatan.txt", adalahFolder = false)
            ),
            tujuanInduk = root,
            namaArsip = "paket.zip",
            format = FormatArsip.ZIP
        )
        val entitasZip = assertIs<HasilOperasi.Sukses<*>>(hasilBuat).nilai as org.fm.com.domain.model.EntitasBerkas

        val hasilDaftar = assertIs<HasilOperasi.Sukses<*>>(
            providerZip.daftarEntri(entitasZip.lokasi, FormatArsip.ZIP)
        ).nilai as org.fm.com.domain.model.RingkasanIsiArsip
        assertTrue(hasilDaftar.daftarEntri.any { it.jalurInternal == "catatan.txt" })
        assertTrue(hasilDaftar.daftarEntri.any { it.jalurInternal == "src/Main.kt" })

        // Baca satu entri secara streaming
        val streamBaca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(
            providerZip.bukaEntriUntukBaca(entitasZip.lokasi, FormatArsip.ZIP, "src/Main.kt")
        ).nilai
        val isiTerbaca = bacaSemuaDariSumber(streamBaca)
        assertEquals("fun main() = println(\"Halo\")", isiTerbaca.decodeToString())

        // Ekstraksi tanpa persetujuan ditolak
        val tujuanEkstrak = LokasiBerkas("/keluar", JenisLokasi.POSIX, "keluar")
        val tanpaKonfirmasi = providerZip.ekstrak(
            entitasZip.lokasi,
            FormatArsip.ZIP,
            tujuanEkstrak,
            OpsiEkstrakArsip(konfirmasiPengguna = false)
        )
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(tanpaKonfirmasi).penyebab)

        // Ekstraksi selektif dengan persetujuan berhasil
        val hasilEkstrak = assertIs<HasilOperasi.Sukses<*>>(
            providerZip.ekstrak(
                entitasZip.lokasi,
                FormatArsip.ZIP,
                tujuanEkstrak,
                OpsiEkstrakArsip(
                    jalurTerpilih = setOf("src"),
                    konfirmasiPengguna = true
                )
            )
        ).nilai as org.fm.com.domain.model.HasilEkstrakArsip
        assertEquals(1, hasilEkstrak.jumlahDiekstrak)
        assertEquals(
            "fun main() = println(\"Halo\")",
            storage.berkas.isiDari("/keluar/src/Main.kt")?.decodeToString()
        )

        // Cek konflik TOLAK_KONFLIK saat ekstraksi ulang
        val konflik = providerZip.ekstrak(
            entitasZip.lokasi,
            FormatArsip.ZIP,
            tujuanEkstrak,
            OpsiEkstrakArsip(
                jalurTerpilih = setOf("src"),
                kebijakanKonflik = KebijakanKonflikEkstrak.TOLAK_KONFLIK,
                konfirmasiPengguna = true
            )
        )
        assertEquals(PenyebabGalat.KONFLIK, assertIs<HasilOperasi.Gagal>(konflik).penyebab)

        // Cek arsip rusak tidak crash
        storage.berkas.simpan(
            DataBerkas("/rusak.zip", "rusak.zip", JenisLokasi.POSIX, adalahFolder = false),
            "INI_BUKAN_ZIP_VALID".encodeToByteArray()
        )
        assertIs<HasilOperasi.Gagal>(
            providerZip.daftarEntri(LokasiBerkas("/rusak.zip", JenisLokasi.POSIX, "rusak.zip"), FormatArsip.ZIP)
        )

        // Cek penolakan pembuatan format ZIP_LAIN
        val tolakBuatApk = providerZipBased.buatArsip(
            daftarSumber = listOf(ItemSumberKompresi(LokasiBerkas("/catatan.txt", JenisLokasi.POSIX, "catatan.txt"), "catatan.txt", false)),
            tujuanInduk = root,
            namaArsip = "app.apk",
            format = FormatArsip.ZIP_LAIN
        )
        assertEquals(PenyebabGalat.TIDAK_DIDUKUNG, assertIs<HasilOperasi.Gagal>(tolakBuatApk).penyebab)
    }

    @Test
    fun zipDenganJalurTraversalDitolakTanpaMenulisKeDisk() = runTest {
        val storage = siapkanPenyimpananContoh()
        val adapter = buatAdapterArsipPlatform()
        val providerZip = ProviderZip(storage, storage, adapter)

        val pathJahat = BantuArsipPenyimpanan.buatPathCacheSementara("uji_zipslip", ".zip")
        try {
            val penulis = adapter.bukaPenulisZip(FileSystem.SYSTEM, pathJahat)
            penulis.mulaiEntri("../../jahat.txt", adalahFolder = false, waktuUbahEpochMs = null)
            val payload = "JAHAT".encodeToByteArray()
            penulis.tulisPotongan(payload, 0, payload.size)
            penulis.tutupEntri()
            penulis.selesaikanDanTutup()

            val bytesZipJahat = FileSystem.SYSTEM.source(pathJahat).buffer().use { it.readByteArray() }
            storage.berkas.simpan(
                DataBerkas("/jahat.zip", "jahat.zip", JenisLokasi.POSIX, adalahFolder = false),
                bytesZipJahat
            )

            val lokasiJahat = LokasiBerkas("/jahat.zip", JenisLokasi.POSIX, "jahat.zip")
            assertEquals(
                PenyebabGalat.VALIDASI,
                assertIs<HasilOperasi.Gagal>(providerZip.daftarEntri(lokasiJahat, FormatArsip.ZIP)).penyebab
            )
            val hasilEkstrak = providerZip.ekstrak(
                lokasiJahat,
                FormatArsip.ZIP,
                LokasiBerkas("/keluar", JenisLokasi.POSIX, "keluar"),
                OpsiEkstrakArsip(konfirmasiPengguna = true)
            )
            assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(hasilEkstrak).penyebab)
            assertEquals(null, storage.berkas.data("/jahat.txt"))
            assertEquals(null, storage.berkas.data("/keluar/jahat.txt"))
        } finally {
            runCatching { FileSystem.SYSTEM.delete(pathJahat, mustExist = false) }
        }
    }

    @Test
    fun tarTgzDanGzipRoundTripSertaBatasUkuranEkstraksi() = runTest {
        val storage = siapkanPenyimpananContoh()
        val adapter = buatAdapterArsipPlatform()
        val providerTar = ProviderTar(storage, storage, adapter)
        val providerGzip = ProviderGzip(storage, adapter)
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")

        // Uji TGZ dengan nama sangat panjang (menguji PAX extended header)
        val namaPanjang = "folder_sangat_panjang_" + "a".repeat(95) + ".txt"
        storage.berkas.simpan(
            DataBerkas("/$namaPanjang", namaPanjang, JenisLokasi.POSIX, adalahFolder = false),
            "ISI_PANJANG".encodeToByteArray()
        )

        val buatTgz = assertIs<HasilOperasi.Sukses<*>>(
            providerTar.buatArsip(
                daftarSumber = listOf(
                    ItemSumberKompresi(LokasiBerkas("/$namaPanjang", JenisLokasi.POSIX, namaPanjang), namaPanjang, false),
                    ItemSumberKompresi(LokasiBerkas("/src", JenisLokasi.POSIX, "src"), "src", true)
                ),
                tujuanInduk = root,
                namaArsip = "arsip.tar.gz",
                format = FormatArsip.TGZ
            )
        ).nilai as org.fm.com.domain.model.EntitasBerkas

        val daftarTgz = assertIs<HasilOperasi.Sukses<*>>(
            providerTar.daftarEntri(buatTgz.lokasi, FormatArsip.TGZ)
        ).nilai as org.fm.com.domain.model.RingkasanIsiArsip
        assertTrue(daftarTgz.daftarEntri.any { it.jalurInternal == namaPanjang })
        assertTrue(daftarTgz.daftarEntri.any { it.jalurInternal == "src/Main.kt" })

        val keluarTgz = LokasiBerkas("/keluar", JenisLokasi.POSIX, "keluar")
        assertIs<HasilOperasi.Sukses<*>>(
            providerTar.ekstrak(
                buatTgz.lokasi,
                FormatArsip.TGZ,
                keluarTgz,
                OpsiEkstrakArsip(konfirmasiPengguna = true)
            )
        )
        assertEquals("ISI_PANJANG", storage.berkas.isiDari("/keluar/$namaPanjang")?.decodeToString())

        // Uji batas ukuran ekstraksi menolak dekompresi melebihi batas
        val melebihiBatas = providerTar.ekstrak(
            buatTgz.lokasi,
            FormatArsip.TGZ,
            LokasiBerkas("/keluar_terbatas", JenisLokasi.POSIX, "keluar_terbatas"),
            OpsiEkstrakArsip(
                konfirmasiPengguna = true,
                batas = KebijakanBatasArsip(batasUkuranTotalEkstrakByte = 5L, batasUkuranSatuEntriByte = 5L)
            )
        )
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(melebihiBatas).penyebab)

        // Uji GZIP tunggal
        val buatGz = assertIs<HasilOperasi.Sukses<*>>(
            providerGzip.buatArsip(
                daftarSumber = listOf(
                    ItemSumberKompresi(LokasiBerkas("/catatan.txt", JenisLokasi.POSIX, "catatan.txt"), "catatan.txt", false)
                ),
                tujuanInduk = root,
                namaArsip = "catatan.txt.gz",
                format = FormatArsip.GZIP
            )
        ).nilai as org.fm.com.domain.model.EntitasBerkas

        assertIs<HasilOperasi.Sukses<*>>(
            providerGzip.ekstrak(
                buatGz.lokasi,
                FormatArsip.GZIP,
                keluarTgz,
                OpsiEkstrakArsip(konfirmasiPengguna = true)
            )
        )
        assertEquals("Catatan penting", storage.berkas.isiDari("/keluar/catatan.txt")?.decodeToString())

        // GZIP menolak banyak item/folder
        val tolakFolderGz = providerGzip.buatArsip(
            daftarSumber = listOf(ItemSumberKompresi(LokasiBerkas("/src", JenisLokasi.POSIX, "src"), "src", true)),
            tujuanInduk = root,
            namaArsip = "src.gz",
            format = FormatArsip.GZIP
        )
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(tolakFolderGz).penyebab)

        // Header TAR rusak tidak crash
        storage.berkas.simpan(
            DataBerkas("/rusak.tar", "rusak.tar", JenisLokasi.POSIX, adalahFolder = false),
            ByteArray(512) { 0x41 }
        )
        assertIs<HasilOperasi.Gagal>(
            providerTar.daftarEntri(LokasiBerkas("/rusak.tar", JenisLokasi.POSIX, "rusak.tar"), FormatArsip.TAR)
        )
    }

    @Test
    fun kompresiZipDanTarGagalJikaSubfolderGagalDibaca() = runTest {
        val storage = siapkanPenyimpananContoh()
        storage.berkas.simpan(DataBerkas("/src/terkunci", "terkunci", JenisLokasi.POSIX, adalahFolder = true))
        val repoGagalSub = object : RepositoriBerkas {
            override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? = storage.kapabilitas(lokasi)
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
                if (lokasi.pengenal == "/src/terkunci") {
                    flowOf(HasilOperasi.Gagal("Izin folder ditolak", PenyebabGalat.IZIN_DITOLAK))
                } else {
                    storage.daftarIsi(lokasi)
                }
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas) = storage.bukaUntukBaca(lokasi)
            override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean) = storage.bukaUntukTulis(lokasi, timpa)
            override suspend fun bukaUntukTulisDi(induk: LokasiBerkas, nama: String, timpa: Boolean) =
                storage.bukaUntukTulisDi(induk, nama, timpa)
        }
        val adapter = buatAdapterArsipPlatform()
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")
        val sumberSrc = listOf(ItemSumberKompresi(LokasiBerkas("/src", JenisLokasi.POSIX, "src"), "src", true))

        val gagalZip = assertIs<HasilOperasi.Gagal>(
            ProviderZip(repoGagalSub, storage, adapter).buatArsip(sumberSrc, root, "gagal.zip", FormatArsip.ZIP)
        )
        assertEquals(PenyebabGalat.IZIN_DITOLAK, gagalZip.penyebab)

        val gagalTar = assertIs<HasilOperasi.Gagal>(
            ProviderTar(repoGagalSub, storage, adapter).buatArsip(sumberSrc, root, "gagal.tar", FormatArsip.TAR)
        )
        assertEquals(PenyebabGalat.IZIN_DITOLAK, gagalTar.penyebab)
    }

    @Test
    fun zip64DenganJalurTraversalDitolakTanpaEkstraksi() = runTest {
        val storage = siapkanPenyimpananContoh()
        val providerZip = ProviderZip(storage, storage, buatAdapterArsipPlatform())

        val namaJahat = "../../jahat64.txt".encodeToByteArray()
        val payload = "JAHAT64".encodeToByteArray()
        val zipBytes = Buffer().apply {
            // Local File Header (30 + nama + payload)
            writeIntLe(0x04034B50)
            writeShortLe(45)
            writeShortLe(0x0800)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(0x0021)
            writeIntLe(0)
            writeIntLe(payload.size)
            writeIntLe(payload.size)
            writeShortLe(namaJahat.size)
            writeShortLe(0)
            write(namaJahat)
            write(payload)
            val cdOffset = size

            // Central Directory Entry (46 + nama)
            writeIntLe(0x02014B50)
            writeShortLe(45)
            writeShortLe(45)
            writeShortLe(0x0800)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(0x0021)
            writeIntLe(0)
            writeIntLe(payload.size)
            writeIntLe(payload.size)
            writeShortLe(namaJahat.size)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(0)
            writeIntLe(0)
            writeIntLe(0)
            write(namaJahat)
            val cdSize = size - cdOffset
            val zip64EocdOffset = size

            // Zip64 End of Central Directory Record (56 byte)
            writeIntLe(0x06064B50)
            writeLongLe(44L)
            writeShortLe(45)
            writeShortLe(45)
            writeIntLe(0)
            writeIntLe(0)
            writeLongLe(1L)
            writeLongLe(1L)
            writeLongLe(cdSize)
            writeLongLe(cdOffset)

            // Zip64 End of Central Directory Locator (20 byte)
            writeIntLe(0x07064B50)
            writeIntLe(0)
            writeLongLe(zip64EocdOffset)
            writeIntLe(1)

            // Standard EOCD dengan penanda Zip64
            writeIntLe(0x06054B50)
            writeShortLe(0)
            writeShortLe(0)
            writeShortLe(0xFFFF)
            writeShortLe(0xFFFF)
            writeIntLe(-1)
            writeIntLe(-1)
            writeShortLe(0)
        }.readByteArray()

        storage.berkas.simpan(DataBerkas("/jahat64.zip", "jahat64.zip", JenisLokasi.POSIX, adalahFolder = false), zipBytes)
        val lok = LokasiBerkas("/jahat64.zip", JenisLokasi.POSIX, "jahat64.zip")
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(providerZip.daftarEntri(lok, FormatArsip.ZIP)).penyebab)
        assertEquals(
            PenyebabGalat.VALIDASI,
            assertIs<HasilOperasi.Gagal>(
                providerZip.ekstrak(lok, FormatArsip.ZIP, LokasiBerkas("/keluar", JenisLokasi.POSIX, "keluar"), OpsiEkstrakArsip(konfirmasiPengguna = true))
            ).penyebab
        )
        assertEquals(null, storage.berkas.data("/keluar/jahat64.txt"))
    }

    @Test
    fun gzipMultiMemberTerbacaLengkapDanToleranTrailingZero() = runTest {
        val storage = siapkanPenyimpananContoh()
        val providerGzip = ProviderGzip(storage, buatAdapterArsipPlatform())
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")

        storage.berkas.simpan(DataBerkas("/a.txt", "a.txt", JenisLokasi.POSIX, false), "abc".encodeToByteArray())
        storage.berkas.simpan(DataBerkas("/b.txt", "b.txt", JenisLokasi.POSIX, false), "def".encodeToByteArray())

        assertIs<HasilOperasi.Sukses<*>>(
            providerGzip.buatArsip(listOf(ItemSumberKompresi(LokasiBerkas("/a.txt", JenisLokasi.POSIX, "a.txt"), "a.txt", false)), root, "a.gz", FormatArsip.GZIP)
        )
        assertIs<HasilOperasi.Sukses<*>>(
            providerGzip.buatArsip(listOf(ItemSumberKompresi(LokasiBerkas("/b.txt", JenisLokasi.POSIX, "b.txt"), "b.txt", false)), root, "b.gz", FormatArsip.GZIP)
        )
        val bytesA = storage.berkas.isiDari("/a.gz")!!
        val bytesB = storage.berkas.isiDari("/b.gz")!!

        storage.berkas.simpan(
            DataBerkas("/multi.txt.gz", "multi.txt.gz", JenisLokasi.POSIX, false),
            bytesA + bytesB
        )
        val bacaMulti = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(
            providerGzip.bukaEntriUntukBaca(LokasiBerkas("/multi.txt.gz", JenisLokasi.POSIX, "multi.txt.gz"), FormatArsip.GZIP, "multi.txt")
        ).nilai
        assertEquals("abcdef", bacaSemuaDariSumber(bacaMulti).decodeToString())

        storage.berkas.simpan(
            DataBerkas("/multi_pad.txt.gz", "multi_pad.txt.gz", JenisLokasi.POSIX, false),
            bytesA + bytesB + ByteArray(16) { 0 }
        )
        val bacaPad = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(
            providerGzip.bukaEntriUntukBaca(LokasiBerkas("/multi_pad.txt.gz", JenisLokasi.POSIX, "multi_pad.txt.gz"), FormatArsip.GZIP, "multi_pad.txt")
        ).nilai
        assertEquals("abcdef", bacaSemuaDariSumber(bacaPad).decodeToString())
    }

    private suspend fun siapkanPenyimpananContoh(): SumberMemoriPenyimpanan {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/src", "src", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/keluar", "keluar", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/keluar_terbatas", "keluar_terbatas", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/src/Main.kt", "Main.kt", JenisLokasi.POSIX, adalahFolder = false),
            "fun main() = println(\"Halo\")".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "Catatan penting".encodeToByteArray()
        )
        return storage
    }

    private suspend fun bacaSemuaDariSumber(sumber: SumberIsiBerkas): ByteArray {
        val buffer = Buffer()
        try {
            while (true) {
                when (val pot = sumber.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        val d = pot.potongan.data
                        if (d.isNotEmpty()) buffer.write(d)
                        if (pot.potongan.selesai) break
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> error(pot.pesan)
                }
            }
            return buffer.readByteArray()
        } finally {
            sumber.tutup()
        }
    }
}