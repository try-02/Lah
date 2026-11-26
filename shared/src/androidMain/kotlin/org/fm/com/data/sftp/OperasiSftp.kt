package org.fm.com.data.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import org.fm.com.domain.sftp.EntriSftp
import org.fm.com.domain.sftp.GagalSftp
import org.fm.com.domain.sftp.PengenalSftp

internal object OperasiSftp {
    private const val MASKER_IZIN = 0xFFF // 0o7777
    private const val MAKS_LOMPATAN_TAUTAN = 16

    fun lolos(path: String): String = buildString(path.length + 4) {
        for (c in path) {
            if (c == '\\' || c == '*' || c == '?' || c == '[' || c == ']') append('\\')
            append(c)
        }
    }

    fun lstatAtauNull(ch: ChannelSftp, path: String): SftpATTRS? =
        try {
            ch.lstat(lolos(path))
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw e
        }

    fun stat(ch: ChannelSftp, path: String): EntriSftp? {
        val a = lstatAtauNull(ch, path) ?: return null
        return keEntri(ch, path, PengenalSftp.nama(path), a)
    }

    fun daftar(ch: ChannelSftp, dir: String): List<EntriSftp> {
        val hasil = ArrayList<EntriSftp>()
        for (item in ch.ls(lolos(dir))) {
            val e = item as ChannelSftp.LsEntry
            val nama = e.filename
            if (nama == "." || nama == "..") continue
            hasil += keEntri(ch, PengenalSftp.gabung(dir, nama), nama, e.attrs)
        }
        return hasil
    }

    private fun keEntri(ch: ChannelSftp, path: String, nama: String, a: SftpATTRS): EntriSftp {
        var folder = a.isDir
        var ukuran = a.size
        var mtime = a.mTime
        if (a.isLink) {
            try {
                val t = ch.stat(lolos(path))
                folder = t.isDir
                ukuran = t.size
                mtime = t.mTime
            } catch (_: SftpException) {
            }
        }
        return EntriSftp(nama, path, folder, a.isLink, ukuran, mtime.toLong() * 1000L, a.permissions and MASKER_IZIN)
    }

    fun buatFolder(ch: ChannelSftp, path: String, rekursif: Boolean) {
        val p = PengenalSftp.normalisasi(path)
        if (!rekursif) {
            if (lstatAtauNull(ch, p) != null) throw GagalSftp.SudahAda(p)
            ch.mkdir(p)
            return
        }
        var cur = ""
        for (b in p.split('/').filter { it.isNotEmpty() }) {
            cur += "/$b"
            val a = lstatAtauNull(ch, cur)
            if (a == null) ch.mkdir(cur) else if (!a.isDir && !a.isLink) throw GagalSftp.SudahAda(cur)
        }
    }

    fun buatBerkasKosong(ch: ChannelSftp, path: String, timpa: Boolean) {
        val tujuan = selesaikanTautan(ch, path)
        val a = lstatAtauNull(ch, tujuan)
        if (a != null) {
            if (a.isDir) throw GagalSftp.AdalahFolder(tujuan)
            if (!timpa) throw GagalSftp.SudahAda(tujuan)
        }
        val sementara = PengenalSftp.gabung(PengenalSftp.induk(tujuan), ".~tmp-${UUID.randomUUID()}")
        val keluar = ch.put(lolos(sementara), ChannelSftp.OVERWRITE)
        try {
            keluar.close()
            a?.permissions?.and(MASKER_IZIN)?.let { ch.chmod(it, lolos(sementara)) }
            if (timpa) gantiNamaTimpa(ch, sementara, tujuan) else gantiNama(ch, sementara, tujuan)
        } catch (t: Throwable) {
            runCatching { ch.rm(lolos(sementara)) }
            throw t
        }
    }

    fun hapus(ch: ChannelSftp, path: String) {
        val a = lstatAtauNull(ch, path) ?: throw GagalSftp.TidakAda(path)
        if (a.isLink || !a.isDir) {
            ch.rm(lolos(path))
            return
        }
        val folder = ArrayList<String>()
        val tumpukan = ArrayDeque<String>()
        tumpukan.addLast(path)
        while (tumpukan.isNotEmpty()) {
            val dir = tumpukan.removeLast()
            folder += dir
            for (item in ch.ls(lolos(dir))) {
                val e = item as ChannelSftp.LsEntry
                if (e.filename == "." || e.filename == "..") continue
                val p = PengenalSftp.gabung(dir, e.filename)
                if (e.attrs.isDir && !e.attrs.isLink) tumpukan.addLast(p) else ch.rm(lolos(p))
            }
        }
        for (d in folder.asReversed()) ch.rmdir(lolos(d))
    }

    fun gantiNama(ch: ChannelSftp, dari: String, ke: String) {
        if (lstatAtauNull(ch, ke) != null) throw GagalSftp.SudahAda(ke)
        ch.rename(lolos(dari), lolos(ke))
    }

