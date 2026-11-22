package org.fm.com.data.arsip

import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source

data class MetaEntriZipMentah(
    val jalurMentah: String,
    val adalahFolder: Boolean,
    val ukuranByte: Long?,
    val waktuUbahEpochMs: Long?
)

interface ArsipZipTerbuka {
    fun daftarJalurRekursif(): List<MetaEntriZipMentah>
    fun bukaSumberEntri(jalurDalamZip: String): Source
    fun tutup()
}

interface PenulisZipStreaming {
    fun mulaiEntri(jalurInternal: String, adalahFolder: Boolean, waktuUbahEpochMs: Long?)
    fun tulisPotongan(data: ByteArray, offset: Int, panjang: Int)
    fun tutupEntri()
    fun selesaikanDanTutup()
    fun batalkanDanTutup()
}

interface AdapterArsipPlatform {
    val sistemBerkasLokal: FileSystem

    fun bukaZipDariPath(fileSystem: FileSystem, pathZip: Path): ArsipZipTerbuka
    fun bukaPenulisZip(fileSystem: FileSystem, pathZipTujuan: Path): PenulisZipStreaming
    fun bukaSumberGzip(sumberTerkompresi: Source): Source
    fun bukaSinkGzip(sinkTujuan: Sink): Sink
}

internal expect fun buatAdapterArsipPlatform(): AdapterArsipPlatform
