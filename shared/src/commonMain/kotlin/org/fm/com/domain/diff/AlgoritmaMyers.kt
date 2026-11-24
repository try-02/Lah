package org.fm.com.domain.diff

private const val BATAS_D_MAKS_PER_SUBRENTANG = 4096
private const val BATAS_PANJANG_BARIS_DIFF_KARAKTER = 1024
private const val INTERVAL_CEK_BATAL = 256

object AlgoritmaMyers {

    internal enum class JenisOp { SAMA, HAPUS, TAMBAH }

    internal data class OpBaris(
        val jenis: JenisOp,
        val indeksA: Int?,
        val indeksB: Int?
    )

    private data class MiddleSnake(
        val xAwal: Int,
        val yAwal: Int,
        val xAkhir: Int,
        val yAkhir: Int
    )

    private sealed interface TugasMyers {
        data class Rentang(val sa: Int, val ea: Int, val sb: Int, val eb: Int) : TugasMyers
        data class TambahSama(val awalA: Int, val awalB: Int, val panjang: Int) : TugasMyers
    }

    fun pecahMenjadiBaris(teks: String): List<String> {
        if (teks.isEmpty()) return emptyList()
        return teks.split('\n')
    }

    fun bandingkanTeks(
        teksA: String,
        teksB: String,
        opsi: OpsiDiff = OpsiDiff(),
        cekBatal: () -> Unit = {}
    ): HasilDiffLengkap {
        val barisA = pecahMenjadiBaris(teksA)
        val barisB = pecahMenjadiBaris(teksB)
        return bandingkanBaris(barisA, barisB, opsi, cekBatal)
    }

