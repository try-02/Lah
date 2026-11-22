@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.benchmark

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.diff.AlgoritmaMyers
import org.fm.com.domain.diff.OpsiDiff
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.DaftarFolderUseCase
import org.fm.com.domain.usecase.FilterBerkasUseCase
import org.fm.com.domain.usecase.HasilBukaBerkasTeks
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.UrutkanBerkasUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi.keUi

class BenchmarkDiffCommon {

    @Test
    fun diffDuaBerkasSatuMegabyteDiBawahTargetP95DuaDetik() = runTest {
        val (teksA, teksB) = bangunDuaTeksSatuMegabyte()
        assertTrue(teksA.encodeToByteArray().size >= 1024 * 1024)
        assertTrue(teksB.encodeToByteArray().size >= 1024 * 1024)

        // Warmup
        AlgoritmaMyers.bandingkanTeks(teksA.take(10_000), teksB.take(10_000), OpsiDiff())

        val durasiList = mutableListOf<Long>()
        val timeSource = TimeSource.Monotonic
        repeat(5) {
            val mark = timeSource.markNow()
            val hasil = AlgoritmaMyers.bandingkanTeks(teksA, teksB, OpsiDiff())
            durasiList.add(mark.elapsedNow().inWholeMilliseconds)
            assertTrue(hasil.statistik.jumlahBlokPerubahan > 0)
        }

        val p95 = hitungP95(durasiList)
        println("BENCHMARK [Common] Diff 2x1 MiB P95: ${p95}ms (Target PRD <= 2000ms)")
        assertTrue(p95 <= 2000L, "P95 Diff 2x1 MiB ($p95 ms) melebihi target 2000 ms")
    }

    @Test
    fun bukaTeksSatuMegabyteDiBawahTargetP95LimaRatusMilidetik() = runTest {
        val (teks1Mb, _) = bangunDuaTeksSatuMegabyte()
        val bytes = teks1Mb.encodeToByteArray()
        val storage = SumberMemoriPenyimpanan()
        val lokasi = LokasiBerkas("/besar.txt", JenisLokasi.POSIX, "besar.txt")
        storage.berkas.simpan(
            DataBerkas(lokasi.pengenal, "besar.txt", JenisLokasi.POSIX, false, ukuranByte = bytes.size.toLong()),
            bytes
        )

        val useCase = BukaBerkasTeksUseCase(storage, batasMaksimalByte = 2L * 1024 * 1024)
        // Warmup
        useCase(lokasi)

        val durasiList = mutableListOf<Long>()
        val timeSource = TimeSource.Monotonic
        repeat(7) {
            val mark = timeSource.markNow()
            val hasil = useCase(lokasi)
            durasiList.add(mark.elapsedNow().inWholeMilliseconds)
            assertTrue(hasil is HasilBukaBerkasTeks.Sukses)
        }

        val p95 = hitungP95(durasiList)
        println("BENCHMARK [Common] Buka Teks 1 MiB P95: ${p95}ms (Target PRD <= 500ms)")
        assertTrue(p95 <= 500L, "P95 Buka Teks 1 MiB ($p95 ms) melebihi target 500 ms")
    }

    @Test
    fun daftarSeribuItemDiBawahTargetP95LimaRatusMilidetik() = runTest {
        val storage = SumberMemoriPenyimpanan()
        storage.berkas.simpan(DataBerkas("/", "Root", JenisLokasi.POSIX, adalahFolder = true))
        for (i in 1..1000) {
            val nama = "item_${i.toString().padStart(4, '0')}.${if (i % 5 == 0) "kt" else "txt"}"
            val folder = i % 10 == 0
            storage.berkas.simpan(
                DataBerkas(
                    pengenal = "/$nama",
                    nama = nama,
                    jenisLokasi = JenisLokasi.POSIX,
                    adalahFolder = folder,
                    ukuranByte = if (folder) null else (i * 10L),
                    waktuUbahEpochMs = 1_700_000_000_000L + i
                )
            )
        }

        val root = LokasiBerkas("/", JenisLokasi.POSIX, "Root")
        val daftarUseCase = DaftarFolderUseCase(storage)
        val filterUseCase = FilterBerkasUseCase()
        val urutkanUseCase = UrutkanBerkasUseCase()

        // Warmup
        daftarUseCase(root, true).first()

        val durasiList = mutableListOf<Long>()
        val timeSource = TimeSource.Monotonic
        repeat(7) {
            val mark = timeSource.markNow()
            val mentah = daftarUseCase(root, tampilkanTersembunyi = true).first()
            val list = (mentah as org.fm.com.domain.model.HasilOperasi.Sukses<List<EntitasBerkas>>).nilai
            val tersaring = filterUseCase(list, tampilkanTersembunyi = true, kataKunci = "item")
            val terurut = urutkanUseCase(tersaring, KriteriaUrutan.NAMA)
            val uiList = terurut.map { it.keUi() }
            durasiList.add(mark.elapsedNow().inWholeMilliseconds)
            assertTrue(uiList.size == 1000)
        }

        val p95 = hitungP95(durasiList)
        println("BENCHMARK [Common] Daftar 1.000 Item P95: ${p95}ms (Target PRD <= 500ms)")
        assertTrue(p95 <= 500L, "P95 Daftar 1.000 item ($p95 ms) melebihi target 500 ms")
    }

    companion object {
        fun hitungP95(daftarMs: List<Long>): Long {
            require(daftarMs.isNotEmpty())
            val terurut = daftarMs.sorted()
            val idx = ((terurut.size * 95 + 99) / 100) - 1
            return terurut[idx.coerceIn(0, terurut.lastIndex)]
        }

        fun bangunDuaTeksSatuMegabyte(): Pair<String, String> {
            val barisAwal = ArrayList<String>(22_000)
            val barisRevisi = ArrayList<String>(22_000)
            var byteAkumulasi = 0

            var i = 1
            while (byteAkumulasi < 1024 * 1024 + 10_000) {
                val teks = "val konfigurasiBarisNomor$i: String = \"Nilai_Konfigurasi_Sistem_$i\" // metadata catatan"
                barisAwal.add(teks)
                when {
                    i % 20 == 0 -> barisRevisi.add("val konfigurasiBarisNomor$i: String = \"Nilai_DIUBAH_$i\" // modifikasi")
                    i % 35 == 0 -> { /* simulasi baris dihapus di B */ }
                    i % 45 == 0 -> {
                        barisRevisi.add(teks)
                        barisRevisi.add("val barisTambahanUntukUji$i: Int = $i")
                    }
                    else -> barisRevisi.add(teks)
                }
                byteAkumulasi += teks.length + 1
                i++
            }
            return barisAwal.joinToString("\n") to barisRevisi.joinToString("\n")
        }
    }
}