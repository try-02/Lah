package org.fm.com.domain.usecase

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class GantiNamaBerkasUseCase(
    private val repositoriOperasi: RepositoriOperasiBerkas
) {
    suspend operator fun invoke(
        sumber: LokasiBerkas,
        namaBaru: String,
        konfirmasi: Boolean = false,
        induk: LokasiBerkas? = null
    ): HasilOperasi<EntitasBerkas> {
        val namaBersih = namaBaru.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            return HasilOperasi.Gagal("Nama baru tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
        }
        return repositoriOperasi.gantiNama(
            PermintaanOperasi(sumber = sumber, tujuan = induk, namaBaru = namaBersih, konfirmasi = konfirmasi)
        )
    }
}