    fun bandingkanBaris(
        barisA: List<String>,
        barisB: List<String>,
        opsi: OpsiDiff = OpsiDiff(),
        cekBatal: () -> Unit = {}
    ): HasilDiffLengkap {
        cekBatal()

        val perluNormalisasi = opsi.adaPengabaian
        val normA = if (perluNormalisasi) {
            List(barisA.size) { normalisasiBaris(barisA[it], opsi) }
        } else {
            barisA
        }
        val normB = if (perluNormalisasi) {
            List(barisB.size) { normalisasiBaris(barisB[it], opsi) }
        } else {
            barisB
        }

        val hashA = IntArray(normA.size) { normA[it].hashCode() }
        val hashB = IntArray(normB.size) { normB[it].hashCode() }

        val nTotal = barisA.size
        val mTotal = barisB.size

        // Trim common prefix & suffix sebelum alokasi buffer Myers
        var prefix = 0
        val minLen = minOf(nTotal, mTotal)
        while (prefix < minLen &&
            hashA[prefix] == hashB[prefix] &&
            normA[prefix] == normB[prefix]
        ) {
            prefix++
            if ((prefix and 0xFF) == 0) cekBatal()
        }

        var suffix = 0
        val sisaMin = minLen - prefix
        while (suffix < sisaMin &&
            hashA[nTotal - 1 - suffix] == hashB[mTotal - 1 - suffix] &&
            normA[nTotal - 1 - suffix] == normB[mTotal - 1 - suffix]
        ) {
            suffix++
            if ((suffix and 0xFF) == 0) cekBatal()
        }

        val operasi = ArrayList<OpBaris>(nTotal + mTotal)
        for (i in 0 until prefix) {
            operasi.add(OpBaris(JenisOp.SAMA, i, i))
        }

        val aStart = prefix
        val aEnd = nTotal - suffix
        val bStart = prefix
        val bEnd = mTotal - suffix

        var terpotongBatas = false
        val nTengah = aEnd - aStart
        val mTengah = bEnd - bStart
        if (nTengah > 0 || mTengah > 0) {
            val maksDGlobal = minOf((nTengah + mTengah + 1) / 2, BATAS_D_MAKS_PER_SUBRENTANG)
            val panjangV = 2 * maksDGlobal + 6
            val vMaju = IntArray(panjangV)
            val vMundur = IntArray(panjangV)
            val langkahCek = intArrayOf(0)

            fun sama(idxA: Int, idxB: Int): Boolean =
                hashA[idxA] == hashB[idxB] && normA[idxA] == normB[idxB]

            fun hitungMiddleSnake(
                sa: Int,
                ea: Int,
                sb: Int,
                eb: Int
            ): MiddleSnake? {
                val n = ea - sa
                val m = eb - sb
                val delta = n - m
                val ganjil = (delta and 1) != 0
                val maksD = minOf((n + m + 1) / 2, BATAS_D_MAKS_PER_SUBRENTANG)
                val offset = maksD + 2

                vMaju[offset + 1] = 0
                vMundur[offset + 1] = n + 1

                for (d in 0..maksD) {
                    if ((++langkahCek[0] and 0x1F) == 0) cekBatal()

                    // Langkah maju
                    var k = -d
                    while (k <= d) {
                        val pilihTurun = k == -d ||
                            (k != d && vMaju[offset + k - 1] < vMaju[offset + k + 1])
                        val xAwal = if (pilihTurun) {
                            vMaju[offset + k + 1]
                        } else {
                            vMaju[offset + k - 1] + 1
                        }
                        val yAwal = xAwal - k
                        var x = xAwal
                        var y = yAwal
                        while (x < n && y < m && sama(sa + x, sb + y)) {
                            x++
                            y++
                        }
                        vMaju[offset + k] = x

                        if (ganjil) {
                            val c = k - delta
                            if (c >= -(d - 1) && c <= (d - 1) && x >= vMundur[offset + c]) {
                                return MiddleSnake(xAwal, yAwal, x, y)
                            }
                        }
                        k += 2
                    }

                    // Langkah mundur
                    var c = -d
                    while (c <= d) {
                        val pilihKiri = c == -d ||
                            (c != d && vMundur[offset + c + 1] < vMundur[offset + c - 1])
                        val xAkhir = if (pilihKiri) {
                            vMundur[offset + c + 1] - 1
                        } else {
                            vMundur[offset + c - 1]
                        }
                        val yAkhir = xAkhir - (c + delta)
                        var x = xAkhir
                        var y = yAkhir
                        while (x > 0 && y > 0 && sama(sa + x - 1, sb + y - 1)) {
                            x--
                            y--
                        }
                        vMundur[offset + c] = x

                        if (!ganjil) {
                            val kMaju = c + delta
                            if (kMaju >= -d && kMaju <= d && vMaju[offset + kMaju] >= x) {
                                return MiddleSnake(x, y, xAkhir, yAkhir)
                            }
                        }
                        c += 2
                    }
                }
                return null
            }

            val tumpukan = ArrayDeque<TugasMyers>()
            tumpukan.addLast(TugasMyers.Rentang(aStart, aEnd, bStart, bEnd))

                while (tumpukan.isNotEmpty()) {
                when (val tugas = tumpukan.removeLast()) {
                    is TugasMyers.TambahSama -> {
                        for (i in 0 until tugas.panjang) {
                            operasi.add(OpBaris(JenisOp.SAMA, tugas.awalA + i, tugas.awalB + i))
                        }
                    }
                    is TugasMyers.Rentang -> {
                        cekBatal()
                        var curSa = tugas.sa
                        var curEa = tugas.ea
                        var curSb = tugas.sb
                        var curEb = tugas.eb

                        var prefLokal = 0
                        while (curSa + prefLokal < curEa &&
                            curSb + prefLokal < curEb &&
                            sama(curSa + prefLokal, curSb + prefLokal)
                        ) {
                            prefLokal++
                        }
                        for (i in 0 until prefLokal) {
                            operasi.add(OpBaris(JenisOp.SAMA, curSa + i, curSb + i))
                        }
                        curSa += prefLokal
                        curSb += prefLokal

                        var sufLokal = 0
                        while (curEa - 1 - sufLokal >= curSa &&
                            curEb - 1 - sufLokal >= curSb &&
                            sama(curEa - 1 - sufLokal, curEb - 1 - sufLokal)
                        ) {
                            sufLokal++
                        }
                        curEa -= sufLokal
                        curEb -= sufLokal

                        if (sufLokal > 0) {
                            tumpukan.addLast(TugasMyers.TambahSama(curEa, curEb, sufLokal))
                        }

                        val n = curEa - curSa
                        val m = curEb - curSb
                        when {
                            n == 0 && m == 0 -> Unit
                            n == 0 -> {
                                for (j in curSb until curEb) {
                                    operasi.add(OpBaris(JenisOp.TAMBAH, null, j))
                                }
                            }
                            m == 0 -> {
                                for (i in curSa until curEa) {
                                    operasi.add(OpBaris(JenisOp.HAPUS, i, null))
                                }
                            }
                            else -> {
                                val snake = hitungMiddleSnake(curSa, curEa, curSb, curEb)
                                if (snake == null) {
                                    terpotongBatas = true
                                    for (i in curSa until curEa) {
                                        operasi.add(OpBaris(JenisOp.HAPUS, i, null))
                                    }
                                    for (j in curSb until curEb) {
                                        operasi.add(OpBaris(JenisOp.TAMBAH, null, j))
                                    }
                                } else {
                                    // Dorong terbalik ke stack LIFO: kiri -> snake -> kanan
                                    tumpukan.addLast(
                                        TugasMyers.Rentang(curSa + snake.xAkhir, curEa, curSb + snake.yAkhir, curEb)
                                    )
                                    val snakeLen = snake.xAkhir - snake.xAwal
                                    if (snakeLen > 0) {
                                        tumpukan.addLast(
                                            TugasMyers.TambahSama(curSa + snake.xAwal, curSb + snake.yAwal, snakeLen)
                                        )
                                    }
                                    tumpukan.addLast(
                                        TugasMyers.Rentang(curSa, curSa + snake.xAwal, curSb, curSb + snake.yAwal)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        for (i in 0 until suffix) {
            operasi.add(OpBaris(JenisOp.SAMA, aEnd + i, bEnd + i))
        }

        return bangunHasilPresentasi(barisA, barisB, operasi, opsi, terpotongBatas, cekBatal)
    }

    fun bentukUnifiedPatch(
        namaBerkasA: String,
        namaBerkasB: String,
        teksA: String,
        teksB: String,
        opsi: OpsiDiff = OpsiDiff(tampilkanBarisSama = false, jumlahBarisKonteks = 3)
    ): String {
        val hasil = bandingkanTeks(
            teksA = teksA,
            teksB = teksB,
            opsi = opsi.copy(tampilkanBarisSama = false)
        )
        if (hasil.statistik.identik) {
            return "--- a/$namaBerkasA\n+++ b/$namaBerkasB\n(berkas identik)\n"
        }
        val sb = StringBuilder()
        sb.append("--- a/").append(namaBerkasA).append('\n')
        sb.append("+++ b/").append(namaBerkasB).append('\n')

        val daftar = hasil.barisUnified
        var idx = 0
        while (idx < daftar.size) {
            if (daftar[idx].jenis == JenisBarisDiff.KOSONG) {
                idx++
                continue
            }
            val akhirHunk = run {
                var j = idx
                while (j < daftar.size && daftar[j].jenis != JenisBarisDiff.KOSONG) j++
                j
            }
            val sub = daftar.subList(idx, akhirHunk)
            val awalA = sub.firstNotNullOfOrNull { it.nomorBarisA } ?: 0
            val awalB = sub.firstNotNullOfOrNull { it.nomorBarisB } ?: 0
            val jmlA = sub.count { it.nomorBarisA != null }
            val jmlB = sub.count { it.nomorBarisB != null }
            sb.append("@@ -").append(awalA).append(',').append(jmlA)
                .append(" +").append(awalB).append(',').append(jmlB).append(" @@\n")
            for (b in sub) {
                val prefiks = when (b.jenis) {
                    JenisBarisDiff.SAMA -> ' '
                    JenisBarisDiff.TAMBAH -> '+'
                    JenisBarisDiff.HAPUS, JenisBarisDiff.UBAH -> '-'
                    JenisBarisDiff.KOSONG -> ' '
                }
                sb.append(prefiks).append(b.teks).append('\n')
            }
            idx = akhirHunk
        }
        return sb.toString()
    }

    private fun bangunHasilPresentasi(
        barisA: List<String>,
        barisB: List<String>,
        operasi: List<OpBaris>,
        opsi: OpsiDiff,
        terpotongBatas: Boolean,
        cekBatal: () -> Unit
    ): HasilDiffLengkap {
        val berdampinganSemua = ArrayList<BarisDiffBerdampingan>(operasi.size)
        val unifiedSemua = ArrayList<BarisDiffUnified>(operasi.size)
        val perubahanSementara = mutableListOf<KelompokPerubahanDiff>()

        var jmlSama = 0
        var jmlTambah = 0
        var jmlHapus = 0

        var idx = 0
        while (idx < operasi.size) {
            if ((idx and 0xFF) == 0) cekBatal()
            val op = operasi[idx]
            if (op.jenis == JenisOp.SAMA) {
                val noA = (op.indeksA ?: 0) + 1
                val noB = (op.indeksB ?: 0) + 1
                val teksA = barisA[op.indeksA!!]
                val teksB = barisB[op.indeksB!!]
                berdampinganSemua.add(
                    BarisDiffBerdampingan(
                        indeks = berdampinganSemua.size,
                        kiri = SelBarisDiff(noA, teksA, JenisBarisDiff.SAMA),
                        kanan = SelBarisDiff(noB, teksB, JenisBarisDiff.SAMA),
                        indeksBlokPerubahan = null
                    )
                )
                unifiedSemua.add(
                    BarisDiffUnified(
                        indeks = unifiedSemua.size,
                        nomorBarisA = noA,
                        nomorBarisB = noB,
                        jenis = JenisBarisDiff.SAMA,
                        teks = teksA
                    )
                )
                jmlSama++
                idx++
            } else {
                val daftarHapus = mutableListOf<Int>()
                val daftarTambah = mutableListOf<Int>()
                while (idx < operasi.size && operasi[idx].jenis != JenisOp.SAMA) {
                    val item = operasi[idx]
                    if (item.jenis == JenisOp.HAPUS) {
                        daftarHapus.add(item.indeksA!!)
                    } else {
                        daftarTambah.add(item.indeksB!!)
                    }
                    idx++
                }
                jmlHapus += daftarHapus.size
                jmlTambah += daftarTambah.size

                val idPerubahan = perubahanSementara.size
                val idxBerdampinganPertama = berdampinganSemua.size
                val idxUnifiedPertama = unifiedSemua.size

                val pasangan = minOf(daftarHapus.size, daftarTambah.size)
                val maksBaris = maxOf(daftarHapus.size, daftarTambah.size)

                val rentangKiriList = ArrayList<List<RentangKarakterBeda>>(pasangan)
                val rentangKananList = ArrayList<List<RentangKarakterBeda>>(pasangan)
                for (p in 0 until pasangan) {
                    val teksKiri = barisA[daftarHapus[p]]
                    val teksKanan = barisB[daftarTambah[p]]
                    val (rk, rn) = hitungDiffKarakter(teksKiri, teksKanan, opsi.pekaHuruf)
                    rentangKiriList.add(rk)
                    rentangKananList.add(rn)
                }

                for (p in 0 until maksBaris) {
                    val punyaKiri = p < daftarHapus.size
                    val punyaKanan = p < daftarTambah.size
                    val selKiri = if (punyaKiri) {
                        val idxA = daftarHapus[p]
                        SelBarisDiff(
                            nomorBaris = idxA + 1,
                            teks = barisA[idxA],
                            jenis = if (punyaKanan) JenisBarisDiff.UBAH else JenisBarisDiff.HAPUS,
                            rentangKarakterBeda = if (p < pasangan) rentangKiriList[p] else emptyList()
                        )
                    } else {
                        SelBarisDiff(null, "", JenisBarisDiff.KOSONG)
                    }
                    val selKanan = if (punyaKanan) {
                        val idxB = daftarTambah[p]
                        SelBarisDiff(
                            nomorBaris = idxB + 1,
                            teks = barisB[idxB],
                            jenis = if (punyaKiri) JenisBarisDiff.UBAH else JenisBarisDiff.TAMBAH,
                            rentangKarakterBeda = if (p < pasangan) rentangKananList[p] else emptyList()
                        )
                    } else {
                        SelBarisDiff(null, "", JenisBarisDiff.KOSONG)
                    }
                    berdampinganSemua.add(
                        BarisDiffBerdampingan(
                            indeks = berdampinganSemua.size,
                            kiri = selKiri,
                            kanan = selKanan,
                            indeksBlokPerubahan = idPerubahan
                        )
                    )
                }

                for (i in daftarHapus.indices) {
                    val idxA = daftarHapus[i]
                    unifiedSemua.add(
                        BarisDiffUnified(
                            indeks = unifiedSemua.size,
                            nomorBarisA = idxA + 1,
                            nomorBarisB = null,
                            jenis = JenisBarisDiff.HAPUS,
                            teks = barisA[idxA],
                            rentangKarakterBeda = if (i < pasangan) rentangKiriList[i] else emptyList(),
                            indeksBlokPerubahan = idPerubahan
                        )
                    )
                }
                for (j in daftarTambah.indices) {
                    val idxB = daftarTambah[j]
                    unifiedSemua.add(
                        BarisDiffUnified(
                            indeks = unifiedSemua.size,
                            nomorBarisA = null,
                            nomorBarisB = idxB + 1,
                            jenis = JenisBarisDiff.TAMBAH,
                            teks = barisB[idxB],
                            rentangKarakterBeda = if (j < pasangan) rentangKananList[j] else emptyList(),
                            indeksBlokPerubahan = idPerubahan
                        )
                    )
                }

                val awalA = if (daftarHapus.isNotEmpty()) daftarHapus.first() + 1 else 0
                val awalB = if (daftarTambah.isNotEmpty()) daftarTambah.first() + 1 else 0
                perubahanSementara.add(
                    KelompokPerubahanDiff(
                        indeksPerubahan = idPerubahan,
                        indeksBarisBerdampinganPertama = idxBerdampinganPertama,
                        indeksBarisUnifiedPertama = idxUnifiedPertama,
                        barisAwalA = awalA,
                        jumlahBarisA = daftarHapus.size,
                        barisAwalB = awalB,
                        jumlahBarisB = daftarTambah.size
                    )
                )
            }
        }

        val statistik = StatistikDiff(
            jumlahBarisA = barisA.size,
            jumlahBarisB = barisB.size,
            jumlahBarisSama = jmlSama,
            jumlahBarisTambah = jmlTambah,
            jumlahBarisHapus = jmlHapus,
            jumlahBlokPerubahan = perubahanSementara.size
        )

        if (opsi.tampilkanBarisSama || perubahanSementara.isEmpty()) {
            return HasilDiffLengkap(
                barisBerdampingan = berdampinganSemua,
                barisUnified = unifiedSemua,
                daftarPerubahan = perubahanSementara,
                statistik = statistik,
                terpotongBatasKompleksitas = terpotongBatas
            )
        }

        return saringKonteks(
            berdampinganSemua = berdampinganSemua,
            unifiedSemua = unifiedSemua,
            perubahanAwal = perubahanSementara,
            konteks = opsi.jumlahBarisKonteks,
            statistik = statistik,
            terpotongBatas = terpotongBatas
        )
    }

    private fun saringKonteks(
        berdampinganSemua: List<BarisDiffBerdampingan>,
        unifiedSemua: List<BarisDiffUnified>,
        perubahanAwal: List<KelompokPerubahanDiff>,
        konteks: Int,
        statistik: StatistikDiff,
        terpotongBatas: Boolean
    ): HasilDiffLengkap {
        val tampilBerdampingan = BooleanArray(berdampinganSemua.size)
        for (i in berdampinganSemua.indices) {
            if (berdampinganSemua[i].indeksBlokPerubahan != null) {
                val mulai = (i - konteks).coerceAtLeast(0)
                val selesai = (i + konteks).coerceAtMost(berdampinganSemua.lastIndex)
                for (k in mulai..selesai) tampilBerdampingan[k] = true
            }
        }
        val hasilBerdampingan = ArrayList<BarisDiffBerdampingan>()
        val petaIndeksBerdampingan = HashMap<Int, Int>()
        var diDalamSkip = false
        var jumlahTersembunyi = 0

        for (i in berdampinganSemua.indices) {
            if (tampilBerdampingan[i]) {
                if (diDalamSkip && jumlahTersembunyi > 0) {
                    val info = "@@ $jumlahTersembunyi baris sama disembunyikan @@"
                    hasilBerdampingan.add(
                        BarisDiffBerdampingan(
                            indeks = hasilBerdampingan.size,
                            kiri = SelBarisDiff(null, info, JenisBarisDiff.KOSONG),
                            kanan = SelBarisDiff(null, info, JenisBarisDiff.KOSONG)
                        )
                    )
                    jumlahTersembunyi = 0
                }
                diDalamSkip = false
                val item = berdampinganSemua[i]
                item.indeksBlokPerubahan?.let { idP ->
                    if (idP !in petaIndeksBerdampingan) {
                        petaIndeksBerdampingan[idP] = hasilBerdampingan.size
                    }
                }
                hasilBerdampingan.add(item.copy(indeks = hasilBerdampingan.size))
            } else {
                diDalamSkip = true
                jumlahTersembunyi++
            }
        }

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

        val tampilUnified = BooleanArray(unifiedSemua.size)
        for (i in unifiedSemua.indices) {
            if (unifiedSemua[i].indeksBlokPerubahan != null) {
                val mulai = (i - konteks).coerceAtLeast(0)
                val selesai = (i + konteks).coerceAtMost(unifiedSemua.lastIndex)
                for (k in mulai..selesai) tampilUnified[k] = true
            }
        }
        val hasilUnified = ArrayList<BarisDiffUnified>()
        val petaIndeksUnified = HashMap<Int, Int>()
        diDalamSkip = false
        jumlahTersembunyi = 0

        for (i in unifiedSemua.indices) {
            if (tampilUnified[i]) {
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
                    jumlahTersembunyi = 0
                }
                diDalamSkip = false
                val item = unifiedSemua[i]
                item.indeksBlokPerubahan?.let { idP ->
                    if (idP !in petaIndeksUnified) {
                        petaIndeksUnified[idP] = hasilUnified.size
                    }
                }
                hasilUnified.add(item.copy(indeks = hasilUnified.size))
            } else {
                diDalamSkip = true
                jumlahTersembunyi++
            }
        }

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

        val perubahanTersaring = perubahanAwal.map { k ->
            k.copy(
                indeksBarisBerdampinganPertama = petaIndeksBerdampingan[k.indeksPerubahan] ?: 0,
                indeksBarisUnifiedPertama = petaIndeksUnified[k.indeksPerubahan] ?: 0
            )
        }

        return HasilDiffLengkap(
            barisBerdampingan = hasilBerdampingan,
            barisUnified = hasilUnified,
            daftarPerubahan = perubahanTersaring,
            statistik = statistik,
            terpotongBatasKompleksitas = terpotongBatas
        )
    }

    internal fun hitungDiffKarakter(
        teksA: String,
        teksB: String,
        pekaHuruf: Boolean = true
    ): Pair<List<RentangKarakterBeda>, List<RentangKarakterBeda>> {
        if (teksA == teksB) return emptyList<RentangKarakterBeda>() to emptyList()
        if (teksA.isEmpty()) {
            return emptyList<RentangKarakterBeda>() to listOf(RentangKarakterBeda(0, teksB.length))
        }
        if (teksB.isEmpty()) {
            return listOf(RentangKarakterBeda(0, teksA.length)) to emptyList()
        }

        fun samaChar(a: Char, b: Char): Boolean =
            if (pekaHuruf) a == b else a.equals(b, ignoreCase = true)

        var prefix = 0
        val minLen = minOf(teksA.length, teksB.length)
        while (prefix < minLen && samaChar(teksA[prefix], teksB[prefix])) {
            prefix++
        }
        var suffix = 0
        val sisa = minLen - prefix
        while (suffix < sisa &&
            samaChar(teksA[teksA.length - 1 - suffix], teksB[teksB.length - 1 - suffix])
        ) {
            suffix++
        }

        val akhirA = teksA.length - suffix
        val akhirB = teksB.length - suffix
        val lenA = akhirA - prefix
        val lenB = akhirB - prefix

        if (lenA == 0 && lenB == 0) return emptyList<RentangKarakterBeda>() to emptyList()
        if (lenA == 0) {
            return emptyList<RentangKarakterBeda>() to listOf(RentangKarakterBeda(prefix, akhirB))
        }
        if (lenB == 0) {
            return listOf(RentangKarakterBeda(prefix, akhirA)) to emptyList()
        }

        if (lenA > BATAS_PANJANG_BARIS_DIFF_KARAKTER || lenB > BATAS_PANJANG_BARIS_DIFF_KARAKTER) {
            return listOf(RentangKarakterBeda(prefix, akhirA)) to listOf(RentangKarakterBeda(prefix, akhirB))
        }

        // LCS DP untuk rentang tengah yang pendek memakai larik datar 1D (nol alokasi bersarang)
        val pitch = lenB + 1
        val lcs = IntArray((lenA + 1) * pitch)
        for (i in lenA - 1 downTo 0) {
            val chA = teksA[prefix + i]
            val barisSekarang = i * pitch
            val barisBawah = (i + 1) * pitch
            for (j in lenB - 1 downTo 0) {
                lcs[barisSekarang + j] = if (samaChar(chA, teksB[prefix + j])) {
                    1 + lcs[barisBawah + (j + 1)]
                } else {
                    maxOf(lcs[barisBawah + j], lcs[barisSekarang + (j + 1)])
                }
            }
        }

        val bedaA = mutableListOf<RentangKarakterBeda>()
        val bedaB = mutableListOf<RentangKarakterBeda>()
        var i = 0
        var j = 0
        while (i < lenA || j < lenB) {
            when {
                i < lenA && j < lenB && samaChar(teksA[prefix + i], teksB[prefix + j]) -> {
                    i++; j++
                }
                j < lenB && (i == lenA || lcs[i * pitch + (j + 1)] >= lcs[(i + 1) * pitch + j]) -> {
                    tambahAtauGabungRentang(bedaB, prefix + j, prefix + j + 1)
                    j++
                }
                else -> {
                    tambahAtauGabungRentang(bedaA, prefix + i, prefix + i + 1)
                    i++
                }
            }
        }
        return bedaA to bedaB
    }

    private fun tambahAtauGabungRentang(
        daftar: MutableList<RentangKarakterBeda>,
        awal: Int,
        akhir: Int
    ) {
        val terakhir = daftar.lastOrNull()
        if (terakhir != null && terakhir.akhirEksklusif == awal) {
            daftar[daftar.lastIndex] = RentangKarakterBeda(terakhir.awal, akhir)
        } else {
            daftar.add(RentangKarakterBeda(awal, akhir))
        }
    }

    private fun normalisasiBaris(baris: String, opsi: OpsiDiff): String {
        val tahapWs = when (opsi.modeWhitespace) {
            ModeWhitespaceDiff.PERSIS -> baris
            ModeWhitespaceDiff.ABAIKAN_TEPI -> baris.trim()
            ModeWhitespaceDiff.ABAIKAN_SEMUA -> {
                val sb = StringBuilder(baris.length)
                for (c in baris) {
                    if (!c.isWhitespace()) sb.append(c)
                }
                sb.toString()
            }
        }
        return if (opsi.pekaHuruf) tahapWs else tahapWs.lowercase()
    }
}