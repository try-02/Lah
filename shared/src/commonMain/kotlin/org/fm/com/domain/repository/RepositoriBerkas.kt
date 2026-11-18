package org.fm.com.domain.repository

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas

/** Kontrak navigator & pembaca berkas. Adapter platform mengimplementasikan ini. */
interface RepositoriBerkas {
    /** Kapabilitas lokasi; `null` bila kombinasi platform/lokasi tidak dikenal. */
    fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan?

    /**
     * Daftar isi folder. Setiap emisi adalah SNAPSHOT PENUH terbaru (bukan batch
     * inkremental); pemakai mengganti state, bukan menggabung. Flow boleh selesai
     * setelah satu emisi (cold single-shot) atau tetap terbuka dan emit ulang saat
     * isi berubah (mis. ContentObserver SAF / refresh manual iOS).
     *
     * Hasil: folder tidak ada → Gagal(TIDAK_DITEMUKAN); bukan folder → Gagal(VALIDASI);
     * folder kosong → Sukses(emptyList()). Hanya anak langsung, tanpa isi berkas.
     */
    fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>>

    /** Buka sumber baca streaming untuk satu berkas. */
    suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas>

    /**
     * Buka tulis untuk entitas yang SUDAH ADA / pengenalnya diketahui
     * (mis. simpan editor T32). Untuk berkas BARU gunakan [bukaUntukTulisDi].
     * Konflik diperiksa ulang secara atomik saat `selesai()`.
     */
    suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas>

    /**
     * Buka tulis untuk berkas di dalam [induk] dengan [nama] satu segmen
     * (divalidasi via ValidasiNama). WAJIB dipakai bila pengenal target tidak bisa
     * ditebak sebelum dokumen dibuat — kasus SAF (T22 salin lintas lokasi, T44 ekstraksi).
     *
     * Kewajiban adapter:
     * - SAF: periksa eksistensi anak bernama sama SEBELUM createDocument (provider
     *   diam-diam membuat "berkas (1).txt"); tanpa [timpa] → Gagal(KONFLIK).
     *   Bila dokumen baru dibuat lalu [SumberTulisBerkas.batalkan] dipanggil,
     *   dokumen itu wajib dihapus — tidak meninggalkan entitas kosong.
     * - POSIX/iOS: tulis ke temp di induk lalu verifikasi (K6).
     */
    suspend fun bukaUntukTulisDi(induk: LokasiBerkas, nama: String, timpa: Boolean): HasilOperasi<SumberTulisBerkas>
}