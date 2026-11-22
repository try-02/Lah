@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.benchmark

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.usecase.DaftarFolderUseCase
import org.fm.com.domain.usecase.FilterBerkasUseCase
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.UrutkanBerkasUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi.keUi
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkDaftarBerkas {

    @Test
    fun daftarSeribuItemDiskLokalDiBawahTargetP95LimaRatusMilidetik() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folderUji = File(appContext.cacheDir, "benchmark_1000").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            // Siapkan 1.000 file fisik di disk perangkat/emulator
            for (i in 1..1000) {
                val f = File(folderUji, "item_${i.toString().padStart(4, '0')}.txt")
                f.writeText("konten $i")
            }

            val storagePosix = PosixSumberDataPenyimpanan(
                fileSystem = FileSystem.SYSTEM,
                platform = PlatformPenyimpanan.ANDROID
            )
            val lokasi = LokasiBerkas(folderUji.absolutePath, JenisLokasi.POSIX, "benchmark_1000")
            val daftarFolder = DaftarFolderUseCase(storagePosix)
            val filter = FilterBerkasUseCase()
            val urutkan = UrutkanBerkasUseCase()

            // Warmup
            runBlocking { daftarFolder(lokasi, true).first() }

            val timeSource = TimeSource.Monotonic
            val durasiList = mutableListOf<Long>()

            repeat(7) {
                val mark = timeSource.markNow()
                val hasil = runBlocking {
                    val mentah = (daftarFolder(lokasi, true).first() as HasilOperasi.Sukses).nilai
                    val tersaring = filter(mentah, tampilkanTersembunyi = true, kataKunci = "")
                    val terurut = urutkan(tersaring, KriteriaUrutan.NAMA)
                    terurut.map { it.keUi() }
                }
                durasiList.add(mark.elapsedNow().inWholeMilliseconds)
                assertTrue(hasil.size == 1000)
            }

            val p95 = BenchmarkDiffCommon.hitungP95(durasiList)
            println("BENCHMARK [Android] Tampilan Awal 1.000 Item Disk P95: ${p95}ms (Target PRD <= 500ms)")
            assertTrue(p95 <= 500L, "P95 Daftar 1.000 item disk ($p95 ms) melebihi target 500 ms")
        } finally {
            folderUji.deleteRecursively()
        }
    }
}