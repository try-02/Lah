package org.fm.com.data.sftp

import android.util.Log
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
import java.io.IOException
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

internal class PinjamanChannel(
    val channel: ChannelSftp,
    private val onTutup: (ChannelSftp, Boolean) -> Unit
) : Closeable {
    constructor(channel: ChannelSftp, lepas: () -> Unit) : this(
        channel,
        { ch, _ ->
            runCatching { ch.disconnect() }
            lepas()
        }
    )

    private val sudah = AtomicBoolean(false)

    @Volatile
    private var sehat = true

    fun tandaiRusak() {
        sehat = false
    }

    override fun close() {
        if (sudah.compareAndSet(false, true)) {
            onTutup(channel, sehat)
        }
    }
}

class KoneksiSftp(
    private val profil: ProfilSftp,
    private val ambilRahasia: suspend () -> RahasiaSftp?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    maksChannel: Int = 6
) : Closeable {

    private val batasChannel = maksChannel.coerceAtLeast(1)
    private val maksIdlePool = minOf(batasChannel, 3)

    private val kunciSesi = Mutex()
    private val slot = Semaphore(batasChannel)
    private val kunciPool = Any()
    private val poolChannel = ArrayDeque<ChannelSftp>()

    @Volatile
    private var sesi: Session? = null

    @Volatile
    private var ditutup = false

    suspend fun uji(): String = denganChannel("uji", null) { it.home }

    suspend fun daftar(path: String): List<EntriSftp> =
        denganChannel("daftar", path) { OperasiSftp.daftar(it, path) }

    suspend fun stat(path: String): EntriSftp? =
        denganChannel("stat", path) { OperasiSftp.stat(it, path) }

    suspend fun buatFolder(path: String, rekursif: Boolean = false) =
        denganChannel("buatFolder", path) { OperasiSftp.buatFolder(it, path, rekursif) }

    suspend fun buatBerkasKosong(path: String, timpa: Boolean = false) =
        denganChannel("buatBerkasKosong", path) { OperasiSftp.buatBerkasKosong(it, path, timpa) }

    suspend fun hapus(path: String) =
        denganChannel("hapus", path) { OperasiSftp.hapus(it, path) }

    suspend fun gantiNama(dari: String, ke: String, timpa: Boolean = false) =
        denganChannel("gantiNama", dari) {
            if (timpa) OperasiSftp.gantiNamaTimpa(it, dari, ke) else OperasiSftp.gantiNama(it, dari, ke)
        }

    suspend fun chmod(path: String, izin: Int) =
        denganChannel("chmod", path) { OperasiSftp.chmod(it, path, izin) }

    suspend fun bukaBaca(path: String): InputStream {
        val tMulai = System.nanoTime()
        var paksaBaru = false
        var percobaan = 0
        while (true) {
            val (p, dariPool) = pinjamDenganInfo(paksaBaru)
            try {
                val masuk = withContext(dispatcher) { p.channel.get(OperasiSftp.lolos(path)) }
                catatPerf("bukaBaca", tMulai, "dariPool=$dariPool")
                return object : FilterInputStream(masuk) {
                    private val sudahTutup = AtomicBoolean(false)

                    @Volatile
                    private var sudahEof = false

                    override fun read(): Int {
                        try {
                            val b = super.read()
                            if (b == -1) sudahEof = true
                            return b
                        } catch (e: IOException) {
                            p.tandaiRusak()
                            throw e
                        }
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        try {
                            val n = super.read(b, off, len)
                            if (n == -1) sudahEof = true
                            return n
                        } catch (e: IOException) {
                            p.tandaiRusak()
                            throw e
                        }
                    }

                    override fun close() {
                        if (!sudahTutup.compareAndSet(false, true)) return
                        if (!sudahEof) {
                            // Jika stream ditutup sebelum EOF, jangan pakai ulang channel agar sisa paket tidak mengotori stream berikutnya.
                            p.tandaiRusak()
                        }
                        try {
                            super.close()
                        } catch (e: Throwable) {
                            p.tandaiRusak()
                            throw e
                        } finally {
                            p.close()
                        }
                    }
                }
            } catch (e: CancellationException) {
                p.tandaiRusak()
                p.close()
                throw e
            } catch (t: Throwable) {
                val putus = apakahKoneksiPutus(t)
                if (!apakahChannelTetapSehat(t)) {
                    p.tandaiRusak()
                }
                if (putus) {
                    runCatching { p.channel.session }.getOrNull()?.let { invalidasi(it) }
                }
                p.close()
                if (dariPool && putus && percobaan == 0) {
                    percobaan++
                    paksaBaru = true
                    continue
                }
                catatPerf("bukaBaca_gagal", tMulai, "err=${t::class.simpleName}")
                throw petakan(t, path)
            }
        }
    }

    suspend fun bukaTulisAtomik(
        path: String,
        timpa: Boolean = true,
        izinEksplisit: Int? = null
    ): StreamTulisAtomik {
        val tMulai = System.nanoTime()
        var paksaBaru = false
        var percobaan = 0
        while (true) {
            val (p, dariPool) = pinjamDenganInfo(paksaBaru)
            try {
                return withContext(dispatcher) {
                    OperasiSftp.siapkanTulisAtomik(p, path, timpa, izinEksplisit)
                }.also {
                    catatPerf("bukaTulisAtomik", tMulai, "dariPool=$dariPool")
                }
            } catch (e: CancellationException) {
                p.tandaiRusak()
                p.close()
                throw e
            } catch (t: Throwable) {
                val putus = apakahKoneksiPutus(t)
                if (!apakahChannelTetapSehat(t)) {
                    p.tandaiRusak()
                }
                if (putus) {
                    runCatching { p.channel.session }.getOrNull()?.let { invalidasi(it) }
                }
                p.close()
                if (dariPool && putus && percobaan == 0) {
                    percobaan++
                    paksaBaru = true
                    continue
                }
                catatPerf("bukaTulisAtomik_gagal", tMulai, "err=${t::class.simpleName}")
                throw petakan(t, path)
            }
        }
    }

    override fun close() {
        ditutup = true
        val sisaPool = synchronized(kunciPool) {
            poolChannel.toList().also { poolChannel.clear() }
        }
        for (ch in sisaPool) {
            runCatching { ch.disconnect() }
        }
        runCatching { sesi?.disconnect() }
        sesi = null
    }

    private suspend fun <T> denganChannel(
        namaAksi: String,
        konteks: String?,
        blok: (ChannelSftp) -> T
    ): T {
        val tMulai = System.nanoTime()
        var paksaBaru = false
        var percobaan = 0
        while (true) {
            val (p, dariPool) = pinjamDenganInfo(paksaBaru)
            try {
                return jalankan(p.channel, blok).also {
                    catatPerf(namaAksi, tMulai, "dariPool=$dariPool")
                }
            } catch (e: CancellationException) {
                p.tandaiRusak()
                throw e
            } catch (t: Throwable) {
                val putus = apakahKoneksiPutus(t)
                if (!apakahChannelTetapSehat(t)) {
                    p.tandaiRusak()
                }
                if (putus) {
                    runCatching { p.channel.session }.getOrNull()?.let { invalidasi(it) }
                }
                if (dariPool && putus && percobaan == 0) {
                    percobaan++
                    paksaBaru = true
                    continue
                }
                catatPerf("${namaAksi}_gagal", tMulai, "dariPool=$dariPool err=${t::class.simpleName}")
                throw petakan(t, konteks)
            } finally {
                p.close()
            }
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

    private suspend fun pinjamDenganInfo(paksaBaru: Boolean = false): Pair<PinjamanChannel, Boolean> {
        slot.acquire()
        try {
            val dariPool = if (paksaBaru) null else ambilDariPool()
            if (dariPool != null) {
                return PinjamanChannel(dariPool, ::kembalikanAtauPutus) to true
            }
            val baru = bukaChannelBaru()
            return PinjamanChannel(baru, ::kembalikanAtauPutus) to false
        } catch (t: Throwable) {
            slot.release()
            throw t
        }
    }

    private fun ambilDariPool(): ChannelSftp? {
        val sesiAktif = sesi ?: return null
        if (ditutup || !sesiAktif.isConnected) return null

        val buangan = mutableListOf<ChannelSftp>()
        var terpilih: ChannelSftp? = null

        synchronized(kunciPool) {
            while (poolChannel.isNotEmpty()) {
                val kandidat = poolChannel.removeLast()
                val sesiKandidat = runCatching { kandidat.session }.getOrNull()
                if (!ditutup &&
                    sesiKandidat === sesiAktif &&
                    sesiAktif.isConnected &&
                    kandidat.isConnected &&
                    !kandidat.isClosed
                ) {
                    terpilih = kandidat
                    break
                } else {
                    buangan.add(kandidat)
                }
            }
        }

        for (ch in buangan) {
            runCatching { ch.disconnect() }
        }
        return terpilih
    }

    private fun kembalikanAtauPutus(ch: ChannelSftp, sehat: Boolean) {
        try {
            var simpan = false
            if (sehat && !ditutup) {
                val sesiAktif = sesi
                val sesiChannel = runCatching { ch.session }.getOrNull()
                if (sesiAktif != null &&
                    sesiChannel === sesiAktif &&
                    sesiAktif.isConnected &&
                    ch.isConnected &&
                    !ch.isClosed
                ) {
                    synchronized(kunciPool) {
                        if (!ditutup &&
                            sesi === sesiAktif &&
                            poolChannel.size < maksIdlePool
                        ) {
                            poolChannel.addLast(ch)
                            simpan = true
                        }
                    }
                }
            }
            if (!simpan) {
                runCatching { ch.disconnect() }
            }
        } finally {
            slot.release()
        }
    }

    private suspend fun bukaChannelBaru(): ChannelSftp {
        repeat(2) { percobaan ->
            val s = pastikanSesi()
            val tCh = System.nanoTime()
            var ch: ChannelSftp? = null
            try {
                return withContext(dispatcher) {
                    val baru = s.openChannel("sftp") as ChannelSftp
                    ch = baru
                    baru.connect(10_000)
                    baru
                }.also {
                    catatPerf("openChannel", tCh, "percobaan=$percobaan")
                }
            } catch (e: CancellationException) {
                runCatching { ch?.disconnect() }
                throw e
            } catch (e: JSchException) {
                runCatching { ch?.disconnect() }
                catatPerf("openChannel_gagal", tCh, "percobaan=$percobaan msg=${e.message}")
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
        val tSesi = System.nanoTime()
        val rahasia = ambilRahasia() ?: throw GagalSftp.RahasiaHilang()
        withContext(dispatcher) { sambung(rahasia) }.also {
            sesi = it
            catatPerf("sambungSesi", tSesi)
        }
    }

    private fun invalidasi(s: Session) {
        val usang = synchronized(kunciPool) {
            val dibuang = mutableListOf<ChannelSftp>()
            val iter = poolChannel.iterator()
            while (iter.hasNext()) {
                val ch = iter.next()
                val ses = runCatching { ch.session }.getOrNull()
                if (ses == null || ses === s || !ch.isConnected || ch.isClosed) {
                    iter.remove()
                    dibuang.add(ch)
                }
            }
            dibuang
        }
        for (ch in usang) {
            runCatching { ch.disconnect() }
        }
        runCatching { s.disconnect() }
        if (sesi === s) sesi = null
    }

    private fun apakahKoneksiPutus(t: Throwable): Boolean = when (t) {
        is GagalSftp.Terputus, is GagalSftp.Jaringan -> true
        is JSchException -> true
        is SftpException -> t.id == ChannelSftp.SSH_FX_NO_CONNECTION ||
            t.id == ChannelSftp.SSH_FX_CONNECTION_LOST
        is IOException -> true
        else -> false
    }

    private fun apakahChannelTetapSehat(t: Throwable): Boolean = when (t) {
        is GagalSftp.Terputus, is GagalSftp.Jaringan -> false
        is GagalSftp -> true
        is SftpException -> t.id != ChannelSftp.SSH_FX_NO_CONNECTION &&
            t.id != ChannelSftp.SSH_FX_CONNECTION_LOST
        else -> false
    }

    private fun catatPerf(aksi: String, mulaiNano: Long, ekstra: String = "") {
        val durasiMs = (System.nanoTime() - mulaiNano) / 1_000_000L
        runCatching {
            val info = if (ekstra.isEmpty()) "" else " $ekstra"
            Log.w("FmSftpPerf", "[${profil.id}] $aksi=${durasiMs}ms$info")
        }
    }

    private fun sambung(rahasia: RahasiaSftp): Session {
        val jejakJsch = ArrayDeque<String>(16)
        val loggerJsch = object : com.jcraft.jsch.Logger {
            override fun isEnabled(level: Int): Boolean = level >= com.jcraft.jsch.Logger.INFO
            override fun log(level: Int, message: String?) {
                if (message.isNullOrBlank()) return
                synchronized(jejakJsch) {
                    if (jejakJsch.size >= 16) jejakJsch.removeFirst()
                    jejakJsch.addLast("L$level: $message")
                }
            }
        }
        val verif = PemverifHostKey(profil.jenisKunciHost, profil.sidikJariHost)
        val jsch = JSch().apply { setInstanceLogger(loggerJsch) }
        jsch.setHostKeyRepository(verif)
        if (rahasia is RahasiaSftp.KunciPribadi) {
            jsch.addIdentity("kunci-${profil.id}", rahasia.pem, null, rahasia.frasaSandi)
        }
        val s = jsch.getSession(profil.namaPengguna, profil.host, profil.port)
        if (rahasia is RahasiaSftp.Sandi) s.setPassword(rahasia.bytes)
        s.setConfig("StrictHostKeyChecking", "yes")
        s.setConfig(
            "PreferredAuthentications",
            if (rahasia is RahasiaSftp.Sandi) "password,keyboard-interactive" else "publickey"
        )
        try {
            s.setServerAliveInterval(15_000)
            s.setServerAliveCountMax(3)
            s.connect(10_000)
        } catch (e: JSchException) {
            val ringkasJejak = synchronized(jejakJsch) { jejakJsch.joinToString(" | ") }
            runCatching {
                Log.w(
                    "FmSftpPerf",
                    "[${profil.id}] sambung_gagal err=${e.message} cause=${e.cause?.javaClass?.simpleName}:${e.cause?.message} jejak=[$ringkasJejak]"
                )
            }
            runCatching { s.disconnect() }
            throw petakanSambung(e, verif)
        }
        return s
    }

    private fun petakanSambung(e: JSchException, v: PemverifHostKey): GagalSftp {
        val d = v.disajikan
        val msg = e.message.orEmpty()
        val namaKelas = e::class.simpleName.orEmpty()

        if (d != null && v.hasil == HostKeyRepository.NOT_INCLUDED) {
            return GagalSftp.HostBelumDipercaya(d.jenis, d.sidikJari)
        }
        if (d != null && v.hasil == HostKeyRepository.CHANGED) {
            return GagalSftp.HostBerubah(d.jenis, d.sidikJari, profil.sidikJariHost.orEmpty())
        }

        if (namaKelas.contains("UnknownHostKey", ignoreCase = true) ||
            msg.contains("reject HostKey", ignoreCase = true) ||
            msg.contains("UnknownHostKey", ignoreCase = true)
        ) {
            if (d != null) {
                return GagalSftp.HostBelumDipercaya(d.jenis, d.sidikJari)
            }
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