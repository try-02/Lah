package org.fm.com.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.fm.com.data.DataBerkas
import org.fm.com.data.ImplementasiRepositoriBerkas
import org.fm.com.data.ImplementasiRepositoriOperasiBerkas
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.SumberDataPenyimpanan
import org.fm.com.data.SumberMemoriPenyimpanan
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi

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

    @Test
    fun pindahLintasLokasiGagalSaatSelesaiTidakMenghapusSumber() = runTest {
        val posix = SumberMemoriPenyimpanan()
        val safGagalSelesai = object : SumberDataPenyimpanan by SumberMemoriPenyimpanan() {
            override suspend fun bukaUntukTulisDi(
                induk: LokasiBerkas, nama: String, timpa: Boolean
            ): HasilOperasi<SumberTulisBerkas> {
                return HasilOperasi.Sukses(object : SumberTulisBerkas {
                    override suspend fun tulis(potongan: PotonganIsi) = HasilOperasi.Sukses(Unit)
                    override suspend fun selesai() = HasilOperasi.Gagal("Gagal verifikasi CRC", PenyebabGalat.TIDAK_DIKETAHUI)
                    override suspend fun batalkan() = HasilOperasi.Sukses(Unit)
                })
            }
        }
        val router = RepositoriRouterBerkas(mapOf(JenisLokasi.POSIX to posix, JenisLokasi.SAF to safGagalSelesai))
        val uc = SalinPindahkanBerkasUseCase(router, router)

        posix.berkas.simpan(DataBerkas("/asal", "asal", JenisLokasi.POSIX, adalahFolder = true))
        posix.berkas.simpan(
            DataBerkas("/asal/penting.txt", "penting.txt", JenisLokasi.POSIX, adalahFolder = false),
            "isi penting".encodeToByteArray()
        )
        val tujuanSaf = LokasiBerkas("content://tree/doc/tujuan", JenisLokasi.SAF, "tujuan")
        val sumber = LokasiBerkas("/asal/penting.txt", JenisLokasi.POSIX, "penting.txt")

        val hasil = uc(listOf(sumber), tujuanSaf, apakahPindah = true)
        assertTrue(hasil.adaGagal)
        assertEquals(0, hasil.berhasil.size)
        assertEquals("isi penting", posix.berkas.isiDari("/asal/penting.txt")?.decodeToString())
    }

    @Test
    fun pindahFolderFallbackStreamingSaatNativeTidakDidukung() = runTest {
        val memori = SumberMemoriPenyimpanan()
        val tanpaCopyMoveNative = object : SumberDataPenyimpanan {
            override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? = memori.kapabilitas(lokasi)
            override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = memori.daftarIsi(lokasi)
            override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> = memori.bukaUntukBaca(lokasi)
            override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean) = memori.bukaUntukTulis(lokasi, timpa)
            override suspend fun bukaUntukTulisDi(induk: LokasiBerkas, nama: String, timpa: Boolean) = memori.bukaUntukTulisDi(induk, nama, timpa)
            override suspend fun buatFolder(permintaan: PermintaanOperasi) = memori.buatFolder(permintaan)
            override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi) = memori.buatBerkasKosong(permintaan)
            override suspend fun gantiNama(permintaan: PermintaanOperasi) = memori.gantiNama(permintaan)
            override suspend fun hapus(permintaan: PermintaanOperasi) = memori.hapus(permintaan)
            override suspend fun salin(permintaan: PermintaanOperasi) =
                HasilOperasi.Gagal("Tidak didukung", PenyebabGalat.TIDAK_DIDUKUNG)
            override suspend fun pindah(permintaan: PermintaanOperasi) =
                HasilOperasi.Gagal("Tidak didukung", PenyebabGalat.TIDAK_DIDUKUNG)
        }
        memori.berkas.simpan(DataBerkas("/akar", "akar", JenisLokasi.POSIX, adalahFolder = true))
        memori.berkas.simpan(DataBerkas("/akar/proyek", "proyek", JenisLokasi.POSIX, adalahFolder = true))
        memori.berkas.simpan(DataBerkas("/akar/proyek/src", "src", JenisLokasi.POSIX, adalahFolder = true))
        memori.berkas.simpan(
            DataBerkas("/akar/proyek/src/Main.kt", "Main.kt", JenisLokasi.POSIX, adalahFolder = false),
            "fun main() {}".encodeToByteArray()
        )
        memori.berkas.simpan(DataBerkas("/akar/tujuan", "tujuan", JenisLokasi.POSIX, adalahFolder = true))

        val uc = SalinPindahkanBerkasUseCase(tanpaCopyMoveNative, tanpaCopyMoveNative)
        val hasil = uc(
            daftarSumber = listOf(LokasiBerkas("/akar/proyek", JenisLokasi.POSIX, "proyek")),
            tujuanInduk = LokasiBerkas("/akar/tujuan", JenisLokasi.POSIX, "tujuan"),
            apakahPindah = true
        )

        assertFalse(hasil.adaGagal)
        assertEquals(1, hasil.berhasil.size)
        assertNull(memori.berkas.data("/akar/proyek"))
        assertEquals("fun main() {}", memori.berkas.isiDari("/akar/tujuan/proyek/src/Main.kt")?.decodeToString())
    }
}