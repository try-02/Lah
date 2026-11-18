package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class UrutkanBerkasUseCaseTest {

    private val useCase = UrutkanBerkasUseCase()

    private fun item(
        nama: String,
        jenis: JenisBerkas,
        ukuran: Long? = 100L,
        waktu: Long? = 1000L
    ) = EntitasBerkas(
        lokasi = LokasiBerkas("/root/$nama", JenisLokasi.POSIX, nama),
        nama = nama,
        jenis = jenis,
        ukuranByte = ukuran,
        waktuUbahEpochMs = waktu
    )

    @Test
    fun urutkanNamaNaikDenganFolderDiAtas() {
        val daftar = listOf(
            item("catatan.txt", JenisBerkas.BERKAS_TEKS),
            item("Zebra", JenisBerkas.FOLDER),
            item("apel.txt", JenisBerkas.BERKAS_TEKS),
            item("Alpha", JenisBerkas.FOLDER)
        )
        val hasil = useCase(daftar, KriteriaUrutan.NAMA, ArahUrutan.NAIK, folderDiAtas = true)
        assertEquals(listOf("Alpha", "Zebra", "apel.txt", "catatan.txt"), hasil.map { it.nama })
    }

    @Test
    fun urutkanUkuranTurun() {
        val daftar = listOf(
            item("kecil.txt", JenisBerkas.BERKAS_TEKS, ukuran = 10L),
            item("besar.txt", JenisBerkas.BERKAS_TEKS, ukuran = 500L),
            item("sedang.txt", JenisBerkas.BERKAS_TEKS, ukuran = 100L)
        )
        val hasil = useCase(daftar, KriteriaUrutan.UKURAN, ArahUrutan.TURUN, folderDiAtas = false)
        assertEquals(listOf("besar.txt", "sedang.txt", "kecil.txt"), hasil.map { it.nama })
    }

    @Test
    fun urutkanTanggalNaik() {
        val daftar = listOf(
            item("kemarin.txt", JenisBerkas.BERKAS_TEKS, waktu = 100L),
            item("besok.txt", JenisBerkas.BERKAS_TEKS, waktu = 300L),
            item("hari_ini.txt", JenisBerkas.BERKAS_TEKS, waktu = 200L)
        )
        val hasil = useCase(daftar, KriteriaUrutan.TANGGAL, ArahUrutan.NAIK, folderDiAtas = false)
        assertEquals(listOf("kemarin.txt", "hari_ini.txt", "besok.txt"), hasil.map { it.nama })
    }
}