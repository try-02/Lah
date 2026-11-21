package org.fm.com.domain.usecase

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.rename.HasilPratinjauRename
import org.fm.com.domain.rename.KonfigurasiNomorUrut
import org.fm.com.domain.rename.OpsiParserPola
import org.fm.com.domain.rename.PratinjauRename

data class TugasRenameItem(
    val sumber: EntitasBerkas,
    val namaBaru: String
)

data class ItemSuksesRename(
    val sumberLama: EntitasBerkas,
    val entitasBaru: EntitasBerkas,
    val pakaiNamaSementara: Boolean = false
)

data class ItemGagalRename(
    val sumber: EntitasBerkas,
    val namaTujuan: String,
    val pesan: String,
    val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
)

data class HasilEksekusiRename(
    val berhasil: List<ItemSuksesRename> = emptyList(),
    val gagal: List<ItemGagalRename> = emptyList(),
    val dilewatiTidakBerubah: Int = 0,
    val dilewatiTidakDapatDiubah: Int = 0
) {
    val adaGagal: Boolean get() = gagal.isNotEmpty()
    val totalDiproses: Int get() = berhasil.size + gagal.size
}

interface PelaksanaRenameMassal {
    suspend fun terapkan(
        daftarTugas: List<TugasRenameItem>,
        onProgres: (indeks: Int, total: Int, namaSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilEksekusiRename
}

data class RingkasanKonfirmasiRename(
    val jumlahAkanDiubah: Int,
    val jumlahTidakBerubah: Int,
    val jumlahTidakDapatDiubah: Int,
    val daftarContohPerubahan: List<Pair<String, String>>
)

sealed interface HasilJalankanRenameMassal {
    data class Selesai(val hasil: HasilEksekusiRename) : HasilJalankanRenameMassal
    data class PerluKonfirmasi(val ringkasan: RingkasanKonfirmasiRename) : HasilJalankanRenameMassal
    data class DitolakValidasi(
        val pesan: String,
        val pratinjau: HasilPratinjauRename? = null
    ) : HasilJalankanRenameMassal
}

class JalankanRenameMassalUseCase(
    private val pelaksana: PelaksanaRenameMassal
) {
    suspend operator fun invoke(
        daftarItem: List<EntitasBerkas>,
        pola: String,
        konfigurasiNomor: KonfigurasiNomorUrut = KonfigurasiNomorUrut(),
        namaDiFolderDisk: Collection<String> = daftarItem.map { it.nama },
        kemampuanLokasi: KemampuanPenyimpanan? = null,
        konfirmasiRingkasan: Boolean = false,
        opsiParser: OpsiParserPola = OpsiParserPola(),
        onProgres: (indeks: Int, total: Int, namaSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilJalankanRenameMassal {
        if (daftarItem.isEmpty()) {
            return HasilJalankanRenameMassal.DitolakValidasi("Tidak ada item yang dipilih")
        }

        val pratinjau = PratinjauRename.bangun(
            daftarItem = daftarItem,
            pola = pola,
            konfigurasiNomor = konfigurasiNomor,
            namaDiFolderDisk = namaDiFolderDisk,
            kemampuanLokasi = kemampuanLokasi,
            opsiParser = opsiParser
        )

        if (pratinjau.galatPola != null) {
            return HasilJalankanRenameMassal.DitolakValidasi(pratinjau.galatPola, pratinjau)
        }

        if (pratinjau.adaKesalahanPenghalang) {
            val bagianGalat = buildList {
                if (pratinjau.jumlahNamaTidakSah > 0) add("${pratinjau.jumlahNamaTidakSah} nama tidak sah")
                if (pratinjau.jumlahTabrakanAntarItem > 0) add("${pratinjau.jumlahTabrakanAntarItem} tabrakan antar-item")
                if (pratinjau.jumlahTabrakanDenganDisk > 0) add("${pratinjau.jumlahTabrakanDenganDisk} bentrok dengan berkas di folder")
            }.joinToString(", ")
            return HasilJalankanRenameMassal.DitolakValidasi(
                pesan = "Perbaiki kesalahan sebelum melanjutkan: $bagianGalat",
                pratinjau = pratinjau
            )
        }

        val tugas = pratinjau.daftarItem
            .filter { it.dapatDiubah }
            .map { TugasRenameItem(sumber = it.entitas, namaBaru = it.namaBaru) }

        if (tugas.isEmpty()) {
            return HasilJalankanRenameMassal.DitolakValidasi(
                pesan = "Tidak ada item yang mengalami perubahan nama atau dapat diubah",
                pratinjau = pratinjau
            )
        }

        if (!konfirmasiRingkasan) {
            return HasilJalankanRenameMassal.PerluKonfirmasi(
                RingkasanKonfirmasiRename(
                    jumlahAkanDiubah = tugas.size,
                    jumlahTidakBerubah = pratinjau.jumlahTidakBerubah,
                    jumlahTidakDapatDiubah = pratinjau.jumlahTidakDapatDiubah,
                    daftarContohPerubahan = tugas.take(8).map { it.sumber.nama to it.namaBaru }
                )
            )
        }

        val hasil = pelaksana.terapkan(tugas, onProgres)
        return HasilJalankanRenameMassal.Selesai(
            hasil.copy(
                dilewatiTidakBerubah = pratinjau.jumlahTidakBerubah,
                dilewatiTidakDapatDiubah = pratinjau.jumlahTidakDapatDiubah
            )
        )
    }
}