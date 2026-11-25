@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.presentation.komponen

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.sftp.PengenalSftp

object PemformatLokasiRamah {
    private val NAMA_BULAN = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )
    private const val SATU_HARI_MS = 24L * 60L * 60L * 1000L
    private const val BATAS_MAKS_EPOCH_MS = 253_402_300_799_999L

    fun formatRingkas(lokasi: LokasiBerkas): String {
        return when (lokasi.jenis) {
            JenisLokasi.POSIX -> formatPathPosix(lokasi.pengenal)
            JenisLokasi.SAF -> formatUriSaf(lokasi.pengenal, lokasi.label)
            JenisLokasi.IOS -> formatUriIos(lokasi.pengenal, lokasi.label)
            JenisLokasi.SFTP -> formatUriSftp(lokasi.pengenal)
        }
    }

    @Suppress("SdCardPath")
    private fun formatUriSftp(pengenal: String): String {
        val lok = PengenalSftp.urai(pengenal) ?: return pengenal
        val pathSingkat = formatPathPosix(lok.path)
        return "sftp:${lok.idProfil}:$pathSingkat"
    }

    fun formatPrompt(lokasi: LokasiBerkas): String {
        val dasar = formatRingkas(lokasi)
        return "$dasar $"
    }

    fun formatMetadataBaris(
        ukuranFormat: String,
        tanggalPendek: String,
        adalahFolder: Boolean,
        dapatDitulis: Boolean
    ): String = buildString {
        append(if (adalahFolder) "dir" else ukuranFormat)
        if (tanggalPendek != "-") {
            append(" · ")
            append(tanggalPendek)
        }
        if (!dapatDitulis) {
            append(" · RO")
        }
    }

    fun formatTanggalPendek(
        epochMs: Long?,
        zonaWaktu: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (epochMs == null || epochMs <= 0L || epochMs > BATAS_MAKS_EPOCH_MS) return "-"
        return runCatching {
            val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zonaWaktu)
            if (dt.year !in 1970..9999) return "-"
            val bulan = NAMA_BULAN[(dt.month.number - 1).coerceIn(0, 11)]
            "${duaDigit(dt.day)} $bulan"
        }.getOrElse { "-" }
    }

    fun formatWaktuRelatif(
        epochMs: Long?,
        sekarangMs: Long = Clock.System.now().toEpochMilliseconds(),
        zonaWaktu: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        if (epochMs == null || epochMs <= 0L || epochMs > BATAS_MAKS_EPOCH_MS) return "-"
        return runCatching {
            val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zonaWaktu)
            if (dt.year !in 1970..9999) return "-"
            val hariIni = Instant.fromEpochMilliseconds(sekarangMs).toLocalDateTime(zonaWaktu)
            val kemarin = Instant.fromEpochMilliseconds(sekarangMs - SATU_HARI_MS).toLocalDateTime(zonaWaktu)
            val jam = "${duaDigit(dt.hour)}:${duaDigit(dt.minute)}"
            when {
                dt.year == hariIni.year && dt.month.number == hariIni.month.number && dt.day == hariIni.day ->
                    "Hari ini $jam"
                dt.year == kemarin.year && dt.month.number == kemarin.month.number && dt.day == kemarin.day ->
                    "Kemarin $jam"
                else -> {
                    val bulan = NAMA_BULAN[(dt.month.number - 1).coerceIn(0, 11)]
                    "${duaDigit(dt.day)} $bulan $jam"
                }
            }
        }.getOrElse { "-" }
    }

    fun ekstensiPendek(nama: String, adalahFolder: Boolean): String? {
        if (adalahFolder) return null
        val titik = nama.lastIndexOf('.')
        if (titik <= 0 || titik >= nama.length - 1) return null
        val ext = nama.substring(titik + 1).lowercase()
        return if (ext.length <= 5) ".$ext" else ".${ext.take(4)}"
    }

    @Suppress("SdCardPath")
    private fun formatPathPosix(path: String): String {
        val bersih = path.trimEnd('/').ifEmpty { "/" }
        return when {
            bersih == "/data/data/com.termux/files/home" -> "~/home"
            bersih.startsWith("/data/data/com.termux/files/home/") ->
                "~/home/${bersih.removePrefix("/data/data/com.termux/files/home/")}"
            bersih.startsWith("/storage/emulated/0/") ->
                "sdcard/${bersih.removePrefix("/storage/emulated/0/")}"
            bersih == "/storage/emulated/0" -> "sdcard"
            bersih.contains("/Android/data/") && bersih.endsWith("/files") -> "app:workspace"
            bersih.contains("/files/") -> "app:${bersih.substringAfter("/files/")}"
            else -> bersih
        }
    }

    @Suppress("SdCardPath")
    private fun formatUriSaf(uriString: String, label: String?): String {
        val setelahScheme = uriString.substringAfter("://", "")
        if (setelahScheme.isEmpty()) return label ?: uriString
        val authority = setelahScheme.substringBefore('/')
        val docRaw = when {
            "/document/" in uriString -> uriString.substringAfterLast("/document/")
            "/tree/" in uriString -> uriString.substringAfter("/tree/").substringBefore("/document/")
            else -> ""
        }
        val docDecoded = decodePercentUtf8(docRaw)

        if (authority.contains("termux", ignoreCase = true)) {
            val termuxHome = "/data/data/com.termux/files/home"
            val pathSingkat = when {
                docDecoded == termuxHome -> "~/home"
                docDecoded.startsWith("$termuxHome/") -> "~/home/${docDecoded.removePrefix("$termuxHome/")}"
                docDecoded.isNotBlank() -> docDecoded
                else -> label ?: "home"
            }
            return "termux:$pathSingkat"
        }

        val awalanPenyedia = when {
            authority.contains("externalstorage") -> ""
            authority.contains("downloads") -> "downloads:"
            else -> "${authority.substringBefore('.').ifBlank { "saf" }}:"
        }

        if (docDecoded.isNotBlank()) {
            val bersih = docDecoded
                .replace("primary:", "internal:")
                .trimStart('/')
            return if (awalanPenyedia.isEmpty()) bersih else "$awalanPenyedia$bersih"
        }
        return label?.takeIf { it.isNotBlank() } ?: "saf:${authority.substringBefore('.')}"
    }

    private fun formatUriIos(pengenal: String, label: String?): String {
        val tanpaFileScheme = decodePercentUtf8(pengenal.removePrefix("file://"))
        return when {
            "/Documents/" in tanpaFileScheme -> "ios:Documents/${tanpaFileScheme.substringAfter("/Documents/")}"
            tanpaFileScheme.endsWith("/Documents") -> "ios:Documents"
            else -> label?.let { "ios:$it" } ?: tanpaFileScheme
        }
    }

    private fun decodePercentUtf8(input: String): String {
        if ('%' !in input) return input
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 2 < input.length) {
                val b = hexKeByte(input[i + 1], input[i + 2])
                if (b != null) {
                    bytes.add(b)
                    i += 3
                    continue
                }
            }
            val chBytes = c.toString().encodeToByteArray()
            for (b in chBytes) {
                bytes.add(b)
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexKeByte(h1: Char, h2: Char): Byte? {
        val n1 = hexKeInt(h1) ?: return null
        val n2 = hexKeInt(h2) ?: return null
        return ((n1 shl 4) or n2).toByte()
    }

    private fun hexKeInt(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }

    private fun duaDigit(angka: Int): String = if (angka < 10) "0$angka" else "$angka"
}