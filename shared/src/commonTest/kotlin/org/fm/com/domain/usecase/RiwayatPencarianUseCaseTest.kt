package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.fm.com.data.persistensi.MemoriRepositoriRiwayatPencarian
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat

class RiwayatPencarianUseCaseTest {

    @Test
    fun catatUrutkanPerbaruiDanHapusRiwayatPencarian() = runTest {
        val repo = MemoriRepositoriRiwayatPencarian()
        val uc = RiwayatPencarianUseCase(repo)

        val gagalKosong = assertIs<HasilOperasi.Gagal>(uc.catat(""))
        assertEquals(PenyebabGalat.VALIDASI, gagalKosong.penyebab)

        assertIs<HasilOperasi.Sukses<Unit>>(
            uc.catat("alpha", pekaHuruf = true, waktuEpochMs = 1000L)
        )
        assertIs<HasilOperasi.Sukses<Unit>>(
            uc.catat("beta.*", gunakanRegex = true, waktuEpochMs = 2000L)
        )
        assertIs<HasilOperasi.Sukses<Unit>>(
            uc.catat("alpha", pekaHuruf = false, kataUtuh = true, waktuEpochMs = 3000L)
        )

        val daftar = uc.ambilRiwayat(batas = 10).first()
        assertEquals(2, daftar.size)
        assertEquals("alpha", daftar[0].kueri)
        assertEquals(true, daftar[0].kataUtuh)
        assertEquals(false, daftar[0].pekaHuruf)
        assertEquals("beta.*", daftar[1].kueri)
        assertEquals(true, daftar[1].gunakanRegex)

        assertIs<HasilOperasi.Sukses<Unit>>(uc.hapusItem("alpha"))
        val setelahHapus = uc.ambilRiwayat().first()
        assertEquals(listOf("beta.*"), setelahHapus.map { it.kueri })

        assertIs<HasilOperasi.Sukses<Unit>>(uc.bersihkanSemua())
        assertEquals(emptyList(), uc.ambilRiwayat().first())
    }
}