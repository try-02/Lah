package org.fm.com.domain.io

import org.fm.com.domain.model.HasilOperasi

/**
 * Sumber tulis streaming. Siklus hidup: AKTIF → (SELESAI | BATAL).
 *
 * - [tulis] berurutan selama AKTIF; flag `PotonganIsi.selesai` diabaikan di sisi tulis.
 * - Kegagalan I/O pada [tulis] bersifat TERMINAL: implementasi wajib membersihkan
 *   data sementara sebelum mengembalikan [HasilOperasi.Gagal], lalu masuk BATAL.
 * - [selesai] memfinalisasi (adapter nyata: temp + verifikasi, K6/PRD §5.2).
 * - [tulis]/[selesai] setelah terminal → Gagal(VALIDASI), bukan exception.
 * - [batalkan] IDEMPOTEN: selalu Sukses(Unit); setelah SELESAI tidak mengubah hasil;
 *   setelah BATAL boleh mengulang pembersihan sisa. Aman dipasang di `finally`.
 * - Karena [batalkan] adalah suspend, saat menangani CancellationException pemanggil
 *   WAJIB memanggilnya dalam `withContext(NonCancellable)`:
 *
 * ```
 * try {
 *     // ... tulis(potongan) ...
 *     selesai()
 * } finally {
 *     withContext(NonCancellable) { batalkan() }
 * }
 * ```
 */
interface SumberTulisBerkas {
    suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit>
    suspend fun selesai(): HasilOperasi<Unit>
    suspend fun batalkan(): HasilOperasi<Unit>
}