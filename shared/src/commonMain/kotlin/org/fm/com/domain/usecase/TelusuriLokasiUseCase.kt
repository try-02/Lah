package org.fm.com.domain.usecase

import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.sftp.PengenalSftp

data class SegmenLokasi(
    val namaTampilan: String,
    val lokasi: LokasiBerkas
)

class TelusuriLokasiUseCase {
    fun buatSegmenBreadcrumb(
        lokasiSaatIni: LokasiBerkas,
        riwayatHierarki: List<LokasiBerkas> = emptyList()
    ): List<SegmenLokasi> {
        return when (lokasiSaatIni.jenis) {
            JenisLokasi.POSIX -> uraiPathPosix(lokasiSaatIni.pengenal)
            JenisLokasi.SFTP -> uraiPathSftp(lokasiSaatIni.pengenal)
            JenisLokasi.SAF, JenisLokasi.IOS -> {
                if (riwayatHierarki.isNotEmpty() && riwayatHierarki.last() == lokasiSaatIni) {
                    riwayatHierarki.map { lok ->
                        SegmenLokasi(
                            namaTampilan = lok.label ?: ambilNamaDariPengenal(lok.pengenal),
                            lokasi = lok
                        )
                    }
                } else {
                    listOf(
                        SegmenLokasi(
                            namaTampilan = lokasiSaatIni.label ?: ambilNamaDariPengenal(lokasiSaatIni.pengenal),
                            lokasi = lokasiSaatIni
                        )
                    )
                }
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

    private fun uraiPathSftp(pengenal: String): List<SegmenLokasi> {
        val lok = PengenalSftp.urai(pengenal) ?: return listOf(SegmenLokasi("SFTP", LokasiBerkas(pengenal, JenisLokasi.SFTP, "SFTP")))
        val segmen = mutableListOf<SegmenLokasi>()
        segmen.add(
            SegmenLokasi("Root", LokasiBerkas(PengenalSftp.buat(lok.idProfil, "/"), JenisLokasi.SFTP, "Root"))
        )
        val bagian = lok.path.split('/').filter { it.isNotBlank() }
        var akumulasi = ""
        for (nama in bagian) {
            akumulasi += "/$nama"
            segmen.add(SegmenLokasi(nama, LokasiBerkas(PengenalSftp.buat(lok.idProfil, akumulasi), JenisLokasi.SFTP, nama)))
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