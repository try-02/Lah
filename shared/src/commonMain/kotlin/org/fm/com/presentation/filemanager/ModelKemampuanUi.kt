package org.fm.com.presentation.filemanager

import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.OperasiBerkas

/**
 * Model UI untuk mengaktifkan/menonaktifkan kontrol berdasarkan kapabilitas penyimpanan (PRD §4.1, §5.1).
 */
data class ModelKemampuanUi(
    val dapatMembuat: Boolean = true,
    val dapatGantiNama: Boolean = true,
    val dapatHapus: Boolean = true,
    val dapatSalin: Boolean = true,
    val dapatPindah: Boolean = true,
    val dapatBagikan: Boolean = true,
    val dapatBukaDengan: Boolean = true,
    val dapatLihatPathPosix: Boolean = true,
    val alasanTidakDidukung: Map<OperasiBerkas, String> = emptyMap()
) {
    fun alasan(operasi: OperasiBerkas): String? = alasanTidakDidukung[operasi]

    companion object {
        val DEFAULT = ModelKemampuanUi()

        fun dari(kemampuan: KemampuanPenyimpanan?): ModelKemampuanUi {
            if (kemampuan == null) return DEFAULT

            val alasanMap = buildMap {
                for (op in OperasiBerkas.entries) {
                    val alasan = kemampuan.alasan(op)
                    if (alasan != null) {
                        put(op, alasan)
                    }
                }
            }

            return ModelKemampuanUi(
                dapatMembuat = kemampuan.apakahDidukung(OperasiBerkas.MEMBUAT),
                dapatGantiNama = kemampuan.apakahDidukung(OperasiBerkas.MENGGANTI_NAMA),
                dapatHapus = kemampuan.apakahDidukung(OperasiBerkas.MENGHAPUS),
                dapatSalin = kemampuan.apakahDidukung(OperasiBerkas.MENYALIN),
                dapatPindah = kemampuan.apakahDidukung(OperasiBerkas.MEMINDAHKAN),
                dapatBagikan = kemampuan.apakahDidukung(OperasiBerkas.MEMBAGIKAN),
                dapatBukaDengan = kemampuan.apakahDidukung(OperasiBerkas.MEMBUKA_DENGAN),
                dapatLihatPathPosix = kemampuan.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX),
                alasanTidakDidukung = alasanMap
            )
        }
    }
}