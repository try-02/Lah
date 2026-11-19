package org.fm.com.presentation.editor

import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.EncodingTeks
import org.fm.com.domain.usecase.JenisAkhirBaris

enum class StatusMuatTab {
    MEMUAT,
    SIAP,
    BINER,
    TERLALU_BESAR,
    GAGAL
}

data class PosisiKursor(
    val baris: Int = 1,
    val kolom: Int = 1,
    val indeksKarakter: Int = 0
)

data class ModelTabEditor(
    val idTab: String,
    val lokasi: LokasiBerkas?,
    val namaBerkas: String,
    val statusMuat: StatusMuatTab = StatusMuatTab.MEMUAT,
    val pesanStatus: String? = null,
    val isiTersimpan: String = "",
    val isiSaatIni: String = "",
    val hanyaBacaPermanen: Boolean = false,
    val modeHanyaBaca: Boolean = hanyaBacaPermanen,
    val wordWrapOverride: Boolean? = null,
    val encoding: EncodingTeks = EncodingTeks.UTF_8,
    val akhirBaris: JenisAkhirBaris = JenisAkhirBaris.LF,
    val ukuranByteAsli: Long? = null,
    val waktuUbahTerakhirMs: Long? = null,
    val jumlahBaris: Int = 1,
    val posisiKursor: PosisiKursor = PosisiKursor()
) {
    val adaPerubahanBelumTersimpan: Boolean
        get() = statusMuat == StatusMuatTab.SIAP && isiSaatIni != isiTersimpan

    val dapatDiedit: Boolean
        get() = statusMuat == StatusMuatTab.SIAP && !modeHanyaBaca

    fun wordWrapEfektif(wordWrapUmum: Boolean): Boolean =
        wordWrapOverride ?: wordWrapUmum
}