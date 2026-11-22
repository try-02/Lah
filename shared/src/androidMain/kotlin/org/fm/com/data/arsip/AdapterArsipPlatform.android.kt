package org.fm.com.data.arsip

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.openZip
import okio.sink
import okio.source

internal actual fun buatAdapterArsipPlatform(): AdapterArsipPlatform = AdapterArsipAndroid()

private class AdapterArsipAndroid : AdapterArsipPlatform {
    override val sistemBerkasLokal: FileSystem
        get() = FileSystem.SYSTEM

    override fun bukaZipDariPath(fileSystem: FileSystem, pathZip: Path): ArsipZipTerbuka {
        val zipFs = fileSystem.openZip(pathZip)
        return OkioZipTerbukaAndroid(zipFs)
    }

    override fun bukaPenulisZip(fileSystem: FileSystem, pathZipTujuan: Path): PenulisZipStreaming {
        val outStream = fileSystem.sink(pathZipTujuan, mustCreate = false).buffer().outputStream()
        return PenulisZipAndroid(fileSystem, pathZipTujuan, ZipOutputStream(outStream))
    }

    override fun bukaSumberGzip(sumberTerkompresi: Source): Source {
        val buffered = sumberTerkompresi.buffer()
        return GZIPInputStream(buffered.inputStream()).source()
    }

    override fun bukaSinkGzip(sinkTujuan: Sink): Sink {
        val buffered = sinkTujuan.buffer()
        return GZIPOutputStream(buffered.outputStream()).sink()
    }
}

private class OkioZipTerbukaAndroid(
    private val zipFs: FileSystem
) : ArsipZipTerbuka {
    override fun daftarJalurRekursif(): List<MetaEntriZipMentah> {
        val akar = "/".toPath()
        return zipFs.listRecursively(akar).mapNotNull { path ->
            val jalur = path.toString().removePrefix("/")
            if (jalur.isEmpty()) return@mapNotNull null
            val meta = zipFs.metadataOrNull(path)
            val adalahFolder = meta?.isDirectory == true
            MetaEntriZipMentah(
                jalurMentah = jalur,
                adalahFolder = adalahFolder,
                ukuranByte = if (adalahFolder) null else meta?.size,
                waktuUbahEpochMs = meta?.lastModifiedAtMillis
            )
        }.toList()
    }

    override fun bukaSumberEntri(jalurDalamZip: String): Source {
        val path = ("/" + jalurDalamZip.trimStart('/')).toPath()
        return zipFs.source(path)
    }

    override fun tutup() {
        runCatching { zipFs.close() }
    }
}

private class PenulisZipAndroid(
    private val fileSystem: FileSystem,
    private val pathZipTujuan: Path,
    private val zipOut: ZipOutputStream
) : PenulisZipStreaming {
    private var entriAktif = false

    override fun mulaiEntri(jalurInternal: String, adalahFolder: Boolean, waktuUbahEpochMs: Long?) {
        val namaBersih = jalurInternal.trimStart('/')
        val namaEntri = if (adalahFolder && !namaBersih.endsWith('/')) {
            "$namaBersih/"
        } else {
            namaBersih.trimEnd('/')
        }
        val entry = ZipEntry(namaEntri).apply {
            if (waktuUbahEpochMs != null && waktuUbahEpochMs > 0L) {
                time = waktuUbahEpochMs
            }
        }
        zipOut.putNextEntry(entry)
        entriAktif = true
    }

    override fun tulisPotongan(data: ByteArray, offset: Int, panjang: Int) {
        if (panjang > 0) {
            zipOut.write(data, offset, panjang)
        }
    }

    override fun tutupEntri() {
        if (entriAktif) {
            zipOut.closeEntry()
            entriAktif = false
        }
    }

    override fun selesaikanDanTutup() {
        if (entriAktif) {
            zipOut.closeEntry()
            entriAktif = false
        }
        zipOut.finish()
        zipOut.close()
    }

    override fun batalkanDanTutup() {
        runCatching {
            if (entriAktif) {
                zipOut.closeEntry()
                entriAktif = false
            }
        }
        runCatching { zipOut.close() }
        runCatching { fileSystem.delete(pathZipTujuan, mustExist = false) }
    }
}