@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package org.fm.com.data.arsip

import kotlin.time.Instant
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import okio.Buffer
import okio.BufferedSink
import okio.EOFException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import okio.openZip
import platform.posix.memset
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

private const val UKURAN_BUFFER_ZLIB = 8192
private const val WINDOW_BITS_GZIP = 31
private const val WINDOW_BITS_RAW_DEFLATE = -15
private const val BATAS_ZIP_32_BIT = 0xFFFFFFFFL

internal actual fun buatAdapterArsipPlatform(): AdapterArsipPlatform = AdapterArsipIos()

private class AdapterArsipIos : AdapterArsipPlatform {
    override val sistemBerkasLokal: FileSystem
        get() = FileSystem.SYSTEM

    override fun bukaZipDariPath(fileSystem: FileSystem, pathZip: Path): ArsipZipTerbuka {
        val zipFs = fileSystem.openZip(pathZip)
        return OkioZipTerbukaIos(zipFs)
    }

    override fun bukaPenulisZip(fileSystem: FileSystem, pathZipTujuan: Path): PenulisZipStreaming {
        val sink = fileSystem.sink(pathZipTujuan, mustCreate = false).buffer()
        return PenulisZipIos(fileSystem, pathZipTujuan, sink)
    }

    override fun bukaSumberGzip(sumberTerkompresi: Source): Source =
        ZlibInflateSource(sumberTerkompresi, WINDOW_BITS_GZIP)

    override fun bukaSinkGzip(sinkTujuan: Sink): Sink =
        ZlibDeflateSink(sinkTujuan, Z_DEFAULT_COMPRESSION, WINDOW_BITS_GZIP)
}

