package org.fm.com.domain.rename

import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat

object ParserTokenPola {

    fun parse(
        pola: String,
        opsi: OpsiParserPola = OpsiParserPola()
    ): HasilParsePola {
        if (pola.isEmpty()) {
            return HasilParsePola.Gagal("Pola nama tidak boleh kosong", 0)
        }

        val hasil = mutableListOf<TokenPola>()
        val bufferLiteral = StringBuilder()

        fun simpanLiteralJikaAda() {
            if (bufferLiteral.isNotEmpty()) {
                hasil.add(TokenPola.TeksLiteral(bufferLiteral.toString()))
                bufferLiteral.clear()
            }
        }

        var i = 0
        while (i < pola.length) {
            when (pola[i]) {
                '{' -> {
                    if (i + 1 < pola.length && pola[i + 1] == '{') {
                        bufferLiteral.append('{')
                        i += 2
                        continue
                    }

                    var indeksTutup = -1
                    var j = i + 1
                    while (j < pola.length) {
                        if (pola[j] == '{') {
                            return HasilParsePola.Gagal(
                                "Token '{' bersarang tidak diperbolehkan pada posisi $j",
                                j
                            )
                        }
                        if (pola[j] == '}') {
                            indeksTutup = j
                            break
                        }
                        j++
                    }

                    if (indeksTutup < 0) {
                        return HasilParsePola.Gagal(
                            "Token '{' pada posisi $i belum ditutup dengan '}'",
                            i
                        )
                    }

                    val isiToken = pola.substring(i + 1, indeksTutup)
                    val token = parseIsiToken(isiToken, i, opsi)
                    if (token is HasilTokenTunggal.Gagal) {
                        return HasilParsePola.Gagal(token.pesan, token.indeks)
                    }

                    simpanLiteralJikaAda()
                    hasil.add((token as HasilTokenTunggal.Sah).token)
                    i = indeksTutup + 1
                }
                '}' -> {
                    if (i + 1 < pola.length && pola[i + 1] == '}') {
                        bufferLiteral.append('}')
                        i += 2
                    } else {
                        return HasilParsePola.Gagal(
                            "Tanda '}' pada posisi $i tidak memiliki pembuka '{'",
                            i
                        )
                    }
                }
                else -> {
                    bufferLiteral.append(pola[i])
                    i++
                }
            }
        }

        simpanLiteralJikaAda()
        if (hasil.isEmpty()) {
            return HasilParsePola.Gagal("Pola nama tidak boleh kosong", 0)
        }
        return HasilParsePola.Sukses(hasil)
    }

    fun pisahNamaDanEkstensi(
        nama: String,
        adalahFolder: Boolean = false
    ): Pair<String, String> {
        if (adalahFolder || nama.isEmpty()) {
            return nama to ""
        }
        val titik = nama.lastIndexOf('.')
        if (titik <= 0 || titik >= nama.length - 1) {
            return nama to ""
        }
        return nama.substring(0, titik) to nama.substring(titik)
    }

    fun formatNomor(nilai: Long, lebarMin: Int?): String {
        val teks = nilai.toString()
        if (lebarMin == null || lebarMin <= 1) return teks
        return if (nilai < 0L) {
            val tanpaMinus = teks.substring(1)
            if (tanpaMinus.length >= lebarMin) teks else "-" + tanpaMinus.padStart(lebarMin, '0')
        } else {
            if (teks.length >= lebarMin) teks else teks.padStart(lebarMin, '0')
        }
    }

    fun terapkan(
        daftarToken: List<TokenPola>,
        namaAsli: String,
        adalahFolder: Boolean = false,
        indeksUrut: Int = 0,
        konfigurasiNomor: KonfigurasiNomorUrut = KonfigurasiNomorUrut()
    ): String {
        require(indeksUrut >= 0) { "indeksUrut tidak boleh negatif" }
        val (namaDasar, ekstensi) = pisahNamaDanEkstensi(namaAsli, adalahFolder)
        val sb = StringBuilder(namaAsli.length + 16)
        for (token in daftarToken) {
            when (token) {
                is TokenPola.TeksLiteral -> sb.append(token.teks)
                is TokenPola.NamaDasar -> sb.append(namaDasar)
                is TokenPola.Ekstensi -> sb.append(ekstensi)
                is TokenPola.NomorUrut -> {
                    val awal = token.angkaAwalInline ?: konfigurasiNomor.angkaAwal
                    val nilai = awal.toLong() + indeksUrut.toLong() * konfigurasiNomor.langkah.toLong()
                    sb.append(formatNomor(nilai, token.lebarMin))
                }
            }
        }
        return sb.toString()
    }

