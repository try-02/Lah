@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.presentation.filemanager

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.SegmenLokasi

enum class ModeTampilan {
    DAFTAR,
    KISI
}

enum class JenisAksiTimpa {
    BUAT_FOLDER,
    BUAT_BERKAS,
    GANTI_NAMA
}

data class PermintaanKonfirmasiTimpa(
    val jenis: JenisAksiTimpa,
    val nama: String,
    val sumber: LokasiBerkas? = null
)

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

data class StateFilemanager(
    val lokasiSaatIni: LokasiBerkas? = null,
    val segmenBreadcrumb: List<SegmenLokasi> = emptyList(),
    val daftarItem: List<ItemBerkasUi> = emptyList(),
    val sedangMemuat: Boolean = false,
    val pesanGalat: String? = null,
    val pesanInfo: String? = null,
    val kriteriaUrutan: KriteriaUrutan = KriteriaUrutan.NAMA,
    val arahUrutan: ArahUrutan = ArahUrutan.NAIK,
    val folderDiAtas: Boolean = true,
    val tampilkanTersembunyi: Boolean = false,
    val modeTampilan: ModeTampilan = ModeTampilan.DAFTAR,
    val kataKunciSaring: String = "",
    val itemTerpilih: Set<LokasiBerkas> = emptySet(),
    val kemampuanLokasi: KemampuanPenyimpanan? = null,
    val kemampuanUi: ModelKemampuanUi = ModelKemampuanUi.DEFAULT,
    val dialogUrutkanTerbuka: Boolean = false,
    val dialogBuatTerbuka: Boolean = false,
    val dialogPropertiItem: ItemBerkasUi? = null,
    val dialogRenameItem: ItemBerkasUi? = null,
    val dialogHapusItems: List<LokasiBerkas>? = null,
    val dialogBukaDenganItem: ItemBerkasUi? = null,
    val dialogBagikanItem: ItemBerkasUi? = null,
    val konfirmasiTimpa: PermintaanKonfirmasiTimpa? = null,
    val dapatNavigasiKembali: Boolean = false
) {
    val jumlahItem: Int get() = daftarItem.size
    val sedangMultiSelect: Boolean get() = itemTerpilih.isNotEmpty()
}

sealed interface IntentFilemanager {
    data class BukaLokasi(val lokasi: LokasiBerkas) : IntentFilemanager
    data class NavigasiKeSegmen(val segmen: SegmenLokasi) : IntentFilemanager
    data object NavigasiKembali : IntentFilemanager
    data object Segarkan : IntentFilemanager
    data class UbahKataKunciSaring(val kataKunci: String) : IntentFilemanager
    data class UbahPengurutan(val kriteria: KriteriaUrutan, val arah: ArahUrutan? = null) : IntentFilemanager
    data object ToggleFolderDiAtas : IntentFilemanager
    data object ToggleFileTersembunyi : IntentFilemanager
    data class UbahModeTampilan(val mode: ModeTampilan) : IntentFilemanager
    data class TogglePilihItem(val lokasi: LokasiBerkas) : IntentFilemanager
    data object PilihSemua : IntentFilemanager
    data object BersihkanPilihan : IntentFilemanager
    data class SetBukaDialogUrutkan(val buka: Boolean) : IntentFilemanager
    data class SetBukaDialogBuat(val buka: Boolean) : IntentFilemanager
    data class BuatFolderBaru(val nama: String) : IntentFilemanager
    data class BuatBerkasBaru(val nama: String) : IntentFilemanager
    data class BukaDialogProperti(val item: ItemBerkasUi?) : IntentFilemanager
    data class BukaDialogRename(val item: ItemBerkasUi?) : IntentFilemanager
    data class EksekusiRename(val sumber: LokasiBerkas, val namaBaru: String) : IntentFilemanager
    data class KonfirmasiTimpaAksi(val permintaan: PermintaanKonfirmasiTimpa) : IntentFilemanager
    data object TutupKonfirmasiTimpa : IntentFilemanager
    data class BukaDialogHapus(val items: List<LokasiBerkas>?) : IntentFilemanager
    data object EksekusiHapusTerpilih : IntentFilemanager
    data class BukaDialogBukaDengan(val item: ItemBerkasUi?) : IntentFilemanager
    data class BukaDialogBagikan(val item: ItemBerkasUi?) : IntentFilemanager
    data class SetPesanInfo(val pesan: String?) : IntentFilemanager
    data object HapusPesanGalat : IntentFilemanager
}

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

    private const val BATAS_MAKS_EPOCH_MS = 253_402_300_799_999L // 9999-12-31T23:59:59.999Z

    fun formatTanggal(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L || epochMs > BATAS_MAKS_EPOCH_MS) return "-"
        return runCatching {
            val dt = Instant.fromEpochMilliseconds(epochMs)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            if (dt.year !in 1970..9999) return "-"
            "${dt.year}-${duaDigit(dt.month.number)}-${duaDigit(dt.day)} " +
                "${duaDigit(dt.hour)}:${duaDigit(dt.minute)}"
        }.getOrElse { "-" }
    }

    private fun duaDigit(angka: Int): String = if (angka < 10) "0$angka" else "$angka"
}