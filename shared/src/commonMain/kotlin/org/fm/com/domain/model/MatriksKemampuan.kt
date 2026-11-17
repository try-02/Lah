package org.fm.com.domain.model

/**
 * Matriks kapabilitas untuk kombinasi platform dan jenis lokasi yang didukung.
 * Bila kombinasi tidak terdaftar, pemanggil mendapat `null` dan UI harus
 * menonaktifkan seluruh opsi, bukan menebak kapabilitas.
 */
object MatriksKemampuan {

    private val semuaOperasi = OperasiBerkas.entries

    /** Storage privat aplikasi: akses penuh, path POSIX asli tersedia. */
    fun untuk(platform: PlatformPenyimpanan, jenisLokasi: JenisLokasi): KemampuanPenyimpanan? =
        matriks[platform to jenisLokasi]

    val matriks: Map<Pair<PlatformPenyimpanan, JenisLokasi>, KemampuanPenyimpanan> by lazy {
        mapOf(
            (PlatformPenyimpanan.ANDROID to JenisLokasi.POSIX) to kemampuanLengkap(
                PlatformPenyimpanan.ANDROID, JenisLokasi.POSIX
            ),
            (PlatformPenyimpanan.ANDROID to JenisLokasi.SAF) to kemampuan(
                PlatformPenyimpanan.ANDROID, JenisLokasi.SAF,
                tidakDidukung = mapOf(
                    OperasiBerkas.MELIHAT_PATH_POSIX to "SAF hanya menyediakan URI, bukan path POSIX asli",
                    OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK to "SAF tidak mendukung symbolic link"
                )
            ),
            (PlatformPenyimpanan.IOS to JenisLokasi.IOS) to kemampuan(
                PlatformPenyimpanan.IOS, JenisLokasi.IOS,
                tidakDidukung = mapOf(
                    OperasiBerkas.MELIHAT_PATH_POSIX to "Lokasi iOS memakai pengenal sandbox, bukan path POSIX asli",
                    OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK to "Sandbox iOS tidak mendukung symbolic link",
                    OperasiBerkas.MEMBAGIKAN to "Bagikan memakai UIActivityViewController dan tidak tersedia untuk semua lokasi"
                )
            )
        )
    }

    private fun kemampuanLengkap(
        platform: PlatformPenyimpanan,
        jenisLokasi: JenisLokasi
    ): KemampuanPenyimpanan = KemampuanPenyimpanan(
        platform, jenisLokasi,
        semuaOperasi.associateWith { StatusDukungan.DIDUKUNG }
    )

    private fun kemampuan(
        platform: PlatformPenyimpanan,
        jenisLokasi: JenisLokasi,
        tidakDidukung: Map<OperasiBerkas, String>
    ): KemampuanPenyimpanan = KemampuanPenyimpanan(
        platform, jenisLokasi,
        semuaOperasi.associateWith { operasi ->
            tidakDidukung[operasi]?.let { StatusDukungan.tidakDidukung(it) }
                ?: StatusDukungan.DIDUKUNG
        }
    )
}
