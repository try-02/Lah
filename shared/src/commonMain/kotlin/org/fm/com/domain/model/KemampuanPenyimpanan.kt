package org.fm.com.domain.model

/** Platform penyimpanan yang menentukan batasan akses berkas. */
enum class PlatformPenyimpanan {
    ANDROID,
    IOS
}

/** Operasi yang bisa dinonaktifkan di UI bila tidak didukung lokasi saat ini. */
enum class OperasiBerkas {
    MELIHAT_DAFTAR,
    MEMBACA,
    MENULIS,
    MEMBUAT,
    MENGGANTI_NAMA,
    MENGHAPUS,
    MENYALIN,
    MEMINDAHKAN,
    MEMBAGIKAN,
    MEMBUKA_DENGAN,
    MEMBOOKMARK,
    MENGKOMPRES,
    MENGEKSTRAK,
    MEMBUAT_TAUTAN_SIMBOLIK,
    MELIHAT_PATH_POSIX
}

/** Status satu operasi. Alasan wajib bila operasi tidak didukung. */
data class StatusDukungan(
    val didukung: Boolean,
    val alasan: String? = null
) {
    init {
        require(didukung || !alasan.isNullOrBlank()) {
            "Operasi yang tidak didukung wajib menyertakan alasan"
        }
    }

    companion object {
        val DIDUKUNG = StatusDukungan(true)

        fun tidakDidukung(alasan: String) = StatusDukungan(false, alasan)
    }
}

/**
 * Kapabilitas penyimpanan untuk satu kombinasi platform dan jenis lokasi.
 * UI memakai [apakahDidukung] untuk menonaktifkan opsi dan [alasan] untuk
 * menjelaskannya — kegagalan tidak boleh dilaporkan sebagai sukses.
 */
data class KemampuanPenyimpanan(
    val platform: PlatformPenyimpanan,
    val jenisLokasi: JenisLokasi,
    private val status: Map<OperasiBerkas, StatusDukungan>
) {
    init {
        val belumLengkap = OperasiBerkas.entries.toSet() - status.keys
        require(belumLengkap.isEmpty()) { "Status belum lengkap: $belumLengkap" }
    }

    fun apakahDidukung(operasi: OperasiBerkas): Boolean =
        status.getValue(operasi).didukung

    fun alasan(operasi: OperasiBerkas): String? = status.getValue(operasi).alasan

    fun operasiDidukung(): Set<OperasiBerkas> =
        status.filterValues { it.didukung }.keys
}
