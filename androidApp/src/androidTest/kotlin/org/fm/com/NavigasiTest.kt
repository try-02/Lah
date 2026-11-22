package org.fm.com

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.fm.com.di.buatKontainerPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigasiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigasiLimaTabUtamaDanMultiSelectBerjalanBenar() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))
            ) {
                App(kontainer = buatKontainerPreview())
            }
        }

        // Tab File awal tampil
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dokumen").assertIsDisplayed()
        composeRule.onNodeWithText("README.md").assertIsDisplayed()

        // Multi-select via long-click pada item lalu tambah item kedua & batalkan
        composeRule.onNodeWithText("Dokumen").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 dipilih").assertIsDisplayed()

        composeRule.onNodeWithText("README.md").performClick()
        composeRule.onNodeWithText("2 dipilih").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Batalkan pilihan").performClick()
        composeRule.onNodeWithText("1 dipilih").assertDoesNotExist()
        composeRule.onNodeWithText("2 dipilih").assertDoesNotExist()

        // Navigasi ke seluruh 5 tab utama
        composeRule.onNodeWithContentDescription("Tab Lokasi Terbaru").performClick()
        composeRule.onNodeWithText("Lokasi Terbaru").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tab Bookmark").performClick()
        composeRule.onAllNodesWithText("Bookmark").assertCountEquals(2)
        composeRule.onNodeWithText("// belum ada bookmark tersimpan").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tab Pencarian Berkas").performClick()
        composeRule.onNodeWithText("Cari Nama").assertIsDisplayed()
        composeRule.onNodeWithText("Cari Isi").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tab Pengaturan").performClick()
        composeRule.onNodeWithText("Homepage (Folder Awal Tab File)").assertIsDisplayed()
        composeRule.onNodeWithText("Word Wrap Bawaan (Umum)").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Tab File Manager").performClick()
        composeRule.onNodeWithText("Dokumen").assertIsDisplayed()
    }

    @Test
    fun tataLetakAdaptifBerubahAntaraNavigationBarDanNavigationRail() {
        // 1. Layar sempit (< 600dp) memakai NavigationBar horizontal di bawah
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 640.dp))
            ) {
                App(kontainer = buatKontainerPreview())
            }
        }
        composeRule.waitForIdle()
        val batasFileSempit = composeRule
            .onNodeWithContentDescription("Tab File Manager")
            .getUnclippedBoundsInRoot()
        val batasAturSempit = composeRule
            .onNodeWithContentDescription("Tab Pengaturan")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Pada layar sempit tab harus sejajar horizontal (NavigationBar)",
            abs(batasFileSempit.top.value - batasAturSempit.top.value) < 12f
        )
    }

    @Test
    fun tataLetakLayarLebarMemakaiNavigationRailVertikal() {
        // 2. Layar lebar (>= 600dp) memakai NavigationRail vertikal di samping
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(800.dp, 640.dp))
            ) {
                App(kontainer = buatKontainerPreview())
            }
        }
        composeRule.waitForIdle()
        val batasFileLebar = composeRule
            .onNodeWithContentDescription("Tab File Manager")
            .getUnclippedBoundsInRoot()
        val batasAturLebar = composeRule
            .onNodeWithContentDescription("Tab Pengaturan")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Pada layar lebar tab harus tersusun vertikal (NavigationRail)",
            batasAturLebar.top > batasFileLebar.bottom
        )
    }
}