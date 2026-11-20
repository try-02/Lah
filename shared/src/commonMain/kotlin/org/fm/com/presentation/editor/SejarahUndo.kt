@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.presentation.editor

import kotlin.time.TimeSource

data class HasilUndoRedo(
    val teks: String,
    val indeksKursor: Int
)

class SejarahUndo(
    private val maksLangkah: Int = 100,
    private val batasJedaGabungMs: Long = 800L,
    private val waktuSekarangMs: () -> Long = defaultWaktuMs()
) {
    init {
        require(maksLangkah > 0) { "maksLangkah harus > 0" }
    }

    private data class DeltaEdit(
        val awal: Int,
        val teksDihapus: String,
        val teksDisisip: String,
        val kursorSebelum: Int,
        val kursorSesudah: Int,
        val waktuMs: Long
    )

    private val tumpukanUndo = mutableListOf<DeltaEdit>()
    private val tumpukanRedo = mutableListOf<DeltaEdit>()

    val dapatUndo: Boolean get() = tumpukanUndo.isNotEmpty()
    val dapatRedo: Boolean get() = tumpukanRedo.isNotEmpty()

    fun catatPerubahan(
        teksSebelum: String,
        teksSesudah: String,
        kursorSebelum: Int,
        kursorSesudah: Int
    ) {
        if (teksSebelum == teksSesudah) return

        val minLen = minOf(teksSebelum.length, teksSesudah.length)
        var prefix = 0
        while (prefix < minLen && teksSebelum[prefix] == teksSesudah[prefix]) {
            prefix++
        }

        var suffix = 0
        while (
            suffix < (minLen - prefix) &&
            teksSebelum[teksSebelum.length - 1 - suffix] == teksSesudah[teksSesudah.length - 1 - suffix]
        ) {
            suffix++
        }

        val teksDihapus = teksSebelum.substring(prefix, teksSebelum.length - suffix)
        val teksDisisip = teksSesudah.substring(prefix, teksSesudah.length - suffix)
        val waktu = waktuSekarangMs()

        tumpukanRedo.clear()

        val puncak = tumpukanUndo.lastOrNull()
        if (puncak != null && dapatDigabung(puncak, prefix, teksDihapus, teksDisisip, waktu)) {
            tumpukanUndo[tumpukanUndo.lastIndex] = puncak.copy(
                teksDisisip = puncak.teksDisisip + teksDisisip,
                kursorSesudah = kursorSesudah,
                waktuMs = waktu
            )
            return
        }

        tumpukanUndo.add(
            DeltaEdit(
                awal = prefix,
                teksDihapus = teksDihapus,
                teksDisisip = teksDisisip,
                kursorSebelum = kursorSebelum,
                kursorSesudah = kursorSesudah,
                waktuMs = waktu
            )
        )
        if (tumpukanUndo.size > maksLangkah) {
            tumpukanUndo.removeAt(0)
        }
    }

    fun undo(teksSaatIni: String): HasilUndoRedo? {
        if (tumpukanUndo.isEmpty()) return null
        val delta = tumpukanUndo.removeAt(tumpukanUndo.lastIndex)
        val akhirDisisip = (delta.awal + delta.teksDisisip.length).coerceAtMost(teksSaatIni.length)
        val awalAman = delta.awal.coerceIn(0, akhirDisisip)
        val hasilTeks = teksSaatIni.replaceRange(awalAman, akhirDisisip, delta.teksDihapus)
        tumpukanRedo.add(delta)
        return HasilUndoRedo(
            teks = hasilTeks,
            indeksKursor = delta.kursorSebelum.coerceIn(0, hasilTeks.length)
        )
    }

    fun redo(teksSaatIni: String): HasilUndoRedo? {
        if (tumpukanRedo.isEmpty()) return null
        val delta = tumpukanRedo.removeAt(tumpukanRedo.lastIndex)
        val akhirDihapus = (delta.awal + delta.teksDihapus.length).coerceAtMost(teksSaatIni.length)
        val awalAman = delta.awal.coerceIn(0, akhirDihapus)
        val hasilTeks = teksSaatIni.replaceRange(awalAman, akhirDihapus, delta.teksDisisip)
        tumpukanUndo.add(delta)
        return HasilUndoRedo(
            teks = hasilTeks,
            indeksKursor = delta.kursorSesudah.coerceIn(0, hasilTeks.length)
        )
    }

    fun bersihkan() {
        tumpukanUndo.clear()
        tumpukanRedo.clear()
    }

    private fun dapatDigabung(
        puncak: DeltaEdit,
        awalBaru: Int,
        dihapusBaru: String,
        disisipBaru: String,
        waktuBaru: Long
    ): Boolean {
        if (batasJedaGabungMs <= 0L) return false
        if (waktuBaru - puncak.waktuMs > batasJedaGabungMs) return false
        if (puncak.teksDihapus.isNotEmpty() || dihapusBaru.isNotEmpty()) return false
        if (puncak.teksDisisip.isEmpty() || disisipBaru.length != 1) return false
        val ch = disisipBaru[0]
        if (ch == '\n' || ch.isWhitespace()) return false
        return awalBaru == puncak.awal + puncak.teksDisisip.length
    }

    private companion object {
        fun defaultWaktuMs(): () -> Long {
            val awal = TimeSource.Monotonic.markNow()
            return { awal.elapsedNow().inWholeMilliseconds }
        }
    }

    internal fun catatDelta(
        awal: Int,
        teksDihapus: String,
        teksDisisip: String,
        kursorSebelum: Int,
        kursorSesudah: Int
    ) {
        if (teksDihapus.isEmpty() && teksDisisip.isEmpty()) return
        val waktu = waktuSekarangMs()
        tumpukanRedo.clear()

        val puncak = tumpukanUndo.lastOrNull()
        if (puncak != null && dapatDigabung(puncak, awal, teksDihapus, teksDisisip, waktu)) {
            tumpukanUndo[tumpukanUndo.lastIndex] = puncak.copy(
                teksDisisip = puncak.teksDisisip + teksDisisip,
                kursorSesudah = kursorSesudah,
                waktuMs = waktu
            )
            return
        }

        tumpukanUndo.add(
            DeltaEdit(
                awal = awal,
                teksDihapus = teksDihapus,
                teksDisisip = teksDisisip,
                kursorSebelum = kursorSebelum,
                kursorSesudah = kursorSesudah,
                waktuMs = waktu
            )
        )
        if (tumpukanUndo.size > maksLangkah) {
            tumpukanUndo.removeAt(0)
        }
    }
}