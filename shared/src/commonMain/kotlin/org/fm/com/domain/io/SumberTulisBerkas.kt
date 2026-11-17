package org.fm.com.domain.io

/**
 * Sumber tulis streaming. [tulis] dipanggil berurutan, lalu [selesai] bila
 * semua potongan berhasil. [batalkan] dipakai bila operasi gagal/dibatalkan:
 * implementasi wajib membersihkan data sementara, bukan meninggalkan hasil setengah jadi.
 */
interface SumberTulisBerkas {
    suspend fun tulis(potongan: PotonganIsi)
    suspend fun selesai()
    suspend fun batalkan()
}
