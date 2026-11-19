package org.fm.com.domain.model

data class EntitasRiwayatPencarian(
    val kueri: String,
    val pekaHuruf: Boolean = false,
    val kataUtuh: Boolean = false,
    val gunakanRegex: Boolean = false,
    val waktuTerakhirDipakaiEpochMs: Long = 0L
) {
    init {
        require(kueri.isNotEmpty()) { "Kueri pencarian tidak boleh kosong" }
    }
}