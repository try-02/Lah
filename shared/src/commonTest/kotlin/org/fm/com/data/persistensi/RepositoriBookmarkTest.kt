package org.fm.com.data.persistensi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.StatusKetersediaanBookmark
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.BookmarkUseCase

class RepositoriBookmarkTest {

    @Test
    fun simpanToggleDeteksiPencabutanIzinDanGantiLokasiBookmark() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/dok", "dok", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/cadangan", "cadangan", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/dok/arsip.tar.gz", "arsip.tar.gz", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(1, 2)
        )

        var izinSafAktif = true
        val lokasiSaf = LokasiBerkas("content://com.android.externalstorage/tree/123", JenisLokasi.SAF, "SDCard")
        val repoHybrid = object : RepositoriBerkas {
            override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? = storage.kapabilitas(lokasi)
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> {
                if (lokasi == lokasiSaf) {
                    return if (izinSafAktif) {
                        flowOf(HasilOperasi.Sukses(emptyList()))
                    } else {
                        flowOf(HasilOperasi.Gagal("Izin SAF telah dicabut pengguna", PenyebabGalat.IZIN_DITOLAK))
                    }
                }
                return storage.daftarIsi(lokasi)
            }
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
                storage.bukaUntukBaca(lokasi)
            override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
                storage.bukaUntukTulis(lokasi, timpa)
            override suspend fun bukaUntukTulisDi(induk: LokasiBerkas, nama: String, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
                storage.bukaUntukTulisDi(induk, nama, timpa)
        }

        val repoBookmark = MemoriRepositoriBookmark()
        val useCase = BookmarkUseCase(repoBookmark, repoHybrid)

        // Toggle tambah lalu hapus
        val tambah = assertIs<HasilOperasi.Sukses<Boolean>>(
            useCase.toggleBookmark(LokasiBerkas("/dok/arsip.tar.gz", JenisLokasi.POSIX, "arsip.tar.gz"), JenisBerkas.ARSIP)
        )
        assertTrue(tambah.nilai)
        assertTrue(useCase.apakahSudahDibookmark(LokasiBerkas("/dok/arsip.tar.gz", JenisLokasi.POSIX, "arsip.tar.gz")))

        val hapus = assertIs<HasilOperasi.Sukses<Boolean>>(
            useCase.toggleBookmark(LokasiBerkas("/dok/arsip.tar.gz", JenisLokasi.POSIX, "arsip.tar.gz"), JenisBerkas.ARSIP)
        )
        assertFalse(hapus.nilai)
        assertFalse(useCase.apakahSudahDibookmark(LokasiBerkas("/dok/arsip.tar.gz", JenisLokasi.POSIX, "arsip.tar.gz")))

        // Simpan SAF folder lalu simulasikan izin dicabut
        val bmSaf = assertIs<HasilOperasi.Sukses<EntitasBookmark>>(
            useCase.tambahBookmark(lokasiSaf, JenisBerkas.FOLDER, adalahFolder = true)
        ).nilai
        assertEquals(JenisTargetBookmark.FOLDER, bmSaf.jenisTarget)
        assertIs<StatusKetersediaanBookmark.Tersedia>(useCase.periksaKetersediaanSatu(bmSaf))

        izinSafAktif = false
        val daftarCek = useCase.periksaKetersediaanDaftar(repoBookmark.daftarBookmark().first())
        assertIs<StatusKetersediaanBookmark.IzinDicabut>(daftarCek.single().status)

        // Tolak pilih ulang ke lokasi yang masih tidak tersedia
        val tolakPengganti = useCase.gantiLokasiBookmark(bmSaf, lokasiSaf, JenisBerkas.FOLDER, true)
        assertEquals(PenyebabGalat.IZIN_DITOLAK, assertIs<HasilOperasi.Gagal>(tolakPengganti).penyebab)

        // Pilih ulang ke lokasi baru yang sah
        val lokasiPengganti = LokasiBerkas("/cadangan", JenisLokasi.POSIX, "cadangan")
        val hasilGanti = assertIs<HasilOperasi.Sukses<EntitasBookmark>>(
            useCase.gantiLokasiBookmark(bmSaf, lokasiPengganti, JenisBerkas.FOLDER, true)
        ).nilai
        assertEquals(lokasiPengganti, hasilGanti.lokasi)
        assertNull(repoBookmark.cariBookmark(lokasiSaf))
        assertNotNull(repoBookmark.cariBookmark(lokasiPengganti))
    }
}