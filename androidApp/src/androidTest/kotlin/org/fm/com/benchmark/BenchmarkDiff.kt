@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.benchmark

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.fm.com.data.posix.PosixSumberDataPenyimpanan
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.HasilBandingkanBerkas
import org.fm.com.domain.usecase.HasilBukaBerkasTeks
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkDiff {

    @Test
    fun bukaDanDiffDuaBerkasSatuMegabyteDiAndroidDiBawahTargetP95() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folderUji = File(appContext.cacheDir, "benchmark_diff").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val (teksA, teksB) = BenchmarkDiffCommon.bangunDuaTeksSatuMegabyte()
            val fileA = File(folderUji, "a_1mb.txt").apply { writeText(teksA) }
            val fileB = File(folderUji, "b_1mb.txt").apply { writeText(teksB) }

            val storage = PosixSumberDataPenyimpanan(
                fileSystem = FileSystem.SYSTEM,
                platform = PlatformPenyimpanan.ANDROID
            )
            val lokA = LokasiBerkas(fileA.absolutePath, JenisLokasi.POSIX, fileA.name)
            val lokB = LokasiBerkas(fileB.absolutePath, JenisLokasi.POSIX, fileB.name)

            val bukaUseCase = BukaBerkasTeksUseCase(storage, batasMaksimalByte = 3L * 1024 * 1024)
            val diffUseCase = BandingkanBerkasUseCase(storage, bukaUseCase)

            // Warmup penuh untuk buka teks dan algoritme diff
            runBlocking {
                bukaUseCase(lokA)
                diffUseCase.muatDanBandingkan(lokA, "a.txt", lokB, "b.txt")
            }

            // 1. Ukur Buka Berkas Teks 1 MiB dari disk Android (Target <= 500 ms)
            val durasiBuka = mutableListOf<Long>()
            val timeSource = TimeSource.Monotonic
            repeat(5) {
                val mark = timeSource.markNow()
                val hasilBuka = runBlocking { bukaUseCase(lokA) }
                durasiBuka.add(mark.elapsedNow().inWholeMilliseconds)
                assertTrue(hasilBuka is HasilBukaBerkasTeks.Sukses)
            }
            val p95Buka = BenchmarkDiffCommon.hitungP95(durasiBuka)
            println("BENCHMARK [Android] Buka 1 MiB Disk P95: ${p95Buka}ms (Target PRD <= 500ms)")
            assertTrue(p95Buka <= 500L, "P95 Buka berkas 1 MiB ($p95Buka ms) melebihi target 500 ms")

            // 2. Ukur Diff 2x1 MiB asinkron pada runtime Android ART (Target <= 2000 ms)
            val durasiDiff = mutableListOf<Long>()
            repeat(5) {
                val mark = timeSource.markNow()
                val hasilDiff = runBlocking {
                    diffUseCase.muatDanBandingkan(lokA, "a.txt", lokB, "b.txt")
                }
                durasiDiff.add(mark.elapsedNow().inWholeMilliseconds)
                assertTrue(hasilDiff is HasilBandingkanBerkas.Sukses)
            }
            val p95Diff = BenchmarkDiffCommon.hitungP95(durasiDiff)
            println("BENCHMARK [Android] Diff 2x1 MiB P95: ${p95Diff}ms (Target PRD <= 2000ms)")
            assertTrue(p95Diff <= 2000L, "P95 Diff 2x1 MiB ($p95Diff ms) melebihi target 2000 ms")
        } finally {
            folderUji.deleteRecursively()
        }
    }
}