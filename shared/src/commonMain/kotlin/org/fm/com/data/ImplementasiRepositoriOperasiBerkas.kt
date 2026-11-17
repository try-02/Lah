package org.fm.com.data

import org.fm.com.domain.model.DataBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.HasilMassal
import org.fm.com.domain.repository.HasilItemGagal
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriOperasiBerkas

/**
 * Implementasi RepositoriOperasiBerkas di `commonMain` tanpa asumsi platform.
 * Kaset kunci setiap entitas menurut [DataBerkas.pengenal] (path unik), sama
 * seperti [ImplementasiRepositoriBerkas]. Setiap kegagalan per item
 * dilaporkan via [HasilItemGagal] bukan exception, agar UI tetap jujur.
 */
class ImplementasiRepositoriOperasiBerkas(
    private val repositoriBerkas: ImplementasiRepositoriBerkas
) : RepositoriOperasiBerkas {

    private fun entitasDi(lokasi: LokasiBerkas): DataBerkas? =
        repositoriBerkas.kaset[lokasi.pengenal]

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        validasiKonfirmasi(permintaan)
        val lokasi = permintaan.tujuan ?: permintaan.sumber
        val entitas = DataBerkas(
            pengenal = lokasi.pengenal,
            nama = lokasi.label ?: lokasi.pengenal,
            jenisLokasi = lokasi.jenis,
            adalahFolder = true
        )
        repositoriBerkas.simpan(entitas)
        return HasilOperasi.Sukses(entitas.keDomain())
    }

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        validasiKonfirmasi(permintaan)
        val lokasi = permintaan.tujuan ?: permintaan.sumber
        val entitas = DataBerkas(
            pengenal = lokasi.pengenal,
            nama = lokasi.label ?: lokasi.pengenal,
            jenisLokasi = lokasi.jenis,
            adalahFolder = false
        )
        repositoriBerkas.simpan(entitas)
        return HasilOperasi.Sukses(entitas.keDomain())
    }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        validasiKonfirmasi(permintaan)
        val tujuan = permintaan.tujuan
            ?: return HasilOperasi.Gagal("Tujuan ganti nama wajib diisi", PenyebabGalat.VALIDASI)
        val sumber = entitasDi(permintaan.sumber)
            ?: return HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        val baru = DataBerkas(
            pengenal = tujuan.pengenal,
            nama = tujuan.label ?: tujuan.pengenal,
            jenisLokasi = tujuan.jenis,
            adalahFolder = sumber.adalahFolder,
            ukuranByte = sumber.ukuranByte,
            waktuUbahEpochMs = sumber.waktuUbahEpochMs,
            tersembunyi = sumber.tersembunyi,
            dapatDibaca = sumber.dapatDibaca,
            dapatDitulis = sumber.dapatDitulis
        )
        repositoriBerkas.simpan(baru)
        repositoriBerkas.hapus(permintaan.sumber.pengenal)
        return HasilOperasi.Sukses(baru.keDomain())
    }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> {
        if (!permintaan.konfirmasi) {
            return HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
        }
        val entitas = entitasDi(permintaan.sumber)
            ?: return HasilOperasi.Gagal("Entitas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        repositoriBerkas.hapus(permintaan.sumber.pengenal)
        return HasilOperasi.Sukses(Unit)
    }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        validasiKonfirmasi(permintaan)
        val tujuan = permintaan.tujuan
            ?: return HasilOperasi.Gagal("Tujuan salin wajib diisi", PenyebabGalat.VALIDASI)
        val sumber = entitasDi(permintaan.sumber)
            ?: return HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        val baru = DataBerkas(
            pengenal = tujuan.pengenal,
            nama = tujuan.label ?: tujuan.pengenal,
            jenisLokasi = tujuan.jenis,
            adalahFolder = sumber.adalahFolder,
            ukuranByte = sumber.ukuranByte,
            waktuUbahEpochMs = sumber.waktuUbahEpochMs,
            tersembunyi = sumber.tersembunyi,
            dapatDibaca = sumber.dapatDibaca,
            dapatDitulis = sumber.dapatDitulis
        )
        repositoriBerkas.simpan(baru)
        return HasilOperasi.Sukses(baru.keDomain())
    }

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        validasiKonfirmasi(permintaan)
        val tujuan = permintaan.tujuan
            ?: return HasilOperasi.Gagal("Tujuan pindah wajib diisi", PenyebabGalat.VALIDASI)
        val sumber = entitasDi(permintaan.sumber)
            ?: return HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        val baru = DataBerkas(
            pengenal = tujuan.pengenal,
            nama = tujuan.label ?: tujuan.pengenal,
            jenisLokasi = tujuan.jenis,
            adalahFolder = sumber.adalahFolder,
            ukuranByte = sumber.ukuranByte,
            waktuUbahEpochMs = sumber.waktuUbahEpochMs,
            tersembunyi = sumber.tersembunyi,
            dapatDibaca = sumber.dapatDibaca,
            dapatDitulis = sumber.dapatDitulis
        )
        repositoriBerkas.simpan(baru)
        repositoriBerkas.hapus(permintaan.sumber.pengenal)
        return HasilOperasi.Sukses(baru.keDomain())
    }

    private fun validasiKonfirmasi(permintaan: PermintaanOperasi) {
        val tujuan = permintaan.tujuan
        if (tujuan != null
            && repositoriBerkas.kaset[tujuan.pengenal] != null
            && !permintaan.konfirmasi
        ) {
            throw IllegalStateException("Konfirmasi wajib untuk timpa tujuan yang sudah ada")
        }
    }
}
