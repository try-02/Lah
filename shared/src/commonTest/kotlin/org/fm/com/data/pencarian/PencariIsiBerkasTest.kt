@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.data.pencarian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterCariIsi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.CariIsiBerkasUseCase

class PencariIsiBerkasTest {

    @Test
    fun memindaiStreamingChunkKecilLewatiBinerDanBatasUkuran() = runTest {
        val repo = ImplementasiRepositoriBerkas(ukuranPotongan = 3)
        repo.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/akar/sub", "sub", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(
            DataBerkas("/akar/sub/kode.kt", "kode.kt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 60L),
            "val halo = 1\r\n// 🚀 halo dunia halo\r\nval x = 2".encodeToByteArray()
        )
        // UTF-8 BOM terfragmentasi pada chunk ukuran 3
        val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "halo dari bom\nbaris dua".encodeToByteArray()
        repo.simpan(
            DataBerkas("/akar/bom.txt", "bom.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = utf8BomBytes.size.toLong()),
            utf8BomBytes
        )
        // Biner mengandung NUL byte harus dilewati
        repo.simpan(
            DataBerkas("/akar/biner.dat", "biner.dat", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 12L),
            byteArrayOf('h'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(), 0x00, 0x01)
        )
        // File melebihi batas ukuran harus dilewati
        repo.simpan(
            DataBerkas("/akar/terlalu_besar.txt", "terlalu_besar.txt", JenisLokasi.POSIX, adalahFolder = false, ukuranByte = 500L),
            "halo ".repeat(100).encodeToByteArray()
        )

        val pencari = PencariIsiBerkas(repo, UnconfinedTestDispatcher(testScheduler))
        val uc = CariIsiBerkasUseCase(pencari)
        val hasil = uc(
            LokasiBerkas("/akar", JenisLokasi.POSIX, "akar"),
            FilterCariIsi(
                kueri = "halo",
                kataUtuh = true,
                hanyaFormatTeksDikenal = false,
                batasUkuranPerFileByte = 200L
            )
        ).last()

        assertNull(hasil.galatFatal)
        assertEquals(1, hasil.jumlahBinerDilewati)
        assertEquals(1, hasil.jumlahTerlaluBesarDilewati)
        assertEquals(listOf("bom.txt", "sub/kode.kt"), hasil.daftarHasil.map { it.lokasiRelatif }.sorted())

        val hasilKode = hasil.daftarHasil.first { it.lokasiRelatif == "sub/kode.kt" }
        assertEquals(3, hasilKode.jumlahKecocokanTotal)
        assertEquals(listOf(1, 2), hasilKode.daftarBarisCocok.map { it.nomorBaris })
        assertEquals(2, hasilKode.daftarBarisCocok[1].jumlahKecocokanDiBaris)
    }

    @Test
    fun batasHasilPerFileDanErrorSatuBerkasTidakMenghentikanPemindaian() = runTest {
        val repoAsli = ImplementasiRepositoriBerkas()
        repoAsli.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        repoAsli.simpan(
            DataBerkas("/akar/1_rusak.txt", "1_rusak.txt", JenisLokasi.POSIX, adalahFolder = false),
            "tidak terbaca".encodeToByteArray()
        )
        repoAsli.simpan(
            DataBerkas("/akar/2_banyak.txt", "2_banyak.txt", JenisLokasi.POSIX, adalahFolder = false),
            "target 1\ntarget 2\ntarget 3\ntarget 4\ntarget 5".encodeToByteArray()
        )

        val repoErrorSatu = object : RepositoriBerkas by repoAsli {
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
                return if (lokasi.pengenal == "/akar/1_rusak.txt") {
                    HasilOperasi.Gagal("Izin baca berkas ditolak", PenyebabGalat.IZIN_DITOLAK)
                } else {
                    repoAsli.bukaUntukBaca(lokasi)
                }
            }
        }

        val pencari = PencariIsiBerkas(repoErrorSatu, UnconfinedTestDispatcher(testScheduler))
        val hasil = pencari.cariIsi(
            LokasiBerkas("/akar", JenisLokasi.POSIX),
            FilterCariIsi(kueri = "target", maksBarisCocokPerFile = 2)
        ).last()

        assertNull(hasil.galatFatal)
        assertEquals(1, hasil.daftarGalatBerkas.size)
        assertEquals("1_rusak.txt", hasil.daftarGalatBerkas.first().lokasiRelatif)
        assertEquals(1, hasil.daftarHasil.size)

        val cocok = hasil.daftarHasil.first()
        assertEquals(5, cocok.jumlahKecocokanTotal)
        assertEquals(2, cocok.daftarBarisCocok.size)
        assertTrue(cocok.daftarTerpotongBatas)
    }

    @Test
    fun pemindaianDapatDibatalkanSetelahEmisiAwal() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        repeat(25) { idx ->
            repo.simpan(
                DataBerkas("/akar/f$idx.txt", "f$idx.txt", JenisLokasi.POSIX, adalahFolder = false),
                "isi cocok $idx".encodeToByteArray()
            )
        }
        val pencari = PencariIsiBerkas(repo, UnconfinedTestDispatcher(testScheduler))
        val sebagian = pencari.cariIsi(
            LokasiBerkas("/akar", JenisLokasi.POSIX),
            FilterCariIsi(kueri = "cocok")
        ).take(2).toList()

        assertEquals(2, sebagian.size)
        assertFalse(sebagian.first().selesai)
    }
}