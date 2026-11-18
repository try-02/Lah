package org.fm.com.domain.usecase

import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.StatusDukungan

/**
 * Use case untuk memeriksa apakah satu operasi tertentu didukung oleh lokasi penyimpanan aktif.
 */
class KemampuanDipakaiUseCase {

    operator fun invoke(kemampuan: KemampuanPenyimpanan?, operasi: OperasiBerkas): StatusDukungan {
        if (kemampuan == null) return StatusDukungan.DIDUKUNG
        val didukung = kemampuan.apakahDidukung(operasi)
        return if (didukung) {
            StatusDukungan.DIDUKUNG
        } else {
            StatusDukungan.tidakDidukung(
                kemampuan.alasan(operasi) ?: "Operasi tidak didukung oleh lokasi penyimpanan saat ini"
            )
        }
    }
}