    fun gantiNamaTimpa(ch: ChannelSftp, dari: String, ke: String) {
        try {
            ch.rename(lolos(dari), lolos(ke))
            return
        } catch (e: SftpException) {
            val ada = lstatAtauNull(ch, ke) ?: throw e
            if (ada.isDir) throw GagalSftp.AdalahFolder(ke)
        }
        val cadangan = PengenalSftp.gabung(PengenalSftp.induk(ke), ".~bak-${UUID.randomUUID()}")
        ch.rename(lolos(ke), lolos(cadangan))
        try {
            ch.rename(lolos(dari), lolos(ke))
        } catch (e: SftpException) {
            runCatching { ch.rename(lolos(cadangan), lolos(ke)) }
            throw e
        }
        runCatching { ch.rm(lolos(cadangan)) }
    }

    fun chmod(ch: ChannelSftp, path: String, izin: Int) {
        ch.chmod(izin and MASKER_IZIN, lolos(path))
    }

    fun selesaikanTautan(ch: ChannelSftp, path: String): String {
        var cur = PengenalSftp.normalisasi(path)
        repeat(MAKS_LOMPATAN_TAUTAN) {
            val a = lstatAtauNull(ch, cur) ?: return cur
            if (!a.isLink) return cur
            val sasaran = ch.readlink(lolos(cur))
            cur = if (sasaran.startsWith("/")) {
                PengenalSftp.normalisasi(sasaran)
            } else {
                PengenalSftp.normalisasi(PengenalSftp.induk(cur) + "/" + sasaran)
            }
        }
        throw GagalSftp.TautanBerputar(path)
    }

    fun siapkanTulisAtomik(
        p: PinjamanChannel,
        path: String,
        timpa: Boolean,
        izinEksplisit: Int? = null
    ): StreamTulisAtomik {
        val ch = p.channel
        val tujuan = selesaikanTautan(ch, path)
        val a = lstatAtauNull(ch, tujuan)
        if (a != null) {
            if (a.isDir) throw GagalSftp.AdalahFolder(tujuan)
            if (!timpa) throw GagalSftp.SudahAda(tujuan)
        }
        val izin = izinEksplisit?.and(MASKER_IZIN) ?: a?.permissions?.and(MASKER_IZIN)
        val sementara = PengenalSftp.gabung(PengenalSftp.induk(tujuan), ".~tmp-${UUID.randomUUID()}")
        val keluar = ch.put(lolos(sementara), ChannelSftp.OVERWRITE)
        return StreamTulisAtomik(p, tujuan, sementara, izin, timpa, keluar)
    }
}

class StreamTulisAtomik internal constructor(
    private val pinjaman: PinjamanChannel,
    val tujuan: String,
    private val sementara: String,
    private val izinAsli: Int?,
    private val timpa: Boolean,
    private val keluar: OutputStream
) : OutputStream() {

    private var tertulis = 0L
    private var gagal = false
    private var selesai = false

    override fun write(b: Int) {
        cobaTulis { keluar.write(b) }
        tertulis += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        cobaTulis { keluar.write(b, off, len) }
        tertulis += len
    }

    override fun flush() {
        cobaTulis { keluar.flush() }
    }

    fun batalkan() {
        gagal = true
        runCatching { close() }
    }

    override fun close() {
        if (selesai) return
        selesai = true
        val ch = pinjaman.channel
        try {
            if (gagal) {
                pinjaman.tandaiRusak()
                runCatching { keluar.close() }
                hapusSementara(ch)
                return
            }
            try {
                keluar.close()
                val a = ch.lstat(OperasiSftp.lolos(sementara))
                if (a.size != tertulis) {
                    throw IOException("Ukuran berkas sementara ${a.size} != $tertulis byte yang ditulis")
                }
                izinAsli?.let { ch.chmod(it, OperasiSftp.lolos(sementara)) }
                if (timpa) {
                    OperasiSftp.gantiNamaTimpa(ch, sementara, tujuan)
                } else {
                    OperasiSftp.gantiNama(ch, sementara, tujuan)
                }
            } catch (e: Throwable) {
                val sehat = e is GagalSftp &&
                    e !is GagalSftp.Terputus &&
                    e !is GagalSftp.Jaringan
                if (!sehat) {
                    pinjaman.tandaiRusak()
                }
                hapusSementara(ch)
                throw when (e) {
                    is GagalSftp -> e
                    is SftpException -> IOException("Gagal menyelesaikan tulis atomik: ${e.message}", e)
                    else -> e
                }
            }
        } finally {
            pinjaman.close()
        }
    }

    private fun hapusSementara(ch: ChannelSftp) {
        runCatching { ch.rm(OperasiSftp.lolos(sementara)) }
    }

    private inline fun cobaTulis(blok: () -> Unit) {
        if (selesai) throw IOException("Stream sudah ditutup")
        try {
            blok()
        } catch (e: IOException) {
            gagal = true
            pinjaman.tandaiRusak()
            throw e
        }
    }
}