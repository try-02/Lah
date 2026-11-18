package org.fm.com.domain.usecase

import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.JenisBerkas

/** Kriteria pengurutan daftar berkas. */
enum class KriteriaUrutan {
    NAMA,
    UKURAN,
    TIPE,
    TANGGAL
}

/** Arah pengurutan. */
enum class ArahUrutan {
    NAIK,
    TURUN
}

/**
 * Use case murni untuk mengurutkan daftar [EntitasBerkas] tanpa efek samping I/O.
 */
class UrutkanBerkasUseCase {

    operator fun invoke(
        daftar: List<EntitasBerkas>,
        kriteria: KriteriaUrutan = KriteriaUrutan.NAMA,
        arah: ArahUrutan = ArahUrutan.NAIK,
        folderDiAtas: Boolean = true
    ): List<EntitasBerkas> {
        if (daftar.size <= 1) return daftar

        val komparatorDasar: Comparator<EntitasBerkas> = when (kriteria) {
            KriteriaUrutan.NAMA -> compareBy { it.nama.lowercase() }
            KriteriaUrutan.UKURAN -> compareBy { it.ukuranByte ?: -1L }
            KriteriaUrutan.TIPE -> compareBy<EntitasBerkas> { it.jenis.name }.thenBy { it.nama.lowercase() }
            KriteriaUrutan.TANGGAL -> compareBy { it.waktuUbahEpochMs ?: -1L }
        }

        val komparatorArah = if (arah == ArahUrutan.TURUN) {
            komparatorDasar.reversed()
        } else {
            komparatorDasar
        }

        return if (folderDiAtas) {
            val (folder, berkas) = daftar.partition { it.jenis == JenisBerkas.FOLDER }
            folder.sortedWith(komparatorArah) + berkas.sortedWith(komparatorArah)
        } else {
            daftar.sortedWith(komparatorArah)
        }
    }
}