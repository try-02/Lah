package org.fm.com

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.presentation.diff.IntentDiff
import org.fm.com.presentation.diff.LayarDiff
import org.fm.com.presentation.diff.ViewModelDiff
import org.fm.com.presentation.tema.TemaAplikasi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiffModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun peralihanModeBerdampinganKeUnifiedPenandaNonWarnaDanLompatPerubahanBekerja() {
        val lokA = LokasiBerkas("/lama.kt", JenisLokasi.POSIX, "lama.kt")
        val lokB = LokasiBerkas("/baru.kt", JenisLokasi.POSIX, "baru.kt")
        val repoUji = RepositoriMemoriSederhana(
            mapOf(
                lokA to "baris satu\nbaris lama\nbaris tiga".encodeToByteArray(),
                lokB to "baris satu\nbaris baru\nbaris tiga\nbaris tambahan".encodeToByteArray()
            )
        )
        val vmDiff = ViewModelDiff(BandingkanBerkasUseCase(repoUji))
        vmDiff.prosesIntent(
            IntentDiff.Bandingkan(
                lokasiA = lokA,
                namaA = "lama.kt",
                lokasiB = lokB,
                namaB = "baru.kt"
            )
        )

        composeRule.setContent {
            TemaAplikasi {
                LayarDiff(viewModel = vmDiff, onKembali = {})
            }
        }

        // Mode awal: Berdampingan
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("A: lama.kt").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("A: lama.kt").assertIsDisplayed()
        composeRule.onNodeWithText("B: baru.kt").assertIsDisplayed()
        composeRule.onNodeWithText("baris lama").assertIsDisplayed()
        composeRule.onNodeWithText("baris baru").assertIsDisplayed()
        composeRule.onNodeWithText("baris tambahan").assertIsDisplayed()

        // Penanda non-warna di aksesibilitas gutter & navigasi blok perubahan (1/2 -> 2/2)
        composeRule.onNodeWithContentDescription("Diubah baris 2").assertExists()
        composeRule.onNodeWithContentDescription("Ditambah baris 4").assertExists()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Perubahan berikutnya").performClick()
        composeRule.onNodeWithText("2/2").assertIsDisplayed()

        // Beralih ke mode Unified
        composeRule.onNodeWithText("Unified").performClick()
        composeRule.onNodeWithText("A: lama.kt").assertDoesNotExist()
        composeRule.onNodeWithText("B: baru.kt").assertDoesNotExist()
        composeRule.onNodeWithText("baris lama").assertIsDisplayed()
        composeRule.onNodeWithText("baris baru").assertIsDisplayed()
        composeRule.onNodeWithText("baris tambahan").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ditambah A:- B:4").assertExists()

        // Kembali ke mode Berdampingan
        composeRule.onNodeWithText("Berdampingan").performClick()
        composeRule.onNodeWithText("A: lama.kt").assertIsDisplayed()
        composeRule.onNodeWithText("B: baru.kt").assertIsDisplayed()
    }

    private class RepositoriMemoriSederhana(
        private val berkas: Map<LokasiBerkas, ByteArray>
    ) : RepositoriBerkas {
        override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? = null

        override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
            flowOf(HasilOperasi.Gagal("Tidak dipakai", PenyebabGalat.TIDAK_DIDUKUNG))

        override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
            val bytes = berkas[lokasi]
                ?: return HasilOperasi.Gagal("Tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            var sudahBaca = false
            return HasilOperasi.Sukses(
                object : SumberIsiBerkas {
                    override val ukuranByte: Long = bytes.size.toLong()
                    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan {
                        if (sudahBaca) return SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
                        sudahBaca = true
                        return SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(bytes, selesai = true))
                    }
                    override suspend fun tutup() = Unit
                }
            )
        }

        override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
            HasilOperasi.Gagal("Hanya baca", PenyebabGalat.TIDAK_DIDUKUNG)

        override suspend fun bukaUntukTulisDi(
            induk: LokasiBerkas,
            nama: String,
            timpa: Boolean
        ): HasilOperasi<SumberTulisBerkas> =
            HasilOperasi.Gagal("Hanya baca", PenyebabGalat.TIDAK_DIDUKUNG)
    }
}