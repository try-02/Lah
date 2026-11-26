package org.fm.com.data.sftp

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.sftp.GagalSftp
import org.fm.com.domain.sftp.HasilUjiKoneksiSftp
import org.fm.com.domain.sftp.LayananKoneksiSftp
import org.fm.com.domain.sftp.PengenalSftp
import org.fm.com.domain.sftp.PenyimpananRahasiaSftp
import org.fm.com.domain.sftp.ProfilSftp
import org.fm.com.domain.sftp.RahasiaSftp
import org.fm.com.domain.sftp.RepositoriProfilSftp

class PengelolaKoneksiSftp(
    private val repositoriProfil: RepositoriProfilSftp,
    private val penyimpananRahasia: PenyimpananRahasiaSftp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LayananKoneksiSftp {

    private val mutexPool = Mutex()
    private val poolKoneksi = HashMap<String, KoneksiSftp>()

    suspend fun ambilAtauBuatKoneksi(idProfil: String): HasilOperasi<KoneksiSftp> {
        val ada = mutexPool.withLock { poolKoneksi[idProfil] }
        if (ada != null) return HasilOperasi.Sukses(ada)

        val profil = repositoriProfil.ambil(idProfil)
            ?: return HasilOperasi.Gagal("Profil SFTP '$idProfil' tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)

        return mutexPool.withLock {
            val cekUlang = poolKoneksi[idProfil]
            if (cekUlang != null) {
                HasilOperasi.Sukses(cekUlang)
            } else {
                val koneksiBaru = KoneksiSftp(
                    profil = profil,
                    ambilRahasia = { penyimpananRahasia.ambil(idProfil) },
                    dispatcher = ioDispatcher
                )
                poolKoneksi[idProfil] = koneksiBaru
                HasilOperasi.Sukses(koneksiBaru)
            }
        }
    }

    suspend fun tutupKoneksi(idProfil: String) {
        val lama = mutexPool.withLock { poolKoneksi.remove(idProfil) }
        lama?.close()
    }

    suspend fun tutupSemua() {
        val semua = mutexPool.withLock {
            poolKoneksi.values.toList().also { poolKoneksi.clear() }
        }
        for (k in semua) k.close()
    }

    override fun amatiDaftarProfil(): Flow<List<ProfilSftp>> =
        repositoriProfil.amatiDaftar()

    override suspend fun daftarProfil(): List<ProfilSftp> =
        repositoriProfil.daftar()

    override suspend fun ambilProfil(id: String): ProfilSftp? =
        repositoriProfil.ambil(id)

    override suspend fun apakahAdaRahasia(idProfil: String): Boolean =
        penyimpananRahasia.ada(idProfil)

    override suspend fun simpanProfilDanRahasia(
        profil: ProfilSftp,
        rahasiaBaru: RahasiaSftp?
    ): HasilOperasi<ProfilSftp> = try {
        tutupKoneksi(profil.id)
        repositoriProfil.simpan(profil)
        if (rahasiaBaru != null) {
            penyimpananRahasia.simpan(profil.id, rahasiaBaru)
        }
        HasilOperasi.Sukses(profil)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HasilOperasi.Gagal(e.message ?: "Gagal menyimpan profil SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
    }

    override suspend fun hapusProfil(id: String): HasilOperasi<Unit> = try {
        tutupKoneksi(id)
        penyimpananRahasia.hapus(id)
        repositoriProfil.hapus(id)
        HasilOperasi.Sukses(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HasilOperasi.Gagal(e.message ?: "Gagal menghapus profil SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
    }

    override suspend fun ujiKoneksi(
        profil: ProfilSftp,
        rahasiaSementara: RahasiaSftp?
    ): HasilUjiKoneksiSftp {
        val koneksiUji = KoneksiSftp(
            profil = profil,
            ambilRahasia = { rahasiaSementara ?: penyimpananRahasia.ambil(profil.id) },
            dispatcher = ioDispatcher,
            maksChannel = 2
        )
        return try {
            val home = koneksiUji.uji()
            HasilUjiKoneksiSftp.Sukses(home)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GagalSftp.HostBelumDipercaya) {
            HasilUjiKoneksiSftp.HostBelumDipercaya(e.jenisKunci, e.sidikJari)
        } catch (e: GagalSftp.HostBerubah) {
            HasilUjiKoneksiSftp.HostBerubah(e.jenisKunci, e.sidikJariBaru, e.sidikJariLama)
        } catch (e: GagalSftp) {
            val (pesan, penyebab) = petakanPesanRamah(e, profil)
            HasilUjiKoneksiSftp.Gagal(pesan, penyebab)
        } catch (e: Exception) {
            HasilUjiKoneksiSftp.Gagal(e.message ?: "Gagal menguji koneksi SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            koneksiUji.close()
        }
    }

    override suspend fun buatKunciEcdsaP256(komentar: String): HasilOperasi<Pair<ByteArray, String>> = try {
        val hasil = PembuatKunciSftp.buatEcdsaP256(komentar)
        HasilOperasi.Sukses(hasil.pem to hasil.kunciPublik)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HasilOperasi.Gagal(e.message ?: "Gagal membuat kunci ECDSA", PenyebabGalat.TIDAK_DIKETAHUI)
    }

    override fun keLokasiBerkas(profil: ProfilSftp): LokasiBerkas = LokasiBerkas(
        pengenal = PengenalSftp.buat(profil.id, profil.pathAwal),
        jenis = JenisLokasi.SFTP,
        label = profil.nama
    )

    companion object {
        internal fun petakanPesanRamah(e: GagalSftp, profil: ProfilSftp? = null): Pair<String, PenyebabGalat> = when (e) {
            is GagalSftp.TidakAda -> (e.message ?: "Tidak ditemukan") to PenyebabGalat.TIDAK_DITEMUKAN
            is GagalSftp.SudahAda -> (e.message ?: "Target sudah ada") to PenyebabGalat.KONFLIK
            is GagalSftp.IzinDitolak,
            is GagalSftp.AutentikasiGagal,
            is GagalSftp.RahasiaHilang,
            is GagalSftp.HostBelumDipercaya,
            is GagalSftp.HostBerubah -> (e.message ?: "Izin ditolak") to PenyebabGalat.IZIN_DITOLAK
            is GagalSftp.AdalahFolder,
            is GagalSftp.TautanBerputar -> (e.message ?: "Input tidak valid") to PenyebabGalat.VALIDASI
            is GagalSftp.Jaringan -> {
                val sebab = e.cause
                val host = profil?.host
                val port = profil?.port
                val pesan = e.message.orEmpty()
                val tambahan = when {
                    host != null && port != null && (host == "127.0.0.1" || host == "localhost") &&
                        (sebab is ConnectException || pesan.contains("refused", ignoreCase = true)) ->
                        "Koneksi ke $host:$port ditolak. Pastikan `sshd` berjalan di Termux."
                    host != null && sebab is UnknownHostException ->
                        "Host '$host' tidak ditemukan."
                    sebab is SocketTimeoutException || sebab?.cause is SocketTimeoutException ||
                        pesan.contains("timed out", ignoreCase = true) ->
                        "Server SSH di ${host ?: "host tujuan"}:${port ?: "?"} tidak merespons. " +
                            "Periksa `sshd` di Termux, nomor port, dan firewall/jaringan."
                    else -> e.message ?: "Kegagalan jaringan SFTP"
                }
                tambahan to PenyebabGalat.TIDAK_DIKETAHUI
            }
            is GagalSftp.Terputus,
            is GagalSftp.Lainnya -> (e.message ?: "Kesalahan SFTP") to PenyebabGalat.TIDAK_DIKETAHUI
        }
    }
}