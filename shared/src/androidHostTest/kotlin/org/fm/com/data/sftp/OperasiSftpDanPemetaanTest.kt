package org.fm.com.data.sftp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.jcraft.jsch.HostKeyRepository
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.fm.com.data.persistensi.DatabaseAplikasi
import org.fm.com.data.persistensi.buatDatabaseAplikasi
import org.fm.com.domain.sftp.JenisAutentikasi
import org.fm.com.domain.sftp.ProfilSftp

class OperasiSftpDanPemetaanTest {

    @Test
    fun escapeGlobJschMeloloskanKarakterKhususDenganBenar() {
        assertEquals(
            "/data/home/a\\*b/\\[x\\] y/c\\?d/e\\\\f",
            OperasiSftp.lolos("/data/home/a*b/[x] y/c?d/e\\f")
        )
        assertEquals("/home/normal/file.txt", OperasiSftp.lolos("/home/normal/file.txt"))
    }

    @Test
    fun pemverifHostKeyMendeteksiBelumDipercayaCocokDanBerubah() {
        val blob1 = buatBlobKunciSsh("ssh-ed25519", byteArrayOf(1, 2, 3, 4))
        val blob2 = buatBlobKunciSsh("ssh-ed25519", byteArrayOf(5, 6, 7, 8))

        val fp1 = AlgoritmaHostKey.sidikJari(blob1)
        assertEquals("ssh-ed25519", AlgoritmaHostKey.jenisDari(blob1))
        assertTrue(fp1.startsWith("SHA256:"))

        val verifBaru = PemverifHostKey(null, null)
        assertEquals(HostKeyRepository.NOT_INCLUDED, verifBaru.check("127.0.0.1", blob1))
        assertEquals(fp1, verifBaru.disajikan?.sidikJari)

        val verifCocok = PemverifHostKey("ssh-ed25519", fp1)
        assertEquals(HostKeyRepository.OK, verifCocok.check("127.0.0.1", blob1))

        val verifBerubah = PemverifHostKey("ssh-ed25519", fp1)
        assertEquals(HostKeyRepository.CHANGED, verifBerubah.check("127.0.0.1", blob2))
    }

    @Test
    fun sqlDelightRepositoriProfilSftpMenyimpanMenguraiDanMenghapusDiPreferensi() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DatabaseAplikasi.Schema.create(driver)
            val db = buatDatabaseAplikasi(driver)
            val repo = SqlDelightRepositoriProfilSftp(db)

            val profil = ProfilSftp(
                id = "termux-1",
                nama = "Termux Lokal",
                host = "127.0.0.1",
                port = 8022,
                namaPengguna = "u0_a123",
                pathAwal = "/data/data/com.termux/files/home",
                autentikasi = JenisAutentikasi.KUNCI_PRIBADI,
                jenisKunciHost = "ssh-ed25519",
                sidikJariHost = "SHA256:contohSidikJari"
            )
            repo.simpan(profil)

            val terbaca = assertNotNull(repo.ambil("termux-1"))
            assertEquals(profil, terbaca)
            assertEquals(1, repo.daftar().size)

            repo.hapus("termux-1")
            assertNull(repo.ambil("termux-1"))
            assertTrue(repo.daftar().isEmpty())
        } finally {
            driver.close()
        }
    }

    private fun buatBlobKunciSsh(jenis: String, isi: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            val jenisBytes = jenis.toByteArray(Charsets.US_ASCII)
            out.writeInt(jenisBytes.size)
            out.write(jenisBytes)
            out.writeInt(isi.size)
            out.write(isi)
        }
        return bos.toByteArray()
    }
}