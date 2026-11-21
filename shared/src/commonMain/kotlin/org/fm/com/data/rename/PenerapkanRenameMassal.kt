package org.fm.com.data.rename

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.rename.ParserTokenPola
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.usecase.HasilEksekusiRename
import org.fm.com.domain.usecase.ItemGagalRename
import org.fm.com.domain.usecase.ItemSuksesRename
import org.fm.com.domain.usecase.PelaksanaRenameMassal
import org.fm.com.domain.usecase.TugasRenameItem

class PenerapkanRenameMassal(
    private val repositoriOperasi: RepositoriOperasiBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PelaksanaRenameMassal {

    override suspend fun terapkan(
        daftarTugas: List<TugasRenameItem>,
        onProgres: (indeks: Int, total: Int, namaSaatIni: String) -> Unit
    ): HasilEksekusiRename = withContext(ioDispatcher) {
        if (daftarTugas.isEmpty()) return@withContext HasilEksekusiRename()

        val tersisa = daftarTugas.toMutableList()
        val kunciSumberAktif = daftarTugas.map { it.sumber.nama.lowercase() }.toMutableSet()
        val kunciTerhalangPermanen = mutableSetOf<String>()
        val kunciDipakaiTemp = HashSet<String>(daftarTugas.size * 3).apply {
            for (t in daftarTugas) {
                add(t.sumber.nama.lowercase())
                add(t.namaBaru.lowercase())
            }
        }

        val berhasil = mutableListOf<ItemSuksesRename>()
        val gagal = mutableListOf<ItemGagalRename>()
        val total = daftarTugas.size
        var diproses = 0

        // Fase A: Eksekusi langsung secara topologis untuk item tanpa siklus/konflik sementara.
        while (tersisa.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val indeksSiap = tersisa.indexOfFirst { t ->
                val targetKey = t.namaBaru.lowercase()
                val sourceKey = t.sumber.nama.lowercase()
                targetKey != sourceKey &&
                    targetKey !in kunciSumberAktif &&
                    targetKey !in kunciTerhalangPermanen
            }
            if (indeksSiap < 0) break

            val tugas = tersisa.removeAt(indeksSiap)
            val sourceKey = tugas.sumber.nama.lowercase()
            diproses++
            onProgres(diproses, total, tugas.sumber.nama)

            when (
                val hasil = repositoriOperasi.gantiNama(
                    PermintaanOperasi(
                        sumber = tugas.sumber.lokasi,
                        namaBaru = tugas.namaBaru,
                        konfirmasi = false
                    )
                )
            ) {
                is HasilOperasi.Sukses -> {
                    kunciSumberAktif.remove(sourceKey)
                    berhasil.add(
                        ItemSuksesRename(
                            sumberLama = tugas.sumber,
                            entitasBaru = hasil.nilai,
                            pakaiNamaSementara = false
                        )
                    )
                }
                is HasilOperasi.Gagal -> {
                    kunciSumberAktif.remove(sourceKey)
                    kunciTerhalangPermanen.add(sourceKey)
                    gagal.add(
                        ItemGagalRename(
                            sumber = tugas.sumber,
                            namaTujuan = tugas.namaBaru,
                            pesan = hasil.pesan,
                            penyebab = hasil.penyebab
                        )
                    )
                }
            }
        }

        // Propagasi item yang targetnya terhalang item gagal dari Fase A.
        while (true) {
            var adaDitolak = false
            val iter = tersisa.iterator()
            while (iter.hasNext()) {
                val tugas = iter.next()
                if (tugas.namaBaru.lowercase() in kunciTerhalangPermanen) {
                    iter.remove()
                    val sourceKey = tugas.sumber.nama.lowercase()
                    kunciSumberAktif.remove(sourceKey)
                    kunciTerhalangPermanen.add(sourceKey)
                    diproses++
                    onProgres(diproses, total, tugas.sumber.nama)
                    gagal.add(
                        ItemGagalRename(
                            sumber = tugas.sumber,
                            namaTujuan = tugas.namaBaru,
                            pesan = "Nama tujuan '${tugas.namaBaru}' terhalang item lain yang gagal diubah",
                            penyebab = PenyebabGalat.KONFLIK
                        )
                    )
                    adaDitolak = true
                }
            }
            if (!adaDitolak) break
        }

        if (tersisa.isEmpty()) {
            return@withContext HasilEksekusiRename(berhasil = berhasil, gagal = gagal)
        }

        // Fase B: Kelompokkan siklus / ubah huruf besar-kecil per komponen terhubung,
        // lalu eksekusi aman 2 langkah melalui nama sementara unik.
        val daftarKomponen = kelompokkanKomponenTerhubung(tersisa)
        for (komponen in daftarKomponen) {
            currentCoroutineContext().ensureActive()
            val staged = mutableListOf<ItemTerjadwalTemp>()
            var gagalTahapTemp: Triple<TugasRenameItem, String, PenyebabGalat>? = null

            try {
                for ((urutanLokal, tugas) in komponen.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    val hasilTemp = pindahkanKeNamaSementara(
                        tugas = tugas,
                        urutanLokal = urutanLokal,
                        kunciDipakai = kunciDipakaiTemp
                    )
                    when (hasilTemp) {
                        is HasilOperasi.Sukses -> staged.add(
                            ItemTerjadwalTemp(tugas = tugas, entitasSementara = hasilTemp.nilai)
                        )
                        is HasilOperasi.Gagal -> {
                            gagalTahapTemp = Triple(tugas, hasilTemp.pesan, hasilTemp.penyebab)
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    pulihkanTahapSementara(staged)
                }
                throw e
            }

            if (gagalTahapTemp != null) {
                withContext(NonCancellable) {
                    pulihkanTahapSementara(staged)
                }
                val (tugasGagal, pesanGagal, penyebabGagal) = gagalTahapTemp
                for (t in komponen) {
                    diproses++
                    onProgres(diproses, total, t.sumber.nama)
                    if (t === tugasGagal) {
                        gagal.add(
                            ItemGagalRename(
                                sumber = t.sumber,
                                namaTujuan = t.namaBaru,
                                pesan = pesanGagal,
                                penyebab = penyebabGagal
                            )
                        )
                    } else {
                        gagal.add(
                            ItemGagalRename(
                                sumber = t.sumber,
                                namaTujuan = t.namaBaru,
                                pesan = "Dibatalkan karena pasangan pertukaran '${tugasGagal.sumber.nama}' gagal dipindahkan sementara: $pesanGagal",
                                penyebab = penyebabGagal
                            )
                        )
                    }
                }
                continue
            }

            val selesaiDiKomponen = mutableListOf<Pair<ItemTerjadwalTemp, EntitasBerkas>>()
            var gagalTahapFinal: Triple<ItemTerjadwalTemp, String, PenyebabGalat>? = null

            try {
                for (itemTemp in staged) {
                    currentCoroutineContext().ensureActive()
                    diproses++
                    onProgres(diproses, total, itemTemp.tugas.sumber.nama)
                    when (
                        val resFinal = repositoriOperasi.gantiNama(
                            PermintaanOperasi(
                                sumber = itemTemp.entitasSementara.lokasi,
                                namaBaru = itemTemp.tugas.namaBaru,
                                konfirmasi = false
                            )
                        )
                    ) {
                        is HasilOperasi.Sukses -> selesaiDiKomponen.add(itemTemp to resFinal.nilai)
                        is HasilOperasi.Gagal -> {
                            gagalTahapFinal = Triple(itemTemp, resFinal.pesan, resFinal.penyebab)
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    pulihkanTahapFinal(selesaiDiKomponen, staged)
                }
                throw e
            }

            if (gagalTahapFinal != null) {
                withContext(NonCancellable) {
                    pulihkanTahapFinal(selesaiDiKomponen, staged)
                }
                val (itemGagal, pesanGagal, penyebabGagal) = gagalTahapFinal
                val sisaBelumDilaporkan = komponen.size - selesaiDiKomponen.size - 1
                if (sisaBelumDilaporkan > 0) {
                    diproses += sisaBelumDilaporkan
                }
                for (t in komponen) {
                    if (t === itemGagal.tugas) {
                        gagal.add(
                            ItemGagalRename(
                                sumber = t.sumber,
                                namaTujuan = t.namaBaru,
                                pesan = pesanGagal,
                                penyebab = penyebabGagal
                            )
                        )
                    } else {
                        gagal.add(
                            ItemGagalRename(
                                sumber = t.sumber,
                                namaTujuan = t.namaBaru,
                                pesan = "Dipulihkan ke nama semula karena '${itemGagal.tugas.sumber.nama}' gagal diterapkan: $pesanGagal",
                                penyebab = penyebabGagal
                            )
                        )
                    }
                }
            } else {
                for ((itemTemp, entitasFinal) in selesaiDiKomponen) {
                    berhasil.add(
                        ItemSuksesRename(
                            sumberLama = itemTemp.tugas.sumber,
                            entitasBaru = entitasFinal,
                            pakaiNamaSementara = true
                        )
                    )
                }
            }
        }

        HasilEksekusiRename(berhasil = berhasil, gagal = gagal)
    }

    private suspend fun pindahkanKeNamaSementara(
        tugas: TugasRenameItem,
        urutanLokal: Int,
        kunciDipakai: MutableSet<String>
    ): HasilOperasi<EntitasBerkas> {
        repeat(3) {
            val namaTemp = buatNamaSementaraUnik(tugas.sumber, urutanLokal, kunciDipakai)
            val hasil = repositoriOperasi.gantiNama(
                PermintaanOperasi(
                    sumber = tugas.sumber.lokasi,
                    namaBaru = namaTemp,
                    konfirmasi = false
                )
            )
            if (hasil is HasilOperasi.Gagal && hasil.penyebab == PenyebabGalat.KONFLIK) {
                return@repeat
            }
            return hasil
        }
        return HasilOperasi.Gagal(
            "Gagal menentukan nama sementara unik untuk '${tugas.sumber.nama}'",
            PenyebabGalat.KONFLIK
        )
    }

    private suspend fun pulihkanTahapSementara(staged: List<ItemTerjadwalTemp>) {
        for (item in staged.asReversed()) {
            runCatching {
                repositoriOperasi.gantiNama(
                    PermintaanOperasi(
                        sumber = item.entitasSementara.lokasi,
                        namaBaru = item.tugas.sumber.nama,
                        konfirmasi = false
                    )
                )
            }
        }
    }

    private suspend fun pulihkanTahapFinal(
        selesaiDiKomponen: List<Pair<ItemTerjadwalTemp, EntitasBerkas>>,
        semuaStaged: List<ItemTerjadwalTemp>
    ) {
        val dipulihkanKeTemp = ArrayList<ItemTerjadwalTemp>(selesaiDiKomponen.size)
        for ((itemTemp, entitasFinal) in selesaiDiKomponen.asReversed()) {
            val hasilBalikTemp = runCatching {
                repositoriOperasi.gantiNama(
                    PermintaanOperasi(
                        sumber = entitasFinal.lokasi,
                        namaBaru = itemTemp.entitasSementara.nama,
                        konfirmasi = false
                    )
                )
            }.getOrNull()
            if (hasilBalikTemp is HasilOperasi.Sukses) {
                dipulihkanKeTemp.add(itemTemp.copy(entitasSementara = hasilBalikTemp.nilai))
            }
        }
        val kunciDipakai = dipulihkanKeTemp.map { it.tugas.sumber.lokasi }.toSet()
        val belumDipindahkanKeFinal = semuaStaged.filter { it.tugas.sumber.lokasi !in kunciDipakai }
        for (item in dipulihkanKeTemp + belumDipindahkanKeFinal) {
            runCatching {
                repositoriOperasi.gantiNama(
                    PermintaanOperasi(
                        sumber = item.entitasSementara.lokasi,
                        namaBaru = item.tugas.sumber.nama,
                        konfirmasi = false
                    )
                )
            }
        }
    }

    private fun kelompokkanKomponenTerhubung(daftar: List<TugasRenameItem>): List<List<TugasRenameItem>> {
        if (daftar.size <= 1) return listOf(daftar)
        val n = daftar.size
        val petaKunciKeIndeks = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val t = daftar[i]
            petaKunciKeIndeks.getOrPut(t.sumber.nama.lowercase()) { mutableListOf() }.add(i)
            petaKunciKeIndeks.getOrPut(t.namaBaru.lowercase()) { mutableListOf() }.add(i)
        }

        val dikunjungi = BooleanArray(n)
        val hasil = mutableListOf<List<TugasRenameItem>>()
        val antrean = ArrayDeque<Int>()

        for (i in 0 until n) {
            if (dikunjungi[i]) continue
            dikunjungi[i] = true
            antrean.addLast(i)
            val indeksKomponen = mutableListOf<Int>()

            while (antrean.isNotEmpty()) {
                val cur = antrean.removeFirst()
                indeksKomponen.add(cur)
                val tugas = daftar[cur]
                val tetangga = buildList {
                    petaKunciKeIndeks[tugas.sumber.nama.lowercase()]?.let(::addAll)
                    petaKunciKeIndeks[tugas.namaBaru.lowercase()]?.let(::addAll)
                }
                for (nb in tetangga) {
                    if (!dikunjungi[nb]) {
                        dikunjungi[nb] = true
                        antrean.addLast(nb)
                    }
                }
            }
            indeksKomponen.sort()
            hasil.add(indeksKomponen.map { daftar[it] })
        }
        return hasil
    }

    private fun buatNamaSementaraUnik(
        sumber: EntitasBerkas,
        urutan: Int,
        kunciDipakai: MutableSet<String>
    ): String {
        val (_, ekstensiAsli) = ParserTokenPola.pisahNamaDanEkstensi(
            nama = sumber.nama,
            adalahFolder = sumber.jenis == JenisBerkas.FOLDER
        )
        val ekstensiAman = if (ekstensiAsli.length <= 24 && ValidasiNama.sah("x$ekstensiAsli")) {
            ekstensiAsli
        } else {
            ""
        }
        while (true) {
            val acak = Random.nextInt(0x100000, 0xFFFFFF).toString(16)
            val kandidat = ".fm_rn_${acak}_${urutan}$ekstensiAman"
            if (kunciDipakai.add(kandidat.lowercase())) {
                return kandidat
            }
        }
    }

    private data class ItemTerjadwalTemp(
        val tugas: TugasRenameItem,
        val entitasSementara: EntitasBerkas
    )
}