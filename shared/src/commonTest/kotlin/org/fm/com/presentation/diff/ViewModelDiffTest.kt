@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.fm.com.presentation.diff

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.domain.diff.ModeWhitespaceDiff
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase

class ViewModelDiffTest {
    @BeforeTest
    fun siapkanMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun pulihkanMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun gantiOpsiTidakMembacaDiskUlangDanNavigasiPerubahanBekerja() = runTest {
        val repoAsli = ImplementasiRepositoriBerkas()
        repoAsli.simpan(DataBerkas("/a.kt", "a.kt", JenisLokasi.POSIX, adalahFolder = false), "awal\n  Halo\ntengah\nlama".encodeToByteArray())
        repoAsli.simpan(DataBerkas("/b.kt", "b.kt", JenisLokasi.POSIX, adalahFolder = false), "awal\nhalo\ntengah\nbaru".encodeToByteArray())

        var jumlahBacaDisk = 0
        val repoPantau = object : RepositoriBerkas by repoAsli {
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
                jumlahBacaDisk++
                return repoAsli.bukaUntukBaca(lokasi)
            }
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val uc = BandingkanBerkasUseCase(
            repositoriBerkas = repoPantau,
            bukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repoPantau, ioDispatcher = dispatcher, computeDispatcher = dispatcher),
            computeDispatcher = dispatcher
        )
        val vm = ViewModelDiff(uc)

        vm.prosesIntent(
            IntentDiff.Bandingkan(
                lokasiA = LokasiBerkas("/a.kt", JenisLokasi.POSIX, "a.kt"),
                namaA = "a.kt",
                lokasiB = LokasiBerkas("/b.kt", JenisLokasi.POSIX, "b.kt"),
                namaB = "b.kt"
            )
        )
        val stAwal = vm.state.first { !it.sedangMemuat && !it.sedangMenghitung && it.statistik != null }
        assertNull(stAwal.pesanGalat)
        assertEquals(2, jumlahBacaDisk)
        assertEquals(2, stAwal.daftarPerubahan.size)
        assertEquals(0, stAwal.indeksPerubahanAktif)

        // Lompat ke perubahan berikutnya & kembali ke pertama
        vm.prosesIntent(IntentDiff.LompatPerubahanBerikutnya)
        assertEquals(1, vm.state.value.indeksPerubahanAktif)
        vm.prosesIntent(IntentDiff.LompatBarisBedaPertama)
        assertEquals(0, vm.state.value.indeksPerubahanAktif)

        // Ganti opsi abaikan whitespace & huruf -> hitung ulang dari memori (jumlahBacaDisk tetap 2!)
        vm.prosesIntent(IntentDiff.TogglePekaHuruf)
        vm.prosesIntent(IntentDiff.UbahModeWhitespace(ModeWhitespaceDiff.ABAIKAN_TEPI))
        val stOpsi = vm.state.first { !it.sedangMenghitung && it.daftarPerubahan.size == 1 }
        assertEquals(2, jumlahBacaDisk)
        assertNotNull(stOpsi.pesanPengabaianAktif)
        assertFalse(stOpsi.statistik!!.identik)

        // Ekspor patch
        vm.prosesIntent(IntentDiff.SiapkanEksporPatch)
        val patch = vm.state.first { it.patchUntukDisalin != null }.patchUntukDisalin!!
        assertTrue(patch.contains("-lama"))
        assertTrue(patch.contains("+baru"))
    }
}