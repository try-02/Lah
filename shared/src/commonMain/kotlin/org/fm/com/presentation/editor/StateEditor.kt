package org.fm.com.presentation.editor

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.LokasiBerkas

data class StateEditor(
    val daftarTab: List<ModelTabEditor> = emptyList(),
    val idTabAktif: String? = null,
    val wordWrapUmum: Boolean = true,
    val tabMenungguKonfirmasiTutup: ModelTabEditor? = null,
    val dialogLompatBarisTerbuka: Boolean = false,
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
        val indeksKursor: Int? = null
    ) : IntentEditor

    data class Undo(val idTab: String) : IntentEditor
    data class Redo(val idTab: String) : IntentEditor

    data class UbahPosisiKursor(
        val idTab: String,
        val indeksKarakter: Int
    ) : IntentEditor

    data class SetBukaDialogLompatBaris(val buka: Boolean) : IntentEditor
    data class LompatKeBaris(
        val idTab: String,
        val nomorBaris: Int
    ) : IntentEditor

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