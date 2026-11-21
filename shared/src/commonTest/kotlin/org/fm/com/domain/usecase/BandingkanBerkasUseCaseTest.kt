@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class BandingkanBerkasUseCaseTest {

    @Test
    fun membandingkanDuaBerkasTeksDanMenolakBiner() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/a.txt", "a.txt", JenisLokasi.POSIX, adalahFolder = false), "satu\ndua\ntiga".encodeToByteArray())
        repo.simpan(DataBerkas("/b.txt", "b.txt", JenisLokasi.POSIX, adalahFolder = false), "satu\nDUA\ntiga\nempat".encodeToByteArray())
        repo.simpan(DataBerkas("/bin.dat", "bin.dat", JenisLokasi.POSIX, adalahFolder = false), byteArrayOf(0x00, 0x01, 0x02))

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BandingkanBerkasUseCase(
            repositoriBerkas = repo,
            bukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repo, ioDispatcher = dispatcher, computeDispatcher = dispatcher),
            computeDispatcher = dispatcher
        )

        val lokA = LokasiBerkas("/a.txt", JenisLokasi.POSIX, "a.txt")
        val lokB = LokasiBerkas("/b.txt", JenisLokasi.POSIX, "b.txt")
        val lokBin = LokasiBerkas("/bin.dat", JenisLokasi.POSIX, "bin.dat")

        val sukses = assertIs<HasilBandingkanBerkas.Sukses>(
            uc.muatDanBandingkan(lokA, "a.txt", lokB, "b.txt")
        )
        assertEquals(2, sukses.hasilDiff.statistik.jumlahBlokPerubahan)
        assertEquals(1, sukses.hasilDiff.statistik.jumlahBarisHapus)
        assertEquals(2, sukses.hasilDiff.statistik.jumlahBarisTambah)

        val biner = assertIs<HasilBandingkanBerkas.BinerDitolak>(
            uc.muatDanBandingkan(lokA, "a.txt", lokBin, "bin.dat")
        )
        assertTrue(biner.alasan.contains("biner", ignoreCase = true))
    }
}