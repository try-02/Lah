package org.fm.com.domain.sftp

import kotlinx.coroutines.flow.Flow
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat

enum class JenisAutentikasi { SANDI, KUNCI_PRIBADI }

/** Data NON-rahasia. Aman disimpan di DB biasa (PreferensiEntity). */
data class ProfilSftp(
    val id: String,
    val nama: String,
    val host: String = "127.0.0.1",
    val port: Int = 8022,
    val namaPengguna: String,
    val pathAwal: String = "/data/data/com.termux/files/home",
    val autentikasi: JenisAutentikasi = JenisAutentikasi.SANDI,
    val jenisKunciHost: String? = null,
    val sidikJariHost: String? = null
)

/** Rahasia. TIDAK PERNAH masuk LokasiBerkas, log, atau toString. */
sealed interface RahasiaSftp {
    class Sandi(val bytes: ByteArray) : RahasiaSftp {
        override fun toString() = "Sandi(***)"
    }

    class KunciPribadi(val pem: ByteArray, val frasaSandi: ByteArray? = null) : RahasiaSftp {
        override fun toString() = "KunciPribadi(***)"
    }
}

data class EntriSftp(
    val nama: String,
    val path: String,
    val adalahFolder: Boolean,
    val adalahTautan: Boolean,
    val ukuran: Long,
    val diubahMs: Long,
    val izin: Int
)

interface RepositoriProfilSftp {
    fun amatiDaftar(): Flow<List<ProfilSftp>>
    suspend fun daftar(): List<ProfilSftp>
    suspend fun ambil(id: String): ProfilSftp?
    suspend fun simpan(profil: ProfilSftp)
    suspend fun hapus(id: String)
}

interface PenyimpananRahasiaSftp {
    suspend fun simpan(idProfil: String, rahasia: RahasiaSftp)
    suspend fun ambil(idProfil: String): RahasiaSftp?
    suspend fun hapus(idProfil: String)
    suspend fun ada(idProfil: String): Boolean
}

sealed interface HasilUjiKoneksiSftp {
    data class Sukses(val pathHome: String) : HasilUjiKoneksiSftp
    data class HostBelumDipercaya(val jenisKunci: String, val sidikJari: String) : HasilUjiKoneksiSftp
    data class HostBerubah(
        val jenisKunci: String,
        val sidikJariBaru: String,
        val sidikJariLama: String
    ) : HasilUjiKoneksiSftp

    data class Gagal(val pesan: String, val penyebab: PenyebabGalat) : HasilUjiKoneksiSftp
}

interface LayananKoneksiSftp {
    fun amatiDaftarProfil(): Flow<List<ProfilSftp>>
    suspend fun daftarProfil(): List<ProfilSftp>
    suspend fun ambilProfil(id: String): ProfilSftp?
    suspend fun apakahAdaRahasia(idProfil: String): Boolean
    suspend fun simpanProfilDanRahasia(
        profil: ProfilSftp,
        rahasiaBaru: RahasiaSftp?
    ): HasilOperasi<ProfilSftp>

    suspend fun hapusProfil(id: String): HasilOperasi<Unit>
    suspend fun ujiKoneksi(
        profil: ProfilSftp,
        rahasiaSementara: RahasiaSftp? = null
    ): HasilUjiKoneksiSftp

    suspend fun buatKunciEcdsaP256(komentar: String = "fm-app"): HasilOperasi<Pair<ByteArray, String>>
    fun keLokasiBerkas(profil: ProfilSftp): LokasiBerkas
}

sealed class GagalSftp(pesan: String, sebab: Throwable? = null) : Exception(pesan, sebab) {
    class RahasiaHilang : GagalSftp("Kredensial SFTP tidak ditemukan atau tidak bisa dibuka. Masukkan ulang.")

    class HostBelumDipercaya(val jenisKunci: String, val sidikJari: String) :
        GagalSftp("Host belum dipercaya ($jenisKunci $sidikJari). Buka Pengaturan SFTP untuk mempercayai kunci host.")

    class HostBerubah(val jenisKunci: String, val sidikJariBaru: String, val sidikJariLama: String) :
        GagalSftp("PERINGATAN: Kunci host SFTP BERUBAH ($sidikJariBaru). Koneksi diblokir demi keamanan.")

    class AutentikasiGagal : GagalSftp("Autentikasi SFTP gagal (periksa pengguna, sandi, atau kunci).")
    class Jaringan(pesan: String, sebab: Throwable? = null) : GagalSftp(pesan, sebab)
    class Terputus(sebab: Throwable? = null) : GagalSftp("Koneksi SFTP terputus.", sebab)
    class TidakAda(val path: String) : GagalSftp("Tidak ditemukan: $path")
    class SudahAda(val path: String) : GagalSftp("Sudah ada: $path")
    class IzinDitolak(val path: String) : GagalSftp("Izin ditolak: $path")
    class AdalahFolder(val path: String) : GagalSftp("Targetnya folder: $path")
    class TautanBerputar(val path: String) : GagalSftp("Rantai symlink terlalu panjang atau berputar: $path")
    class Lainnya(pesan: String, sebab: Throwable? = null) : GagalSftp(pesan, sebab)
}

data class LokasiSftp(val idProfil: String, val path: String)

object PengenalSftp {
    const val SKEMA = "sftp://"
    private val POLA_ID = Regex("[A-Za-z0-9_-]{1,64}")

    fun apakahIdValid(id: String): Boolean = POLA_ID.matches(id)

    fun buat(idProfil: String, path: String): String {
        require(POLA_ID.matches(idProfil)) { "idProfil tidak valid" }
        return SKEMA + idProfil + normalisasi(path)
    }

    fun urai(pengenal: String): LokasiSftp? {
        if (!pengenal.startsWith(SKEMA)) return null
        val sisa = pengenal.removePrefix(SKEMA)
        val garis = sisa.indexOf('/')
        val id = if (garis < 0) sisa else sisa.substring(0, garis)
        if (!POLA_ID.matches(id)) return null
        val path = if (garis < 0) "/" else sisa.substring(garis)
        if ('\u0000' in path) return null
        return LokasiSftp(id, normalisasi(path))
    }

    fun normalisasi(path: String): String {
        require('\u0000' !in path) { "Path mengandung NUL" }
        val bagian = ArrayDeque<String>()
        for (b in path.split('/')) {
            when (b) {
                "", "." -> Unit
                ".." -> if (bagian.isNotEmpty()) bagian.removeLast()
                else -> bagian.addLast(b)
            }
        }
        return bagian.joinToString(separator = "/", prefix = "/")
    }

    fun induk(path: String): String =
        normalisasi(path).substringBeforeLast('/').ifEmpty { "/" }

    fun nama(path: String): String = normalisasi(path).substringAfterLast('/')

    fun gabung(induk: String, nama: String): String {
        require(nama.isNotEmpty() && nama != "." && nama != ".." && '/' !in nama && '\u0000' !in nama) {
            "Nama berkas tidak valid"
        }
        return normalisasi("$induk/$nama")
    }
}