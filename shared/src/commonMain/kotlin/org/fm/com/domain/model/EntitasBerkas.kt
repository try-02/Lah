package org.fm.com.domain.model

/**
 * Entitas berkas atau folder di layer domain.
 *
 * Ukuran dan waktu boleh null bila penyedia tidak memberi informasi. Model ini
 * sengaja bebas dari API platform, UI, dan database agar bisa dipakai di semua
 * source set bersama.
 */
data class EntitasBerkas(
    val lokasi: LokasiBerkas,
    val nama: String,
    val jenis: JenisBerkas,
    val ukuranByte: Long? = null,
    val waktuUbahEpochMs: Long? = null,
    val tersembunyi: Boolean = false,
    val dapatDibaca: Boolean = true,
    val dapatDitulis: Boolean = true
) {
    init {
        require(nama.isNotBlank()) { "Nama entitas tidak boleh kosong" }
        require(ukuranByte?.let { it >= 0 } != false) { "Ukuran entitas tidak boleh negatif" }
    }
}
