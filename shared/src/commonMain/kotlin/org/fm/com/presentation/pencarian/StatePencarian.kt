package org.fm.com.presentation.pencarian

import androidx.compose.runtime.Immutable
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.GalatBacaCariIsi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.domain.model.ProgresCariIsi
import org.fm.com.domain.model.ProgresCariNama
import org.fm.com.presentation.filemanager.PemetaanDomainUi
import kotlinx.datetime.TimeZone

enum class ModePencarian {
    NAMA,
    ISI
}

enum class SatuanUkuranFilter(val label: String, val pengaliByte: Long) {
    BYTE("B", 1L),
    KIB("KiB", 1024L),
    MIB("MiB", 1024L * 1024L)
}

enum class RentangTanggalFilter(val label: String, val durasiMs: Long?) {
    SEMUA("Semua", null),
    HARI_INI("24 Jam", 24L * 60L * 60L * 1000L),
    TUJUH_HARI("7 Hari", 7L * 24L * 60L * 60L * 1000L),
    TIGA_PULUH_HARI("30 Hari", 30L * 24L * 60L * 60L * 1000L)
}

enum class OpsiBatasUkuranIsi(val label: String, val byte: Long) {
    LIMA_RATUS_KIB("512 KiB", 512L * 1024L),
    DUA_MIB("2 MiB", 2L * 1024L * 1024L),
    LIMA_MIB("5 MiB", 5L * 1024L * 1024L)
}

data class ItemHasilCariNamaUi(
    val entitas: EntitasBerkas,
    val nama: String,
    val lokasiRelatif: String,
    val jenis: JenisBerkas,
    val adalahFolder: Boolean,
    val ukuranFormat: String,
    val tanggalFormat: String
)

data class BarisCocokUi(
    val nomorBaris: Int,
    val potonganTeks: String,
    val jumlahKecocokanDiBaris: Int
)

@Immutable
data class ItemHasilCariIsiUi(
    val entitas: EntitasBerkas,
    val nama: String,
    val lokasiRelatif: String,
    val jumlahKecocokanTotal: Int,
    val daftarBarisCocok: List<BarisCocokUi>,
    val daftarTerpotongBatas: Boolean
)

@Immutable
data class StatePencarian(
    val folderAkar: LokasiBerkas? = null,
    val modePencarian: ModePencarian = ModePencarian.NAMA,
    val kueri: String = "",
    val rekursif: Boolean = true,
    val sertakanTersembunyi: Boolean = false,
    val filterEkstensiInput: String = "",
    val ukuranMinInput: String = "",
    val ukuranMaksInput: String = "",
    val satuanUkuran: SatuanUkuranFilter = SatuanUkuranFilter.KIB,
    val filterTerbuka: Boolean = false,
    // Opsi khusus cari nama
    val modeCocokNama: ModeCocokNama = ModeCocokNama.SEBAGIAN,
    val pekaHurufNama: Boolean = false,
    val filterJenisNama: FilterJenisEntitas = FilterJenisEntitas.SEMUA,
    val rentangTanggal: RentangTanggalFilter = RentangTanggalFilter.SEMUA,
    // Opsi khusus cari isi
    val pekaHurufIsi: Boolean = false,
    val kataUtuhIsi: Boolean = false,
    val gunakanRegexIsi: Boolean = false,
    val hanyaFormatTeksIsi: Boolean = true,
    val batasUkuranIsi: OpsiBatasUkuranIsi = OpsiBatasUkuranIsi.DUA_MIB,
    // Status eksekusi & hasil
    val sedangMencari: Boolean = false,
    val pernahMencari: Boolean = false,
    val hasilNama: List<ItemHasilCariNamaUi> = emptyList(),
    val hasilIsi: List<ItemHasilCariIsiUi> = emptyList(),
    val jumlahBerkasDipindai: Int = 0,
    val jumlahFolderDipindai: Int = 0,
    val jumlahBinerDilewati: Int = 0,
    val jumlahTerlaluBesarDilewati: Int = 0,
    val jumlahPeringatanFolder: Int = 0,
    val daftarGalatBerkas: List<GalatBacaCariIsi> = emptyList(),
    val itemDiprosesSaatIni: String = "",
    val terpotongBatasHasil: Boolean = false,
    val pesanGalat: String? = null
)

sealed interface IntentPencarian {
    data class SetFolderAkar(val lokasi: LokasiBerkas?) : IntentPencarian
    data class UbahModePencarian(val mode: ModePencarian) : IntentPencarian
    data class UbahKueri(val kueri: String) : IntentPencarian
    data object ToggleFilterTerbuka : IntentPencarian
    data object ToggleRekursif : IntentPencarian
    data object ToggleTersembunyi : IntentPencarian
    data class UbahFilterEkstensi(val input: String) : IntentPencarian
    data class UbahUkuranMin(val input: String) : IntentPencarian
    data class UbahUkuranMaks(val input: String) : IntentPencarian
    data class UbahSatuanUkuran(val satuan: SatuanUkuranFilter) : IntentPencarian
    data class UbahModeCocokNama(val mode: ModeCocokNama) : IntentPencarian
    data object TogglePekaHurufNama : IntentPencarian
    data class UbahFilterJenisNama(val jenis: FilterJenisEntitas) : IntentPencarian
    data class UbahRentangTanggal(val rentang: RentangTanggalFilter) : IntentPencarian
    data object TogglePekaHurufIsi : IntentPencarian
    data object ToggleKataUtuhIsi : IntentPencarian
    data object ToggleRegexIsi : IntentPencarian
    data object ToggleHanyaFormatTeksIsi : IntentPencarian
    data class UbahBatasUkuranIsi(val batas: OpsiBatasUkuranIsi) : IntentPencarian
    data object ResetFilter : IntentPencarian
    data object MulaiPencarian : IntentPencarian
    data object BatalkanPencarian : IntentPencarian
    data object HapusGalat : IntentPencarian
}

fun ProgresCariNama.keUiList(): List<ItemHasilCariNamaUi> {
        val zonaWaktu = TimeZone.currentSystemDefault()
        return daftarTemuan.map { item ->
            val e = item.entitas
            val adalahFolder = e.jenis == JenisBerkas.FOLDER
            ItemHasilCariNamaUi(
                entitas = e,
                nama = e.nama,
                lokasiRelatif = item.lokasiRelatif,
                jenis = e.jenis,
                adalahFolder = adalahFolder,
                ukuranFormat = PemetaanDomainUi.formatUkuran(e.ukuranByte, adalahFolder),
                tanggalFormat = PemetaanDomainUi.formatTanggal(e.waktuUbahEpochMs, zonaWaktu)
            )
        }
    }

fun ProgresCariIsi.keUiList(): List<ItemHasilCariIsiUi> =
    daftarHasil.map { berkas ->
        ItemHasilCariIsiUi(
            entitas = berkas.entitas,
            nama = berkas.entitas.nama,
            lokasiRelatif = berkas.lokasiRelatif,
            jumlahKecocokanTotal = berkas.jumlahKecocokanTotal,
            daftarBarisCocok = berkas.daftarBarisCocok.map {
                BarisCocokUi(
                    nomorBaris = it.nomorBaris,
                    potonganTeks = it.potonganTeks,
                    jumlahKecocokanDiBaris = it.jumlahKecocokanDiBaris
                )
            },
            daftarTerpotongBatas = berkas.daftarTerpotongBatas
        )
    }