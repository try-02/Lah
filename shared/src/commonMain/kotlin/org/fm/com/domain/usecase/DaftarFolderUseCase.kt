package org.fm.com.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas

/**
 * Use case untuk memuat daftar isi folder dari [RepositoriBerkas]
 * dan menyaring berkas tersembunyi jika diminta.
 */
class DaftarFolderUseCase(
    private val repositoriBerkas: RepositoriBerkas
) {
    operator fun invoke(
        lokasi: LokasiBerkas,
        tampilkanTersembunyi: Boolean = false
    ): Flow<HasilOperasi<List<EntitasBerkas>>> {
        return repositoriBerkas.daftarIsi(lokasi).map { hasil ->
            when (hasil) {
                is HasilOperasi.Sukses -> {
                    val tersaring = if (tampilkanTersembunyi) {
                        hasil.nilai
                    } else {
                        hasil.nilai.filterNot { it.tersembunyi }
                    }
                    HasilOperasi.Sukses(tersaring)
                }
                is HasilOperasi.Gagal -> hasil
            }
        }
    }
}