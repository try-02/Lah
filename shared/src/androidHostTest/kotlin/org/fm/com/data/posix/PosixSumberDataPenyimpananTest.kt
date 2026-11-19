package org.fm.com.data.posix

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.repository.PermintaanOperasi

class PosixSumberDataPenyimpananTest {
    private val fs = FileSystem.SYSTEM
    private val sumber = PosixSumberDataPenyimpanan(fs, PlatformPenyimpanan.ANDROID)

    @Test
    fun crudDanStreamingPosixBerjalanNyata() = runTest {
        val rootPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fm_posix_uji_${Random.nextLong().toString(16)}"
        fs.createDirectory(rootPath, mustCreate = true)
        try {
            val rootLok = LokasiBerkas(rootPath.toString(), JenisLokasi.POSIX, "Uji")

            val sub = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                sumber.buatFolder(PermintaanOperasi(sumber = rootLok, namaBaru = "Proyek"))
            ).nilai

            val tulis = assertIs<HasilOperasi.Sukses<SumberTulisBerkas>>(
                sumber.bukaUntukTulisDi(sub.lokasi, "catatan.txt", timpa = false)
            ).nilai
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.tulis(PotonganIsi("Halo ".encodeToByteArray())))
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.tulis(PotonganIsi("Okio!".encodeToByteArray())))
            assertIs<HasilOperasi.Sukses<Unit>>(tulis.selesai())

            val lokCatatan = assertNotNull(tulis.lokasiTarget)
            val baca = assertIs<HasilOperasi.Sukses<SumberIsiBerkas>>(sumber.bukaUntukBaca(lokCatatan)).nilai
            var hasilByte = ByteArray(0)
            while (true) {
                val p = assertIs<SumberIsiBerkas.HasilPotongan.Isi>(baca.potongan())
                hasilByte += p.potongan.data
                if (p.potongan.selesai) break
            }
            baca.tutup()
            assertEquals("Halo Okio!", hasilByte.decodeToString())

            val konflik = assertIs<HasilOperasi.Gagal>(
                sumber.bukaUntukTulisDi(sub.lokasi, "catatan.txt", timpa = false)
            )
            assertEquals(PenyebabGalat.KONFLIK, konflik.penyebab)

            val rename = assertIs<HasilOperasi.Sukses<EntitasBerkas>>(
                sumber.gantiNama(PermintaanOperasi(sumber = lokCatatan, namaBaru = "baru.txt"))
            ).nilai
            assertEquals("baru.txt", rename.nama)

            val daftar = assertIs<HasilOperasi.Sukses<List<EntitasBerkas>>>(
                sumber.daftarIsi(sub.lokasi).first()
            ).nilai
            assertEquals(listOf("baru.txt"), daftar.map { it.nama })

            val pindahDirKeDiri = assertIs<HasilOperasi.Gagal>(
                sumber.pindah(PermintaanOperasi(sumber = sub.lokasi, tujuan = sub.lokasi))
            )
            assertEquals(PenyebabGalat.VALIDASI, pindahDirKeDiri.penyebab)

            assertIs<HasilOperasi.Sukses<Unit>>(
                sumber.hapus(PermintaanOperasi(sumber = sub.lokasi, konfirmasi = true))
            )
            assertNull(fs.metadataOrNull(rootPath / "Proyek"))
            assertTrue(fs.exists(rootPath))
        } finally {
            runCatching { fs.deleteRecursively(rootPath, mustExist = false) }
        }
    }
}