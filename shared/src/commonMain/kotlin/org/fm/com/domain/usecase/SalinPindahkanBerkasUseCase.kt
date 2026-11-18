package org.fm.com.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.HasilItemGagal
import org.fm.com.domain.repository.HasilMassal
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class SalinPindahkanBerkasUseCase(
    private val repositoriBerkas: RepositoriBerkas,
    private val repositoriOperasi: RepositoriOperasiBerkas
) {
    suspend operator fun invoke(
        daftarSumber: List<LokasiBerkas>,
        tujuanInduk: LokasiBerkas,
        apakahPindah: Boolean,
        onProgres: (indeks: Int, total: Int, itemSaatIni: LokasiBerkas) -> Unit = { _, _, _ -> }
    ): HasilMassal {
        if (daftarSumber.isEmpty()) return HasilMassal.KOSONG

        val berhasil = mutableListOf<EntitasBerkas>()
        val gagal = mutableListOf<HasilItemGagal>()
        val total = daftarSumber.size

        for ((indeks, sumber) in daftarSumber.withIndex()) {
            onProgres(indeks + 1, total, sumber)

            val hasil = if (sumber.jenis == tujuanInduk.jenis) {
                // Coba native operasional via repositori
                val permintaan = PermintaanOperasi(sumber = sumber, tujuan = tujuanInduk, konfirmasi = false)
                val hasilNative = if (apakahPindah) {
                    repositoriOperasi.pindah(permintaan)
                } else {
                    repositoriOperasi.salin(permintaan)
                }

                if (hasilNative is HasilOperasi.Gagal && hasilNative.penyebab == PenyebabGalat.TIDAK_DIDUKUNG) {
                    // Fallback streaming jika adapter lokal tidak mendukung copy/move native
                    salinViaStreaming(sumber, tujuanInduk, apakahPindah)
                } else {
                    hasilNative
                }
            } else {
                // Lintas jenis lokasi (K9): wajib streaming
                salinViaStreaming(sumber, tujuanInduk, apakahPindah)
            }

            when (hasil) {
                is HasilOperasi.Sukses -> berhasil.add(hasil.nilai)
                is HasilOperasi.Gagal -> gagal.add(
                    HasilItemGagal(sumber = sumber, pesan = hasil.pesan, penyebab = hasil.penyebab)
                )
            }
        }

        return HasilMassal(berhasil = berhasil, gagal = gagal)
    }

    private suspend fun salinViaStreaming(
        sumber: LokasiBerkas,
        tujuanInduk: LokasiBerkas,
        apakahPindah: Boolean
    ): HasilOperasi<EntitasBerkas> {
        val namaTarget = sumber.label ?: sumber.pengenal.trimEnd('/').substringAfterLast('/')
        if (namaTarget.isBlank()) {
            return HasilOperasi.Gagal("Pengenal sumber tidak valid", PenyebabGalat.VALIDASI)
        }

        val readerHasil = repositoriBerkas.bukaUntukBaca(sumber)
        val reader = when (readerHasil) {
            is HasilOperasi.Sukses -> readerHasil.nilai
            is HasilOperasi.Gagal -> return readerHasil
        }

        val writerHasil = repositoriBerkas.bukaUntukTulisDi(tujuanInduk, namaTarget, timpa = false)
        val writer = when (writerHasil) {
            is HasilOperasi.Sukses -> writerHasil.nilai
            is HasilOperasi.Gagal -> {
                reader.tutup()
                return writerHasil
            }
        }

        var transferSukses = false
        try {
            while (true) {
                when (val potonganHasil = reader.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        val p = potonganHasil.potongan
                        if (p.data.isNotEmpty()) {
                            val tulisHasil = writer.tulis(p)
                            if (tulisHasil is HasilOperasi.Gagal) {
                                return tulisHasil
                            }
                        }
                        if (p.selesai) break
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> {
                        return HasilOperasi.Gagal(potonganHasil.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                    }
                }
            }
            val finalHasil = writer.selesai()
            if (finalHasil is HasilOperasi.Sukses) {
                transferSukses = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Kegagalan streaming", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            reader.tutup()
            if (!transferSukses) {
                withContext(NonCancellable) {
                    writer.batalkan()
                }
            }
        }

        if (apakahPindah) {
            val hapusHasil = repositoriOperasi.hapus(PermintaanOperasi(sumber = sumber, konfirmasi = true))
            if (hapusHasil is HasilOperasi.Gagal) {
                return HasilOperasi.Gagal("Berhasil disalin tetapi gagal menghapus sumber", hapusHasil.penyebab)
            }
        }

        val entitasTujuan = EntitasBerkas(
            lokasi = LokasiBerkas(
                pengenal = "${tujuanInduk.pengenal.trimEnd('/')}/$namaTarget",
                jenis = tujuanInduk.jenis,
                label = namaTarget
            ),
            nama = namaTarget,
            jenis = org.fm.com.domain.model.JenisBerkas.LAIN
        )
        return HasilOperasi.Sukses(entitasTujuan)
    }
}