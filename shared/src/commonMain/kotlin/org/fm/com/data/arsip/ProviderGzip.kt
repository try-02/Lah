package org.fm.com.data.arsip

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer
import okio.use
import org.fm.com.domain.arsip.ArsipProvider
import org.fm.com.domain.arsip.KapabilitasArsip
import org.fm.com.domain.arsip.MatriksArsip
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntriArsip
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.RepositoriBerkas

class ProviderGzip(
    private val repositoriBerkas: RepositoriBerkas,
    private val adapterPlatform: AdapterArsipPlatform = buatAdapterArsipPlatform(),
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ArsipProvider {

    override val formatDidukung: Set<FormatArsip> = setOf(FormatArsip.GZIP)

    override fun kapabilitas(format: FormatArsip): KapabilitasArsip? =
        if (format == FormatArsip.GZIP) MatriksArsip.untuk(FormatArsip.GZIP) else null

    override suspend fun daftarEntri(
        sumber: LokasiBerkas,
        format: FormatArsip,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<RingkasanIsiArsip> = withContext(ioDispatcher) {
        if (format != FormatArsip.GZIP) {
            return@withContext HasilOperasi.Gagal("Format bukan GZIP: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }

        val pathSumber = when (
            val siap = BantuArsipPenyimpanan.siapkanPathLokalUntukBaca(
                sumber = sumber,
                repositoriBerkas = repositoriBerkas,
                fileSystemLokal = adapterPlatform.sistemBerkasLokal,
                direktoriTemp = direktoriTemp
            )
        ) {
            is SumberPathLokal.Tersedia -> siap
            is SumberPathLokal.Gagal -> return@withContext HasilOperasi.Gagal(siap.pesan, siap.penyebab)
        }

        try {
            val infoHeader = bacaHeaderDanValidasiGzip(pathSumber)
                ?: return@withContext HasilOperasi.Gagal(
                    "Berkas bukan arsip GZIP yang sah atau arsip rusak",
                    PenyebabGalat.VALIDASI
                )

            val namaDalam = tentukanNamaEntriTunggal(sumber, infoHeader.namaAsli)
            val ukuranTerkompresi = runCatching {
                pathSumber.fileSystem.metadataOrNull(pathSumber.path)?.size
            }.getOrNull()
            val entri = EntriArsip(
                jalurInternal = namaDalam,
                nama = namaDalam,
                adalahFolder = false,
                ukuranTidakTerkompresiByte = null,
                ukuranTerkompresiByte = ukuranTerkompresi,
                waktuUbahEpochMs = infoHeader.waktuModifikasiEpochMs,
                terenkripsi = false,
                metodeKompresi = "GZIP"
            )
            HasilOperasi.Sukses(
                RingkasanIsiArsip(
                    format = FormatArsip.GZIP,
                    daftarEntri = listOf(entri),
                    totalUkuranTidakTerkompresiByte = 0L,
                    adaUkuranTidakDiketahui = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal membaca metadata GZIP",
                PenyebabGalat.VALIDASI
            )
        } finally {
            if (pathSumber.harusDihapusSetelahnya) {
                withContext(NonCancellable) {
                    runCatching { pathSumber.fileSystem.delete(pathSumber.path, mustExist = false) }
                }
            }
        }
    }

    override suspend fun bukaEntriUntukBaca(
        sumber: LokasiBerkas,
        format: FormatArsip,
        jalurInternal: String,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<SumberIsiBerkas> = withContext(ioDispatcher) {
        if (format != FormatArsip.GZIP) {
            return@withContext HasilOperasi.Gagal("Format bukan GZIP: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }
        val validasi = ValidasiPathArsip.validasi(jalurInternal, adalahFolder = false)
        val jalurAman = when (validasi) {
            is HasilValidasiPathArsip.Sah -> validasi.jalurNormal
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Jalur entri tidak valid", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(validasi.alasan, PenyebabGalat.VALIDASI)
            }
        }

        val pathSumber = when (
            val siap = BantuArsipPenyimpanan.siapkanPathLokalUntukBaca(
                sumber = sumber,
                repositoriBerkas = repositoriBerkas,
                fileSystemLokal = adapterPlatform.sistemBerkasLokal,
                direktoriTemp = direktoriTemp
            )
        ) {
            is SumberPathLokal.Tersedia -> siap
            is SumberPathLokal.Gagal -> return@withContext HasilOperasi.Gagal(siap.pesan, siap.penyebab)
        }

        val cleanupTemp = {
            if (pathSumber.harusDihapusSetelahnya) {
                runCatching { pathSumber.fileSystem.delete(pathSumber.path, mustExist = false) }
            }
        }

        try {
            val infoHeader = bacaHeaderDanValidasiGzip(pathSumber)
            if (infoHeader == null) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    "Berkas bukan arsip GZIP yang sah atau arsip rusak",
                    PenyebabGalat.VALIDASI
                )
            }
            val namaTunggal = tentukanNamaEntriTunggal(sumber, infoHeader.namaAsli)
            if (jalurAman != namaTunggal) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    "Entri '$jalurAman' tidak ditemukan dalam arsip GZIP",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            }

            val raw = pathSumber.fileSystem.source(pathSumber.path)
            val gzipSource: Source = adapterPlatform.bukaSumberGzip(raw)
            HasilOperasi.Sukses(
                SumberIsiOkioTerkelola(
                    ukuranByte = null,
                    source = gzipSource,
                    batasUkuranByte = batas.batasUkuranSatuEntriByte,
                    pembersihAkhir = cleanupTemp
                )
            )
        } catch (e: CancellationException) {
            cleanupTemp()
            throw e
        } catch (e: Exception) {
            cleanupTemp()
            HasilOperasi.Gagal(e.message ?: "Gagal membuka aliran baca GZIP", PenyebabGalat.VALIDASI)
        }
    }

    override suspend fun ekstrak(
        sumber: LokasiBerkas,
        format: FormatArsip,
        folderTujuan: LokasiBerkas,
        opsi: OpsiEkstrakArsip,
        onProgres: (selesai: Int, total: Int, jalurSaatIni: String) -> Unit
    ): HasilOperasi<HasilEkstrakArsip> = withContext(ioDispatcher) {
        if (!opsi.konfirmasiPengguna) {
            return@withContext HasilOperasi.Gagal(
                "Ekstraksi arsip memerlukan persetujuan pengguna",
                PenyebabGalat.VALIDASI
            )
        }
        if (format != FormatArsip.GZIP) {
            return@withContext HasilOperasi.Gagal("Format bukan GZIP: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }

        val pathSumber = when (
            val siap = BantuArsipPenyimpanan.siapkanPathLokalUntukBaca(
                sumber = sumber,
                repositoriBerkas = repositoriBerkas,
                fileSystemLokal = adapterPlatform.sistemBerkasLokal,
                direktoriTemp = direktoriTemp
            )
        ) {
            is SumberPathLokal.Tersedia -> siap
            is SumberPathLokal.Gagal -> return@withContext HasilOperasi.Gagal(siap.pesan, siap.penyebab)
        }

        try {
            val infoHeader = bacaHeaderDanValidasiGzip(pathSumber)
                ?: return@withContext HasilOperasi.Gagal(
                    "Berkas bukan arsip GZIP yang sah atau arsip rusak",
                    PenyebabGalat.VALIDASI
                )
            val namaTunggal = tentukanNamaEntriTunggal(sumber, infoHeader.namaAsli)
            if (!ValidasiPathArsip.apakahCocokFilterEkstrak(namaTunggal, opsi.jalurTerpilih)) {
                return@withContext HasilOperasi.Sukses(
                    HasilEkstrakArsip(
                        jumlahDiekstrak = 0,
                        jumlahDilewati = 1,
                        totalByteDiekstrak = 0L,
                        daftarBerkasHasil = emptyList()
                    )
                )
            }

            onProgres(1, 1, namaTunggal)
            val cacheIsiFolder = HashMap<LokasiBerkas, MutableMap<String, EntitasBerkas>>()
            val raw = pathSumber.fileSystem.source(pathSumber.path)
            val gzipSource = adapterPlatform.bukaSumberGzip(raw)

            val hasilTulis = gzipSource.use { src ->
                BantuArsipPenyimpanan.tulisEntriHasilKeRepositori(
                    source = src,
                    indukTujuan = folderTujuan,
                    namaBerkas = namaTunggal,
                    kebijakanKonflik = opsi.kebijakanKonflik,
                    batasSatuEntriByte = minOf(opsi.batas.batasUkuranSatuEntriByte, opsi.batas.batasUkuranTotalEkstrakByte),
                    sisaBatasTotalByte = opsi.batas.batasUkuranTotalEkstrakByte,
                    repositoriBerkas = repositoriBerkas,
                    cacheIsiFolder = cacheIsiFolder
                )
            }

            when (hasilTulis) {
                is HasilTulisEntriEkstrak.Sukses -> HasilOperasi.Sukses(
                    HasilEkstrakArsip(
                        jumlahDiekstrak = 1,
                        jumlahDilewati = 0,
                        totalByteDiekstrak = hasilTulis.byteDitulis,
                        daftarBerkasHasil = listOf(hasilTulis.entitas)
                    )
                )
                is HasilTulisEntriEkstrak.Dilewati -> HasilOperasi.Sukses(
                    HasilEkstrakArsip(
                        jumlahDiekstrak = 0,
                        jumlahDilewati = 1,
                        totalByteDiekstrak = 0L,
                        daftarBerkasHasil = emptyList()
                    )
                )
                is HasilTulisEntriEkstrak.Konflik -> HasilOperasi.Gagal(
                    "Konflik nama di tujuan untuk: ${hasilTulis.nama}",
                    PenyebabGalat.KONFLIK
                )
                is HasilTulisEntriEkstrak.Gagal -> HasilOperasi.Gagal(
                    hasilTulis.pesan,
                    hasilTulis.penyebab
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal mengekstrak arsip GZIP (arsip mungkin rusak)",
                PenyebabGalat.VALIDASI
            )
        } finally {
            if (pathSumber.harusDihapusSetelahnya) {
                withContext(NonCancellable) {
                    runCatching { pathSumber.fileSystem.delete(pathSumber.path, mustExist = false) }
                }
            }
        }
    }

    override suspend fun buatArsip(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        format: FormatArsip,
        konfirmasiTimpa: Boolean,
        kataSandi: String?,
        onProgres: (selesai: Int, total: Int, itemSaatIni: String) -> Unit
    ): HasilOperasi<EntitasBerkas> = withContext(ioDispatcher) {
        if (format != FormatArsip.GZIP) {
            return@withContext HasilOperasi.Gagal("Format bukan GZIP: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }
        if (!ValidasiNama.sah(namaArsip)) {
            return@withContext HasilOperasi.Gagal("Nama arsip tidak sah: $namaArsip", PenyebabGalat.VALIDASI)
        }
        if (kataSandi != null) {
            return@withContext HasilOperasi.Gagal(
                "Format GZIP tidak mendukung enkripsi kata sandi",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        if (daftarSumber.size != 1 || daftarSumber.first().adalahFolder) {
            return@withContext HasilOperasi.Gagal(
                "GZIP hanya mendukung kompresi satu berkas tunggal (gunakan TGZ atau ZIP untuk folder/banyak berkas)",
                PenyebabGalat.VALIDASI
            )
        }

        val item = daftarSumber.first()
        val sumberBaca = when (val buka = repositoriBerkas.bukaUntukBaca(item.lokasi)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return@withContext buka
        }

        val fileSystemLokal = adapterPlatform.sistemBerkasLokal
        val pathTemp = BantuArsipPenyimpanan.buatPathCacheSementara("gzip_out", ".gz", direktoriTemp)
        var selesaiTulis = false

        try {
            onProgres(1, 1, item.namaDalamArsip)
            val rawSink = fileSystemLokal.sink(pathTemp, mustCreate = false)
            adapterPlatform.bukaSinkGzip(rawSink).buffer().use { gzipOut ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val pot = sumberBaca.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val data = pot.potongan.data
                            if (data.isNotEmpty()) {
                                gzipOut.write(data)
                            }
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            return@withContext HasilOperasi.Gagal(pot.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                    }
                }
                gzipOut.flush()
            }
            selesaiTulis = true

            BantuArsipPenyimpanan.tulisFileTempKeRepositori(
                fileSystemLokal = fileSystemLokal,
                pathTempSumber = pathTemp,
                tujuanInduk = tujuanInduk,
                namaArsip = namaArsip,
                timpa = konfirmasiTimpa,
                repositoriBerkas = repositoriBerkas
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal mengompres berkas ke GZIP", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                sumberBaca.tutup()
                if (!selesaiTulis) {
                    runCatching { fileSystemLokal.delete(pathTemp, mustExist = false) }
                }
                runCatching { fileSystemLokal.delete(pathTemp, mustExist = false) }
            }
        }
    }

    internal suspend fun tulisUlangDenganPengganti(
        pathArsipLama: Path,
        jalurTarget: String,
        pathFilePengganti: Path,
        pathTempOut: Path
    ): HasilOperasi<Unit> = withContext(ioDispatcher) {
        val fs = adapterPlatform.sistemBerkasLokal
        bacaHeaderDanValidasiGzip(SumberPathLokal.Tersedia(pathArsipLama, fs, harusDihapusSetelahnya = false))
            ?: return@withContext HasilOperasi.Gagal(
                "Berkas bukan arsip GZIP yang sah",
                PenyebabGalat.VALIDASI
            )
        if (jalurTarget.isBlank()) {
            return@withContext HasilOperasi.Gagal("Jalur entri tidak valid", PenyebabGalat.VALIDASI)
        }

        var selesai = false
        try {
            val rawSink = fs.sink(pathTempOut, mustCreate = false)
            adapterPlatform.bukaSinkGzip(rawSink).buffer().use { gzipOut ->
                fs.source(pathFilePengganti).buffer().use { src ->
                    val buf = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buf)
                        if (n == -1) break
                        gzipOut.write(buf, 0, n)
                    }
                }
                gzipOut.flush()
            }
            selesai = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext HasilOperasi.Gagal(
                e.message ?: "Gagal menulis ulang arsip GZIP",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                if (!selesai) {
                    runCatching { fs.delete(pathTempOut, mustExist = false) }
                }
            }
        }

        val infoBaru = bacaHeaderDanValidasiGzip(
            SumberPathLokal.Tersedia(pathTempOut, fs, harusDihapusSetelahnya = false)
        )
        if (infoBaru == null) {
            runCatching { fs.delete(pathTempOut, mustExist = false) }
            HasilOperasi.Gagal("Verifikasi arsip GZIP hasil pembaruan gagal", PenyebabGalat.TIDAK_DIKETAHUI)
        } else {
            HasilOperasi.Sukses(Unit)
        }
    }

    private data class InfoHeaderGzip(
        val namaAsli: String?,
        val waktuModifikasiEpochMs: Long?
    )

    private fun bacaHeaderDanValidasiGzip(pathSumber: SumberPathLokal.Tersedia): InfoHeaderGzip? {
        return try {
            pathSumber.fileSystem.source(pathSumber.path).buffer().use { src ->
                val header = ByteArray(10)
                var pos = 0
                while (pos < 10) {
                    val n = src.read(header, pos, 10 - pos)
                    if (n == -1) return null
                    pos += n
                }
                if ((header[0].toInt() and 0xFF) != 0x1F ||
                    (header[1].toInt() and 0xFF) != 0x8B ||
                    (header[2].toInt() and 0xFF) != 8
                ) {
                    return null
                }
                val flags = header[3].toInt() and 0xFF
                val mtimeDetik = (header[4].toLong() and 0xFFL) or
                    ((header[5].toLong() and 0xFFL) shl 8) or
                    ((header[6].toLong() and 0xFFL) shl 16) or
                    ((header[7].toLong() and 0xFFL) shl 24)

                if ((flags and 0x04) != 0) {
                    val xlen = src.readShortLe().toInt() and 0xFFFF
                    src.skip(xlen.toLong())
                }
                var namaAsli: String? = null
                if ((flags and 0x08) != 0) {
                    val bufNama = mutableListOf<Byte>()
                    while (bufNama.size < 512) {
                        val b = src.readByte()
                        if (b == 0.toByte()) break
                        bufNama.add(b)
                    }
                    namaAsli = bufNama.toByteArray().decodeToString()
                }
                InfoHeaderGzip(
                    namaAsli = namaAsli,
                    waktuModifikasiEpochMs = if (mtimeDetik > 0L) mtimeDetik * 1000L else null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tentukanNamaEntriTunggal(sumber: LokasiBerkas, namaDariHeader: String?): String {
        val kandidatHeader = namaDariHeader?.trim()?.takeIf { it.isNotEmpty() }
        if (kandidatHeader != null) {
            val bersih = kandidatHeader.substringAfterLast('/').substringAfterLast('\\')
            if (ValidasiNama.sah(bersih)) return bersih
        }
        val namaArsip = sumber.label?.takeIf { it.isNotBlank() }
            ?: sumber.pengenal.trimEnd('/').substringAfterLast('/')
        val lower = namaArsip.lowercase()
        val hasilPotong = when {
            lower.endsWith(".gzip") && namaArsip.length > 5 -> namaArsip.dropLast(5)
            lower.endsWith(".gz") && namaArsip.length > 3 -> namaArsip.dropLast(3)
            else -> "${namaArsip}.out"
        }
        return if (ValidasiNama.sah(hasilPotong)) hasilPotong else "isi_berkas"
    }
}