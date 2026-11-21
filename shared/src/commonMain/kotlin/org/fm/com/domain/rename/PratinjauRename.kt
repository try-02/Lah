package org.fm.com.domain.rename

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.KemampuanPenyimpanan

enum class StatusItemPratinjauRename {
    SIAP,
    TIDAK_BERUBAH,
    NAMA_TIDAK_SAH,
    TABRAKAN_ANTAR_ITEM,
    TABRAKAN_DENGAN_DISK,
    TIDAK_DAPAT_DIUBAH
}

data class ItemPratinjauRename(
    val indeks: Int,
    val entitas: EntitasBerkas,
    val namaLama: String,
    val namaBaru: String,
    val status: StatusItemPratinjauRename,
    val alasan: String? = null
) {
    val dapatDiubah: Boolean
        get() = status == StatusItemPratinjauRename.SIAP

    val adalahGalatPenghalang: Boolean
        get() = status == StatusItemPratinjauRename.NAMA_TIDAK_SAH ||
            status == StatusItemPratinjauRename.TABRAKAN_ANTAR_ITEM ||
            status == StatusItemPratinjauRename.TABRAKAN_DENGAN_DISK
}

data class HasilPratinjauRename(
    val pola: String,
    val galatPola: String? = null,
    val daftarItem: List<ItemPratinjauRename> = emptyList(),
    val jumlahSiapUbah: Int = 0,
    val jumlahTidakBerubah: Int = 0,
    val jumlahTidakDapatDiubah: Int = 0,
    val jumlahNamaTidakSah: Int = 0,
    val jumlahTabrakanAntarItem: Int = 0,
    val jumlahTabrakanDenganDisk: Int = 0
) {
    val adaKesalahanPenghalang: Boolean
        get() = galatPola != null ||
            jumlahNamaTidakSah > 0 ||
            jumlahTabrakanAntarItem > 0 ||
            jumlahTabrakanDenganDisk > 0

    val dapatDieksekusi: Boolean
        get() = !adaKesalahanPenghalang && jumlahSiapUbah > 0
}

object PratinjauRename {

    fun bangun(
        daftarItem: List<EntitasBerkas>,
        pola: String,
        konfigurasiNomor: KonfigurasiNomorUrut = KonfigurasiNomorUrut(),
        namaDiFolderDisk: Collection<String> = daftarItem.map { it.nama },
        kemampuanLokasi: KemampuanPenyimpanan? = null,
        opsiParser: OpsiParserPola = OpsiParserPola()
    ): HasilPratinjauRename {
        val hasilParse = ParserTokenPola.parse(pola, opsiParser)
        if (hasilParse is HasilParsePola.Gagal) {
            val itemFallback = daftarItem.mapIndexed { idx, entitas ->
                val alasanTakBisa = ValidasiRename.periksaAlasanTidakDapatDiubah(entitas, kemampuanLokasi)
                ItemPratinjauRename(
                    indeks = idx,
                    entitas = entitas,
                    namaLama = entitas.nama,
                    namaBaru = entitas.nama,
                    status = if (alasanTakBisa != null) {
                        StatusItemPratinjauRename.TIDAK_DAPAT_DIUBAH
                    } else {
                        StatusItemPratinjauRename.TIDAK_BERUBAH
                    },
                    alasan = alasanTakBisa
                )
            }
            return HasilPratinjauRename(
                pola = pola,
                galatPola = hasilParse.pesan,
                daftarItem = itemFallback,
                jumlahTidakBerubah = itemFallback.count { it.status == StatusItemPratinjauRename.TIDAK_BERUBAH },
                jumlahTidakDapatDiubah = itemFallback.count { it.status == StatusItemPratinjauRename.TIDAK_DAPAT_DIUBAH }
            )
        }

        val tokens = (hasilParse as HasilParsePola.Sukses).daftarToken
        val kandidat = daftarItem.mapIndexed { idx, entitas ->
            val adalahFolder = entitas.jenis == JenisBerkas.FOLDER
            val namaUsulan = ParserTokenPola.terapkan(
                daftarToken = tokens,
                namaAsli = entitas.nama,
                adalahFolder = adalahFolder,
                indeksUrut = idx,
                konfigurasiNomor = konfigurasiNomor
            )
            KandidatRename(
                indeks = idx,
                entitas = entitas,
                namaUsulan = namaUsulan
            )
        }

        val itemTervalidasi = ValidasiRename.validasi(
            kandidat = kandidat,
            namaDiFolderDisk = namaDiFolderDisk,
            kemampuanLokasi = kemampuanLokasi
        )

        var siap = 0
        var tidakBerubah = 0
        var tidakDapatDiubah = 0
        var tidakSah = 0
        var tabrakanAntar = 0
        var tabrakanDisk = 0

        for (item in itemTervalidasi) {
            when (item.status) {
                StatusItemPratinjauRename.SIAP -> siap++
                StatusItemPratinjauRename.TIDAK_BERUBAH -> tidakBerubah++
                StatusItemPratinjauRename.TIDAK_DAPAT_DIUBAH -> tidakDapatDiubah++
                StatusItemPratinjauRename.NAMA_TIDAK_SAH -> tidakSah++
                StatusItemPratinjauRename.TABRAKAN_ANTAR_ITEM -> tabrakanAntar++
                StatusItemPratinjauRename.TABRAKAN_DENGAN_DISK -> tabrakanDisk++
            }
        }

        return HasilPratinjauRename(
            pola = pola,
            galatPola = null,
            daftarItem = itemTervalidasi,
            jumlahSiapUbah = siap,
            jumlahTidakBerubah = tidakBerubah,
            jumlahTidakDapatDiubah = tidakDapatDiubah,
            jumlahNamaTidakSah = tidakSah,
            jumlahTabrakanAntarItem = tabrakanAntar,
            jumlahTabrakanDenganDisk = tabrakanDisk
        )
    }
}