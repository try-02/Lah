package org.fm.com.presentation.terbaru

import org.fm.com.domain.model.EntitasRiwayatLokasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.filemanager.PemetaanDomainUi

data class ItemRiwayatUi(
    val lokasi: LokasiBerkas,
    val nama: String,
    val jenis: JenisBerkas,
    val pathFormat: String,
    val waktuFormat: String,
    val adalahFolder: Boolean
)

data class StateTerbaru(
    val daftarItem: List<ItemRiwayatUi> = emptyList(),
    val sedangMemuat: Boolean = false,
    val pesanGalat: String? = null,
    val dialogHapusSemuaTerbuka: Boolean = false
) {
    val jumlahItem: Int get() = daftarItem.size
}

sealed interface IntentTerbaru {
    data object Segarkan : IntentTerbaru
    data class HapusItem(val lokasi: LokasiBerkas) : IntentTerbaru
    data class SetBukaDialogHapusSemua(val buka: Boolean) : IntentTerbaru
    data object KonfirmasiHapusSemua : IntentTerbaru
    data object HapusPesanGalat : IntentTerbaru
}

fun EntitasRiwayatLokasi.keUi(): ItemRiwayatUi = ItemRiwayatUi(
    lokasi = lokasi,
    nama = lokasi.label ?: lokasi.pengenal.trimEnd('/').substringAfterLast('/').ifBlank { "Root" },
    jenis = if (adalahFolder) JenisBerkas.FOLDER else JenisBerkas.LAIN,
    pathFormat = lokasi.pengenal,
    waktuFormat = PemetaanDomainUi.formatTanggal(waktuTerakhirDibukaEpochMs),
    adalahFolder = adalahFolder
)