package org.fm.com.domain.rename

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.ValidasiNama

private const val BATAS_BYTE_SEGMAN_NAMA = 255

data class KandidatRename(
    val indeks: Int,
    val entitas: EntitasBerkas,
    val namaUsulan: String
)

object ValidasiRename {

    fun validasiNamaSegmen(nama: String): String? {
        if (nama.isBlank()) {
            return "Nama berkas hasil tidak boleh kosong"
        }
        if (!ValidasiNama.sah(nama)) {
            return "Nama '$nama' mengandung karakter terlarang (/, \\, NUL) atau bernilai '.' / '..'"
        }
        if (nama.encodeToByteArray().size > BATAS_BYTE_SEGMAN_NAMA) {
            return "Nama '$nama' melebihi batas panjang $BATAS_BYTE_SEGMAN_NAMA byte"
        }
        return null
    }

    fun periksaAlasanTidakDapatDiubah(
        entitas: EntitasBerkas,
        kemampuanLokasi: KemampuanPenyimpanan?
    ): String? {
        if (kemampuanLokasi != null && !kemampuanLokasi.apakahDidukung(OperasiBerkas.MENGGANTI_NAMA)) {
            return kemampuanLokasi.alasan(OperasiBerkas.MENGGANTI_NAMA)
                ?: "Lokasi penyimpanan tidak mendukung penggantian nama"
        }
        if (entitas.lokasi.jenis == JenisLokasi.POSIX && entitas.lokasi.pengenal == "/") {
            return "Direktori root tidak dapat diganti namanya"
        }
        if (!entitas.dapatDitulis) {
            return "Item berstatus hanya-baca atau tidak memiliki izin tulis"
        }
        return null
    }

    fun validasi(
        kandidat: List<KandidatRename>,
        namaDiFolderDisk: Collection<String>,
        kemampuanLokasi: KemampuanPenyimpanan? = null
    ): List<ItemPratinjauRename> {
        if (kandidat.isEmpty()) return emptyList()

        val statusSementara = ArrayList<StatusSementara>(kandidat.size)
        for (item in kandidat) {
            val alasanTakBisa = periksaAlasanTidakDapatDiubah(item.entitas, kemampuanLokasi)
            if (alasanTakBisa != null) {
                statusSementara.add(
                    StatusSementara(
                        kandidat = item,
                        namaEfektif = item.entitas.nama,
                        status = StatusItemPratinjauRename.TIDAK_DAPAT_DIUBAH,
                        alasan = alasanTakBisa,
                        siapSementara = false
                    )
                )
                continue
            }

            val alasanNama = validasiNamaSegmen(item.namaUsulan)
            if (alasanNama != null) {
                statusSementara.add(
                    StatusSementara(
                        kandidat = item,
                        namaEfektif = item.entitas.nama,
                        status = StatusItemPratinjauRename.NAMA_TIDAK_SAH,
                        alasan = alasanNama,
                        siapSementara = false
                    )
                )
                continue
            }

            if (item.namaUsulan == item.entitas.nama) {
                statusSementara.add(
                    StatusSementara(
                        kandidat = item,
                        namaEfektif = item.entitas.nama,
                        status = StatusItemPratinjauRename.TIDAK_BERUBAH,
                        alasan = null,
                        siapSementara = false
                    )
                )
                continue
            }

            statusSementara.add(
                StatusSementara(
                    kandidat = item,
                    namaEfektif = item.namaUsulan,
                    status = StatusItemPratinjauRename.SIAP,
                    alasan = null,
                    siapSementara = true
                )
            )
        }

        // Hitung sisa item di disk setelah item SIAP mengosongkan nama lamanya.
        val sisaDiDisk = HashMap<String, Int>()
        for (namaDisk in namaDiFolderDisk) {
            val kunci = namaDisk.lowercase()
            sisaDiDisk[kunci] = (sisaDiDisk[kunci] ?: 0) + 1
        }
        for (item in statusSementara) {
            if (item.siapSementara) {
                val kunciLama = item.kandidat.entitas.nama.lowercase()
                val jumlah = sisaDiDisk[kunciLama] ?: 0
                if (jumlah > 0) {
                    sisaDiDisk[kunciLama] = jumlah - 1
                }
            }
        }

        // Hitung frekuensi nama efektif akhir pada seluruh batch.
        val jumlahTarget = HashMap<String, Int>()
        val jumlahTargetTetap = HashMap<String, Int>()
        for (item in statusSementara) {
            val kunci = item.namaEfektif.lowercase()
            jumlahTarget[kunci] = (jumlahTarget[kunci] ?: 0) + 1
            if (!item.siapSementara) {
                jumlahTargetTetap[kunci] = (jumlahTargetTetap[kunci] ?: 0) + 1
            }
        }

        return statusSementara.map { item ->
            if (!item.siapSementara) {
                ItemPratinjauRename(
                    indeks = item.kandidat.indeks,
                    entitas = item.kandidat.entitas,
                    namaLama = item.kandidat.entitas.nama,
                    namaBaru = item.namaEfektif,
                    status = item.status,
                    alasan = item.alasan
                )
            } else {
                val kunciTarget = item.namaEfektif.lowercase()
                val bentrokDalamBatch = (jumlahTarget[kunciTarget] ?: 0) > 1
                val bentrokDenganDisk = (sisaDiDisk[kunciTarget] ?: 0) > 0

                when {
                    bentrokDalamBatch -> {
                        val adaTetap = (jumlahTargetTetap[kunciTarget] ?: 0) > 0
                        val pesan = if (adaTetap) {
                            "Nama '${item.namaEfektif}' bentrok dengan item lain yang tidak berubah"
                        } else {
                            "Nama '${item.namaEfektif}' bentrok dengan item lain dalam daftar rename"
                        }
                        ItemPratinjauRename(
                            indeks = item.kandidat.indeks,
                            entitas = item.kandidat.entitas,
                            namaLama = item.kandidat.entitas.nama,
                            namaBaru = item.namaEfektif,
                            status = StatusItemPratinjauRename.TABRAKAN_ANTAR_ITEM,
                            alasan = pesan
                        )
                    }
                    bentrokDenganDisk -> {
                        ItemPratinjauRename(
                            indeks = item.kandidat.indeks,
                            entitas = item.kandidat.entitas,
                            namaLama = item.kandidat.entitas.nama,
                            namaBaru = item.namaEfektif,
                            status = StatusItemPratinjauRename.TABRAKAN_DENGAN_DISK,
                            alasan = "Berkas atau folder '${item.namaEfektif}' sudah ada di folder ini"
                        )
                    }
                    else -> {
                        ItemPratinjauRename(
                            indeks = item.kandidat.indeks,
                            entitas = item.kandidat.entitas,
                            namaLama = item.kandidat.entitas.nama,
                            namaBaru = item.namaEfektif,
                            status = StatusItemPratinjauRename.SIAP,
                            alasan = null
                        )
                    }
                }
            }
        }
    }

    private data class StatusSementara(
        val kandidat: KandidatRename,
        val namaEfektif: String,
        val status: StatusItemPratinjauRename,
        val alasan: String?,
        val siapSementara: Boolean
    )
}