package org.fm.com.domain.arsip

import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.PenyebabGalat

object MatriksArsip {

    val semuaKapabilitas: Map<FormatArsip, KapabilitasArsip> = mapOf(
        FormatArsip.ZIP to KapabilitasArsip(
            format = FormatArsip.ZIP,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = true,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = true,
            catatan = "Baca, ekstrak, dan buat ZIP standar; edit isi dalam arsip dan enkripsi dijadwalkan P1"
        ),
        FormatArsip.TAR to KapabilitasArsip(
            format = FormatArsip.TAR,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = true,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = true,
            catatan = "Format TAR standar tidak memiliki enkripsi bawaan"
        ),
        FormatArsip.GZIP to KapabilitasArsip(
            format = FormatArsip.GZIP,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = true,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = false,
            catatan = "Kompresi berkas tunggal; untuk banyak berkas atau folder gunakan TGZ atau ZIP"
        ),
        FormatArsip.TGZ to KapabilitasArsip(
            format = FormatArsip.TGZ,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = true,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = true,
            catatan = "Arsip TAR terkompresi GZIP"
        ),
        FormatArsip.JAR to KapabilitasArsip(
            format = FormatArsip.JAR,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = true,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = true,
            catatan = "Arsip Java berbasis struktur ZIP"
        ),
        FormatArsip.ZIP_LAIN to KapabilitasArsip(
            format = FormatArsip.ZIP_LAIN,
            dapatBaca = true,
            dapatEkstrak = true,
            dapatBuat = false,
            dapatUbahIsi = false,
            dapatEnkripsi = false,
            mendukungBanyakEntri = true,
            catatan = "Format berbasis ZIP (APK, DOCX, EPUB, dll.) hanya mendukung baca & ekstrak aman"
        )
    )

    fun untuk(format: FormatArsip): KapabilitasArsip =
        semuaKapabilitas.getValue(format)

    fun formatYangBisaDibuat(): List<FormatArsip> =
        FormatArsip.entries.filter { untuk(it).dapatBuat }
}

class RegistriArsipProvider(
    providers: List<ArsipProvider>
) {
    private val petaProvider: Map<FormatArsip, ArsipProvider> = buildMap {
        for (provider in providers) {
            for (format in provider.formatDidukung) {
                put(format, provider)
            }
        }
    }

    fun providerUntuk(format: FormatArsip): ArsipProvider? = petaProvider[format]

    fun kapabilitas(format: FormatArsip): KapabilitasArsip =
        petaProvider[format]?.kapabilitas(format) ?: MatriksArsip.untuk(format)

    fun deteksiDanCekDukungan(namaBerkas: String): HasilOperasi<FormatArsip> {
        val format = FormatArsip.dariNamaBerkas(namaBerkas)
            ?: return HasilOperasi.Gagal(
                "Format arsip berkas '$namaBerkas' belum didukung",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        if (providerUntuk(format) == null) {
            return HasilOperasi.Gagal(
                "Penyedia arsip untuk format ${format.namaTampilan} belum terdaftar",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        return HasilOperasi.Sukses(format)
    }
}