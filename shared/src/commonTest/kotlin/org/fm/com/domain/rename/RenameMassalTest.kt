package org.fm.com.domain.rename

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.rename.PenerapkanRenameMassal
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.usecase.HasilJalankanRenameMassal
import org.fm.com.domain.usecase.JalankanRenameMassalUseCase

class RenameMassalTest {

    private fun entitas(
        nama: String,
        dapatDitulis: Boolean = true,
        jenis: JenisBerkas = JenisBerkas.BERKAS_TEKS
    ) = EntitasBerkas(
        lokasi = LokasiBerkas("/$nama", JenisLokasi.POSIX, nama),
        nama = nama,
        jenis = jenis,
        dapatDitulis = dapatDitulis
    )

    @Test
    fun deteksiNamaTidakSahTabrakanDanItemTakDapatDiubah() {
        val itemA = entitas("a.txt")
        val itemB = entitas("b.txt")
        val itemRo = entitas("kunci.txt", dapatDitulis = false)

        val tabrakanAntar = PratinjauRename.bangun(
            daftarItem = listOf(itemA, itemB),
            pola = "sama.txt",
            namaDiFolderDisk = listOf("a.txt", "b.txt")
        )
        assertFalse(tabrakanAntar.dapatDieksekusi)
        assertEquals(2, tabrakanAntar.jumlahTabrakanAntarItem)

        val tabrakanDisk = PratinjauRename.bangun(
            daftarItem = listOf(itemA),
            pola = "ada.txt",
            namaDiFolderDisk = listOf("a.txt", "ada.txt")
        )
        assertFalse(tabrakanDisk.dapatDieksekusi)
        assertEquals(1, tabrakanDisk.jumlahTabrakanDenganDisk)

        val namaTerlarang = PratinjauRename.bangun(
            daftarItem = listOf(itemA),
            pola = "sub/{P}",
            namaDiFolderDisk = listOf("a.txt")
        )
        assertFalse(namaTerlarang.dapatDieksekusi)
        assertEquals(1, namaTerlarang.jumlahNamaTidakSah)

        val campuranTakBisa = PratinjauRename.bangun(
            daftarItem = listOf(itemA, itemRo),
            pola = "{P}_{N}{S}",
            namaDiFolderDisk = listOf("a.txt", "kunci.txt")
        )
        assertTrue(campuranTakBisa.dapatDieksekusi)
        assertEquals(1, campuranTakBisa.jumlahSiapUbah)
        assertEquals(1, campuranTakBisa.jumlahTidakDapatDiubah)

        val saf = MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        assertTrue(
            PratinjauRename.bangun(
                daftarItem = listOf(itemA),
                pola = "{P}_{N}{S}",
                kemampuanLokasi = saf
            ).dapatDieksekusi
        )
    }

    @Test
    fun eksekusiMemerlukanKonfirmasiMendukungSiklusTanpaMenimpaDanCatatGagalPerItem() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false),
            "ISI_A".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/b.txt", "b.txt", JenisLokasi.POSIX, adalahFolder = false),
            "ISI_B".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/tetap.txt", "tetap.txt", JenisLokasi.POSIX, adalahFolder = false),
            "ISI_TETAP".encodeToByteArray()
        )

        val useCase = JalankanRenameMassalUseCase(PenerapkanRenameMassal(storage.operasi))
        val itemA = entitas("a.txt")
        val itemB = entitas("b.txt")

        // Pratinjau pertukaran siklus (a.txt -> b.txt, b.txt -> a.txt) valid:
        val pratinjauTukar = PratinjauRename.bangun(
            daftarItem = listOf(itemA, itemB),
            pola = "{P}_tukar",
            namaDiFolderDisk = listOf("a.txt", "b.txt", "tetap.txt")
        )
        assertTrue(pratinjauTukar.dapatDieksekusi)

        // Tanpa konfirmasi -> harus meminta ringkasan konfirmasi dulu
        val butuhKonfirmasi = useCase(
            daftarItem = listOf(itemA, itemB),
            pola = "{P}_{zN}{S}",
            namaDiFolderDisk = listOf("a.txt", "b.txt", "tetap.txt"),
            konfirmasiRingkasan = false
        )
        assertIs<HasilJalankanRenameMassal.PerluKonfirmasi>(butuhKonfirmasi)

        // Uji langsung siklus pertukaran via PenerapkanRenameMassal: a.txt <-> b.txt
        val pelaksana = PenerapkanRenameMassal(storage.operasi)
        val hasilTukar = pelaksana.terapkan(
            listOf(
                org.fm.com.domain.usecase.TugasRenameItem(itemA, "b.txt"),
                org.fm.com.domain.usecase.TugasRenameItem(itemB, "a.txt")
            )
        )
        assertFalse(hasilTukar.adaGagal)
        assertEquals(2, hasilTukar.berhasil.size)
        assertTrue(hasilTukar.berhasil.all { it.pakaiNamaSementara })
        assertEquals("ISI_A", storage.berkas.isiDari("/b.txt")?.decodeToString())
        assertEquals("ISI_B", storage.berkas.isiDari("/a.txt")?.decodeToString())

        // Uji kegagalan per item: satu item bentrok di disk (tetap.txt) + satu item sukses
        val hasilParsial = pelaksana.terapkan(
            listOf(
                org.fm.com.domain.usecase.TugasRenameItem(entitas("a.txt"), "tetap.txt"),
                org.fm.com.domain.usecase.TugasRenameItem(entitas("b.txt"), "baru.txt")
            )
        )
        assertEquals(1, hasilParsial.berhasil.size)
        assertEquals(1, hasilParsial.gagal.size)
        assertEquals("ISI_TETAP", storage.berkas.isiDari("/tetap.txt")?.decodeToString())
        assertEquals("ISI_A", storage.berkas.isiDari("/baru.txt")?.decodeToString())
    }
}