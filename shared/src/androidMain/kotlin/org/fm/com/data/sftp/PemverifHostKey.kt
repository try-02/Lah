package org.fm.com.data.sftp

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

internal class KunciHostDisajikan(val jenis: String, val sidikJari: String)

internal object AlgoritmaHostKey {
    fun untuk(jenisKunci: String): String =
        if (jenisKunci == "ssh-rsa") "rsa-sha2-512,rsa-sha2-256" else jenisKunci

    fun jenisDari(blob: ByteArray): String {
        if (blob.size < 4) return "?"
        val n = ((blob[0].toInt() and 0xFF) shl 24) or
            ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or
            (blob[3].toInt() and 0xFF)
        if (n < 0 || n > 64 || n > blob.size - 4) return "?"
        return String(blob, 4, n, Charsets.US_ASCII)
    }

    fun sidikJari(blob: ByteArray): String =
        "SHA256:" + Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
}

internal class PemverifHostKey(
    private val jenisTerpercaya: String?,
    private val sidikJariTerpercaya: String?
) : HostKeyRepository {

    @Volatile
    var disajikan: KunciHostDisajikan? = null
        private set

    @Volatile
    var hasil: Int = -1
        private set

    override fun check(host: String?, key: ByteArray?): Int {
        if (key == null) {
            hasil = HostKeyRepository.NOT_INCLUDED
            return hasil
        }
        val jenis = AlgoritmaHostKey.jenisDari(key)
        val sidik = AlgoritmaHostKey.sidikJari(key)
        disajikan = KunciHostDisajikan(jenis, sidik)
        val v = when {
            sidikJariTerpercaya == null -> HostKeyRepository.NOT_INCLUDED
            jenis == jenisTerpercaya &&
                MessageDigest.isEqual(sidik.toByteArray(), sidikJariTerpercaya.toByteArray()) ->
                HostKeyRepository.OK
            else -> HostKeyRepository.CHANGED
        }
        hasil = v
        return v
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "berkas-sftp-pin"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}