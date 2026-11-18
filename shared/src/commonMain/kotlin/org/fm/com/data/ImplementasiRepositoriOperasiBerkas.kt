package org.fm.com.data

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

/**
 * Validasi bentuk permintaan (nama, parameter wajib, konfirmasi hapus) lalu delegasi
 * ke primitif repositori yang memeriksa konflik/eksistensi secara atomik di bawah mutex.
 * Tidak ada throw exception validasi — semua kegagalan via [HasilOperasi.Gagal].
 */
class ImplementasiRepositoriOperasiBerkas(
    private val repositoriBerkas: ImplementasiRepositoriBerkas
) : RepositoriOperasiBerkas {

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, folder = true)

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, folder = false)

    private suspend fun buat(permintaan: PermintaanOperasi, folder: Boolean): HasilOperasi<EntitasBerkas> {
        val nama = permintaan.namaBaru
            ?: return HasilOperasi.Gagal("namaBaru wajib diisi untuk buat folder/berkas", PenyebabGalat.VALIDASI)
        if (!namaSah(nama)) return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        return repositoriBerkas.buatEntitas(permintaan.sumber, nama, folder, permintaan.konfirmasi)
    }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        val nama = permintaan.namaBaru
            ?: return HasilOperasi.Gagal("namaBaru wajib diisi untuk ganti nama", PenyebabGalat.VALIDASI)
        if (!namaSah(nama)) return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        return repositoriBerkas.gantiNamaPohon(permintaan.sumber, nama, permintaan.konfirmasi)
    }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> {
        if (!permintaan.konfirmasi) {
            return HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
        }
        return if (repositoriBerkas.hapusPohon(permintaan.sumber.pengenal)) {
            HasilOperasi.Sukses(Unit)
        } else {
            HasilOperasi.Gagal("Entitas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        }
    }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = false)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = true)

    private suspend fun salinAtauPindah(permintaan: PermintaanOperasi, pindah: Boolean): HasilOperasi<EntitasBerkas> {
        val induk = permintaan.tujuan
            ?: return HasilOperasi.Gagal("tujuan (folder induk) wajib diisi untuk salin/pindah", PenyebabGalat.VALIDASI)
        permintaan.namaBaru?.let {
            if (!namaSah(it)) return HasilOperasi.Gagal("Nama tidak sah: $it", PenyebabGalat.VALIDASI)
        }
        return repositoriBerkas.salinAtauPindahPohon(
            permintaan.sumber, induk, permintaan.namaBaru, permintaan.konfirmasi, pindah
        )
    }

    private fun namaSah(nama: String): Boolean =
        nama.isNotBlank() && nama != "." && nama != ".." && '/' !in nama && '\\' !in nama
}