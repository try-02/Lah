package org.fm.com.domain.repository

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas

/** Permintaan operasi yang mengubah penyimpanan. */
data class PermintaanOperasi(
    val sumber: LokasiBerkas,
    val tujuan: LokasiBerkas? = null,
    /** Wajib `true` untuk hapus, timpa, rename massal, dan ekstraksi. */
    val konfirmasi: Boolean = false
)

/**
 * Kontrak operasi berkas. Kegagalan per item dilaporkan, bukan ditelan.
 *
 * Aturan konfirmasi: [buatFolder], [buatBerkasKosong], dan [gantiNama] hanya
 * memerlukan konfirmasi bila sumbernya sudah ada (timpa); [hapus] selalu
 * memerlukan `konfirmasi = true`; [salin] dan [pindah] memerlukan konfirmasi
 * bila tujuannya sudah ada.
 */
interface RepositoriOperasiBerkas {

    suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas>

    suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas>

    suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas>

    suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit>

    suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas>

    suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas>
}

/**
 * Hasil satu operasi massal. Item yang gagal tetap dilaporkan satu per satu;
 * keberhasilan satu item tidak boleh menutupi kegagalan item lain.
 */
data class HasilMassal(
    val berhasil: List<EntitasBerkas> = emptyList(),
    val gagal: List<HasilItemGagal> = emptyList()
) {
    val adaGagal: Boolean get() = gagal.isNotEmpty()

    companion object {
        val KOSONG = HasilMassal()
    }
}

/** Satu kegagalan massal beserta alasan yang siap ditampilkan. */
data class HasilItemGagal(
    val sumber: LokasiBerkas,
    val pesan: String,
    val penyebab: org.fm.com.domain.model.PenyebabGalat =
        org.fm.com.domain.model.PenyebabGalat.TIDAK_DIKETAHUI
)