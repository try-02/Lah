package org.fm.com.domain.usecase

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class BuatBerkasUseCase(
    private val repositoriOperasi: RepositoriOperasiBerkas
) {
    suspend fun buatFolder(induk: LokasiBerkas, nama: String, konfirmasi: Boolean = false): HasilOperasi<EntitasBerkas> {
        val namaBersih = nama.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            return HasilOperasi.Gagal("Nama folder tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        return repositoriOperasi.buatFolder(
            PermintaanOperasi(sumber = induk, namaBaru = namaBersih, konfirmasi = konfirmasi)
        )
    }

    suspend fun buatBerkasKosong(induk: LokasiBerkas, nama: String, konfirmasi: Boolean = false): HasilOperasi<EntitasBerkas> {
        val namaBersih = nama.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            return HasilOperasi.Gagal("Nama berkas tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        return repositoriOperasi.buatBerkasKosong(
            PermintaanOperasi(sumber = induk, namaBaru = namaBersih, konfirmasi = konfirmasi)
        )
    }
}