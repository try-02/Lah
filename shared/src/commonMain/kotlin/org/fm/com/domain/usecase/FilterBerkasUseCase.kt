package org.fm.com.domain.usecase

import org.fm.com.domain.model.EntitasBerkas

/**
 * Use case untuk menyaring daftar berkas berdasarkan visibilitas tersembunyi dan kata kunci.
 */
class FilterBerkasUseCase {

    operator fun invoke(
        daftar: List<EntitasBerkas>,
        tampilkanTersembunyi: Boolean = false,
        kataKunci: String = ""
    ): List<EntitasBerkas> {
        var hasil = if (!tampilkanTersembunyi) {
            daftar.filterNot { it.tersembunyi }
        } else {
            daftar
        }

        val kueri = kataKunci.trim().lowercase()
        if (kueri.isNotEmpty()) {
            hasil = hasil.filter { it.nama.lowercase().contains(kueri) }
        }

        return hasil
    }
}