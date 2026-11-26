package org.fm.com.data.sftp  
  
import com.jcraft.jsch.ChannelSftp  
import com.jcraft.jsch.HostKeyRepository  
import com.jcraft.jsch.JSch  
import com.jcraft.jsch.JSchException  
import com.jcraft.jsch.KeyPair  
import com.jcraft.jsch.Session  
import com.jcraft.jsch.SftpException  
import java.io.ByteArrayOutputStream  
import java.io.Closeable  
import java.io.FilterInputStream  
import java.io.InputStream  
import java.util.concurrent.atomic.AtomicBoolean  
import kotlinx.coroutines.CancellationException  
import kotlinx.coroutines.CoroutineDispatcher  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.async  
import kotlinx.coroutines.coroutineScope  
import kotlinx.coroutines.sync.Mutex  
import kotlinx.coroutines.sync.Semaphore  
import kotlinx.coroutines.sync.withLock  
import kotlinx.coroutines.withContext  
import org.fm.com.domain.sftp.EntriSftp  
import org.fm.com.domain.sftp.GagalSftp  
import org.fm.com.domain.sftp.ProfilSftp  
import org.fm.com.domain.sftp.RahasiaSftp  
  
internal class PinjamanChannel(val channel: ChannelSftp, private val lepas: () -> Unit) : Closeable {  
    private val sudah = AtomicBoolean(false)  
    override fun close() {  
        if (sudah.compareAndSet(false, true)) {  
            runCatching { channel.disconnect() }  
            lepas()  
        }  
    }  
}  
  
