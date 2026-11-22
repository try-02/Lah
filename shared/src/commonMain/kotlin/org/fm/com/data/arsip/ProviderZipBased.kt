package org.fm.com.data.arsip

import org.fm.com.domain.arsip.ArsipProvider
import org.fm.com.domain.arsip.KapabilitasArsip
import org.fm.com.domain.arsip.MatriksArsip
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip

class ProviderZipBased(
    private val providerZip: ProviderZip
) : ArsipProvider {

    override val formatDidukung: Set<FormatArsip> = setOf(FormatArsip.JAR, FormatArsip.ZIP_LAIN)

    override fun kapabilitas(format: FormatArsip): KapabilitasArsip? =
        if (format in formatDidukung) MatriksArsip.untuk(format) else null

    override suspend fun daftarEntri(
        sumber: LokasiBerkas,
        format: FormatArsip,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<RingkasanIsiArsip> {
        if (format !in formatDidukung) {
            return HasilOperasi.Gagal("Format tidak didukung provider ZIP-based: ${format.namaTampilan}", PenyebabGalat.TIDAK_DIDUKUNG)
        }
        return providerZip.daftarEntri(sumber, format, kataSandi, batas)
    }

    override suspend fun bukaEntriUntukBaca(
        sumber: LokasiBerkas,
        format: FormatArsip,
        jalurInternal: String,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<SumberIsiBerkas> {
        if (format !in formatDidukung) {
            return HasilOperasi.Gagal("Format tidak didukung provider ZIP-based: ${format.namaTampilan}", PenyebabGalat.TIDAK_DIDUKUNG)
        }
        return providerZip.bukaEntriUntukBaca(sumber, format, jalurInternal, kataSandi, batas)
    }

    override suspend fun ekstrak(
        sumber: LokasiBerkas,
        format: FormatArsip,
        folderTujuan: LokasiBerkas,
        opsi: OpsiEkstrakArsip,
        onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit
    ): HasilOperasi<HasilEkstrakArsip> {
        if (format !in formatDidukung) {
            return HasilOperasi.Gagal("Format tidak didukung provider ZIP-based: ${format.namaTampilan}", PenyebabGalat.TIDAK_DIDUKUNG)
        }
        return providerZip.ekstrak(sumber, format, folderTujuan, opsi, onProgres)
    }

    override suspend fun buatArsip(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean,
        kataSandi: String?,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit
    ): HasilOperasi<EntitasBerkas> {
        val kap = kapabilitas(format)
        if (kap == null || !kap.dapatBuat) {
            return HasilOperasi.Gagal(
                kap?.catatan ?: "Pembuatan arsip untuk format ${format.namaTampilan} tidak didukung",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        return providerZip.buatArsip(
            daftarSumber = daftarSumber,
            tujuanInduk = tujuanInduk,
            namaArsip = namaArsip,
            format = format,
            konfirmasiTimpa = konfirmasiTimpa,
            kataSandi = kataSandi,
            onProgres = onProgres
        )
    }
}