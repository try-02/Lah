package org.fm.com.benchmark

object BantuBenchmark {

    fun hitungP95(daftarMs: List<Long>): Long {
        require(daftarMs.isNotEmpty()) { "Daftar durasi tidak boleh kosong" }
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
                i % 200 in 0..1 -> barisRevisi.add("val konfigurasiBarisNomor$i: String = \"Nilai_DIUBAH_$i\" // modifikasi")
                i % 350 == 0 -> { /* simulasi baris dihapus di B */ }
                i % 450 == 0 -> {
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