package org.fm.com.data

import org.fm.com.domain.model.JenisLokasi

/**
 * Model Data untuk satu entitas hasil pembacaan penyimpanan.
 * Field mentah dari penyedia (URI, timestamp ms, flag platform), boleh kosong.
 * Konversi ke domain lewat [PemetaanDataDomain.keDomain].
 */
data class DataBerkas(
    val pengenal: String,
    val nama: String,
    val jenisLokasi: JenisLokasi,
    val adalahFolder: Boolean,
    val adalahTautan: Boolean = false,
    val ukuranByte: Long? = null,
    val waktuUbahEpochMs: Long? = null,
    val tersembunyi: Boolean = false,
    val dapatDibaca: Boolean = true,
    val dapatDitulis: Boolean = true
)

/** Model Data untuk satu folder hasil penelusuran penyimpanan. */
data class DataFolder(
    val pengenal: String,
    val nama: String,
    val jenisLokasi: JenisLokasi,
    val indukPengenal: String? = null
)