private class OkioZipTerbukaIos(
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

private fun galatZlib(kode: Int, stream: z_stream): IOException =
    IOException("zlib ($kode): ${stream.msg?.toKString() ?: "aliran terkompresi tidak valid"}")

private class ZlibInflateSource(
    private val source: Source,
    windowBits: Int
) : Source {
    private val stream = nativeHeap.alloc<z_stream>()
    private val input = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
    private val output = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
    private val tempInBuffer = Buffer()
    private var tertutup = false
    private var selesai = false
    private var sumberHabis = false

    init {
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val kode = inflateInit2_(
            stream.ptr,
            windowBits,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().toInt()
        )
        if (kode != Z_OK) {
            val err = galatZlib(kode, stream)
            nativeHeap.free(input)
            nativeHeap.free(output)
            nativeHeap.free(stream.ptr)
            throw err
        }
    }

    override fun timeout(): Timeout = source.timeout()

    override fun read(sink: Buffer, byteCount: Long): Long {
        check(!tertutup) { "Sumber GZIP sudah ditutup" }
        require(byteCount >= 0L) { "byteCount tidak boleh negatif: $byteCount" }
        if (byteCount == 0L) return 0L
        if (selesai) return -1L

        while (true) {
            if (stream.avail_in == 0u && !sumberHabis) {
                val dibaca = source.read(tempInBuffer, UKURAN_BUFFER_ZLIB.toLong())
                if (dibaca == -1L) {
                    sumberHabis = true
                } else {
                    if (dibaca == 0L) throw IOException("Aliran sumber terkompresi tidak memberi data")
                    val bytes = tempInBuffer.readByteArray(dibaca)
                    for (i in bytes.indices) {
                        input[i] = bytes[i].toUByte()
                    }
                    stream.next_in = input
                    stream.avail_in = dibaca.toInt().toUInt()
                }
            }

            val kapasitas = minOf(byteCount, UKURAN_BUFFER_ZLIB.toLong()).toInt().toUInt()
            stream.next_out = output
            stream.avail_out = kapasitas

            val sebelumIn = stream.avail_in
            val kode = inflate(stream.ptr, Z_NO_FLUSH)
            val tertulis = (kapasitas - stream.avail_out).toInt()

            if (kode != Z_OK && kode != Z_STREAM_END && kode != Z_BUF_ERROR) {
                throw galatZlib(kode, stream)
            }

            if (tertulis > 0) {
                val outBytes = ByteArray(tertulis) { idx -> output[idx].toByte() }
                sink.write(outBytes)
            }

            if (kode == Z_STREAM_END) {
                selesai = true
            }

            if (tertulis > 0) return tertulis.toLong()
            if (selesai) return -1L
            if (sumberHabis && stream.avail_in == 0u) {
                throw EOFException("Aliran terkompresi GZIP terpotong sebelum penutup")
            }
            if (sebelumIn == stream.avail_in && sebelumIn > 0u) {
                throw IOException("Aliran terkompresi GZIP rusak")
            }
        }
    }

    override fun close() {
        if (tertutup) return
        tertutup = true
        try {
            source.close()
        } finally {
            inflateEnd(stream.ptr)
            nativeHeap.free(input)
            nativeHeap.free(output)
            nativeHeap.free(stream.ptr)
        }
    }
}

private class ZlibDeflateSink(
    private val sink: Sink,
    level: Int,
    windowBits: Int
) : Sink {
    private val stream = nativeHeap.alloc<z_stream>()
    private val input = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
    private val output = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
    private val tempOutBuffer = Buffer()
    private var tertutup = false

    init {
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val kode = deflateInit2_(
            stream.ptr,
            level,
            Z_DEFLATED,
            windowBits,
            8,
            Z_DEFAULT_STRATEGY,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().toInt()
        )
        if (kode != Z_OK) {
            val err = galatZlib(kode, stream)
            nativeHeap.free(input)
            nativeHeap.free(output)
            nativeHeap.free(stream.ptr)
            throw err
        }
    }

    override fun timeout(): Timeout = sink.timeout()

    private fun kirimDeflate(flush: Int): Int {
        stream.next_out = output
        stream.avail_out = UKURAN_BUFFER_ZLIB.toUInt()
        val kode = deflate(stream.ptr, flush)
        if (kode != Z_OK && kode != Z_STREAM_END && kode != Z_BUF_ERROR) {
            throw galatZlib(kode, stream)
        }
        val jumlah = UKURAN_BUFFER_ZLIB - stream.avail_out.toInt()
        if (jumlah > 0) {
            val outBytes = ByteArray(jumlah) { idx -> output[idx].toByte() }
            tempOutBuffer.write(outBytes)
            sink.write(tempOutBuffer, jumlah.toLong())
        }
        return kode
    }

    override fun write(source: Buffer, byteCount: Long) {
        check(!tertutup) { "Sink GZIP sudah ditutup" }
        require(byteCount >= 0L) { "byteCount tidak boleh negatif: $byteCount" }
        if (source.size < byteCount) throw EOFException("Sumber byte kurang dari permintaan")

        var sisa = byteCount
        while (sisa > 0L) {
            val jumlah = minOf(sisa, UKURAN_BUFFER_ZLIB.toLong()).toInt()
            val bytes = source.readByteArray(jumlah.toLong())
            for (i in 0 until jumlah) {
                input[i] = bytes[i].toUByte()
            }
            stream.next_in = input
            stream.avail_in = jumlah.toUInt()
            do {
                kirimDeflate(Z_NO_FLUSH)
            } while (stream.avail_in > 0u)
            sisa -= jumlah
        }
    }

    override fun flush() {
        check(!tertutup) { "Sink GZIP sudah ditutup" }
        do {
            kirimDeflate(Z_SYNC_FLUSH)
        } while (stream.avail_out == 0u)
        sink.flush()
    }

    override fun close() {
        if (tertutup) return
        tertutup = true
        try {
            while (kirimDeflate(Z_FINISH) != Z_STREAM_END) {
                // Kuras seluruh sisa blok DEFLATE hingga penutup GZIP selesai ditulis.
            }
        } finally {
            deflateEnd(stream.ptr)
            nativeHeap.free(input)
            nativeHeap.free(output)
            nativeHeap.free(stream.ptr)
            sink.close()
        }
    }
}

private class PenulisZipIos(
    private val fileSystem: FileSystem,
    private val pathZipTujuan: Path,
    private val sink: BufferedSink
) : PenulisZipStreaming {

    private data class EntriCd(
        val offsetHeaderLokal: Long,
        val metode: Int,
        val flags: Int,
        val dosTime: Int,
        val dosDate: Int,
        val crc32: UInt,
        val ukuranTerkompresi: Long,
        val ukuranAsli: Long,
        val namaBytes: ByteArray,
        val adalahFolder: Boolean
    )

    private val daftarCd = mutableListOf<EntriCd>()
    private var posisiByte: Long = 0L
    private var ditutup = false

    private var entriAktif = false
    private var entriOffsetLokal = 0L
    private var entriFolder = false
    private var entriMetode = 8
    private var entriFlags = 0x0808
    private var entriDosTime = 0
    private var entriDosDate = 0x0021
    private var entriNamaBytes = ByteArray(0)
    private var entriUkuranAsli = 0L
    private var entriUkuranKompres = 0L
    private var entriCrc = Crc32Ieee()

    private var streamAktif: z_stream? = null
    private var bufIn: CPointer<UByteVar>? = null
    private var bufOut: CPointer<UByteVar>? = null

    override fun mulaiEntri(jalurInternal: String, adalahFolder: Boolean, waktuUbahEpochMs: Long?) {
        check(!ditutup) { "Penulis ZIP sudah ditutup" }
        check(!entriAktif) { "Entri sebelumnya belum ditutup" }

        val bersih = jalurInternal.trimStart('/')
        val namaFinal = if (adalahFolder && !bersih.endsWith('/')) {
            "$bersih/"
        } else {
            bersih.trimEnd('/')
        }
        val namaBytes = namaFinal.encodeToByteArray()
        val (dosTime, dosDate) = keDosWaktuTanggal(waktuUbahEpochMs)

        entriOffsetLokal = posisiByte
        entriFolder = adalahFolder
        entriMetode = if (adalahFolder) 0 else 8
        entriFlags = if (adalahFolder) 0x0800 else 0x0808
        entriDosTime = dosTime
        entriDosDate = dosDate
        entriNamaBytes = namaBytes
        entriUkuranAsli = 0L
        entriUkuranKompres = 0L
        entriCrc = Crc32Ieee()

        // Local File Header (30 byte + nama)
        tulisIntLe(0x04034B50)
        tulisShortLe(20)
        tulisShortLe(entriFlags)
        tulisShortLe(entriMetode)
        tulisShortLe(entriDosTime)
        tulisShortLe(entriDosDate)
        tulisIntLe(0) // CRC-32 ditulis di Data Descriptor bila non-folder
        tulisIntLe(0) // compressed size
        tulisIntLe(0) // uncompressed size
        tulisShortLe(namaBytes.size)
        tulisShortLe(0) // extra length
        tulisBytes(namaBytes, 0, namaBytes.size)

        if (!adalahFolder) {
            siapkanDeflaterEntri()
        }
        entriAktif = true
    }

    override fun tulisPotongan(data: ByteArray, offset: Int, panjang: Int) {
        if (panjang <= 0) return
        check(entriAktif && !entriFolder) { "Tidak dapat menulis isi saat tidak ada entri berkas aktif" }

        entriCrc.perbarui(data, offset, panjang)
        entriUkuranAsli += panjang
        if (entriUkuranAsli > BATAS_ZIP_32_BIT) {
            throw IOException("Ukuran entri ZIP melebihi batas format 32-bit (4 GiB)")
        }

        val strm = checkNotNull(streamAktif)
        val input = checkNotNull(bufIn)

        var pos = offset
        var sisa = panjang
        while (sisa > 0) {
            val blok = minOf(sisa, UKURAN_BUFFER_ZLIB)
            for (i in 0 until blok) {
                input[i] = data[pos + i].toUByte()
            }
            strm.next_in = input
            strm.avail_in = blok.toUInt()
            do {
                kurasDeflaterEntri(strm, Z_NO_FLUSH)
            } while (strm.avail_in > 0u)
            pos += blok
            sisa -= blok
        }
    }

    override fun tutupEntri() {
        if (!entriAktif) return
        try {
            if (!entriFolder) {
                val strm = checkNotNull(streamAktif)
                while (kurasDeflaterEntri(strm, Z_FINISH) != Z_STREAM_END) {
                    // Kuras hingga akhir blok DEFLATE.
                }
                val crcNilai = entriCrc.nilai()
                // Data Descriptor (16 byte)
                tulisIntLe(0x08074B50)
                tulisIntLe(crcNilai.toInt())
                tulisIntLe(entriUkuranKompres.toInt())
                tulisIntLe(entriUkuranAsli.toInt())
                daftarCd.add(
                    EntriCd(
                        offsetHeaderLokal = entriOffsetLokal,
                        metode = entriMetode,
                        flags = entriFlags,
                        dosTime = entriDosTime,
                        dosDate = entriDosDate,
                        crc32 = crcNilai,
                        ukuranTerkompresi = entriUkuranKompres,
                        ukuranAsli = entriUkuranAsli,
                        namaBytes = entriNamaBytes,
                        adalahFolder = false
                    )
                )
            } else {
                daftarCd.add(
                    EntriCd(
                        offsetHeaderLokal = entriOffsetLokal,
                        metode = 0,
                        flags = entriFlags,
                        dosTime = entriDosTime,
                        dosDate = entriDosDate,
                        crc32 = 0u,
                        ukuranTerkompresi = 0L,
                        ukuranAsli = 0L,
                        namaBytes = entriNamaBytes,
                        adalahFolder = true
                    )
                )
            }
        } finally {
            bebaskanDeflaterEntri()
            entriAktif = false
        }
    }

    override fun selesaikanDanTutup() {
        if (ditutup) return
        if (entriAktif) tutupEntri()

        val cdOffset = posisiByte
        if (cdOffset > BATAS_ZIP_32_BIT || daftarCd.size > 0xFFFF) {
            throw IOException("Jumlah entri atau ukuran arsip melebihi batas format ZIP standar")
        }

        for (e in daftarCd) {
            tulisIntLe(0x02014B50)
            tulisShortLe(20) // version made by
            tulisShortLe(20) // version needed
            tulisShortLe(e.flags)
            tulisShortLe(e.metode)
            tulisShortLe(e.dosTime)
            tulisShortLe(e.dosDate)
            tulisIntLe(e.crc32.toInt())
            tulisIntLe(e.ukuranTerkompresi.toInt())
            tulisIntLe(e.ukuranAsli.toInt())
            tulisShortLe(e.namaBytes.size)
            tulisShortLe(0) // extra len
            tulisShortLe(0) // comment len
            tulisShortLe(0) // disk number
            tulisShortLe(0) // internal attrs
            tulisIntLe(if (e.adalahFolder) 0x10 else 0) // external attrs
            tulisIntLe(e.offsetHeaderLokal.toInt())
            tulisBytes(e.namaBytes, 0, e.namaBytes.size)
        }

        val cdSize = posisiByte - cdOffset
        // End of Central Directory (22 byte)
        tulisIntLe(0x06054B50)
        tulisShortLe(0) // disk
        tulisShortLe(0) // cd disk
        tulisShortLe(daftarCd.size)
        tulisShortLe(daftarCd.size)
        tulisIntLe(cdSize.toInt())
        tulisIntLe(cdOffset.toInt())
        tulisShortLe(0) // comment len

        sink.flush()
        sink.close()
        ditutup = true
    }

    override fun batalkanDanTutup() {
        ditutup = true
        entriAktif = false
        bebaskanDeflaterEntri()
        runCatching { sink.close() }
        runCatching { fileSystem.delete(pathZipTujuan, mustExist = false) }
    }

    private fun siapkanDeflaterEntri() {
        val strm = nativeHeap.alloc<z_stream>()
        val input = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
        val output = nativeHeap.allocArray<UByteVar>(UKURAN_BUFFER_ZLIB)
        memset(strm.ptr, 0, sizeOf<z_stream>().convert())
        val kode = deflateInit2_(
            strm.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            WINDOW_BITS_RAW_DEFLATE,
            8,
            Z_DEFAULT_STRATEGY,
            zlibVersion()?.toKString(),
            sizeOf<z_stream>().toInt()
        )
        if (kode != Z_OK) {
            val err = galatZlib(kode, strm)
            nativeHeap.free(input)
            nativeHeap.free(output)
            nativeHeap.free(strm.ptr)
            throw err
        }
        streamAktif = strm
        bufIn = input
        bufOut = output
    }

    private fun kurasDeflaterEntri(strm: z_stream, flush: Int): Int {
        val output = checkNotNull(bufOut)
        strm.next_out = output
        strm.avail_out = UKURAN_BUFFER_ZLIB.toUInt()
        val kode = deflate(strm.ptr, flush)
        if (kode != Z_OK && kode != Z_STREAM_END && kode != Z_BUF_ERROR) {
            throw galatZlib(kode, strm)
        }
        val keluar = UKURAN_BUFFER_ZLIB - strm.avail_out.toInt()
        if (keluar > 0) {
            val bytes = ByteArray(keluar) { idx -> output[idx].toByte() }
            tulisBytes(bytes, 0, keluar)
            entriUkuranKompres += keluar
            if (entriUkuranKompres > BATAS_ZIP_32_BIT) {
                throw IOException("Ukuran terkompresi melebihi batas ZIP 32-bit")
            }
        }
        return kode
    }

    private fun bebaskanDeflaterEntri() {
        streamAktif?.let { strm ->
            runCatching { deflateEnd(strm.ptr) }
            nativeHeap.free(strm.ptr)
        }
        streamAktif = null
        bufIn?.let { nativeHeap.free(it) }
        bufIn = null
        bufOut?.let { nativeHeap.free(it) }
        bufOut = null
    }

    private fun tulisBytes(bytes: ByteArray, offset: Int, len: Int) {
        if (len <= 0) return
        sink.write(bytes, offset, len)
        posisiByte += len
    }

    private fun tulisShortLe(v: Int) {
        sink.writeShortLe(v)
        posisiByte += 2L
    }

    private fun tulisIntLe(v: Int) {
        sink.writeIntLe(v)
        posisiByte += 4L
    }

    private fun keDosWaktuTanggal(epochMs: Long?): Pair<Int, Int> {
        if (epochMs == null || epochMs <= 0L) return 0 to 0x0021
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)
        if (dt.year < 1980) return 0 to 0x0021
        val tahun = (dt.year - 1980).coerceIn(0, 127)
        val dosDate = (tahun shl 9) or (dt.month.number shl 5) or dt.day
        val dosTime = (dt.hour shl 11) or (dt.minute shl 5) or (dt.second shr 1)
        return dosTime to dosDate
    }
}

private class Crc32Ieee {
    private var nilaiCrc: UInt = 0xFFFFFFFFu

    fun perbarui(data: ByteArray, offset: Int, panjang: Int) {
        var c = nilaiCrc
        val akhir = offset + panjang
        for (i in offset until akhir) {
            val idx = ((c xor (data[i].toInt().toUInt() and 0xFFu)) and 0xFFu).toInt()
            c = (c shr 8) xor TABEL_CRC[idx]
        }
        nilaiCrc = c
    }

    fun nilai(): UInt = nilaiCrc xor 0xFFFFFFFFu

    private companion object {
        val TABEL_CRC: UIntArray = UIntArray(256) { n ->
            var c = n.toUInt()
            repeat(8) {
                c = if ((c and 1u) != 0u) {
                    (c shr 1) xor 0xEDB88320u
                } else {
                    c shr 1
                }
            }
            c
        }
    }
}