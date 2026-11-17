package org.fm.com.data

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

/**
 * Model Data untuk satu entitas hasil pembacaan penyimpanan.
 *
 * Berbeda dari [EntitasBerkas] domain: field di sini diturunkan mentah dari
 * penyedia penyimpanan (URI, timestamp ms, flag platform) dan boleh kosong.
 * Konversi ke domain dilakukan lewat [PemetaanDataDomain.keDomain].
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