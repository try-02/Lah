package org.fm.com.domain.model

import kotlin.coroutines.cancellation.CancellationException

/**
Hasil operasi tanpa exception lintas layer. `Gagal` memuat alasan yang siap
ditampilkan; kegagalan tidak pernah dilaporkan sebagai sukses.

Catatan: SecurityException (Android) / NSError permission (iOS) dipetakan ke
[PenyebabGalat.IZIN_DITOLAK] oleh adapter platform (androidMain/iosMain, T9–T12),
bukan di sini.
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
        } catch (e: NoSuchElementException) {
            Gagal(e.message ?: "Entitas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: Exception) {
            Gagal(e.message ?: "Operasi gagal", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        suspend fun <T> dariSuspend(blok: suspend () -> T): HasilOperasi<T> = try {
            Sukses(blok())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            Gagal(e.message ?: "Input tidak valid", PenyebabGalat.VALIDASI)
        } catch (e: NoSuchElementException) {
            Gagal(e.message ?: "Entitas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
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