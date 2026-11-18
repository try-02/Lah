package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas

class SalinPindahkanBerkasUseCaseTest {

    private val repo = ImplementasiRepositoriBerkas()
    private val op = ImplementasiRepositoriOperasiBerkas(repo)
    private val useCase = SalinPindahkanBerkasUseCase(repo, op)

    @Test
    fun salinMassalNativeBerhasil() = runTest {
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/target", "target", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/1.txt", "1.txt", JenisLokasi.POSIX, adalahFolder = false), "halo 1".encodeToByteArray())
        repo.simpan(DataBerkas("/root/2.txt", "2.txt", JenisLokasi.POSIX, adalahFolder = false), "halo 2".encodeToByteArray())

        val sumber = listOf(
            LokasiBerkas("/root/1.txt", JenisLokasi.POSIX, "1.txt"),
            LokasiBerkas("/root/2.txt", JenisLokasi.POSIX, "2.txt")
        )
        val tujuan = LokasiBerkas("/root/target", JenisLokasi.POSIX, "target")

        val hasil = useCase(sumber, tujuan, apakahPindah = false)
        assertFalse(hasil.adaGagal)
        assertEquals(2, hasil.berhasil.size)

        // Verifikasi berkas asli dan salinan tetap ada
        assertNotNull(repo.data("/root/1.txt"))
        assertNotNull(repo.data("/root/target/1.txt"))
        assertEquals("halo 1", repo.isiDari("/root/target/1.txt")?.decodeToString())
    }

    @Test
    fun pindahMassalMenghapusSumber() = runTest {
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/target", "target", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/pindah.txt", "pindah.txt", JenisLokasi.POSIX, adalahFolder = false), "pindah me".encodeToByteArray())

        val sumber = listOf(LokasiBerkas("/root/pindah.txt", JenisLokasi.POSIX, "pindah.txt"))
        val tujuan = LokasiBerkas("/root/target", JenisLokasi.POSIX, "target")

        val hasil = useCase(sumber, tujuan, apakahPindah = true)
        assertFalse(hasil.adaGagal)
        assertEquals(1, hasil.berhasil.size)

        assertNull(repo.data("/root/pindah.txt"))
        assertNotNull(repo.data("/root/target/pindah.txt"))
        assertEquals("pindah me", repo.isiDari("/root/target/pindah.txt")?.decodeToString())
    }
}