@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.benchmark

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.TimeSource
import kotlinx.coroutines.runBlocking
import org.fm.com.di.buatKontainerAndroid
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkStartup {

    @Test
    fun startupInisialisasiKontainerDanLokasiAwalDiBawahTargetP95SatuKomaLimaDetik() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val timeSource = TimeSource.Monotonic
        val durasiList = mutableListOf<Long>()

        // 1 iterasi cold start murni + 4 iterasi konsistensi
        repeat(5) {
            val mark = timeSource.markNow()
            val kontainerAndroid = buatKontainerAndroid(appContext)
            val (lokasiAwal, pintasan) = runBlocking { kontainerAndroid.muatLokasiAwalDanPintasan() }
            val kontainerApp = kontainerAndroid.keKontainerAplikasi(lokasiAwal, pintasan)
            durasiList.add(mark.elapsedNow().inWholeMilliseconds)
            assertTrue(kontainerApp.lokasiPintasan.isNotEmpty())
        }

        val p95 = BantuBenchmark.hitungP95(durasiList)
        println("BENCHMARK [Android] Cold Start P95: ${p95}ms (Target PRD <= 1500ms)")
        assertTrue("P95 Cold Start ($p95 ms) melebihi target 1500 ms", p95 <= 1500L)
    }
}