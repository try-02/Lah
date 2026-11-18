package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.presentation.filemanager.ModelKemampuanUi

class KemampuanDipakaiUseCaseTest {

    private val useCase = KemampuanDipakaiUseCase()

    @Test
    fun safMenonaktifkanPathPosixDanMenyertakanAlasan() {
        val kemampuanSaf = MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        assertNotNull(kemampuanSaf)

        val statusPosix = useCase(kemampuanSaf, OperasiBerkas.MELIHAT_PATH_POSIX)
        assertFalse(statusPosix.didukung)
        assertNotNull(statusPosix.alasan)

        val modelUi = ModelKemampuanUi.dari(kemampuanSaf)
        assertFalse(modelUi.dapatLihatPathPosix)
        assertTrue(modelUi.dapatMembuat)
        assertTrue(modelUi.dapatHapus)
    }

    @Test
    fun iosMenonaktifkanPathPosixDanSimbolikLink() {
        val kemampuanIos = MatriksKemampuan.untuk(PlatformPenyimpanan.IOS, JenisLokasi.IOS)
        assertNotNull(kemampuanIos)

        val statusPosix = useCase(kemampuanIos, OperasiBerkas.MELIHAT_PATH_POSIX)
        assertFalse(statusPosix.didukung)

        val modelUi = ModelKemampuanUi.dari(kemampuanIos)
        assertFalse(modelUi.dapatLihatPathPosix)
        assertTrue(modelUi.dapatSalin)
        assertTrue(modelUi.dapatPindah)
    }

    @Test
    fun kemampuanNullMengembalikanDukunganPenuhBawaan() {
        val status = useCase(null, OperasiBerkas.MEMBUAT)
        assertTrue(status.didukung)
        assertEquals(null, status.alasan)
    }
}