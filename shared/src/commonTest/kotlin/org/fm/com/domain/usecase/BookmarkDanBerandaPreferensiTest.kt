package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.data.persistensi.MemoriRepositoriBeranda
import org.fm.com.data.persistensi.MemoriRepositoriBookmark
import org.fm.com.data.persistensi.MemoriRepositoriPreferensi
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.StatusKetersediaanBookmark

class BookmarkDanBerandaPreferensiTest {

    @Test
    fun bookmarkMenyimpanBerkasFolderArsipDeteksiHilangDanPilihUlang() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/proyek", "proyek", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/cadangan", "cadangan", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(
            DataBerkas("/proyek/Main.kt", "Main.kt", JenisLokasi.POSIX, adalahFolder = false),
            "fun main() {}".encodeToByteArray()
        )
        storage.berkas.simpan(
            DataBerkas("/proyek/paket.zip", "paket.zip", JenisLokasi.POSIX, adalahFolder = false),
            byteArrayOf(1, 2, 3)
        )

        val repoBookmark = MemoriRepositoriBookmark()
        val useCase = BookmarkUseCase(repoBookmark, storage)

        val lokFolder = LokasiBerkas("/proyek", JenisLokasi.POSIX, "proyek")
        val lokBerkas = LokasiBerkas("/proyek/Main.kt", JenisLokasi.POSIX, "Main.kt")
        val lokArsip = LokasiBerkas("/proyek/paket.zip", JenisLokasi.POSIX, "paket.zip")

        val bFolder = assertIs<HasilOperasi.Sukses<*>>(
            useCase.tambahBookmark(lokFolder, JenisBerkas.FOLDER, adalahFolder = true)
        ).nilai as org.fm.com.domain.model.EntitasBookmark
        val bBerkas = assertIs<HasilOperasi.Sukses<*>>(
            useCase.tambahBookmark(lokBerkas, JenisBerkas.BERKAS_TEKS, adalahFolder = false)
        ).nilai as org.fm.com.domain.model.EntitasBookmark
        val bArsip = assertIs<HasilOperasi.Sukses<*>>(
            useCase.tambahBookmark(lokArsip, JenisBerkas.ARSIP, adalahFolder = false)
        ).nilai as org.fm.com.domain.model.EntitasBookmark

        assertEquals(JenisTargetBookmark.FOLDER, bFolder.jenisTarget)
        assertEquals(JenisTargetBookmark.BERKAS, bBerkas.jenisTarget)
        assertEquals(JenisTargetBookmark.ARSIP, bArsip.jenisTarget)

        // Simulasikan lokasi/arsip hilang dari penyimpanan
        storage.berkas.hapusPohon("/proyek/Main.kt")
        val statusHilang = useCase.periksaKetersediaanSatu(bBerkas)
        assertIs<StatusKetersediaanBookmark.LokasiHilang>(statusHilang)

        // Pilih ulang lokasi ke folder pengganti
        val lokBaru = LokasiBerkas("/cadangan", JenisLokasi.POSIX, "cadangan")
        val terganti = assertIs<HasilOperasi.Sukses<*>>(
            useCase.gantiLokasiBookmark(bFolder, lokBaru, JenisBerkas.FOLDER, adalahFolderBaru = true)
        ).nilai as org.fm.com.domain.model.EntitasBookmark
        assertEquals("/cadangan", terganti.lokasi.pengenal)
        assertIs<StatusKetersediaanBookmark.Tersedia>(useCase.periksaKetersediaanSatu(terganti))
    }

    @Test
    fun berandaTetapkanResetDanTidakMemberiAksesOtomatisSaatHilang() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/bawaan", "Bawaan", JenisLokasi.POSIX, adalahFolder = true))
        storage.berkas.simpan(DataBerkas("/kerja", "Kerja", JenisLokasi.POSIX, adalahFolder = true))

        val bawaan = LokasiBerkas("/bawaan", JenisLokasi.POSIX, "Bawaan")
        val kerja = LokasiBerkas("/kerja", JenisLokasi.POSIX, "Kerja")
        val repoBeranda = MemoriRepositoriBeranda()
        val useCase = BerandaUseCase(repoBeranda, storage, bawaan)

        // Default memakai folder bawaan
        val awal = useCase.selesaikanLokasiAwal()
        assertIs<HasilResolusiBeranda.GunakanBawaan>(awal)
        assertEquals(bawaan, awal.lokasiDigunakan)

        // Tetapkan beranda ke folder kerja
        assertIs<HasilOperasi.Sukses<*>>(useCase.tetapkanBeranda(kerja))
        val khusus = useCase.selesaikanLokasiAwal()
        assertIs<HasilResolusiBeranda.GunakanKhusus>(khusus)
        assertEquals(kerja, khusus.lokasiDigunakan)

        // Jika folder beranda dihapus/izin dicabut -> tidak memberi akses otomatis & fallback ke bawaan
        storage.berkas.hapusPohon("/kerja")
        val hilang = useCase.selesaikanLokasiAwal()
        val statusHilang = assertIs<HasilResolusiBeranda.IzinAtauLokasiHilang>(hilang)
        assertEquals(kerja, statusHilang.lokasiBermasalah)
        assertEquals(bawaan, statusHilang.lokasiDigunakan)

        // Reset ke bawaan
        assertIs<HasilOperasi.Sukses<*>>(useCase.resetKeBawaan())
        assertNull(useCase.ambilKonfigurasi().lokasiKhusus)
        assertIs<HasilResolusiBeranda.GunakanBawaan>(useCase.selesaikanLokasiAwal())
    }

    @Test
    fun preferensiWordWrapUmumTersimpanDanReaktif() = runTest {
        val repo = MemoriRepositoriPreferensi()
        val useCase = PreferensiUseCase(repo)

        assertTrue(useCase.ambilPreferensi().wordWrapUmum)
        assertIs<HasilOperasi.Sukses<Unit>>(useCase.setWordWrapUmum(false))
        assertFalse(useCase.amatiPreferensi().first().wordWrapUmum)
        assertIs<HasilOperasi.Sukses<Unit>>(useCase.setWordWrapUmum(true))
        assertTrue(useCase.ambilPreferensi().wordWrapUmum)
    }
}