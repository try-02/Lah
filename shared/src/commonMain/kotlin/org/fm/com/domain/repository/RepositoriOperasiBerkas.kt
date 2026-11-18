package org.fm.com.domain.repository

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

/**
 * Permintaan operasi yang mengubah penyimpanan. Semantik per operasi (seragam
 * untuk POSIX, SAF `DocumentsContract`, dan iOS `FileManager`):
 *
 * - [buatFolder]/[buatBerkasKosong]: [sumber] = folder induk; [namaBaru] WAJIB
 *   (nama tampilan entitas baru). [tujuan] diabaikan.
 * - [gantiNama]: [sumber] = entitas yang diubah; [namaBaru] WAJIB (induk tetap;
 *   cocok dengan `renameDocument(uri, displayName)` SAF). [tujuan] diabaikan.
 * - [salin]/[pindah]: [sumber] = entitas asal; [tujuan] WAJIB = folder induk tujuan;
 *   [namaBaru] opsional, bawaan = nama asal.
 * - [hapus]: [sumber] = entitas; [konfirmasi] wajib `true`.
 *
 * [konfirmasi] wajib `true` bila TARGET hasil komputasi sudah ada (timpa),
 * dan selalu untuk hapus, rename massal, serta ekstraksi.
 */
data class PermintaanOperasi(
    val sumber: LokasiBerkas,
    val tujuan: LokasiBerkas? = null,
    val namaBaru: String? = null,
    val konfirmasi: Boolean = false
)

/**
 * Kontrak operasi berkas per item. Kegagalan dilaporkan via [HasilOperasi.Gagal],
 * bukan exception. Eksekusi massal (agregasi [HasilMassal]) hidup di use case
 * T22 (salin/pindah) dan T42 (rename massal), bukan di kontrak ini.
 *
 * Aturan konfirmasi: [buatFolder], [buatBerkasKosong], [gantiNama], [salin], dan
 * [pindah] memerlukan konfirmasi bila TUJUANNYA sudah ada (timpa);
 * [hapus] selalu memerlukan `konfirmasi = true`.
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
 * Hasil agregasi satu operasi massal — dipakai use case T22/T42.
 * Item gagal dilaporkan satu per satu; sukses item lain tidak menutupi kegagalan.
 */
data class HasilMassal(
    val berhasil: List<EntitasBerkas> = emptyList(),
    val gagal: List<HasilItemGagal> = emptyList()
) {
    val adaGagal: Boolean get() = gagal.isNotEmpty()
    companion object { val KOSONG = HasilMassal() }
}

/** Satu kegagalan massal beserta alasan yang siap ditampilkan. */
data class HasilItemGagal(
    val sumber: LokasiBerkas,
    val pesan: String,
    val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
)