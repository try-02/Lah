package org.fm.com.domain.usecase

import org.fm.com.domain.arsip.KapabilitasArsip
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntriArsip
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip
import org.fm.com.domain.model.ValidasiNama

data class LapisanJejakArsip(
    val namaArsip: String,
    val format: FormatArsip,
    val kapabilitas: KapabilitasArsip,
    val kedalaman: Int
)

data class StatusLapisanArsip(
    val jejakLapisan: List<LapisanJejakArsip>,
    val ringkasan: RingkasanIsiArsip,
    val kedalamanSaatIni: Int
) {
    val lapisanAktif: LapisanJejakArsip get() = jejakLapisan.last()
}

data class KonteksEditArsip(
    val sumberArsipAkar: LokasiBerkas,
    val namaArsipTampilan: String,
    val formatArsipAktif: FormatArsip,
    val rantaiJalurArsipBersarang: List<String> = emptyList(),
    val jalurEntriTarget: String,
    val idSesiArsip: String? = null
)

data class HasilSiapkanEditArsip(
    val entitasSementara: EntitasBerkas,
    val konteks: KonteksEditArsip
)

interface PelaksanaPenjelajahanArsip {
    suspend fun bukaArsipAkar(
        idSesi: String,
        sumber: LokasiBerkas,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip>

    suspend fun masukArsipBersarang(
        idSesi: String,
        jalurArsipDalamLapisan: String,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip>

    suspend fun kembaliKeLapisan(
        idSesi: String,
        indeksLapisanTujuan: Int
    ): HasilOperasi<StatusLapisanArsip>

    suspend fun ekstrak(
        idSesi: String,
        folderTujuan: LokasiBerkas,
        opsi: OpsiEkstrakArsip,
        onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<HasilEkstrakArsip>

    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        untukEdit: Boolean = false,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<HasilSiapkanEditArsip>

    suspend fun perbaruiBerkasDalamArsip(
        konteks: KonteksEditArsip,
        lokasiBerkasEdit: LokasiBerkas,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip?>

    suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip?

    suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas)

    suspend fun tutupSesi(idSesi: String)
}

interface PelaksanaPembuatanArsip {
    suspend fun buatDanVerifikasi(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean = false,
        kataSandi: String? = null,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<EntitasBerkas>
}

class EkstrakArsipUseCase(
    private val pelaksana: PelaksanaPenjelajahanArsip
) {
    suspend fun bukaAkar(
        idSesi: String,
        sumber: LokasiBerkas,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip> =
        pelaksana.bukaArsipAkar(idSesi, sumber, kataSandi, batas)

    suspend fun masukArsipBersarang(
        idSesi: String,
        jalurArsipDalamLapisan: String,
        kedalamanSaatIni: Int,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip> {
        val cek = validasiKedalaman(kedalamanSaatIni + 1, batas)
        if (cek is HasilOperasi.Gagal) return cek
        return pelaksana.masukArsipBersarang(idSesi, jalurArsipDalamLapisan, kataSandi, batas)
    }

    suspend fun kembaliKeLapisan(
        idSesi: String,
        indeksLapisanTujuan: Int
    ): HasilOperasi<StatusLapisanArsip> {
        if (indeksLapisanTujuan < 0) {
            return HasilOperasi.Gagal("Indeks lapisan arsip tidak valid", PenyebabGalat.VALIDASI)
        }
        return pelaksana.kembaliKeLapisan(idSesi, indeksLapisanTujuan)
    }

    suspend fun ekstrak(
        idSesi: String,
        folderTujuan: LokasiBerkas,
        opsi: OpsiEkstrakArsip,
        onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<HasilEkstrakArsip> {
        if (!opsi.konfirmasiPengguna) {
            return HasilOperasi.Gagal(
                "Ekstraksi arsip membutuhkan persetujuan pengguna terlebih dahulu",
                PenyebabGalat.VALIDASI
            )
        }
        return pelaksana.ekstrak(idSesi, folderTujuan, opsi, onProgres)
    }

    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        untukEdit: Boolean = false,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<HasilSiapkanEditArsip> =
        pelaksana.siapkanPratinjauBerkas(idSesi, jalurInternal, untukEdit, kataSandi, batas)

    suspend fun perbaruiBerkasDalamArsip(
        konteks: KonteksEditArsip,
        lokasiBerkasEdit: LokasiBerkas,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip?> =
        pelaksana.perbaruiBerkasDalamArsip(konteks, lokasiBerkasEdit, batas)

    suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip? =
        pelaksana.ambilStatusSesi(idSesi)

    suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas) {
        pelaksana.bersihkanBerkasSementara(lokasi)
    }

    suspend fun tutupSesi(idSesi: String) {
        pelaksana.tutupSesi(idSesi)
    }

    companion object {
        fun validasiKedalaman(
            kedalamanTujuan: Int,
            batas: KebijakanBatasArsip
        ): HasilOperasi<Unit> {
            if (kedalamanTujuan < 1) {
                return HasilOperasi.Gagal("Kedalaman arsip tidak valid", PenyebabGalat.VALIDASI)
            }
            if (kedalamanTujuan > batas.batasKedalamanArsipMaks) {
                return HasilOperasi.Gagal(
                    "Kedalaman arsip bertingkat melebihi batas aman (maksimal ${batas.batasKedalamanArsipMaks} tingkat)",
                    PenyebabGalat.VALIDASI
                )
            }
            return HasilOperasi.Sukses(Unit)
        }

        fun saringEntriUntukFolder(
            semuaEntri: List<EntriArsip>,
            folderSaatIni: String
        ): List<EntriArsip> {
            val awalan = folderSaatIni.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val folderSintetis = LinkedHashMap<String, EntriArsip>()
            val entriLangsung = LinkedHashMap<String, EntriArsip>()

            for (entri in semuaEntri) {
                val jalur = entri.jalurInternal.trim('/')
                if (jalur.isEmpty() || !jalur.startsWith(awalan)) continue
                val sisa = jalur.removePrefix(awalan)
                if (sisa.isEmpty()) continue

                val garisMiring = sisa.indexOf('/')
                if (garisMiring < 0) {
                    entriLangsung[jalur] = entri
                } else {
                    val namaSubfolder = sisa.substring(0, garisMiring)
                    val jalurSubfolder = "$awalan$namaSubfolder"
                    if (jalurSubfolder !in entriLangsung && jalurSubfolder !in folderSintetis) {
                        folderSintetis[jalurSubfolder] = EntriArsip(
                            jalurInternal = jalurSubfolder,
                            nama = namaSubfolder,
                            adalahFolder = true,
                            adalahArsipBersarang = false
                        )
                    }
                }
            }

            val gabungan = LinkedHashMap<String, EntriArsip>(folderSintetis.size + entriLangsung.size)
            for ((k, v) in folderSintetis) gabungan[k] = v
            for ((k, v) in entriLangsung) gabungan[k] = v

            return gabungan.values.sortedWith(
                compareByDescending<EntriArsip> { it.adalahFolder }
                    .thenBy { it.nama.lowercase() }
            )
        }
    }
}

class KompresArsipUseCase(
    private val pelaksana: PelaksanaPembuatanArsip
) {
    suspend operator fun invoke(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean = false,
        kataSandi: String? = null,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<EntitasBerkas> {
        val namaBersih = namaArsip.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            return HasilOperasi.Gagal("Nama berkas arsip tidak sah: $namaArsip", PenyebabGalat.VALIDASI)
        }
        if (daftarSumber.isEmpty()) {
            return HasilOperasi.Gagal("Pilih minimal satu berkas atau folder untuk dikompres", PenyebabGalat.VALIDASI)
        }
        return pelaksana.buatDanVerifikasi(
            daftarSumber = daftarSumber,
            tujuanInduk = tujuanInduk,
            namaArsip = namaBersih,
            format = format,
            konfirmasiTimpa = konfirmasiTimpa,
            kataSandi = kataSandi,
            onProgres = onProgres
        )
    }
}