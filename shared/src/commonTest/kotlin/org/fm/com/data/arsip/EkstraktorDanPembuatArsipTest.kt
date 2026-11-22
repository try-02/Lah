package org.fm.com.data.arsip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.arsip.RegistriArsipProvider
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.KompresArsipUseCase

class EkstraktorDanPembuatArsipTest {

    @Test
    fun penjelajahanArsipBertingkatBatasKedalamanDanEkstraksiSelektif() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/kerja", "kerja", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/keluar", "keluar", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/kerja/app.json", "app.json", JenisLokasi.POSIX, adalahFolder = false),
            "{\"versi\":1}".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/kerja/catatan.txt", "catatan.txt", JenisLokasi.POSIX, adalahFolder = false),
            "Catatan dalam TAR".encodeToByteArray()
        )

        val adapter = buatAdapterArsipPlatform()
        val providerZip = ProviderZip(storage, storage, adapter)
        val providerZipBased = ProviderZipBased(providerZip)
        val providerTar = ProviderTar(storage, storage, adapter)
        val providerGzip = ProviderGzip(storage, adapter)
        val registri = RegistriArsipProvider(
            listOf(providerZip, providerZipBased, providerTar, providerGzip)
        )

        val pembuat = PembuatArsip(storage, registri)
        val kompresUseCase = KompresArsipUseCase(pembuat)
        val ekstraktor = EkstraktorArsip(registri, adapter)
        val ekstrakUseCase = EkstrakArsipUseCase(ekstraktor)

        val dirKerja = LokasiBerkas("/kerja", JenisLokasi.POSIX, "kerja")
        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")

        // 1. Buat tingkat terdalam: konfig.zip berisi app.json
        val konfigZip = assertIs<HasilOperasi.Sukses<*>>(
            kompresUseCase(
                daftarSumber = listOf(
                    ItemSumberKompresi(LokasiBerkas("/kerja/app.json", JenisLokasi.POSIX, "app.json"), "app.json", false)
                ),
                tujuanInduk = dirKerja,
                namaArsip = "konfig.zip",
                format = FormatArsip.ZIP
            )
        ).nilai as org.fm.com.domain.model.EntitasBerkas

        // 2. Buat tingkat tengah: sumber.tar berisi konfig.zip & catatan.txt
        val sumberTar = assertIs<HasilOperasi.Sukses<*>>(
            kompresUseCase(
                daftarSumber = listOf(
                    ItemSumberKompresi(konfigZip.lokasi, "konfig.zip", false),
                    ItemSumberKompresi(LokasiBerkas("/kerja/catatan.txt", JenisLokasi.POSIX, "catatan.txt"), "catatan.txt", false)
                ),
                tujuanInduk = dirKerja,
                namaArsip = "sumber.tar",
                format = FormatArsip.TAR
            )
        ).nilai as org.fm.com.domain.model.EntitasBerkas

        // 3. Buat tingkat akar: proyek.zip berisi sumber.tar
        val proyekZip = assertIs<HasilOperasi.Sukses<*>>(
            kompresUseCase(
                daftarSumber = listOf(
                    ItemSumberKompresi(sumberTar.lokasi, "sumber.tar", false)
                ),
                tujuanInduk = root,
                namaArsip = "proyek.zip",
                format = FormatArsip.ZIP
            )
        ).nilai as org.fm.com.domain.model.EntitasBerkas

        // Konflik saat buat arsip dengan nama sama tanpa konfirmasiTimpa ditolak
        val konflikBuat = kompresUseCase(
            daftarSumber = listOf(ItemSumberKompresi(sumberTar.lokasi, "sumber.tar", false)),
            tujuanInduk = root,
            namaArsip = "proyek.zip",
            format = FormatArsip.ZIP,
            konfirmasiTimpa = false
        )
        assertEquals(PenyebabGalat.KONFLIK, assertIs<HasilOperasi.Gagal>(konflikBuat).penyebab)

        val idSesi = "uji_nested"
        val batasDuaTingkat = KebijakanBatasArsip(batasKedalamanArsipMaks = 2)

        // Tingkat 1: proyek.zip
        val lapis1 = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.bukaAkar(idSesi, proyekZip.lokasi, batas = batasDuaTingkat)
        ).nilai as org.fm.com.domain.usecase.StatusLapisanArsip
        assertEquals(1, lapis1.kedalamanSaatIni)
        assertTrue(lapis1.ringkasan.daftarEntri.any { it.jalurInternal == "sumber.tar" && it.adalahArsipBersarang })

        // Tingkat 2: sumber.tar
        val lapis2 = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.masukArsipBersarang(idSesi, "sumber.tar", lapis1.kedalamanSaatIni, batas = batasDuaTingkat)
        ).nilai as org.fm.com.domain.usecase.StatusLapisanArsip
        assertEquals(2, lapis2.kedalamanSaatIni)
        assertTrue(lapis2.ringkasan.daftarEntri.any { it.jalurInternal == "konfig.zip" && it.adalahArsipBersarang })

        // Tingkat 3 ditolak jika batasKedalamanArsipMaks = 2
        val ditolakBatas = ekstrakUseCase.masukArsipBersarang(
            idSesi,
            "konfig.zip",
            lapis2.kedalamanSaatIni,
            batas = batasDuaTingkat
        )
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(ditolakBatas).penyebab)

        // Dengan batas default (5), tingkat 3 berhasil dibuka
        val lapis3 = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.masukArsipBersarang(idSesi, "konfig.zip", lapis2.kedalamanSaatIni)
        ).nilai as org.fm.com.domain.usecase.StatusLapisanArsip
        assertEquals(3, lapis3.kedalamanSaatIni)
        assertTrue(lapis3.ringkasan.daftarEntri.any { it.jalurInternal == "app.json" })

        // Pratinjau file dari kedalaman 3
        val pratinjau = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.siapkanPratinjauBerkas(idSesi, "app.json")
        ).nilai as org.fm.com.domain.model.EntitasBerkas
        assertEquals(false, pratinjau.dapatDitulis)
        val pathPratinjau = pratinjau.lokasi.pengenal.toPath()
        try {
            assertTrue(FileSystem.SYSTEM.exists(pathPratinjau))
        } finally {
            runCatching { FileSystem.SYSTEM.delete(pathPratinjau, mustExist = false) }
        }

        // Ekstraksi selektif di tingkat 3 memerlukan konfirmasi
        val keluar = LokasiBerkas("/keluar", JenisLokasi.POSIX, "keluar")
        val tanpaPersetujuan = ekstrakUseCase.ekstrak(
            idSesi,
            keluar,
            OpsiEkstrakArsip(jalurTerpilih = setOf("app.json"), konfirmasiPengguna = false)
        )
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(tanpaPersetujuan).penyebab)

        val hasilEkstrak = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.ekstrak(
                idSesi,
                keluar,
                OpsiEkstrakArsip(jalurTerpilih = setOf("app.json"), konfirmasiPengguna = true)
            )
        ).nilai as org.fm.com.domain.model.HasilEkstrakArsip
        assertEquals(1, hasilEkstrak.jumlahDiekstrak)
        assertEquals("{\"versi\":1}", storage.berkas.isiDari("/keluar/app.json")?.decodeToString())

        // Kembali ke lapisan 1 & tutup sesi membersihkan cache sementara
        val balikLapis1 = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.kembaliKeLapisan(idSesi, 0)
        ).nilai as org.fm.com.domain.usecase.StatusLapisanArsip
        assertEquals(1, balikLapis1.kedalamanSaatIni)
        ekstrakUseCase.tutupSesi(idSesi)
    }
}