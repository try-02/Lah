package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class FilterBerkasUseCaseTest {

    private val useCase = FilterBerkasUseCase()

    private fun item(nama: String, tersembunyi: Boolean = false) = EntitasBerkas(
        lokasi = LokasiBerkas("/root/$nama", JenisLokasi.POSIX, nama),
        nama = nama,
        jenis = JenisBerkas.BERKAS_TEKS,
        tersembunyi = tersembunyi
    )

    @Test
    fun menyaringFileTersembunyiDanKueriPencarian() {
        val daftar = listOf(
            item("catatan.txt", tersembunyi = false),
            item(".gitignore", tersembunyi = true),
            item("foto.png", tersembunyi = false)
        )

        val tanpaTersembunyi = useCase(daftar, tampilkanTersembunyi = false)
        assertEquals(listOf("catatan.txt", "foto.png"), tanpaTersembunyi.map { it.nama })

        val denganKueri = useCase(daftar, tampilkanTersembunyi = true, kataKunci = "git")
        assertEquals(listOf(".gitignore"), denganKueri.map { it.nama })
    }
}