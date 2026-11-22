package org.fm.com

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.fm.com.di.buatKontainerPreview
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.editor.IntentEditor
import org.fm.com.presentation.editor.LayarEditor
import org.fm.com.presentation.editor.ViewModelEditor
import org.fm.com.presentation.tema.TemaAplikasi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogSimpanTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tutupTabBelumTersimpanMemunculkanDialogSimpanBuangBatalDanMenyimpanDenganBenar() {
        val kontainer = buatKontainerPreview()
        val vmEditor = ViewModelEditor(
            repositoriBerkas = kontainer.repositoriBerkas,
            riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
            simpanBerkasUseCase = kontainer.simpanBerkasUseCase
        )
        val lokasiReadme = LokasiBerkas("/README.md", JenisLokasi.POSIX, "README.md")
        vmEditor.prosesIntent(
            IntentEditor.BukaBerkas(
                EntitasBerkas(
                    lokasi = lokasiReadme,
                    nama = "README.md",
                    jenis = JenisBerkas.BERKAS_TEKS,
                    dapatDibaca = true,
                    dapatDitulis = true
                )
            )
        )

        composeRule.setContent {
            TemaAplikasi {
                LayarEditor(viewModel = vmEditor)
            }
        }

        // Tunggu isi awal terbuka
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }

        // Ubah isi dokumen -> status berubah menjadi "● diubah"
        composeRule.onAllNodes(hasSetTextAction())[0]
            .performTextReplacement("# File Manager & Code Editor\nDiubah dari UI test")
        composeRule.onNodeWithText("● diubah").assertIsDisplayed()

        // Tekan tutup tab -> muncul dialog Simpan / Buang / Batal
        composeRule.onNodeWithContentDescription("Tutup tab README.md").performClick()
        composeRule.onNodeWithText("Perubahan Belum Tersimpan").assertIsDisplayed()

        // 1. Pilih Batal -> dialog tertutup dan tab tetap terbuka dengan perubahan
        composeRule.onNodeWithText("Batal").performClick()
        composeRule.onNodeWithText("Perubahan Belum Tersimpan").assertDoesNotExist()
        composeRule.onNodeWithText("● diubah").assertIsDisplayed()

        // 2. Tutup lagi lalu pilih Simpan -> perubahan tersimpan dan tab tertutup
        composeRule.onNodeWithContentDescription("Tutup tab README.md").performClick()
        composeRule.onNodeWithText("Perubahan Belum Tersimpan").assertIsDisplayed()
        composeRule.onNodeWithText("Simpan").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("// belum ada berkas terbuka").assertIsDisplayed()

        // Pastikan isi pada penyimpanan benar-benar berubah sesuai editan
        val isiTersimpan = runBlocking { bacaTeksDariRepo(kontainer.repositoriBerkas, lokasiReadme) }
        assertEquals("# File Manager & Code Editor\nDiubah dari UI test", isiTersimpan)
    }

    @Test
    fun pilihBuangMenutupTabTanpaMenyimpanPerubahanKePenyimpanan() {
        val kontainer = buatKontainerPreview()
        val vmEditor = ViewModelEditor(
            repositoriBerkas = kontainer.repositoriBerkas,
            riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
            simpanBerkasUseCase = kontainer.simpanBerkasUseCase
        )
        val lokasiCatatan = LokasiBerkas("/Dokumen/Catatan.txt", JenisLokasi.POSIX, "Catatan.txt")
        vmEditor.prosesIntent(
            IntentEditor.BukaBerkas(
                EntitasBerkas(
                    lokasi = lokasiCatatan,
                    nama = "Catatan.txt",
                    jenis = JenisBerkas.BERKAS_TEKS,
                    dapatDibaca = true,
                    dapatDitulis = true
                )
            )
        )

        composeRule.setContent {
            TemaAplikasi {
                LayarEditor(viewModel = vmEditor)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodes(hasSetTextAction())[0]
            .performTextReplacement("Perubahan yang akan dibuang")
        composeRule.onNodeWithContentDescription("Tutup tab Catatan.txt").performClick()
        composeRule.onNodeWithText("Perubahan Belum Tersimpan").assertIsDisplayed()

        composeRule.onNodeWithText("Buang").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
        }

        val isiAsli = runBlocking { bacaTeksDariRepo(kontainer.repositoriBerkas, lokasiCatatan) }
        assertEquals("Rencana pengembangan File Manager & Code Editor", isiAsli)
    }

    private suspend fun bacaTeksDariRepo(
        repo: org.fm.com.domain.repository.RepositoriBerkas,
        lokasi: LokasiBerkas
    ): String {
        val sumber = (repo.bukaUntukBaca(lokasi) as HasilOperasi.Sukses).nilai
        val keluar = mutableListOf<Byte>()
        try {
            while (true) {
                when (val pot = sumber.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        for (b in pot.potongan.data) keluar.add(b)
                        if (pot.potongan.selesai) break
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> error(pot.pesan)
                }
            }
        } finally {
            sumber.tutup()
        }
        return keluar.toByteArray().decodeToString()
    }
}