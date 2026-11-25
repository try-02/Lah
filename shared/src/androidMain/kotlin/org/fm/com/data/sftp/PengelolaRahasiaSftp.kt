package org.fm.com.data.sftp

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.fm.com.domain.sftp.GagalSftp
import org.fm.com.domain.sftp.RahasiaSftp

class PengelolaRahasiaSftp(private val alias: String = "berkas_sftp_rahasia_v1") {

    fun segel(rahasia: RahasiaSftp, idProfil: String): String {
        val mentah = kodekan(rahasia)
        try {
            return Base64.getEncoder().encodeToString(enkripsi(mentah, idProfil.toByteArray(Charsets.UTF_8)))
        } finally {
            mentah.fill(0)
        }
    }

    fun buka(teks: String, idProfil: String): RahasiaSftp {
        val paket = try {
            Base64.getDecoder().decode(teks)
        } catch (_: IllegalArgumentException) {
            throw GagalSftp.RahasiaHilang()
        }
        val mentah = dekripsi(paket, idProfil.toByteArray(Charsets.UTF_8))
        try {
            return urai(mentah)
        } finally {
            mentah.fill(0)
        }
    }

    @Synchronized
    private fun kunci(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun enkripsi(plain: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMASI)
        c.init(Cipher.ENCRYPT_MODE, kunci())
        c.updateAAD(aad)
        val iv = c.iv
        val ct = c.doFinal(plain)
        return byteArrayOf(VERSI) + iv + ct
    }

    private fun dekripsi(paket: ByteArray, aad: ByteArray): ByteArray {
        if (paket.size < 1 + IV_BYTE + TAG_BYTE || paket[0] != VERSI) throw GagalSftp.RahasiaHilang()
        try {
            val c = Cipher.getInstance(TRANSFORMASI)
            c.init(Cipher.DECRYPT_MODE, kunci(), GCMParameterSpec(TAG_BYTE * 8, paket, 1, IV_BYTE))
            c.updateAAD(aad)
            return c.doFinal(paket, 1 + IV_BYTE, paket.size - 1 - IV_BYTE)
        } catch (_: GeneralSecurityException) {
            throw GagalSftp.RahasiaHilang()
        }
    }

    private fun kodekan(r: RahasiaSftp): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { o ->
            when (r) {
                is RahasiaSftp.Sandi -> {
                    o.writeByte(TIPE_SANDI)
                    tulisBlok(o, r.bytes)
                }
                is RahasiaSftp.KunciPribadi -> {
                    o.writeByte(TIPE_KUNCI)
                    tulisBlok(o, r.pem)
                    tulisBlok(o, r.frasaSandi ?: ByteArray(0))
                }
            }
        }
        return bos.toByteArray()
    }

    private fun urai(b: ByteArray): RahasiaSftp {
        DataInputStream(b.inputStream()).use { i ->
            return when (i.readUnsignedByte()) {
                TIPE_SANDI -> RahasiaSftp.Sandi(bacaBlok(i))
                TIPE_KUNCI -> {
                    val pem = bacaBlok(i)
                    val frasa = bacaBlok(i)
                    RahasiaSftp.KunciPribadi(pem, frasa.takeIf { it.isNotEmpty() })
                }
                else -> throw GagalSftp.RahasiaHilang()
            }
        }
    }

    private fun tulisBlok(o: DataOutputStream, data: ByteArray) {
        o.writeInt(data.size)
        o.write(data)
    }

    private fun bacaBlok(i: DataInputStream): ByteArray {
        val n = i.readInt()
        if (n < 0 || n > MAKS_BLOK) throw GagalSftp.RahasiaHilang()
        return ByteArray(n).also { i.readFully(it) }
    }

    private companion object {
        const val TRANSFORMASI = "AES/GCM/NoPadding"
        const val VERSI: Byte = 1
        const val IV_BYTE = 12
        const val TAG_BYTE = 16
        const val TIPE_SANDI = 1
        const val TIPE_KUNCI = 2
        const val MAKS_BLOK = 1 shl 20
    }
}