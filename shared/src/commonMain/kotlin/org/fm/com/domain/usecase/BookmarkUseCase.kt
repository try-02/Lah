@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.domain.usecase

import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.BookmarkDenganStatus
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.StatusKetersediaanBookmark
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriBookmark

class BookmarkUseCase(
    private val repositoriBookmark: RepositoriBookmark,
    private val repositoriBerkas: RepositoriBerkas,
    private val clock: Clock = Clock.System
) {
    fun amatiDaftarBookmark(): Flow<List<EntitasBookmark>> =
        repositoriBookmark.daftarBookmark()

    suspend fun apakahSudahDibookmark(lokasi: LokasiBerkas): Boolean =
        repositoriBookmark.cariBookmark(lokasi) != null

    suspend fun tambahBookmark(
        lokasi: LokasiBerkas,
        jenisBerkas: JenisBerkas? = null,
        adalahFolder: Boolean = jenisBerkas == JenisBerkas.FOLDER
    ): HasilOperasi<EntitasBookmark> {
        val target = tentukanJenisTarget(lokasi, jenisBerkas, adalahFolder)
        val entitas = EntitasBookmark(
            lokasi = lokasi,
            jenisTarget = target,
            waktuDibuatEpochMs = clock.now().toEpochMilliseconds()
        )
        return when (val simpan = repositoriBookmark.simpanBookmark(entitas)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(entitas)
            is HasilOperasi.Gagal -> simpan
        }
    }

    suspend fun toggleBookmark(
        lokasi: LokasiBerkas,
        jenisBerkas: JenisBerkas? = null,
        adalahFolder: Boolean = jenisBerkas == JenisBerkas.FOLDER
    ): HasilOperasi<Boolean> {
        val ada = repositoriBookmark.cariBookmark(lokasi)
        return if (ada != null) {
            when (val hapus = repositoriBookmark.hapusBookmark(lokasi)) {
                is HasilOperasi.Sukses -> HasilOperasi.Sukses(false)
                is HasilOperasi.Gagal -> hapus
            }
        } else {
            when (val tambah = tambahBookmark(lokasi, jenisBerkas, adalahFolder)) {
                is HasilOperasi.Sukses -> HasilOperasi.Sukses(true)
                is HasilOperasi.Gagal -> tambah
            }
        }
    }

    suspend fun gantiLokasiBookmark(
        bookmarkLama: EntitasBookmark,
        lokasiBaru: LokasiBerkas,
        jenisBerkasBaru: JenisBerkas? = null,
        adalahFolderBaru: Boolean = bookmarkLama.adalahFolder
    ): HasilOperasi<EntitasBookmark> {
        val jenisTargetBaru = tentukanJenisTarget(lokasiBaru, jenisBerkasBaru, adalahFolderBaru)
        val statusBaru = periksaKetersediaanSatu(
            EntitasBookmark(lokasiBaru, jenisTargetBaru, bookmarkLama.waktuDibuatEpochMs)
        )
        if (statusBaru.apakahBermasalah) {
            val pesan = when (statusBaru) {
                is StatusKetersediaanBookmark.IzinDicabut -> statusBaru.pesan
                is StatusKetersediaanBookmark.LokasiHilang -> statusBaru.pesan
                is StatusKetersediaanBookmark.TidakTersedia -> statusBaru.pesan
                else -> "Lokasi pengganti tidak dapat diakses"
            }
            return HasilOperasi.Gagal(pesan, PenyebabGalat.IZIN_DITOLAK)
        }
        val entitasBaru = EntitasBookmark(
            lokasi = lokasiBaru,
            jenisTarget = jenisTargetBaru,
            waktuDibuatEpochMs = clock.now().toEpochMilliseconds()
        )
        return when (val ganti = repositoriBookmark.gantiLokasiBookmark(bookmarkLama.lokasi, entitasBaru)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(entitasBaru)
            is HasilOperasi.Gagal -> ganti
        }
    }

    suspend fun hapusBookmark(lokasi: LokasiBerkas): HasilOperasi<Unit> =
        repositoriBookmark.hapusBookmark(lokasi)

    suspend fun hapusSemua(): HasilOperasi<Unit> =
        repositoriBookmark.hapusSemua()

    suspend fun periksaKetersediaanDaftar(
        daftar: List<EntitasBookmark>
    ): List<BookmarkDenganStatus> = daftar.map { item ->
        BookmarkDenganStatus(
            bookmark = item,
            status = periksaKetersediaanSatu(item)
        )
    }

    suspend fun periksaKetersediaanSatu(bookmark: EntitasBookmark): StatusKetersediaanBookmark {
        return if (bookmark.adalahFolder) {
            when (val hasil = repositoriBerkas.daftarIsi(bookmark.lokasi).first()) {
                is HasilOperasi.Sukses -> StatusKetersediaanBookmark.Tersedia
                is HasilOperasi.Gagal -> petakanGalatKetersediaan(hasil)
            }
        } else {
            when (val hasil = repositoriBerkas.bukaUntukBaca(bookmark.lokasi)) {
                is HasilOperasi.Sukses -> {
                    withContext(NonCancellable) { hasil.nilai.tutup() }
                    StatusKetersediaanBookmark.Tersedia
                }
                is HasilOperasi.Gagal -> petakanGalatKetersediaan(hasil)
            }
        }
    }

    private fun petakanGalatKetersediaan(gagal: HasilOperasi.Gagal): StatusKetersediaanBookmark =
        when (gagal.penyebab) {
            PenyebabGalat.IZIN_DITOLAK -> StatusKetersediaanBookmark.IzinDicabut(gagal.pesan)
            PenyebabGalat.TIDAK_DITEMUKAN -> StatusKetersediaanBookmark.LokasiHilang(gagal.pesan)
            else -> StatusKetersediaanBookmark.TidakTersedia(gagal.pesan)
        }

    companion object {
        fun tentukanJenisTarget(
            lokasi: LokasiBerkas,
            jenisBerkas: JenisBerkas?,
            adalahFolder: Boolean
        ): JenisTargetBookmark {
            if (adalahFolder || jenisBerkas == JenisBerkas.FOLDER) {
                return JenisTargetBookmark.FOLDER
            }
            if (jenisBerkas == JenisBerkas.ARSIP) {
                return JenisTargetBookmark.ARSIP
            }
            val nama = lokasi.label?.takeIf { it.isNotBlank() }
                ?: lokasi.pengenal.trimEnd('/').substringAfterLast('/')
            return if (FormatArsip.dariNamaBerkas(nama) != null) {
                JenisTargetBookmark.ARSIP
            } else {
                JenisTargetBookmark.BERKAS
            }
        }
    }
}