    fun hasilkanNama(
        pola: String,
        namaAsli: String,
        adalahFolder: Boolean = false,
        indeksUrut: Int = 0,
        konfigurasiNomor: KonfigurasiNomorUrut = KonfigurasiNomorUrut(),
        opsi: OpsiParserPola = OpsiParserPola()
    ): HasilOperasi<String> {
        return when (val parsed = parse(pola, opsi)) {
            is HasilParsePola.Sukses -> HasilOperasi.Sukses(
                terapkan(
                    daftarToken = parsed.daftarToken,
                    namaAsli = namaAsli,
                    adalahFolder = adalahFolder,
                    indeksUrut = indeksUrut,
                    konfigurasiNomor = konfigurasiNomor
                )
            )
            is HasilParsePola.Gagal -> HasilOperasi.Gagal(parsed.pesan, PenyebabGalat.VALIDASI)
        }
    }

    private sealed interface HasilTokenTunggal {
        data class Sah(val token: TokenPola) : HasilTokenTunggal
        data class Gagal(val pesan: String, val indeks: Int) : HasilTokenTunggal
    }

    private fun parseIsiToken(
        isi: String,
        indeksAwal: Int,
        opsi: OpsiParserPola
    ): HasilTokenTunggal {
        if (isi.isEmpty()) {
            return HasilTokenTunggal.Gagal("Token kosong '{}' tidak valid pada posisi $indeksAwal", indeksAwal)
        }
        return when (isi) {
            "P" -> HasilTokenTunggal.Sah(TokenPola.NamaDasar)
            "S" -> HasilTokenTunggal.Sah(TokenPola.Ekstensi)
            "N" -> HasilTokenTunggal.Sah(TokenPola.NomorUrut(lebarMin = null, angkaAwalInline = null))
            "zN" -> HasilTokenTunggal.Sah(TokenPola.NomorUrut(lebarMin = 2, angkaAwalInline = null))
            else -> {
                if (isi.startsWith("zN:")) {
                    val bagianLebar = isi.substring(3)
                    val lebar = parseLebar(bagianLebar, isi, indeksAwal, opsi.batasLebarMaks)
                    return when (lebar) {
                        is HasilAngka.Sah -> HasilTokenTunggal.Sah(
                            TokenPola.NomorUrut(lebarMin = lebar.nilai, angkaAwalInline = null)
                        )
                        is HasilAngka.Gagal -> HasilTokenTunggal.Gagal(lebar.pesan, indeksAwal)
                    }
                }
                if (isi.startsWith("z") && isi.length > 1) {
                    val indeksTitikDua = isi.indexOf(':')
                    val bagianAngka = if (indeksTitikDua < 0) {
                        isi.substring(1)
                    } else {
                        isi.substring(1, indeksTitikDua)
                    }
                    if (bagianAngka.isNotEmpty() && semuaDigitAscii(bagianAngka)) {
                        if (!opsi.izinkanAngkaAwalInline) {
                            return HasilTokenTunggal.Gagal(
                                "Angka awal inline '{$isi}' tidak diaktifkan; gunakan {zN} atau {zN:W}",
                                indeksAwal
                            )
                        }
                        val angkaAwal = bagianAngka.toIntOrNull()
                            ?: return HasilTokenTunggal.Gagal(
                                "Angka awal pada token '{$isi}' terlalu besar",
                                indeksAwal
                            )
                        val lebar = if (indeksTitikDua < 0) {
                            2
                        } else {
                            val bagianLebar = isi.substring(indeksTitikDua + 1)
                            when (val hasilLebar = parseLebar(bagianLebar, isi, indeksAwal, opsi.batasLebarMaks)) {
                                is HasilAngka.Sah -> hasilLebar.nilai
                                is HasilAngka.Gagal -> return HasilTokenTunggal.Gagal(hasilLebar.pesan, indeksAwal)
                            }
                        }
                        return HasilTokenTunggal.Sah(
                            TokenPola.NomorUrut(lebarMin = lebar, angkaAwalInline = angkaAwal)
                        )
                    }
                }
                HasilTokenTunggal.Gagal("Token tidak dikenal: '{$isi}'", indeksAwal)
            }
        }
    }

    private sealed interface HasilAngka {
        data class Sah(val nilai: Int) : HasilAngka
        data class Gagal(val pesan: String) : HasilAngka
    }

    private fun parseLebar(
        bagianLebar: String,
        isiToken: String,
        indeksAwal: Int,
        batasLebarMaks: Int
    ): HasilAngka {
        if (bagianLebar.isEmpty()) {
            return HasilAngka.Gagal("Lebar padding pada token '{$isiToken}' tidak boleh kosong")
        }
        if (!semuaDigitAscii(bagianLebar)) {
            return HasilAngka.Gagal("Lebar padding pada token '{$isiToken}' harus berupa angka positif")
        }
        val nilai = bagianLebar.toIntOrNull()
        if (nilai == null || nilai < 1 || nilai > batasLebarMaks) {
            return HasilAngka.Gagal(
                "Lebar padding pada '{$isiToken}' harus antara 1 dan $batasLebarMaks (posisi $indeksAwal)"
            )
        }
        return HasilAngka.Sah(nilai)
    }

    private fun semuaDigitAscii(teks: String): Boolean {
        for (c in teks) {
            if (c !in '0'..'9') return false
        }
        return true
    }
}