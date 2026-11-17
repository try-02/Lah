package org.fm.com.domain.model

/**
 * Hasil operasi tanpa exception lintas layer. `Galat` memuat alasan yang siap
 * ditampilkan; kegagalan tidak pernah dilaporkan sebagai sukses.
 */
sealed interface HasilOperasi<out T> {

    data class Sukses<out T>(val nilai: T) : HasilOperasi<T>

    data class Gagal(
        val pesan: String,
        val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
    ) : HasilOperasi<Nothing>

    companion object {
        fun <T> dari(blok: () -> T): HasilOperasi<T> = try {
            Sukses(blok())
        } catch (e: IllegalArgumentException) {
            Gagal(e.message ?: "Input tidak valid", PenyebabGalat.VALIDASI)
        } catch (e: Exception) {
            Gagal(e.message ?: "Operasi gagal", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
}

enum class PenyebabGalat {
    VALIDASI,
    IZIN_DITOLAK,
    TIDAK_DITEMUKAN,
    KONFLIK,
    TIDAK_DIDUKUNG,
    TIDAK_DIKETAHUI
}
