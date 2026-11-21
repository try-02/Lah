### 1. Perbaikan `saringKonteks` di `shared/src/commonMain/kotlin/org/fm/com/domain/diff/AlgoritmaMyers.kt`
Tambahkan penutupan pemisah konteks bila masih ada baris identik tersembunyi di akhir file setelah loop `for (i in berdampinganSemua.indices)` dan setelah loop `for (i in unifiedSemua.indices)`:

Pada blok `hasilBerdampingan` (tepat setelah penutup `for (i in berdampinganSemua.indices)`):
```kotlin
        if (diDalamSkip && jumlahTersembunyi > 0) {
            val info = "@@ $jumlahTersembunyi baris sama disembunyikan @@"
            hasilBerdampingan.add(
                BarisDiffBerdampingan(
                    indeks = hasilBerdampingan.size,
                    kiri = SelBarisDiff(null, info, JenisBarisDiff.KOSONG),
                    kanan = SelBarisDiff(null, info, JenisBarisDiff.KOSONG)
                )
            )
        }
```

Dan pada blok `hasilUnified` (tepat setelah penutup `for (i in unifiedSemua.indices)`):
```kotlin
        if (diDalamSkip && jumlahTersembunyi > 0) {
            hasilUnified.add(
                BarisDiffUnified(
                    indeks = hasilUnified.size,
                    nomorBarisA = null,
                    nomorBarisB = null,
                    jenis = JenisBarisDiff.KOSONG,
                    teks = "",
                    keteranganPemisahKonteks = "@@ $jumlahTersembunyi baris sama disembunyikan @@"
                )
            )
        }
```

---

### 2. Perbarui Asersi di `shared/src/commonTest/kotlin/org/fm/com/domain/diff/AlgoritmaMyersTest.kt`
Ganti bagian akhir fungsi `opsiAbaikanWhitespacePekaHurufDanKonteksBekerja`:
```kotlin
        val hanyaBedaKonteks = AlgoritmaMyers.bandingkanTeks(
            "1\n2\n3\n4\n5\nlama\n7\n8\n9\n10",
            "1\n2\n3\n4\n5\nbaru\n7\n8\n9\n10",
            OpsiDiff(tampilkanBarisSama = false, jumlahBarisKonteks = 1)
        )
        // 4 baris tersembunyi di awal + baris 5, baris 6 (ubah), baris 7 + 3 baris tersembunyi di akhir
        val barisIsi = hanyaBedaKonteks.barisBerdampingan.filterNot {
            it.kiri.jenis == JenisBarisDiff.KOSONG && it.kanan.jenis == JenisBarisDiff.KOSONG
        }
        assertEquals(3, barisIsi.size)
        assertEquals(listOf("5", "lama", "7"), barisIsi.map { it.kiri.teks })
        assertEquals("@@ 4 baris sama disembunyikan @@", hanyaBedaKonteks.barisBerdampingan.first().kiri.teks)
        assertEquals("@@ 3 baris sama disembunyikan @@", hanyaBedaKonteks.barisBerdampingan.last().kiri.teks)
```

---
