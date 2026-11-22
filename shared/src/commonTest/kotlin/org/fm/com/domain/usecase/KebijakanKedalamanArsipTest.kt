package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.fm.com.domain.model.EntriArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat

class KebijakanKedalamanArsipTest {

    @Test
    fun validasiKedalamanMenolakMelebihiBatasDanNilaiTidakValid() {
        val batasTiga = KebijakanBatasArsip(batasKedalamanArsipMaks = 3)

        assertIs<HasilOperasi.Sukses<Unit>>(EkstrakArsipUseCase.validasiKedalaman(1, batasTiga))
        assertIs<HasilOperasi.Sukses<Unit>>(EkstrakArsipUseCase.validasiKedalaman(3, batasTiga))

        val lebihBatas = EkstrakArsipUseCase.validasiKedalaman(4, batasTiga)
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(lebihBatas).penyebab)

        val nol = EkstrakArsipUseCase.validasiKedalaman(0, batasTiga)
        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(nol).penyebab)

        assertFailsWith<IllegalArgumentException> {
            KebijakanBatasArsip(batasKedalamanArsipMaks = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            KebijakanBatasArsip(batasUkuranTotalEkstrakByte = 0L)
        }
    }

    @Test
    fun masukArsipBersarangMemblokirSebelumMemanggilPelaksanaSaatMelebihiBatas() = runTest {
        var pelaksanaDipanggil = false
        val pelaksanaStub = object : PelaksanaPenjelajahanArsip {
            override suspend fun bukaArsipAkar(
                idSesi: String,
                sumber: LokasiBerkas,
                kataSandi: String?,
                batas: KebijakanBatasArsip
            ): HasilOperasi<StatusLapisanArsip> = error("Tidak dipanggil")

            override suspend fun masukArsipBersarang(
                idSesi: String,
                jalurArsipDalamLapisan: String,
                kataSandi: String?,
                batas: KebijakanBatasArsip
            ): HasilOperasi<StatusLapisanArsip> {
                pelaksanaDipanggil = true
                error("Tidak boleh dipanggil saat melebihi batas kedalaman")
            }

            override suspend fun kembaliKeLapisan(
                idSesi: String,
                indeksLapisanTujuan: Int
            ): HasilOperasi<StatusLapisanArsip> = error("Tidak dipanggil")

            override suspend fun ekstrak(
                idSesi: String,
                folderTujuan: LokasiBerkas,
                opsi: OpsiEkstrakArsip,
                onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit
            ): HasilOperasi<HasilEkstrakArsip> = error("Tidak dipanggil")

            override suspend fun siapkanPratinjauBerkas(
                idSesi: String,
                jalurInternal: String,
                untukEdit: Boolean,
                kataSandi: String?,
                batas: KebijakanBatasArsip
            ): HasilOperasi<HasilSiapkanEditArsip> = error("Tidak dipanggil")

            override suspend fun perbaruiBerkasDalamArsip(
                konteks: KonteksEditArsip,
                lokasiBerkasEdit: LokasiBerkas,
                batas: KebijakanBatasArsip
            ): HasilOperasi<StatusLapisanArsip?> = error("Tidak dipanggil")

            override suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip? = null

            override suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas) = Unit

            override suspend fun tutupSesi(idSesi: String) = Unit
        }

        val useCase = EkstrakArsipUseCase(pelaksanaStub)
        val batas = KebijakanBatasArsip(batasKedalamanArsipMaks = 2)
        val hasil = useCase.masukArsipBersarang(
            idSesi = "sesi",
            jalurArsipDalamLapisan = "konfig.zip",
            kedalamanSaatIni = 2,
            batas = batas
        )

        assertEquals(PenyebabGalat.VALIDASI, assertIs<HasilOperasi.Gagal>(hasil).penyebab)
        assertFalse(pelaksanaDipanggil)
    }

    @Test
    fun saringEntriUntukFolderMembentukFolderSintetisDanMengurutkanFolderDiAtas() {
        val entri = listOf(
            EntriArsip("README.md", "README.md", adalahFolder = false),
            EntriArsip("src/main/App.kt", "App.kt", adalahFolder = false),
            EntriArsip("src/main/util/Log.kt", "Log.kt", adalahFolder = false),
            EntriArsip("paket.zip", "paket.zip", adalahFolder = false)
        )

        val diAkar = EkstrakArsipUseCase.saringEntriUntukFolder(entri, "")
        assertEquals(listOf("src", "paket.zip", "README.md"), diAkar.map { it.nama })
        assertTrue(diAkar.first().adalahFolder)
        assertTrue(diAkar[1].adalahArsipBersarang)

        val diSrc = EkstrakArsipUseCase.saringEntriUntukFolder(entri, "src")
        assertEquals(listOf("main"), diSrc.map { it.nama })
        assertTrue(diSrc.single().adalahFolder)

        val diMain = EkstrakArsipUseCase.saringEntriUntukFolder(entri, "src/main")
        assertEquals(listOf("util", "App.kt"), diMain.map { it.nama })
    }
}