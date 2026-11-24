package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

class OperasiDasarUseCaseTest {

    private val repo = ImplementasiRepositoriBerkas()
    private val op = ImplementasiRepositoriOperasiBerkas(repo)

    private val buatUc = BuatBerkasUseCase(op)
    private val renameUc = GantiNamaBerkasUseCase(op)
    private val hapusUc = HapusBerkasUseCase(op)

    private val root = LokasiBerkas("/root", JenisLokasi.POSIX, "root")

    @Test
    fun buatValidasiNamaDanBerhasil() = runTest {
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))

        val namaInvalid = buatUc.buatFolder(root, "../ilegal")
        val galatInvalid = assertIs<HasilOperasi.Gagal>(namaInvalid)
        assertEquals(PenyebabGalat.VALIDASI, galatInvalid.penyebab)

        val sukses = buatUc.buatFolder(root, "FolderBaru")
        assertIs<HasilOperasi.Sukses<*>>(sukses)
        assertEquals("FolderBaru", repo.data("/root/FolderBaru")?.nama)

        val berkasSukses = buatUc.buatBerkasKosong(root, "catatan.txt")
        assertIs<HasilOperasi.Sukses<*>>(berkasSukses)
        assertEquals("catatan.txt", repo.data("/root/catatan.txt")?.nama)
    }

    @Test
    fun gantiNamaValidasiDanKonflik() = runTest {
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/lama.txt", "lama.txt", JenisLokasi.POSIX, adalahFolder = false))
        repo.simpan(DataBerkas("/root/ada.txt", "ada.txt", JenisLokasi.POSIX, adalahFolder = false))

        val sumber = LokasiBerkas("/root/lama.txt", JenisLokasi.POSIX, "lama.txt")

        val konflik = renameUc(sumber, "ada.txt", konfirmasi = false)
        val galatKonflik = assertIs<HasilOperasi.Gagal>(konflik)
        assertEquals(PenyebabGalat.KONFLIK, galatKonflik.penyebab)

        val sukses = renameUc(sumber, "baru.txt")
        assertIs<HasilOperasi.Sukses<*>>(sukses)
        assertEquals("baru.txt", repo.data("/root/baru.txt")?.nama)

        val suksesDenganInduk = renameUc(LokasiBerkas("/root/baru.txt", JenisLokasi.POSIX, "baru.txt"), "akhir.txt", induk = root)
        assertIs<HasilOperasi.Sukses<*>>(suksesDenganInduk)
        assertEquals("akhir.txt", repo.data("/root/akhir.txt")?.nama)
    }

    @Test
    fun hapusWajibKonfirmasi() = runTest {
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/buang.txt", "buang.txt", JenisLokasi.POSIX, adalahFolder = false))

        val target = LokasiBerkas("/root/buang.txt", JenisLokasi.POSIX, "buang.txt")

        val tanpaKonfirmasi = hapusUc(target, konfirmasi = false)
        assertIs<HasilOperasi.Gagal>(tanpaKonfirmasi)

        val denganKonfirmasi = hapusUc(target, konfirmasi = true)
        assertIs<HasilOperasi.Sukses<*>>(denganKonfirmasi)
        assertEquals(null, repo.data("/root/buang.txt"))
    }
}