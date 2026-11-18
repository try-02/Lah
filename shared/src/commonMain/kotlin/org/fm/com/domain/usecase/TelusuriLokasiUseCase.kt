package org.fm.com.domain.usecase

import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

/** Representasi satu segmen breadcrumb yang dapat diklik pengguna. */
data class SegmenLokasi(
    val namaTampilan: String,
    val lokasi: LokasiBerkas
)

/**
 * Use case murni untuk mengurai jejak navigasi (breadcrumb) dan menentukan folder induk.
 */
class TelusuriLokasiUseCase {

    fun buatSegmenBreadcrumb(
        lokasiSaatIni: LokasiBerkas,
        riwayatHierarki: List<LokasiBerkas> = emptyList()
    ): List<SegmenLokasi> {
        if (riwayatHierarki.isNotEmpty() && riwayatHierarki.last() == lokasiSaatIni) {
            return riwayatHierarki.map { lok ->
                SegmenLokasi(
                    namaTampilan = lok.label ?: ambilNamaDariPengenal(lok.pengenal),
                    lokasi = lok
                )
            }
        }

        return when (lokasiSaatIni.jenis) {
            JenisLokasi.POSIX -> uraiPathPosix(lokasiSaatIni.pengenal)
            JenisLokasi.SAF, JenisLokasi.IOS -> {
                listOf(
                    SegmenLokasi(
                        namaTampilan = lokasiSaatIni.label ?: ambilNamaDariPengenal(lokasiSaatIni.pengenal),
                        lokasi = lokasiSaatIni
                    )
                )
            }
        }
    }

    private fun uraiPathPosix(path: String): List<SegmenLokasi> {
        val segmen = mutableListOf<SegmenLokasi>()
        segmen.add(SegmenLokasi("Root", LokasiBerkas("/", JenisLokasi.POSIX, "Root")))

        val bagian = path.split('/').filter { it.isNotBlank() }
        var akumulasiPath = ""
        for (nama in bagian) {
            akumulasiPath += "/$nama"
            segmen.add(SegmenLokasi(nama, LokasiBerkas(akumulasiPath, JenisLokasi.POSIX, nama)))
        }
        return segmen
    }

    private fun ambilNamaDariPengenal(pengenal: String): String {
        val dipotong = pengenal.trimEnd('/')
        val idx = dipotong.lastIndexOf('/')
        return if (idx >= 0 && idx < dipotong.length - 1) {
            dipotong.substring(idx + 1)
        } else {
            dipotong.ifBlank { "Lokasi" }
        }
    }
}