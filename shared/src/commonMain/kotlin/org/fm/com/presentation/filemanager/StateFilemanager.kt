package org.fm.com.presentation.filemanager

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.KriteriaUrutan

/** Mode tampilan daftar berkas di layar. */
enum class ModeTampilan {
    DAFTAR,
    KISI
}

/** Model item berkas untuk layer presentasi/UI (bebas dari I/O di Composable). */
data class ItemBerkasUi(
    val lokasi: LokasiBerkas,
    val nama: String,
    val jenis: JenisBerkas,
    val ukuranFormat: String,
    val tanggalFormat: String,
    val tersembunyi: Boolean,
    val adalahFolder: Boolean,
    val dapatDibaca: Boolean,
    val dapatDitulis: Boolean
)

/** State UDF immutable untuk layar file manager. */
data class StateFilemanager(
    val lokasiSaatIni: LokasiBerkas? = null,
    val daftarItem: List<ItemBerkasUi> = emptyList(),
    val sedangMemuat: Boolean = false,
    val pesanGalat: String? = null,
    val kriteriaUrutan: KriteriaUrutan = KriteriaUrutan.NAMA,
    val arahUrutan: ArahUrutan = ArahUrutan.NAIK,
    val folderDiAtas: Boolean = true,
    val tampilkanTersembunyi: Boolean = false,
    val modeTampilan: ModeTampilan = ModeTampilan.DAFTAR,
    val itemTerpilih: Set<LokasiBerkas> = emptySet(),
    val kemampuanLokasi: KemampuanPenyimpanan? = null
) {
    val jumlahItem: Int get() = daftarItem.size
    val sedangMultiSelect: Boolean get() = itemTerpilih.isNotEmpty()
}

/** Intent / Aksi pengguna untuk menggerakkan StateFilemanager. */
sealed interface IntentFilemanager {
    data class BukaLokasi(val lokasi: LokasiBerkas) : IntentFilemanager
    data object Segarkan : IntentFilemanager
    data class UbahPengurutan(val kriteria: KriteriaUrutan, val arah: ArahUrutan? = null) : IntentFilemanager
    data object ToggleFolderDiAtas : IntentFilemanager
    data object ToggleFileTersembunyi : IntentFilemanager
    data class UbahModeTampilan(val mode: ModeTampilan) : IntentFilemanager
    data class TogglePilihItem(val lokasi: LokasiBerkas) : IntentFilemanager
    data object PilihSemua : IntentFilemanager
    data object BersihkanPilihan : IntentFilemanager
    data object HapusPesanGalat : IntentFilemanager
}

/** Mapper Domain -> UI Model. */
object PemetaanDomainUi {

    fun EntitasBerkas.keUi(): ItemBerkasUi = ItemBerkasUi(
        lokasi = lokasi,
        nama = nama,
        jenis = jenis,
        ukuranFormat = formatUkuran(ukuranByte, jenis == JenisBerkas.FOLDER),
        tanggalFormat = formatTanggal(waktuUbahEpochMs),
        tersembunyi = tersembunyi,
        adalahFolder = jenis == JenisBerkas.FOLDER,
        dapatDibaca = dapatDibaca,
        dapatDitulis = dapatDitulis
    )

    fun formatUkuran(ukuranByte: Long?, adalahFolder: Boolean): String {
        if (adalahFolder) return "-"
        if (ukuranByte == null || ukuranByte < 0) return "-"
        if (ukuranByte < 1024) return "$ukuranByte B"
        val kb = ukuranByte / 1024.0
        if (kb < 1024) return "${formatSatuDesimal(kb)} KiB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${formatSatuDesimal(mb)} MiB"
        val gb = mb / 1024.0
        return "${formatSatuDesimal(gb)} GiB"
    }

    private fun formatSatuDesimal(nilai: Double): String {
        val dibulatkan = (nilai * 10).toLong() / 10.0
        val str = dibulatkan.toString()
        return if (str.endsWith(".0")) str.substringBefore(".0") else str
    }

    fun formatTanggal(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "-"
        val totalDetik = epochMs / 1000
        val detikPerHari = 86400L
        var sisaHari = totalDetik / detikPerHari

        var tahun = 1970
        while (true) {
            val kabisat = (tahun % 4 == 0 && tahun % 100 != 0) || (tahun % 400 == 0)
            val hariDalamTahun = if (kabisat) 366 else 365
            if (sisaHari >= hariDalamTahun) {
                sisaHari -= hariDalamTahun
                tahun++
            } else {
                break
            }
        }

        val kabisat = (tahun % 4 == 0 && tahun % 100 != 0) || (tahun % 400 == 0)
        val hariPerBulan = intArrayOf(31, if (kabisat) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var bulan = 1
        for (h in hariPerBulan) {
            if (sisaHari >= h) {
                sisaHari -= h
                bulan++
            } else {
                break
            }
        }
        val hari = sisaHari.toInt() + 1

        val detikSisaHari = totalDetik % detikPerHari
        val jam = (detikSisaHari / 3600).toInt()
        val menit = ((detikSisaHari % 3600) / 60).toInt()

        return "${tahun}-${duaDigit(bulan)}-${duaDigit(hari)} ${duaDigit(jam)}:${duaDigit(menit)}"
    }

    private fun duaDigit(angka: Int): String = if (angka < 10) "0$angka" else "$angka"
}