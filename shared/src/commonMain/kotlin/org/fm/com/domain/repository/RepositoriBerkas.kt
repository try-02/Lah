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
     * Daftar isi folder. Kontrak emisi: setiap emisi adalah SNAPSHOT PENUH terbaru
     * isi folder (bukan batch inkremental). Pemakai mengganti state, bukan menggabung.
     * Adapter boleh emit sekali (statis) atau berulang saat isi berubah
     * (mis. ContentObserver SAF / refresh manual iOS).
     *
     * Hasil: folder tidak ada → Gagal(TIDAK_DITEMUKAN); bukan folder → Gagal(VALIDASI);
     * folder kosong → Sukses(emptyList()). Hanya anak langsung, tanpa isi berkas.
     */
    fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>>

    /** Buka sumber baca streaming untuk satu berkas. */
    suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas>

    /**
     * Buat sumber tulis streaming; [timpa] butuh konfirmasi pemanggil bila target ada.
     * Implementasi wajib memeriksa ulang konflik saat `selesai()` (bukan hanya saat buka).
     * Siklus hidup & aturan error: lihat [SumberTulisBerkas].
     */
    suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas>
}