package org.fm.com.domain.rename

sealed interface TokenPola {
    data class TeksLiteral(val teks: String) : TokenPola {
        init {
            require(teks.isNotEmpty()) { "Teks literal token tidak boleh kosong" }
        }
    }

    data object NamaDasar : TokenPola
    data object Ekstensi : TokenPola

    data class NomorUrut(
        val lebarMin: Int? = null,
        val angkaAwalInline: Int? = null
    ) : TokenPola {
        init {
            require(lebarMin == null || lebarMin >= 1) {
                "lebarMin harus >= 1 jika ditentukan"
            }
            require(angkaAwalInline == null || angkaAwalInline >= 0) {
                "angkaAwalInline tidak boleh negatif"
            }
        }
    }
}

data class OpsiParserPola(
    val izinkanAngkaAwalInline: Boolean = true,
    val batasLebarMaks: Int = 20
) {
    init {
        require(batasLebarMaks >= 1) { "batasLebarMaks harus >= 1" }
    }
}

data class KonfigurasiNomorUrut(
    val angkaAwal: Int = 1,
    val langkah: Int = 1
) {
    init {
        require(angkaAwal >= 0) { "angkaAwal tidak boleh negatif" }
        require(langkah >= 1) { "langkah minimal 1" }
    }
}

sealed interface HasilParsePola {
    data class Sukses(val daftarToken: List<TokenPola>) : HasilParsePola
    data class Gagal(val pesan: String, val indeksKesalahan: Int = -1) : HasilParsePola
}