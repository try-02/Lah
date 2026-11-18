package org.fm.com.domain.io

import org.fm.com.domain.model.HasilOperasi

/**
 * Sumber tulis streaming. Siklus hidup: AKTIF → (SELESAI | BATAL).
 *
 * - [tulis] dipanggil berurutan selama AKTIF. Flag `PotonganIsi.selesai` diabaikan
 *   di sisi tulis; finalisasi hanya lewat [selesai].
 * - [selesai] memfinalisasi tulisan (adapter nyata: temp + verifikasi, K6/PRD §5.2).
 * - [batalkan] wajib membersihkan data sementara; tidak meninggalkan hasil setengah jadi.
 * - Panggilan apa pun setelah SELESAI/BATAL mengembalikan [HasilOperasi.Gagal] (VALIDASI).
 * - Kegagalan I/O (disk penuh, izin dicabut di tengah tulis) dilaporkan via
 *   [HasilOperasi.Gagal]; implementasi TIDAK melempar exception operasional.
 */
interface SumberTulisBerkas {
    suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit>
    suspend fun selesai(): HasilOperasi<Unit>
    suspend fun batalkan(): HasilOperasi<Unit>
}