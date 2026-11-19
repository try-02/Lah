package org.fm.com.presentation.editor

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.RentangKecocokan

data class StateCariGanti(
    val terbuka: Boolean = false,
    val kueriCari: String = "",
    val teksPengganti: String = "",
    val pekaHuruf: Boolean = false,
    val kataUtuh: Boolean = false,
    val gunakanRegex: Boolean = false,
    val daftarKecocokan: List<RentangKecocokan> = emptyList(),
    val indeksKecocokanAktif: Int = -1,
    val galatPola: String? = null,
    val sedangMencari: Boolean = false,
    val konfirmasiGantiSemuaJumlah: Int? = null
) {
    val jumlahKecocokan: Int
        get() = daftarKecocokan.size

    val kecocokanAktif: RentangKecocokan?
        get() = daftarKecocokan.getOrNull(indeksKecocokanAktif)
}

data class StateEditor(
    val daftarTab: List<ModelTabEditor> = emptyList(),
    val idTabAktif: String? = null,
    val wordWrapUmum: Boolean = true,
    val tabMenungguKonfirmasiTutup: ModelTabEditor? = null,
    val dialogLompatBarisTerbuka: Boolean = false,
    val cariGanti: StateCariGanti = StateCariGanti(),
    val riwayatPencarian: List<ItemRiwayatPencarianUi> = emptyList(),
    val pesanInfo: String? = null
) {
    val tabAktif: ModelTabEditor?
        get() = daftarTab.firstOrNull { it.idTab == idTabAktif }

    val jumlahTab: Int
        get() = daftarTab.size

    val adaTabBelumTersimpan: Boolean
        get() = daftarTab.any { it.adaPerubahanBelumTersimpan }
}

sealed interface IntentEditor {
    data class BukaBerkas(
        val entitas: EntitasBerkas,
        val barisTujuan: Int? = null
    ) : IntentEditor

    data class BukaLokasi(
        val lokasi: LokasiBerkas,
        val nama: String? = null,
        val dapatDitulis: Boolean = true,
        val ukuranByte: Long? = null,
        val waktuUbahMs: Long? = null,
        val barisTujuan: Int? = null
    ) : IntentEditor

    data class BuatTabKosong(
        val nama: String = "Tanpa Judul.txt"
    ) : IntentEditor

    data class PilihTab(val idTab: String) : IntentEditor
    data class MintaTutupTab(val idTab: String) : IntentEditor
    data object KonfirmasiBuangDanTutupTab : IntentEditor
    data object BatalTutupTab : IntentEditor
    data class TutupSemuaTab(val paksa: Boolean = false) : IntentEditor

    data class UbahTeks(
        val idTab: String,
        val teksBaru: String,
        val indeksKursor: Int? = null,
        val indeksAkhirSeleksi: Int? = null
    ) : IntentEditor

    data class Undo(val idTab: String) : IntentEditor
    data class Redo(val idTab: String) : IntentEditor

    data class UbahPosisiKursor(
        val idTab: String,
        val indeksKarakter: Int,
        val indeksAkhirSeleksi: Int = indeksKarakter
    ) : IntentEditor

    data class SetBukaDialogLompatBaris(val buka: Boolean) : IntentEditor
    data class LompatKeBaris(
        val idTab: String,
        val nomorBaris: Int
    ) : IntentEditor

    data class SetBukaDialogCariGanti(val buka: Boolean) : IntentEditor
    data class UbahKueriCari(val kueri: String) : IntentEditor
    data class UbahTeksPengganti(val teks: String) : IntentEditor
    data object TogglePekaHurufCari : IntentEditor
    data object ToggleKataUtuhCari : IntentEditor
    data object ToggleRegexCari : IntentEditor
    data object CariBerikutnya : IntentEditor
    data object CariSebelumnya : IntentEditor
    data object GantiSatu : IntentEditor
    data object MintaGantiSemua : IntentEditor
    data object KonfirmasiGantiSemua : IntentEditor
    data object BatalGantiSemua : IntentEditor
    data class PilihRiwayatPencarian(val item: ItemRiwayatPencarianUi) : IntentEditor
    data class HapusItemRiwayatPencarian(val kueri: String) : IntentEditor
    data object BersihkanRiwayatPencarian : IntentEditor

    data class ToggleModeHanyaBaca(val idTab: String) : IntentEditor
    data class ToggleWordWrapTab(val idTab: String) : IntentEditor
    data class SetWordWrapUmum(val aktif: Boolean) : IntentEditor
    data class MuatUlangTab(val idTab: String) : IntentEditor

    data class TandaiTersimpan(
        val idTab: String,
        val lokasiBaru: LokasiBerkas? = null,
        val namaBaru: String? = null,
        val waktuUbahBaruMs: Long? = null
    ) : IntentEditor

    data class SetPesanInfo(val pesan: String?) : IntentEditor
}