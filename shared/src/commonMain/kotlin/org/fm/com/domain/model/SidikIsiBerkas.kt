package org.fm.com.domain.model

data class SidikIsiBerkas(
    val hashFnv64: Long,
    val ukuranByte: Long
)

object PenghitungSidikIsi {
    private val OFFSET_BASIS: Long = 0xcbf29ce484222325uL.toLong()
    private val FNV_PRIME: Long = 0x100000001b3uL.toLong()

    fun dariBytes(bytes: ByteArray): SidikIsiBerkas {
        val ak = Akumulator()
        ak.perbarui(bytes)
        return ak.hasil()
    }

    class Akumulator {
        private var hash: Long = OFFSET_BASIS
        private var totalByte: Long = 0L

        fun perbarui(data: ByteArray, offset: Int = 0, panjang: Int = data.size - offset) {
            if (panjang <= 0) return
            var h = hash
            val batas = offset + panjang
            for (i in offset until batas) {
                h = h xor (data[i].toLong() and 0xFFL)
                h *= FNV_PRIME
            }
            hash = h
            totalByte += panjang
        }

        val ukuranSaatIni: Long
            get() = totalByte

        fun hasil(): SidikIsiBerkas = SidikIsiBerkas(hashFnv64 = hash, ukuranByte = totalByte)
    }
}