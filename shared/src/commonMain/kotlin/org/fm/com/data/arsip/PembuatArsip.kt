package org.fm.com.data.arsip

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.arsip.RegistriArsipProvider
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.PelaksanaPembuatanArsip

class PembuatArsip(
    private val repositoriBerkas: RepositoriBerkas,
    private val registriArsipProvider: RegistriArsipProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PelaksanaPembuatanArsip {

    override suspend fun buatDanVerifikasi(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean,
        kataSandi: String?,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit
    ): HasilOperasi<EntitasBerkas> = withContext(ioDispatcher) {
        currentCoroutineContext().ensureActive()

        if (!ValidasiNama.sah(namaArsip)) {
            return@withContext HasilOperasi.Gagal("Nama arsip tidak sah: $namaArsip", PenyebabGalat.VALIDASI)
        }
        if (daftarSumber.isEmpty()) {
            return@withContext HasilOperasi.Gagal("Pilih minimal satu item untuk dikompres", PenyebabGalat.VALIDASI)
        }

        val provider = registriArsipProvider.providerUntuk(format)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip untuk format ${format.namaTampilan} belum terdaftar",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val kap = registriArsipProvider.kapabilitas(format)
        if (!kap.dapatBuat) {
            return@withContext HasilOperasi.Gagal(
                kap.catatan ?: "Format ${format.namaTampilan} tidak mendukung pembuatan arsip baru",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        if (!kap.mendukungBanyakEntri && (daftarSumber.size != 1 || daftarSumber.first().adalahFolder)) {
            return@withContext HasilOperasi.Gagal(
                "Format ${format.namaTampilan} hanya mendukung kompresi satu berkas tunggal",
                PenyebabGalat.VALIDASI
            )
        }

        if (!konfirmasiTimpa) {
            when (val isiTujuan = BantuArsipPenyimpanan.muatAnakFolderTerindeks(repositoriBerkas, tujuanInduk)) {
                is HasilOperasi.Sukses -> {
                    val konflik = isiTujuan.nilai[namaArsip.lowercase()]
                    if (konflik != null) {
                        return@withContext HasilOperasi.Gagal(
                            "Berkas arsip '$namaArsip' sudah ada di folder tujuan",
                            PenyebabGalat.KONFLIK
                        )
                    }
                }
                is HasilOperasi.Gagal -> return@withContext isiTujuan
            }
        }

        val hasilBuat = provider.buatArsip(
            daftarSumber = daftarSumber,
            tujuanInduk = tujuanInduk,
            namaArsip = namaArsip,
            format = format,
            konfirmasiTimpa = konfirmasiTimpa,
            kataSandi = kataSandi,
            onProgres = onProgres
        )
        val entitasBaru = when (hasilBuat) {
            is HasilOperasi.Sukses -> hasilBuat.nilai
            is HasilOperasi.Gagal -> return@withContext hasilBuat
        }

        // Verifikasi hasil arsip di tujuan akhir dapat dibuka dan dibaca ulang daftar entrinya.
        val verifikasi = provider.daftarEntri(
            sumber = entitasBaru.lokasi,
            format = format,
            kataSandi = kataSandi
        )
        when (verifikasi) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(entitasBaru)
            is HasilOperasi.Gagal -> HasilOperasi.Gagal(
                "Arsip '${entitasBaru.nama}' gagal diverifikasi di tujuan: ${verifikasi.pesan}",
                verifikasi.penyebab
            )
        }
    }
}