package org.fm.com.domain.arsip

import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.RingkasanIsiArsip

data class KapabilitasArsip(
    val format: FormatArsip,
    val dapatBaca: Boolean,
    val dapatEkstrak: Boolean,
    val dapatBuat: Boolean,
    val dapatUbahIsi: Boolean,
    val dapatEnkripsi: Boolean,
    val mendukungBanyakEntri: Boolean = format != FormatArsip.GZIP,
    val catatan: String? = null
)

interface ArsipProvider {
    val formatDidukung: Set<FormatArsip>

    fun kapabilitas(format: FormatArsip): KapabilitasArsip?

    suspend fun daftarEntri(
        sumber: LokasiBerkas,
        format: FormatArsip,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<RingkasanIsiArsip>

    suspend fun bukaEntriUntukBaca(
        sumber: LokasiBerkas,
        format: FormatArsip,
        jalurInternal: String,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<SumberIsiBerkas>

    suspend fun ekstrak(
        sumber: LokasiBerkas,
        format: FormatArsip,
        folderTujuan: LokasiBerkas,
        opsi: OpsiEkstrakArsip,
        onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<HasilEkstrakArsip>

    suspend fun buatArsip(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean = false,
        kataSandi: String? = null,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit = { _, _, _ -> }
    ): HasilOperasi<EntitasBerkas>
}