package org.fm.com.domain.usecase

import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class HapusBerkasUseCase(
    private val repositoriOperasi: RepositoriOperasiBerkas
) {
    suspend operator fun invoke(sumber: LokasiBerkas, konfirmasi: Boolean): HasilOperasi<Unit> {
        if (!konfirmasi) {
            return HasilOperasi.Gagal("Penghapusan memerlukan konfirmasi", PenyebabGalat.VALIDASI)
        }
        return repositoriOperasi.hapus(
            PermintaanOperasi(sumber = sumber, konfirmasi = true)
        )
    }
}