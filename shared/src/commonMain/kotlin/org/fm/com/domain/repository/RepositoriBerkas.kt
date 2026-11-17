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

    /** Daftar isi folder secara bertahap (lazy), tanpa memuat isi berkas. */
    fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>>

    /** Buka sumber baca streaming untuk satu berkas. */
    suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas>

    /** Buat sumber tulis streaming; [timpa] butuh konfirmasi pemanggil. */
    suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas>
}
