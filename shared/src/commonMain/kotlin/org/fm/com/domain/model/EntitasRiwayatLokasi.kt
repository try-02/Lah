package org.fm.com.domain.model

data class EntitasRiwayatLokasi(
    val lokasi: LokasiBerkas,
    val adalahFolder: Boolean,
    val waktuTerakhirDibukaEpochMs: Long
)