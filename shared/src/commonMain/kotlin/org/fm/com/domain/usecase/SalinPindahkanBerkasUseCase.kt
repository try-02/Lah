package org.fm.com.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
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
        timpaKonflik: Boolean = false,
        onProgres: (indeks: Int, total: Int, itemSaatIni: LokasiBerkas) -> Unit = { _, _, _ -> }
    ): HasilMassal {
        if (daftarSumber.isEmpty()) return HasilMassal.KOSONG
        val berhasil = mutableListOf<EntitasBerkas>()
        val gagal = mutableListOf<HasilItemGagal>()
        val total = daftarSumber.size
        for ((indeks, sumber) in daftarSumber.withIndex()) {
            currentCoroutineContext().ensureActive()
            onProgres(indeks + 1, total, sumber)
            val hasil = if (sumber.jenis == tujuanInduk.jenis) {
                val permintaan = PermintaanOperasi(
                    sumber = sumber,
                    tujuan = tujuanInduk,
                    konfirmasi = timpaKonflik
                )
                val hasilNative = if (apakahPindah) {
                    repositoriOperasi.pindah(permintaan)
                } else {
                    repositoriOperasi.salin(permintaan)
                }
                if (hasilNative is HasilOperasi.Gagal && hasilNative.penyebab == PenyebabGalat.TIDAK_DIDUKUNG) {
                    salinViaStreaming(sumber, tujuanInduk, apakahPindah, timpaKonflik)
                } else {
                    hasilNative
                }
            } else {
                salinViaStreaming(sumber, tujuanInduk, apakahPindah, timpaKonflik)
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
        apakahPindah: Boolean,
        timpaKonflik: Boolean,
        namaEksplisit: String? = null,
        folderTerbuatBaru: MutableSet<String> = mutableSetOf(),
        dikunjungi: MutableSet<String> = mutableSetOf()
    ): HasilOperasi<EntitasBerkas> {
        currentCoroutineContext().ensureActive()
        val kunciKunjungan = "${sumber.jenis.name}:${sumber.pengenal}"
        if (!dikunjungi.add(kunciKunjungan)) {
            return HasilOperasi.Gagal("Terdeteksi siklus folder saat menyalin: ${sumber.pengenal}", PenyebabGalat.VALIDASI)
        }
        val namaTarget = namaEksplisit
            ?: sumber.label?.takeIf { it.isNotBlank() }
            ?: sumber.pengenal.trimEnd('/').substringAfterLast('/')
        if (!ValidasiNama.sah(namaTarget)) {
            return HasilOperasi.Gagal("Nama sumber tidak valid untuk disalin: $namaTarget", PenyebabGalat.VALIDASI)
        }

        val readerHasil = repositoriBerkas.bukaUntukBaca(sumber)
        if (readerHasil is HasilOperasi.Gagal) {
            if (readerHasil.penyebab == PenyebabGalat.VALIDASI) {
                if (sumber.jenis == tujuanInduk.jenis && sumber.jenis != JenisLokasi.POSIX) {
                    // Backend non-POSIX tidak punya cek awalan path; telusuri pohon sumber sebelum
                    // menulis apa pun agar pemindahan ke turunan sendiri tidak menghapus salinannya.
                    when (val turunan = apakahTurunanFolder(sumber, tujuanInduk)) {
                        is HasilOperasi.Gagal -> return turunan
                        is HasilOperasi.Sukses -> if (turunan.nilai) {
                            return HasilOperasi.Gagal(
                                "Tidak bisa menyalin/memindah folder ke dalam turunannya sendiri",
                                PenyebabGalat.VALIDASI
                            )
                        }
                    }
                }
                return salinFolderViaStreaming(
                    sumberFolder = sumber,
                    namaFolder = namaTarget,
                    tujuanInduk = tujuanInduk,
                    apakahPindah = apakahPindah,
                    timpaKonflik = timpaKonflik,
                    folderTerbuatBaru = folderTerbuatBaru,
                    dikunjungi = dikunjungi
                )
            }
            return readerHasil
        }

        val reader = (readerHasil as HasilOperasi.Sukses).nilai
        val writerHasil = repositoriBerkas.bukaUntukTulisDi(tujuanInduk, namaTarget, timpa = timpaKonflik)
        val writer = when (writerHasil) {
            is HasilOperasi.Sukses -> writerHasil.nilai
            is HasilOperasi.Gagal -> {
                withContext(NonCancellable) { reader.tutup() }
                return writerHasil
            }
        }

        val targetPraTulis = writer.lokasiTarget
        if (targetPraTulis != null && samaLokasi(targetPraTulis, sumber)) {
            withContext(NonCancellable) {
                reader.tutup()
                writer.batalkan()
            }
            return if (apakahPindah) {
                HasilOperasi.Sukses(
                    EntitasBerkas(
                        lokasi = targetPraTulis,
                        nama = targetPraTulis.label ?: namaTarget,
                        jenis = JenisBerkas.LAIN
                    )
                )
            } else {
                HasilOperasi.Gagal(
                    "Berkas sudah berada di folder yang sama: $namaTarget",
                    PenyebabGalat.VALIDASI
                )
            }
        }

        var transferSukses = false
        var gagalTransfer: HasilOperasi.Gagal? = null
        var totalByte = 0L
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                when (val potonganHasil = reader.potongan()) {
                    is SumberIsiBerkas.HasilPotongan.Isi -> {
                        val p = potonganHasil.potongan
                        if (p.data.isNotEmpty()) {
                            val tulisHasil = writer.tulis(p)
                            if (tulisHasil is HasilOperasi.Gagal) {
                                gagalTransfer = tulisHasil
                                break
                            }
                            totalByte += p.data.size
                        }
                        if (p.selesai) break
                    }
                    is SumberIsiBerkas.HasilPotongan.Gagal -> {
                        gagalTransfer = HasilOperasi.Gagal(potonganHasil.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                        break
                    }
                }
            }
            if (gagalTransfer == null) {
                when (val finalHasil = writer.selesai()) {
                    is HasilOperasi.Sukses -> transferSukses = true
                    is HasilOperasi.Gagal -> gagalTransfer = finalHasil
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            gagalTransfer = HasilOperasi.Gagal(e.message ?: "Kegagalan streaming", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                reader.tutup()
                if (!transferSukses) {
                    writer.batalkan()
                }
            }
        }

        if (gagalTransfer != null) {
            return gagalTransfer
        }

        val lokasiHasil = writer.lokasiTarget
            ?: return HasilOperasi.Gagal(
                "Backend tulis tidak mengembalikan lokasi hasil",
                PenyebabGalat.TIDAK_DIKETAHUI
            )

        if (apakahPindah && !samaLokasi(lokasiHasil, sumber)) {
            val hapusHasil = repositoriOperasi.hapus(PermintaanOperasi(sumber = sumber, konfirmasi = true))
            if (hapusHasil is HasilOperasi.Gagal) {
                return HasilOperasi.Gagal(
                    "Berhasil disalin tetapi gagal menghapus sumber: ${hapusHasil.pesan}",
                    hapusHasil.penyebab
                )
            }
        }

        return HasilOperasi.Sukses(
            EntitasBerkas(
                lokasi = lokasiHasil,
                nama = lokasiHasil.label ?: namaTarget,
                jenis = JenisBerkas.LAIN,
                ukuranByte = totalByte
            )
        )
    }

    /**
     * True bila [kandidat] adalah subfolder (pada kedalaman berapa pun) dari [sumberFolder].
     * Hanya membaca; BFS dengan penjaga siklus dan berhenti pada kecocokan pertama.
     */
    private suspend fun apakahTurunanFolder(
        sumberFolder: LokasiBerkas,
        kandidat: LokasiBerkas
    ): HasilOperasi<Boolean> {
        val antrean = ArrayDeque<LokasiBerkas>()
        val dilihat = mutableSetOf("${sumberFolder.jenis.name}:${sumberFolder.pengenal}")
        antrean.addLast(sumberFolder)
        while (antrean.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = antrean.removeFirst()
            val daftarAnak = when (val hasil = repositoriBerkas.daftarIsi(folder).first()) {
                is HasilOperasi.Sukses -> hasil.nilai
                is HasilOperasi.Gagal -> return hasil
            }
            for (anak in daftarAnak) {
                if (anak.jenis != JenisBerkas.FOLDER) continue
                if (samaLokasi(anak.lokasi, kandidat)) return HasilOperasi.Sukses(true)
                if (dilihat.add("${anak.lokasi.jenis.name}:${anak.lokasi.pengenal}")) {
                    antrean.addLast(anak.lokasi)
                }
            }
        }
        return HasilOperasi.Sukses(false)
    }

    private fun samaLokasi(a: LokasiBerkas, b: LokasiBerkas): Boolean =
        a.jenis == b.jenis && a.pengenal == b.pengenal

    private suspend fun salinFolderViaStreaming(
        sumberFolder: LokasiBerkas,
        namaFolder: String,
        tujuanInduk: LokasiBerkas,
        apakahPindah: Boolean,
        timpaKonflik: Boolean,
        folderTerbuatBaru: MutableSet<String>,
        dikunjungi: MutableSet<String>
    ): HasilOperasi<EntitasBerkas> {
        if (samaLokasi(sumberFolder, tujuanInduk)) {
            return HasilOperasi.Gagal(
                "Tidak bisa menyalin/memindah folder ke dalam dirinya sendiri",
                PenyebabGalat.VALIDASI
            )
        }
        if (sumberFolder.jenis == JenisLokasi.POSIX && tujuanInduk.jenis == JenisLokasi.POSIX) {
            val awalanSumber = if (sumberFolder.pengenal.endsWith("/")) {
                sumberFolder.pengenal
            } else {
                "${sumberFolder.pengenal}/"
            }
            if (tujuanInduk.pengenal.startsWith(awalanSumber)) {
                return HasilOperasi.Gagal(
                    "Tidak bisa menyalin/memindah folder ke dalam turunannya sendiri",
                    PenyebabGalat.VALIDASI
                )
            }
        }

        val daftarAnakHasil = repositoriBerkas.daftarIsi(sumberFolder).first()
        val daftarAnak = when (daftarAnakHasil) {
            is HasilOperasi.Sukses -> daftarAnakHasil.nilai
            is HasilOperasi.Gagal -> return daftarAnakHasil
        }

        for (anak in daftarAnak) {
            if (samaLokasi(anak.lokasi, tujuanInduk)) {
                return HasilOperasi.Gagal(
                    "Tidak bisa menyalin/memindah folder ke dalam turunannya sendiri",
                    PenyebabGalat.VALIDASI
                )
            }
        }

        val folderTujuanSudahAda = if (timpaKonflik) {
            val isiTujuan = repositoriBerkas.daftarIsi(tujuanInduk).first()
            (isiTujuan as? HasilOperasi.Sukses)?.nilai?.firstOrNull {
                it.nama.equals(namaFolder, ignoreCase = true)
            }
        } else {
            null
        }

        val entitasFolderTujuan = if (folderTujuanSudahAda != null) {
            if (folderTujuanSudahAda.jenis != JenisBerkas.FOLDER) {
                return HasilOperasi.Gagal(
                    "Tidak dapat menimpa berkas '$namaFolder' dengan folder",
                    PenyebabGalat.VALIDASI
                )
            }
            if (samaLokasi(folderTujuanSudahAda.lokasi, sumberFolder)) {
                return if (apakahPindah) {
                    HasilOperasi.Sukses(folderTujuanSudahAda)
                } else {
                    HasilOperasi.Gagal(
                        "Folder sudah berada di lokasi yang sama: $namaFolder",
                        PenyebabGalat.VALIDASI
                    )
                }
            }
            folderTujuanSudahAda
        } else {
            val buatFolderHasil = repositoriOperasi.buatFolder(
                PermintaanOperasi(sumber = tujuanInduk, namaBaru = namaFolder, konfirmasi = false)
            )
            when (buatFolderHasil) {
                is HasilOperasi.Sukses -> buatFolderHasil.nilai
                is HasilOperasi.Gagal -> return buatFolderHasil
            }
        }

        folderTerbuatBaru.add("${entitasFolderTujuan.lokasi.jenis.name}:${entitasFolderTujuan.lokasi.pengenal}")

        for (anak in daftarAnak) {
            currentCoroutineContext().ensureActive()
            val kunciAnak = "${anak.lokasi.jenis.name}:${anak.lokasi.pengenal}"
            if (kunciAnak in folderTerbuatBaru) {
                continue
            }
            if (anak.jenis == JenisBerkas.TAUTAN) {
                return HasilOperasi.Gagal(
                    "Salin streaming tidak mendukung tautan simbolik: ${anak.nama}",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }
            val hasilAnak = if (anak.jenis == JenisBerkas.FOLDER) {
                salinFolderViaStreaming(
                    sumberFolder = anak.lokasi,
                    namaFolder = anak.nama,
                    tujuanInduk = entitasFolderTujuan.lokasi,
                    apakahPindah = false,
                    timpaKonflik = timpaKonflik,
                    folderTerbuatBaru = folderTerbuatBaru,
                    dikunjungi = dikunjungi
                )
            } else {
                salinViaStreaming(
                    sumber = anak.lokasi,
                    tujuanInduk = entitasFolderTujuan.lokasi,
                    apakahPindah = false,
                    timpaKonflik = timpaKonflik,
                    namaEksplisit = anak.nama,
                    folderTerbuatBaru = folderTerbuatBaru,
                    dikunjungi = dikunjungi
                )
            }
            if (hasilAnak is HasilOperasi.Gagal) {
                return hasilAnak
            }
        }

        if (apakahPindah && !samaLokasi(entitasFolderTujuan.lokasi, sumberFolder)) {
            val hapusSumber = repositoriOperasi.hapus(
                PermintaanOperasi(sumber = sumberFolder, konfirmasi = true)
            )
            if (hapusSumber is HasilOperasi.Gagal) {
                return HasilOperasi.Gagal(
                    "Folder berhasil disalin tetapi gagal menghapus sumber: ${hapusSumber.pesan}",
                    hapusSumber.penyebab
                )
            }
        }

        return HasilOperasi.Sukses(entitasFolderTujuan)
    }
}