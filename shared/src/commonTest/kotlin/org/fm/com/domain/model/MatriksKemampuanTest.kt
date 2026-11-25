package org.fm.com.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MatriksKemampuanTest {

    @Test
    fun `setiap kombinasi terdaftar memiliki status lengkap`() {
        val platform = PlatformPenyimpanan.entries
        val lokasi = JenisLokasi.entries
        for (p in platform) {
            for (l in lokasi) {
                val kemampuan = MatriksKemampuan.untuk(p, l) ?: continue
                assertEquals(
                    OperasiBerkas.entries.size,
                    kemampuan.operasiDidukung().size +
                        OperasiBerkas.entries.count { !kemampuan.apakahDidukung(it) }
                )
            }
        }
    }

    @Test
    fun `posix android mendukung semua operasi`() {
        val kemampuan = assertNotNull(
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.POSIX)
        )
        OperasiBerkas.entries.forEach { operasi ->
            assertEquals(true, kemampuan.apakahDidukung(operasi), "$operasi seharusnya didukung")
        }
    }

    @Test
    fun `saf dan ios menonaktifkan path posix beserta alasannya`() {
        val saf = assertNotNull(
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        )
        assertEquals(false, saf.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertEquals(false, saf.apakahDidukung(OperasiBerkas.MEMBUAT_TAUTAN_SIMBOLIK))
        assertNotNull(saf.alasan(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertEquals(true, saf.apakahDidukung(OperasiBerkas.MEMBACA))

        val ios = assertNotNull(
            MatriksKemampuan.untuk(PlatformPenyimpanan.IOS, JenisLokasi.IOS)
        )
        assertEquals(false, ios.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertNotNull(ios.alasan(OperasiBerkas.MEMBAGIKAN))
    }

    @Test
    fun `kombinasi tak terdaftar menghasilkan null`() {
        assertNull(
            MatriksKemampuan.untuk(PlatformPenyimpanan.IOS, JenisLokasi.SAF)
        )
    }

    @Test
    fun `status tak didukung wajib punya alasan`() {
        assertFailsWith<IllegalArgumentException> {
            StatusDukungan(false)
        }
    }

    @Test
    fun `sftp android mendukung path posix tanpa share eksternal dan ios null`() {
        val sftpAndroid = assertNotNull(
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SFTP)
        )
        assertEquals(true, sftpAndroid.apakahDidukung(OperasiBerkas.MELIHAT_PATH_POSIX))
        assertEquals(true, sftpAndroid.apakahDidukung(OperasiBerkas.MENGGANTI_NAMA))
        assertEquals(false, sftpAndroid.apakahDidukung(OperasiBerkas.MEMBAGIKAN))
        assertNotNull(sftpAndroid.alasan(OperasiBerkas.MEMBAGIKAN))
        assertNull(MatriksKemampuan.untuk(PlatformPenyimpanan.IOS, JenisLokasi.SFTP))
    }
}
