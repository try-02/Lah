package org.fm.com.domain.io

/** Satu potongan isi berkas. Ukuran potongan ditentukan adaptor/platform. */
data class PotonganIsi(val data: ByteArray, val selesai: Boolean = false) {

    override fun equals(other: Any?): Boolean =
        other is PotonganIsi && other.data.contentEquals(data) && other.selesai == selesai

    override fun hashCode(): Int = data.contentHashCode() * 31 + selesai.hashCode()

    companion object {
        val KOSONG = PotonganIsi(ByteArray(0), selesai = true)
    }
}

/**
 * Sumber baca streaming. Pemanggil mengonsumsi [potongan] berulang sampai
 * `selesai = true`. Tidak pernah mengasumsikan berkas muat di memori.
 */
interface SumberIsiBerkas {
    val ukuranByte: Long?
    suspend fun potongan(): HasilPotongan
    suspend fun tutup()

    sealed interface HasilPotongan {
        data class Isi(val potongan: PotonganIsi) : HasilPotongan
        data class Gagal(val pesan: String) : HasilPotongan
    }
}