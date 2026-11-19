package org.fm.com.domain.usecase

import kotlin.coroutines.cancellation.CancellationException

data class OpsiCariEditor(
    val pekaHuruf: Boolean = false,
    val kataUtuh: Boolean = false,
    val gunakanRegex: Boolean = false
)

data class RentangKecocokan(
    val awal: Int,
    val akhirEksklusif: Int
) {
    init {
        require(awal >= 0 && akhirEksklusif >= awal) {
            "Rentang kecocokan tidak valid: [$awal, $akhirEksklusif)"
        }
    }

    val panjang: Int
        get() = akhirEksklusif - awal
}

sealed interface HasilCariEditor {
    data class Sukses(val daftarKecocokan: List<RentangKecocokan>) : HasilCariEditor
    data class PolaTidakValid(val pesan: String) : HasilCariEditor
}

sealed interface HasilOperasiGanti {
    data class Sukses(
        val teksBaru: String,
        val jumlahUbahan: Int,
        val posisiKursorSetelahnya: Int,
        val rentangBerikutnya: RentangKecocokan? = null
    ) : HasilOperasiGanti

    data class PolaTidakValid(val pesan: String) : HasilOperasiGanti
}

class CariGantiUseCase {

    fun cariSemua(
        teks: String,
        kueri: String,
        opsi: OpsiCariEditor = OpsiCariEditor()
    ): HasilCariEditor {
        if (kueri.isEmpty()) {
            return HasilCariEditor.Sukses(emptyList())
        }

        if (!opsi.gunakanRegex) {
            if (kueri.length > teks.length) {
                return HasilCariEditor.Sukses(emptyList())
            }
            val hasil = mutableListOf<RentangKecocokan>()
            val ignoreCase = !opsi.pekaHuruf
            var indeks = 0
            while (indeks <= teks.length - kueri.length) {
                val ketemu = teks.indexOf(kueri, startIndex = indeks, ignoreCase = ignoreCase)
                if (ketemu < 0) break
                val akhir = ketemu + kueri.length
                if (!opsi.kataUtuh || apakahBatasKataUtuh(teks, ketemu, akhir)) {
                    hasil.add(RentangKecocokan(ketemu, akhir))
                }
                indeks = akhir
            }
            return HasilCariEditor.Sukses(hasil)
        }

        val opsiRegex = buildSet {
            add(RegexOption.MULTILINE)
            if (!opsi.pekaHuruf) {
                add(RegexOption.IGNORE_CASE)
            }
        }
        val regex = try {
            Regex(kueri, opsiRegex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilCariEditor.PolaTidakValid(formatPesanErrorRegex(e))
        }

        return try {
            val hasil = mutableListOf<RentangKecocokan>()
            for (cocok in regex.findAll(teks)) {
                val awal = cocok.range.first
                val akhir = cocok.range.last + 1
                if (opsi.kataUtuh && (awal == akhir || !apakahBatasKataUtuh(teks, awal, akhir))) {
                    continue
                }
                hasil.add(RentangKecocokan(awal, akhir))
            }
            HasilCariEditor.Sukses(hasil)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilCariEditor.PolaTidakValid(formatPesanErrorRegex(e))
        }
    }

    fun pilihKecocokanBerikutnya(
        daftar: List<RentangKecocokan>,
        awalSeleksi: Int,
        akhirSeleksi: Int
    ): Int {
        if (daftar.isEmpty()) return -1
        val awal = minOf(awalSeleksi, akhirSeleksi)
        val akhir = maxOf(awalSeleksi, akhirSeleksi)

        val indeksAktif = daftar.indexOfFirst { it.awal == awal && it.akhirEksklusif == akhir }
        if (indeksAktif >= 0) {
            return (indeksAktif + 1) % daftar.size
        }

        val idxBerikutnya = daftar.indexOfFirst { it.awal >= awal }
        return if (idxBerikutnya >= 0) idxBerikutnya else 0
    }

    fun pilihKecocokanSebelumnya(
        daftar: List<RentangKecocokan>,
        awalSeleksi: Int,
        akhirSeleksi: Int
    ): Int {
        if (daftar.isEmpty()) return -1
        val awal = minOf(awalSeleksi, akhirSeleksi)
        val akhir = maxOf(awalSeleksi, akhirSeleksi)

        val indeksAktif = daftar.indexOfFirst { it.awal == awal && it.akhirEksklusif == akhir }
        if (indeksAktif >= 0) {
            return (indeksAktif - 1 + daftar.size) % daftar.size
        }

        val idxSebelumnya = daftar.indexOfLast { it.awal < awal }
        return if (idxSebelumnya >= 0) idxSebelumnya else daftar.lastIndex
    }

    fun gantiSatu(
        teks: String,
        kueri: String,
        teksPengganti: String,
        awalSeleksi: Int,
        akhirSeleksi: Int,
        opsi: OpsiCariEditor = OpsiCariEditor()
    ): HasilOperasiGanti {
        val kursorAwalAman = awalSeleksi.coerceIn(0, teks.length)
        if (kueri.isEmpty()) {
            return HasilOperasiGanti.Sukses(
                teksBaru = teks,
                jumlahUbahan = 0,
                posisiKursorSetelahnya = kursorAwalAman,
                rentangBerikutnya = null
            )
        }

        val daftar = when (val hasilCari = cariSemua(teks, kueri, opsi)) {
            is HasilCariEditor.Sukses -> hasilCari.daftarKecocokan
            is HasilCariEditor.PolaTidakValid -> return HasilOperasiGanti.PolaTidakValid(hasilCari.pesan)
        }
        if (daftar.isEmpty()) {
            return HasilOperasiGanti.Sukses(
                teksBaru = teks,
                jumlahUbahan = 0,
                posisiKursorSetelahnya = kursorAwalAman,
                rentangBerikutnya = null
            )
        }

        val awal = minOf(awalSeleksi, akhirSeleksi).coerceIn(0, teks.length)
        val akhir = maxOf(awalSeleksi, akhirSeleksi).coerceIn(0, teks.length)

        val target = daftar.firstOrNull { it.awal == awal && it.akhirEksklusif == akhir }
            ?: daftar.firstOrNull { it.awal >= awal }
            ?: daftar.first()

        val teksBaru = teks.replaceRange(target.awal, target.akhirEksklusif, teksPengganti)
        val kursorSetelah = target.awal + teksPengganti.length

        val daftarSesudah = (cariSemua(teksBaru, kueri, opsi) as? HasilCariEditor.Sukses)
            ?.daftarKecocokan
            .orEmpty()
        val rentangBerikutnya = daftarSesudah.firstOrNull { it.awal >= kursorSetelah }
            ?: daftarSesudah.firstOrNull { it.akhirEksklusif <= target.awal }

        return HasilOperasiGanti.Sukses(
            teksBaru = teksBaru,
            jumlahUbahan = 1,
            posisiKursorSetelahnya = kursorSetelah,
            rentangBerikutnya = rentangBerikutnya
        )
    }

    fun gantiSemua(
        teks: String,
        kueri: String,
        teksPengganti: String,
        opsi: OpsiCariEditor = OpsiCariEditor()
    ): HasilOperasiGanti {
        if (kueri.isEmpty()) {
            return HasilOperasiGanti.Sukses(
                teksBaru = teks,
                jumlahUbahan = 0,
                posisiKursorSetelahnya = 0,
                rentangBerikutnya = null
            )
        }

        val daftar = when (val hasilCari = cariSemua(teks, kueri, opsi)) {
            is HasilCariEditor.Sukses -> hasilCari.daftarKecocokan
            is HasilCariEditor.PolaTidakValid -> return HasilOperasiGanti.PolaTidakValid(hasilCari.pesan)
        }
        if (daftar.isEmpty()) {
            return HasilOperasiGanti.Sukses(
                teksBaru = teks,
                jumlahUbahan = 0,
                posisiKursorSetelahnya = 0,
                rentangBerikutnya = null
            )
        }

        val sb = StringBuilder(teks.length)
        var posisi = 0
        var jumlahUbahan = 0
        var posisiKursorTerakhir = 0
        for (rentang in daftar) {
            if (rentang.awal < posisi) continue
            sb.append(teks, posisi, rentang.awal)
            sb.append(teksPengganti)
            posisiKursorTerakhir = sb.length
            posisi = rentang.akhirEksklusif
            jumlahUbahan++
        }
        sb.append(teks, posisi, teks.length)

        return HasilOperasiGanti.Sukses(
            teksBaru = sb.toString(),
            jumlahUbahan = jumlahUbahan,
            posisiKursorSetelahnya = posisiKursorTerakhir,
            rentangBerikutnya = null
        )
    }

    private fun apakahBatasKataUtuh(teks: String, awal: Int, akhirEksklusif: Int): Boolean {
        val kiriAman = awal == 0 || !apakahKarakterKata(teks[awal - 1])
        val kananAman = akhirEksklusif == teks.length || !apakahKarakterKata(teks[akhirEksklusif])
        return kiriAman && kananAman
    }

    private fun apakahKarakterKata(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_'

    private fun formatPesanErrorRegex(e: Exception): String {
        val barisPertama = e.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return if (barisPertama != null) {
            "Pola regex tidak valid: $barisPertama"
        } else {
            "Pola regex tidak valid"
        }
    }
}