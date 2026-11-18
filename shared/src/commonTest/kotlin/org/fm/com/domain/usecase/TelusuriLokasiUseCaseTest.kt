package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class TelusuriLokasiUseCaseTest {

    private val useCase = TelusuriLokasiUseCase()

    @Test
    fun segmenPosixDiuraiDenganTepat() {
        val lokasi = LokasiBerkas("/sdcard/Dokumen/Kerja", JenisLokasi.POSIX)
        val segmen = useCase.buatSegmenBreadcrumb(lokasi)

        assertEquals(4, segmen.size)
        assertEquals("Root", segmen[0].namaTampilan)
        assertEquals("/", segmen[0].lokasi.pengenal)
        assertEquals("sdcard", segmen[1].namaTampilan)
        assertEquals("/sdcard", segmen[1].lokasi.pengenal)
        assertEquals("Dokumen", segmen[2].namaTampilan)
        assertEquals("/sdcard/Dokumen", segmen[2].lokasi.pengenal)
        assertEquals("Kerja", segmen[3].namaTampilan)
        assertEquals("/sdcard/Dokumen/Kerja", segmen[3].lokasi.pengenal)
    }

    @Test
    fun segmenMempertahankanRiwayatHierarki() {
        val root = LokasiBerkas("content://tree/primary", JenisLokasi.SAF, "Dokumen Utama")
        val sub = LokasiBerkas("content://tree/primary/sub", JenisLokasi.SAF, "Subfolder")
        val segmen = useCase.buatSegmenBreadcrumb(sub, listOf(root, sub))

        assertEquals(2, segmen.size)
        assertEquals("Dokumen Utama", segmen[0].namaTampilan)
        assertEquals("Subfolder", segmen[1].namaTampilan)
    }
}