package org.fm.com.data.arsip

import org.fm.com.domain.model.ValidasiNama

private const val BATAS_SEGMEN_JALUR_ARSIP_BAWAAN = 128
private const val BATAS_BYTE_SEGMEN_ARSIP = 255

sealed interface HasilValidasiPathArsip {
    data class Sah(
        val jalurNormal: String,
        val daftarSegmen: List<String>,
        val namaSegmenAkhir: String
    ) : HasilValidasiPathArsip

    data object AbaikanRoot : HasilValidasiPathArsip

    data class TidakSah(
        val jalurMentah: String,
        val alasan: String
    ) : HasilValidasiPathArsip
}

object ValidasiPathArsip {

    fun validasi(
        jalurMentah: String,
        adalahFolder: Boolean = false,
        batasSegmenMaks: Int = BATAS_SEGMEN_JALUR_ARSIP_BAWAAN
    ): HasilValidasiPathArsip {
        if ('\u0000' in jalurMentah) {
            return HasilValidasiPathArsip.TidakSah(
                jalurMentah,
                "Jalur arsip mengandung karakter NUL ilegal"
            )
        }

        val digantiSlash = jalurMentah.replace('\\', '/')
        if (digantiSlash.startsWith("/")) {
            return HasilValidasiPathArsip.TidakSah(
                jalurMentah,
                "Zip Slip dicegah: jalur absolut berawalan '/' tidak diizinkan"
            )
        }
        if (digantiSlash.length >= 2 && digantiSlash[0].isLetter() && digantiSlash[1] == ':') {
            return HasilValidasiPathArsip.TidakSah(
                jalurMentah,
                "Zip Slip dicegah: jalur mengandung awalan drive absolut"
            )
        }

        var bersih = digantiSlash.trimEnd('/')
        if (bersih.isEmpty()) {
            return if (adalahFolder && (digantiSlash == "." || digantiSlash == "./")) {
                HasilValidasiPathArsip.AbaikanRoot
            } else {
                HasilValidasiPathArsip.TidakSah(jalurMentah, "Jalur entri arsip kosong")
            }
        }
        if (bersih == ".") {
            return HasilValidasiPathArsip.AbaikanRoot
        }

        while (bersih.startsWith("./")) {
            bersih = bersih.removePrefix("./")
        }
        if (bersih.isEmpty() || bersih == ".") {
            return HasilValidasiPathArsip.AbaikanRoot
        }

        val bagian = bersih.split('/')
        val segmenSah = ArrayList<String>(bagian.size)
        for (segmen in bagian) {
            if (segmen.isEmpty()) {
                return HasilValidasiPathArsip.TidakSah(
                    jalurMentah,
                    "Jalur arsip mengandung pemisah '//' atau segmen kosong"
                )
            }
            if (segmen == ".." || segmen == ".") {
                return HasilValidasiPathArsip.TidakSah(
                    jalurMentah,
                    "Zip Slip dicegah: segmen '$segmen' tidak diizinkan dalam jalur arsip"
                )
            }
            if (!ValidasiNama.sah(segmen)) {
                return HasilValidasiPathArsip.TidakSah(
                    jalurMentah,
                    "Segmen nama '$segmen' dalam arsip tidak sah"
                )
            }
            if (segmen.encodeToByteArray().size > BATAS_BYTE_SEGMEN_ARSIP) {
                return HasilValidasiPathArsip.TidakSah(
                    jalurMentah,
                    "Segmen nama '$segmen' melebihi batas $BATAS_BYTE_SEGMEN_ARSIP byte"
                )
            }
            segmenSah.add(segmen)
        }

        if (segmenSah.size > batasSegmenMaks) {
            return HasilValidasiPathArsip.TidakSah(
                jalurMentah,
                "Kedalaman jalur arsip melebihi batas maksimal ($batasSegmenMaks segmen)"
            )
        }

        return HasilValidasiPathArsip.Sah(
            jalurNormal = segmenSah.joinToString("/"),
            daftarSegmen = segmenSah,
            namaSegmenAkhir = segmenSah.last()
        )
    }

    fun apakahCocokFilterEkstrak(
        jalurNormal: String,
        jalurTerpilih: Set<String>?
    ): Boolean {
        if (jalurTerpilih == null) return true
        for (target in jalurTerpilih) {
            val t = target.trim('/')
            if (t.isEmpty()) continue
            if (jalurNormal == t || jalurNormal.startsWith("$t/")) {
                return true
            }
        }
        return false
    }
}