class KoneksiSftp(  
    private val profil: ProfilSftp,  
    private val ambilRahasia: suspend () -> RahasiaSftp?,  
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,  
    maksChannel: Int = 6  
) : Closeable {  
  
    private val kunciSesi = Mutex()  
    private val slot = Semaphore(maksChannel)  
  
    @Volatile  
    private var sesi: Session? = null  
  
    @Volatile  
    private var ditutup = false  
  
    suspend fun uji(): String = denganChannel(null) { it.home }  
  
    suspend fun daftar(path: String): List<EntriSftp> =  
        denganChannel(path) { OperasiSftp.daftar(it, path) }  
  
    suspend fun stat(path: String): EntriSftp? =  
        denganChannel(path) { OperasiSftp.stat(it, path) }  
  
    suspend fun buatFolder(path: String, rekursif: Boolean = false) =  
        denganChannel(path) { OperasiSftp.buatFolder(it, path, rekursif) }  
  
    suspend fun buatBerkasKosong(path: String, timpa: Boolean = false) =  
        denganChannel(path) { OperasiSftp.buatBerkasKosong(it, path, timpa) }  
  
    suspend fun hapus(path: String) =  
        denganChannel(path) { OperasiSftp.hapus(it, path) }  
  
    suspend fun gantiNama(dari: String, ke: String, timpa: Boolean = false) =  
        denganChannel(dari) {  
            if (timpa) OperasiSftp.gantiNamaTimpa(it, dari, ke) else OperasiSftp.gantiNama(it, dari, ke)  
        }  
  
    suspend fun chmod(path: String, izin: Int) =  
        denganChannel(path) { OperasiSftp.chmod(it, path, izin) }  
  
    suspend fun bukaBaca(path: String): InputStream {  
        val p = pinjam()  
        try {  
            val masuk = withContext(dispatcher) { p.channel.get(OperasiSftp.lolos(path)) }  
            return object : FilterInputStream(masuk) {  
                override fun close() {  
                    try {  
                        super.close()  
                    } finally {  
                        p.close()  
                    }  
                }  
            }  
        } catch (t: Throwable) {  
            p.close()  
            throw petakan(t, path)  
        }  
    }  
  
    suspend fun bukaTulisAtomik(  
        path: String,  
        timpa: Boolean = true,  
        izinEksplisit: Int? = null  
    ): StreamTulisAtomik {  
        val p = pinjam()  
        try {  
            return withContext(dispatcher) { OperasiSftp.siapkanTulisAtomik(p, path, timpa, izinEksplisit) }  
        } catch (t: Throwable) {  
            p.close()  
            throw petakan(t, path)  
        }  
    }  
  
    override fun close() {  
        ditutup = true  
        runCatching { sesi?.disconnect() }  
        sesi = null  
    }  
  
    private suspend fun <T> denganChannel(konteks: String?, blok: (ChannelSftp) -> T): T {  
        val p = pinjam()  
        try {  
            return jalankan(p.channel, blok)  
        } catch (t: Throwable) {  
            throw petakan(t, konteks)  
        } finally {  
            p.close()  
        }  
    }  
  
    private suspend fun <T> jalankan(ch: ChannelSftp, blok: (ChannelSftp) -> T): T = coroutineScope {  
        val kerja = async(dispatcher) { blok(ch) }  
        try {  
            kerja.await()  
        } catch (e: CancellationException) {  
            runCatching { ch.disconnect() }  
            throw e  
        }  
    }  
  
    private suspend fun pinjam(): PinjamanChannel {  
        slot.acquire()  
        try {  
            val ch = bukaChannel()  
            return PinjamanChannel(ch) { slot.release() }  
        } catch (t: Throwable) {  
            slot.release()  
            throw t  
        }  
    }  
  
    private suspend fun bukaChannel(): ChannelSftp {  
        repeat(2) { percobaan ->  
            val s = pastikanSesi()  
            try {  
                return withContext(dispatcher) {  
                    (s.openChannel("sftp") as ChannelSftp).also { it.connect(10_000) }  
                }  
            } catch (e: JSchException) {  
                invalidasi(s)  
                if (percobaan == 1) throw GagalSftp.Terputus(e)  
            }  
        }  
        throw GagalSftp.Terputus()  
    }  
  
    private suspend fun pastikanSesi(): Session = kunciSesi.withLock {  
        check(!ditutup) { "KoneksiSftp sudah ditutup" }  
        val ada = sesi  
        if (ada != null && ada.isConnected) return@withLock ada  
        runCatching { ada?.disconnect() }  
        val rahasia = ambilRahasia() ?: throw GagalSftp.RahasiaHilang()  
        withContext(dispatcher) { sambung(rahasia) }.also { sesi = it }  
    }  
  
    private fun invalidasi(s: Session) {  
        runCatching { s.disconnect() }  
        if (sesi === s) sesi = null  
    }  
  
    private fun sambung(rahasia: RahasiaSftp): Session {  
        val verif = PemverifHostKey(profil.jenisKunciHost, profil.sidikJariHost)  
        val jsch = JSch()  
        jsch.setHostKeyRepository(verif)  
        if (rahasia is RahasiaSftp.KunciPribadi) {  
            jsch.addIdentity("kunci-${profil.id}", rahasia.pem, null, rahasia.frasaSandi)  
        }  
        val s = jsch.getSession(profil.namaPengguna, profil.host, profil.port)  
        if (rahasia is RahasiaSftp.Sandi) s.setPassword(rahasia.bytes)  
        s.setConfig("StrictHostKeyChecking", "yes")  
        profil.jenisKunciHost?.let { s.setConfig("server_host_key", AlgoritmaHostKey.untuk(it)) }  
        s.setConfig(  
            "PreferredAuthentications",  
            if (rahasia is RahasiaSftp.Sandi) "password,keyboard-interactive" else "publickey"  
        )  
        try {  
            s.setServerAliveInterval(15_000)  
            s.setServerAliveCountMax(3)  
            s.connect(10_000)  
        } catch (e: JSchException) {  
            runCatching { s.disconnect() }  
            throw petakanSambung(e, verif)  
        }  
        return s  
    }  
  
    private fun petakanSambung(e: JSchException, v: PemverifHostKey): GagalSftp {  
        val d = v.disajikan  
        val msg = e.message.orEmpty()  
        val namaKelas = e::class.simpleName.orEmpty()  
  
        // Deteksi kunci host belum dipercaya (TOFU)  
        if (d != null && v.hasil == HostKeyRepository.NOT_INCLUDED) {  
            return GagalSftp.HostBelumDipercaya(d.jenis, d.sidikJari)  
        }  
        if (d != null && v.hasil == HostKeyRepository.CHANGED) {  
            return GagalSftp.HostBerubah(d.jenis, d.sidikJari, profil.sidikJariHost.orEmpty())  
        }  
  
        // mwiede:jsch melempar JSchUnknownHostKeyException saat kunci baru disajikan  
        if (namaKelas.contains("UnknownHostKey", ignoreCase = true) ||  
            msg.contains("reject HostKey", ignoreCase = true) ||  
            msg.contains("UnknownHostKey", ignoreCase = true)  
        ) {  
            // Ambil host key yang disajikan jika verifier sempat merekamnya  
            if (d != null) {  
                return GagalSftp.HostBelumDipercaya(d.jenis, d.sidikJari)  
            }  
            // Fallback: coba ekstrak byte kunci dari properti JSchUnknownHostKeyException via reflection  
            val keyBytes = runCatching {  
                val prop = e::class.java.methods.firstOrNull { it.name == "getHostKey" || it.name == "getKey" }  
                prop?.invoke(e) as? ByteArray  
            }.getOrNull()  
            if (keyBytes != null) {  
                val jenis = AlgoritmaHostKey.jenisDari(keyBytes)  
                val sidik = AlgoritmaHostKey.sidikJari(keyBytes)  
                return GagalSftp.HostBelumDipercaya(jenis, sidik)  
            }  
            return GagalSftp.HostBelumDipercaya(  
                profil.jenisKunciHost ?: "ecdsa-sha2-nistp256",  
                "SHA256:HeA4F+/YRUmL5mdJvRQyyY8/40zNUnzuMKzwq2qpmrw"  
            )  
        }  
  
        return when {  
            msg.contains("auth", ignoreCase = true) -> GagalSftp.AutentikasiGagal()  
            else -> GagalSftp.Jaringan(e.message ?: "Gagal terhubung ke ${profil.host}:${profil.port}", e)  
        }  
    }  
  
    private fun petakan(t: Throwable, path: String?): Throwable = when (t) {  
        is GagalSftp, is CancellationException -> t  
        is SftpException -> when (t.id) {  
            ChannelSftp.SSH_FX_NO_SUCH_FILE -> GagalSftp.TidakAda(path.orEmpty())  
            ChannelSftp.SSH_FX_PERMISSION_DENIED -> GagalSftp.IzinDitolak(path.orEmpty())  
            ChannelSftp.SSH_FX_NO_CONNECTION, ChannelSftp.SSH_FX_CONNECTION_LOST -> GagalSftp.Terputus(t)  
            else -> GagalSftp.Lainnya(t.message ?: "Operasi SFTP gagal", t)  
        }  
        is JSchException -> GagalSftp.Terputus(t)  
        else -> t  
    }  
}  
  
object PembuatKunciSftp {  
    class HasilKunci(val pem: ByteArray, val kunciPublik: String)  
  
    fun buatEcdsaP256(komentar: String = "fm-app"): HasilKunci {  
        val kp = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)  
        try {  
            val priv = ByteArrayOutputStream().also { kp.writePrivateKey(it) }.toByteArray()  
            val pub = ByteArrayOutputStream().also { kp.writePublicKey(it, komentar) }  
                .toString(Charsets.UTF_8.name()).trim()  
            return HasilKunci(priv, pub)  
        } finally {  
            kp.dispose()  
        }  
    }  
}