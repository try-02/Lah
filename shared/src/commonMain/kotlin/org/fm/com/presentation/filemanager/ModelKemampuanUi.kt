package org.fm.com.presentation.filemanager

import androidx.compose.runtime.Immutable
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.usecase.KemampuanDipakaiUseCase

@Immutable
data class ModelKemampuanUi(
    val dapatMembuat: Boolean = true,
    val dapatGantiNama: Boolean = true,
    val dapatHapus: Boolean = true,
    val dapatSalin: Boolean = true,
    val dapatPindah: Boolean = true,
    val dapatBagikan: Boolean = true,
    val dapatBukaDengan: Boolean = true,
    val dapatBuatTautanSimbolik: Boolean = true,
    val dapatLihatPathPosix: Boolean = true,
    val alasanTidakDidukung: Map<OperasiBerkas, String> = emptyMap()
) {
    fun alasan(operasi: OperasiBerkas): String? = alasanTidakDidukung[operasi]

    companion object {
        val DEFAULT = ModelKemampuanUi()

        fun dari(
            kemampuan: KemampuanPenyimpanan?,
            useCase: KemampuanDipakaiUseCase = KemampuanDipakaiUseCase()
        ): ModelKemampuanUi {
            if (kemampuan == null) return DEFAULT
            val statusPerOperasi = OperasiBerkas.entries.associateWith { useCase(kemampuan, it) }
            val alasanMap = buildMap {
                for ((op, status) in statusPerOperasi) {
                    val alasan = status.alasan
                    if (!status.didukung && alasan != null) {
                        put(op, alasan)
                    }
                }
            }
            return ModelKemampuanUi(
                dapatMembuat = statusPerOperasi.getValue(OperasiBerkas.MEMBUAT).didukung,
                dapatGantiNama = statusPerOperasi.getValue(OperasiBerkas.MENGGANTI_NAMA).didukung,
                dapatHapus = statusPerOperasi.getValue(OperasiBerkas.MENGHAPUS).didukung,
                dapatSalin = statusPerOperasi.getValue(OperasiBerkas.MENYALIN).didukung,
                dapatPindah = statusPerOperasi.getValue(OperasiBerkas.MEMINDAHKAN).didukung,
                dapatBagikan = statusPerOperasi.getValue(OperasiBerkas.MEMBAGIKAN).didukung,
                dapatBukaDengan = statusPerOperasi.getValue(OperasiBerkas.MEMBUKA_DENGAN).didukung,
                dapatBuatTautanSimbolik = statusPerOperasi.getValue(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK).didukung,
                dapatLihatPathPosix = statusPerOperasi.getValue(OperasiBerkas.MELIHAT_PATH_POSIX).didukung,
                alasanTidakDidukung = alasanMap
            )
        }
    }
}