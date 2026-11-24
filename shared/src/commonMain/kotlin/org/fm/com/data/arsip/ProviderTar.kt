package org.fm.com.data.arsip

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.EOFException
import okio.FileSystem
import okio.Path
import okio.Source
import okio.Timeout
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
import org.fm.com.domain.model.ItemGagalArsip
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas

private const val UKURAN_BLOK_TAR = 512
private const val BATAS_PAX_HEADER_BYTE = 64 * 1024L

class ProviderTar(
    private val repositoriBerkas: RepositoriBerkas,
    private val repositoriOperasi: RepositoriOperasiBerkas,
    private val adapterPlatform: AdapterArsipPlatform = buatAdapterArsipPlatform(),
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ArsipProvider {

    override val formatDidukung: Set<FormatArsip> = setOf(FormatArsip.TAR, FormatArsip.TGZ)

    override fun kapabilitas(format: FormatArsip): KapabilitasArsip? =
        if (format in formatDidukung) MatriksArsip.untuk(format) else null

    override suspend fun daftarEntri(
        sumber: LokasiBerkas,
        format: FormatArsip,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<RingkasanIsiArsip> = withContext(ioDispatcher) {
        if (format !in formatDidukung) {
            return@withContext HasilOperasi.Gagal("Format bukan TAR/TGZ: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
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

        var bufferedTar: BufferedSource? = null
        try {
            bufferedTar = bukaStreamTar(pathSumber, format)
            val hasil = mutableListOf<EntriArsip>()
            val sudahAda = HashSet<String>()
            var totalUkuran = 0L

            while (true) {
                currentCoroutineContext().ensureActive()
                val hdr = when (val bacaHdr = bacaEntriBerikutnya(bufferedTar)) {
                    is HasilBacaHeaderTar.Akhir -> break
                    is HasilBacaHeaderTar.Galat -> return@withContext HasilOperasi.Gagal(bacaHdr.pesan, PenyebabGalat.VALIDASI)
                    is HasilBacaHeaderTar.Entri -> bacaHdr.data
                }

                if (hdr.ukuranByte > 0L) {
                    val totalSkip = hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte)
                    lewatiTepat(bufferedTar, totalSkip)
                }

                val valPath = ValidasiPathArsip.validasi(hdr.jalurMentah, hdr.adalahFolder)
                when (valPath) {
                    is HasilValidasiPathArsip.AbaikanRoot -> continue
                    is HasilValidasiPathArsip.TidakSah -> {
                        return@withContext HasilOperasi.Gagal(valPath.alasan, PenyebabGalat.VALIDASI)
                    }
                    is HasilValidasiPathArsip.Sah -> {
                        if (!sudahAda.add(valPath.jalurNormal)) continue
                        if (hasil.size >= batas.batasJumlahEntriMaks) {
                            return@withContext HasilOperasi.Gagal(
                                "Jumlah entri arsip melebihi batas maksimal (${batas.batasJumlahEntriMaks})",
                                PenyebabGalat.VALIDASI
                            )
                        }
                        if (!hdr.adalahFolder) totalUkuran += hdr.ukuranByte
                        hasil.add(
                            EntriArsip(
                                jalurInternal = valPath.jalurNormal,
                                nama = valPath.namaSegmenAkhir,
                                adalahFolder = hdr.adalahFolder,
                                ukuranTidakTerkompresiByte = if (hdr.adalahFolder) null else hdr.ukuranByte,
                                ukuranTerkompresiByte = null,
                                waktuUbahEpochMs = hdr.waktuUbahEpochMs,
                                terenkripsi = false,
                                metodeKompresi = if (format == FormatArsip.TGZ) "TAR+GZIP" else "TAR",
                                adalahTautanSimbolik = hdr.adalahTautan
                            )
                        )
                    }
                }
            }

            HasilOperasi.Sukses(
                RingkasanIsiArsip(
                    format = format,
                    daftarEntri = hasil.sortedBy { it.jalurInternal.lowercase() },
                    totalUkuranTidakTerkompresiByte = totalUkuran,
                    adaUkuranTidakDiketahui = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Berkas TAR/TGZ rusak atau bukan format TAR yang sah",
                PenyebabGalat.VALIDASI
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { bufferedTar?.close() }
                if (pathSumber.harusDihapusSetelahnya) {
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
        if (format !in formatDidukung) {
            return@withContext HasilOperasi.Gagal("Format bukan TAR/TGZ: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }
        val valTarget = ValidasiPathArsip.validasi(jalurInternal, adalahFolder = false)
        val jalurTarget = when (valTarget) {
            is HasilValidasiPathArsip.Sah -> valTarget.jalurNormal
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Tidak dapat membaca akar arsip sebagai berkas", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(valTarget.alasan, PenyebabGalat.VALIDASI)
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

        var bufferedTar: BufferedSource? = null
        try {
            bufferedTar = bukaStreamTar(pathSumber, format)
            while (true) {
                currentCoroutineContext().ensureActive()
                val hdr = when (val bacaHdr = bacaEntriBerikutnya(bufferedTar)) {
                    is HasilBacaHeaderTar.Akhir -> {
                        bufferedTar.close()
                        cleanupTemp()
                        return@withContext HasilOperasi.Gagal(
                            "Entri '$jalurTarget' tidak ditemukan dalam arsip",
                            PenyebabGalat.TIDAK_DITEMUKAN
                        )
                    }
                    is HasilBacaHeaderTar.Galat -> {
                        bufferedTar.close()
                        cleanupTemp()
                        return@withContext HasilOperasi.Gagal(bacaHdr.pesan, PenyebabGalat.VALIDASI)
                    }
                    is HasilBacaHeaderTar.Entri -> bacaHdr.data
                }

                val valPath = ValidasiPathArsip.validasi(hdr.jalurMentah, hdr.adalahFolder)
                if (valPath is HasilValidasiPathArsip.Sah && valPath.jalurNormal == jalurTarget) {
                    if (hdr.adalahFolder) {
                        bufferedTar.close()
                        cleanupTemp()
                        return@withContext HasilOperasi.Gagal("Entri '$jalurTarget' adalah folder", PenyebabGalat.VALIDASI)
                    }
                    if (hdr.adalahTautan) {
                        bufferedTar.close()
                        cleanupTemp()
                        return@withContext HasilOperasi.Gagal(
                            "Entri '$jalurTarget' adalah tautan simbolik dan ditolak demi keamanan",
                            PenyebabGalat.VALIDASI
                        )
                    }
                    if (hdr.ukuranByte > batas.batasUkuranSatuEntriByte) {
                        bufferedTar.close()
                        cleanupTemp()
                        return@withContext HasilOperasi.Gagal(
                            "Ukuran entri '$jalurTarget' melebihi batas baca (${batas.batasUkuranSatuEntriByte} byte)",
                            PenyebabGalat.VALIDASI
                        )
                    }

                    val sumberEntriTerbatas = SumberTerbatasOkio(bufferedTar, hdr.ukuranByte)
                    return@withContext HasilOperasi.Sukses(
                        SumberIsiOkioTerkelola(
                            ukuranByte = hdr.ukuranByte,
                            source = sumberEntriTerbatas,
                            batasUkuranByte = batas.batasUkuranSatuEntriByte,
                            pembersihAkhir = {
                                runCatching { bufferedTar.close() }
                                cleanupTemp()
                            }
                        )
                    )
                }

                if (hdr.ukuranByte > 0L) {
                    lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                }
            }
            @Suppress("UNREACHABLE_CODE")
            HasilOperasi.Gagal("Entri tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: CancellationException) {
            runCatching { bufferedTar?.close() }
            cleanupTemp()
            throw e
        } catch (e: Exception) {
            runCatching { bufferedTar?.close() }
            cleanupTemp()
            HasilOperasi.Gagal(
                e.message ?: "Gagal membaca entri '$jalurTarget' dari arsip TAR/TGZ",
                PenyebabGalat.VALIDASI
            )
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
        if (format !in formatDidukung) {
            return@withContext HasilOperasi.Gagal("Format bukan TAR/TGZ: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
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

        var bufferedTar: BufferedSource? = null
        try {
            bufferedTar = bukaStreamTar(pathSumber, format)
            val cacheFolder = HashMap<String, LokasiBerkas>()
            val cacheIsiFolder = HashMap<LokasiBerkas, MutableMap<String, EntitasBerkas>>()
            val daftarBerkasHasil = mutableListOf<EntitasBerkas>()
            val daftarKonflik = mutableListOf<String>()
            val daftarGagal = mutableListOf<ItemGagalArsip>()

            var jumlahDiekstrak = 0
            var jumlahDilewati = 0
            var totalByte = 0L
            var urutanDiproses = 0

            while (true) {
                currentCoroutineContext().ensureActive()
                val hdr = when (val bacaHdr = bacaEntriBerikutnya(bufferedTar)) {
                    is HasilBacaHeaderTar.Akhir -> break
                    is HasilBacaHeaderTar.Galat -> {
                        return@withContext HasilOperasi.Gagal(bacaHdr.pesan, PenyebabGalat.VALIDASI)
                    }
                    is HasilBacaHeaderTar.Entri -> bacaHdr.data
                }

                val valPath = ValidasiPathArsip.validasi(hdr.jalurMentah, hdr.adalahFolder)
                val pathSah = when (valPath) {
                    is HasilValidasiPathArsip.AbaikanRoot -> {
                        if (hdr.ukuranByte > 0L) {
                            lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                        }
                        continue
                    }
                    is HasilValidasiPathArsip.TidakSah -> {
                        if (hdr.ukuranByte > 0L) {
                            lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                        }
                        if (ValidasiPathArsip.apakahCocokFilterEkstrak(hdr.jalurMentah, opsi.jalurTerpilih)) {
                            daftarGagal.add(ItemGagalArsip(hdr.jalurMentah, valPath.alasan, PenyebabGalat.VALIDASI))
                        }
                        continue
                    }
                    is HasilValidasiPathArsip.Sah -> valPath
                }

                if (!ValidasiPathArsip.apakahCocokFilterEkstrak(pathSah.jalurNormal, opsi.jalurTerpilih)) {
                    if (hdr.ukuranByte > 0L) {
                        lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                    }
                    continue
                }

                urutanDiproses++
                onProgres(urutanDiproses, 0, pathSah.jalurNormal)

                if (hdr.adalahTautan) {
                    if (hdr.ukuranByte > 0L) {
                        lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                    }
                    daftarGagal.add(
                        ItemGagalArsip(
                            pathSah.jalurNormal,
                            "Tautan simbolik dalam arsip dilewati demi keamanan",
                            PenyebabGalat.VALIDASI
                        )
                    )
                    continue
                }

                if (hdr.adalahFolder) {
                    if (hdr.ukuranByte > 0L) {
                        lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                    }
                    when (
                        val resDir = BantuArsipPenyimpanan.pastikanFolderTujuan(
                            daftarSegmenFolder = pathSah.daftarSegmen,
                            folderAkarTujuan = folderTujuan,
                            repositoriBerkas = repositoriBerkas,
                            repositoriOperasi = repositoriOperasi,
                            cacheFolder = cacheFolder,
                            cacheIsiFolder = cacheIsiFolder
                        )
                    ) {
                        is HasilOperasi.Sukses -> Unit
                        is HasilOperasi.Gagal -> daftarGagal.add(
                            ItemGagalArsip(pathSah.jalurNormal, resDir.pesan, resDir.penyebab)
                        )
                    }
                    continue
                }

                if (hdr.ukuranByte > opsi.batas.batasUkuranSatuEntriByte) {
                    lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                    daftarGagal.add(
                        ItemGagalArsip(
                            pathSah.jalurNormal,
                            "Ukuran entri melebihi batas per berkas (${opsi.batas.batasUkuranSatuEntriByte} byte)",
                            PenyebabGalat.VALIDASI
                        )
                    )
                    continue
                }

                val segmenFolder = if (pathSah.daftarSegmen.size > 1) {
                    pathSah.daftarSegmen.dropLast(1)
                } else {
                    emptyList()
                }
                val indukTujuan = when (
                    val resDir = BantuArsipPenyimpanan.pastikanFolderTujuan(
                        daftarSegmenFolder = segmenFolder,
                        folderAkarTujuan = folderTujuan,
                        repositoriBerkas = repositoriBerkas,
                        repositoriOperasi = repositoriOperasi,
                        cacheFolder = cacheFolder,
                        cacheIsiFolder = cacheIsiFolder
                    )
                ) {
                    is HasilOperasi.Sukses -> resDir.nilai
                    is HasilOperasi.Gagal -> {
                        if (hdr.ukuranByte > 0L) {
                            lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                        }
                        daftarGagal.add(ItemGagalArsip(pathSah.jalurNormal, resDir.pesan, resDir.penyebab))
                        continue
                    }
                }

                val sisaBatasTotal = (opsi.batas.batasUkuranTotalEkstrakByte - totalByte).coerceAtLeast(0L)
                val sumberEntri = SumberTerbatasOkio(bufferedTar, hdr.ukuranByte)
                val hasilTulis = BantuArsipPenyimpanan.tulisEntriHasilKeRepositori(
                    source = sumberEntri,
                    indukTujuan = indukTujuan,
                    namaBerkas = pathSah.namaSegmenAkhir,
                    kebijakanKonflik = opsi.kebijakanKonflik,
                    batasSatuEntriByte = opsi.batas.batasUkuranSatuEntriByte,
                    sisaBatasTotalByte = sisaBatasTotal,
                    repositoriBerkas = repositoriBerkas,
                    cacheIsiFolder = cacheIsiFolder
                )

                val sisaBelumDibaca = sumberEntri.sisaByte + hitungPaddingBlok(hdr.ukuranByte)
                if (sisaBelumDibaca > 0L) {
                    lewatiTepat(bufferedTar, sisaBelumDibaca)
                }

                when (hasilTulis) {
                    is HasilTulisEntriEkstrak.Sukses -> {
                        jumlahDiekstrak++
                        totalByte += hasilTulis.byteDitulis
                        daftarBerkasHasil.add(hasilTulis.entitas)
                    }
                    is HasilTulisEntriEkstrak.Dilewati -> jumlahDilewati++
                    is HasilTulisEntriEkstrak.Konflik -> daftarKonflik.add(pathSah.jalurNormal)
                    is HasilTulisEntriEkstrak.Gagal -> daftarGagal.add(
                        ItemGagalArsip(pathSah.jalurNormal, hasilTulis.pesan, hasilTulis.penyebab)
                    )
                }
            }

            if (jumlahDiekstrak == 0 && daftarKonflik.isNotEmpty() && daftarGagal.isEmpty()) {
                return@withContext HasilOperasi.Gagal(
                    "Konflik nama di tujuan untuk: ${daftarKonflik.first()}",
                    PenyebabGalat.KONFLIK
                )
            }
            if (jumlahDiekstrak == 0 && daftarGagal.isNotEmpty()) {
                val pertama = daftarGagal.first()
                return@withContext HasilOperasi.Gagal(pertama.pesan, pertama.penyebab)
            }

            HasilOperasi.Sukses(
                HasilEkstrakArsip(
                    jumlahDiekstrak = jumlahDiekstrak,
                    jumlahDilewati = jumlahDilewati,
                    totalByteDiekstrak = totalByte,
                    daftarBerkasHasil = daftarBerkasHasil,
                    daftarKonflik = daftarKonflik,
                    daftarGagal = daftarGagal
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                e.message ?: "Gagal mengekstrak arsip TAR/TGZ",
                PenyebabGalat.VALIDASI
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { bufferedTar?.close() }
                if (pathSumber.harusDihapusSetelahnya) {
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
        if (format !in formatDidukung) {
            return@withContext HasilOperasi.Gagal("Format bukan TAR/TGZ: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
        }
        if (!ValidasiNama.sah(namaArsip)) {
            return@withContext HasilOperasi.Gagal("Nama arsip tidak sah: $namaArsip", PenyebabGalat.VALIDASI)
        }
        if (kataSandi != null) {
            return@withContext HasilOperasi.Gagal(
                "Format ${format.namaTampilan} tidak mendukung enkripsi kata sandi",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        if (daftarSumber.isEmpty()) {
            return@withContext HasilOperasi.Gagal("Pilih minimal satu item untuk diarsipkan", PenyebabGalat.VALIDASI)
        }

        val itemSiap = kumpulkanItemRekursif(daftarSumber)
        val daftarItem = when (itemSiap) {
            is HasilOperasi.Sukses -> itemSiap.nilai
            is HasilOperasi.Gagal -> return@withContext itemSiap
        }

        val fileSystemLokal = adapterPlatform.sistemBerkasLokal
        val ekstensiTemp = if (format == FormatArsip.TGZ) ".tar.gz" else ".tar"
        val pathTemp = BantuArsipPenyimpanan.buatPathCacheSementara("tar_out", ekstensiTemp, direktoriTemp)
        val pathTempIsi = BantuArsipPenyimpanan.buatPathCacheSementara("tar_entry", ".bin", direktoriTemp)

        var selesaiTulis = false
        try {
            val rawSink = fileSystemLokal.sink(pathTemp, mustCreate = false)
            val targetSink = if (format == FormatArsip.TGZ) {
                adapterPlatform.bukaSinkGzip(rawSink)
            } else {
                rawSink
            }
            targetSink.buffer().use { out ->
                val total = daftarItem.size
                for ((idx, item) in daftarItem.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    onProgres(idx + 1, total, item.jalurDalamArsip)

                    if (item.adalahFolder) {
                        val jalurFolder = if (item.jalurDalamArsip.endsWith('/')) {
                            item.jalurDalamArsip
                        } else {
                            "${item.jalurDalamArsip}/"
                        }
                        tulisEntriHeaderTar(
                            out = out,
                            jalur = jalurFolder,
                            ukuranByte = 0L,
                            adalahFolder = true,
                            waktuUbahEpochMs = item.waktuUbahEpochMs
                        )
                    } else {
                        val ukuranAktual = salinSumberKeTemp(item.lokasi, fileSystemLokal, pathTempIsi)
                        val ukuran = when (ukuranAktual) {
                            is HasilOperasi.Sukses -> ukuranAktual.nilai
                            is HasilOperasi.Gagal -> return@withContext ukuranAktual
                        }
                        tulisEntriHeaderTar(
                            out = out,
                            jalur = item.jalurDalamArsip,
                            ukuranByte = ukuran,
                            adalahFolder = false,
                            waktuUbahEpochMs = item.waktuUbahEpochMs
                        )
                        if (ukuran > 0L) {
                            fileSystemLokal.source(pathTempIsi).buffer().use { src ->
                                out.writeAll(src)
                            }
                            val pad = hitungPaddingBlok(ukuran)
                            if (pad > 0L) {
                                out.write(ByteArray(pad.toInt()))
                            }
                        }
                    }
                }
                // Dua blok 512 byte nol penutup standar TAR
                out.write(ByteArray(UKURAN_BLOK_TAR * 2))
                out.flush()
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
            HasilOperasi.Gagal(e.message ?: "Gagal membuat arsip ${format.namaTampilan}", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                if (!selesaiTulis) {
                    runCatching { fileSystemLokal.delete(pathTemp, mustExist = false) }
                }
                runCatching { fileSystemLokal.delete(pathTemp, mustExist = false) }
                runCatching { fileSystemLokal.delete(pathTempIsi, mustExist = false) }
            }
        }
    }

    internal suspend fun tulisUlangDenganPengganti(
        pathArsipLama: Path,
        format: FormatArsip,
        jalurTarget: String,
        pathFilePengganti: Path,
        pathTempOut: Path,
        waktuBaruEpochMs: Long? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<Unit> = withContext(ioDispatcher) {
        if (format !in formatDidukung) {
            return@withContext HasilOperasi.Gagal("Format bukan TAR/TGZ", PenyebabGalat.VALIDASI)
        }
        val validasiTarget = ValidasiPathArsip.validasi(jalurTarget, adalahFolder = false)
        val jalurNormal = when (validasiTarget) {
            is HasilValidasiPathArsip.Sah -> validasiTarget.jalurNormal
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Jalur entri tidak valid", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(validasiTarget.alasan, PenyebabGalat.VALIDASI)
            }
        }

        val fs = adapterPlatform.sistemBerkasLokal
        val ukuranPengganti = fs.metadataOrNull(pathFilePengganti)?.size
            ?: return@withContext HasilOperasi.Gagal("Berkas edit sementara tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)

        var bufferedTar: BufferedSource? = null
        var selesai = false
        try {
            bufferedTar = bukaStreamTar(SumberPathLokal.Tersedia(pathArsipLama, fs, false), format)
            val rawSink = fs.sink(pathTempOut, mustCreate = false)
            val targetSink = if (format == FormatArsip.TGZ) {
                adapterPlatform.bukaSinkGzip(rawSink)
            } else {
                rawSink
            }
            var ketemuTarget = false

            targetSink.buffer().use { out ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val hdr = when (val baca = bacaEntriBerikutnya(bufferedTar)) {
                        is HasilBacaHeaderTar.Akhir -> break
                        is HasilBacaHeaderTar.Galat -> {
                            return@withContext HasilOperasi.Gagal(baca.pesan, PenyebabGalat.VALIDASI)
                        }
                        is HasilBacaHeaderTar.Entri -> baca.data
                    }

                    val valPath = ValidasiPathArsip.validasi(hdr.jalurMentah, hdr.adalahFolder)
                    val pathSah = when (valPath) {
                        is HasilValidasiPathArsip.AbaikanRoot -> {
                            if (hdr.ukuranByte > 0L) {
                                lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                            }
                            continue
                        }
                        is HasilValidasiPathArsip.TidakSah -> {
                            return@withContext HasilOperasi.Gagal(valPath.alasan, PenyebabGalat.VALIDASI)
                        }
                        is HasilValidasiPathArsip.Sah -> valPath
                    }

                    if (hdr.adalahTautan) {
                        return@withContext HasilOperasi.Gagal(
                            "Arsip mengandung tautan simbolik sehingga penulisan ulang dibatalkan demi keamanan",
                            PenyebabGalat.VALIDASI
                        )
                    }

                    if (hdr.adalahFolder) {
                        if (hdr.ukuranByte > 0L) {
                            lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                        }
                        tulisEntriHeaderTar(
                            out = out,
                            jalur = "${pathSah.jalurNormal}/",
                            ukuranByte = 0L,
                            adalahFolder = true,
                            waktuUbahEpochMs = hdr.waktuUbahEpochMs
                        )
                        continue
                    }

                    if (pathSah.jalurNormal == jalurNormal) {
                        ketemuTarget = true
                        if (hdr.ukuranByte > 0L) {
                            lewatiTepat(bufferedTar, hdr.ukuranByte + hitungPaddingBlok(hdr.ukuranByte))
                        }
                        tulisEntriHeaderTar(
                            out = out,
                            jalur = jalurNormal,
                            ukuranByte = ukuranPengganti,
                            adalahFolder = false,
                            waktuUbahEpochMs = waktuBaruEpochMs ?: hdr.waktuUbahEpochMs
                        )
                        if (ukuranPengganti > 0L) {
                            fs.source(pathFilePengganti).buffer().use { src ->
                                out.writeAll(src)
                            }
                            val pad = hitungPaddingBlok(ukuranPengganti)
                            if (pad > 0L) out.write(ByteArray(pad.toInt()))
                        }
                    } else {
                        tulisEntriHeaderTar(
                            out = out,
                            jalur = pathSah.jalurNormal,
                            ukuranByte = hdr.ukuranByte,
                            adalahFolder = false,
                            waktuUbahEpochMs = hdr.waktuUbahEpochMs
                        )
                        if (hdr.ukuranByte > 0L) {
                            salinTepat(bufferedTar, out, hdr.ukuranByte)
                            val pad = hitungPaddingBlok(hdr.ukuranByte)
                            if (pad > 0L) {
                                lewatiTepat(bufferedTar, pad)
                                out.write(ByteArray(pad.toInt()))
                            }
                        }
                    }
                }
                out.write(ByteArray(UKURAN_BLOK_TAR * 2))
                out.flush()
            }

            if (!ketemuTarget) {
                return@withContext HasilOperasi.Gagal(
                    "Entri '$jalurNormal' tidak ditemukan dalam arsip TAR/TGZ",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            }
            selesai = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext HasilOperasi.Gagal(
                e.message ?: "Gagal menulis ulang arsip ${format.namaTampilan}",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { bufferedTar?.close() }
                if (!selesai) {
                    runCatching { fs.delete(pathTempOut, mustExist = false) }
                }
            }
        }

        val lokasiSementara = LokasiBerkas(pathTempOut.toString(), JenisLokasi.POSIX, pathTempOut.name)
        when (val verifikasi = daftarEntri(lokasiSementara, format, batas = batas)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(Unit)
            is HasilOperasi.Gagal -> {
                runCatching { fs.delete(pathTempOut, mustExist = false) }
                HasilOperasi.Gagal("Verifikasi arsip hasil pembaruan gagal: ${verifikasi.pesan}", verifikasi.penyebab)
            }
        }
    }

    private suspend fun salinTepat(src: BufferedSource, dst: okio.BufferedSink, jumlahByte: Long) {
        var sisa = jumlahByte
        val buf = ByteArray(8192)
        while (sisa > 0L) {
            currentCoroutineContext().ensureActive()
            val minta = minOf(sisa, buf.size.toLong()).toInt()
            val n = src.read(buf, 0, minta)
            if (n == -1) throw EOFException("Isi entri TAR terpotong saat menulis ulang arsip")
            dst.write(buf, 0, n)
            sisa -= n
        }
    }

    private fun bukaStreamTar(pathSumber: SumberPathLokal.Tersedia, format: FormatArsip): BufferedSource {
        val raw = pathSumber.fileSystem.source(pathSumber.path)
        val sourceTar = if (format == FormatArsip.TGZ) {
            adapterPlatform.bukaSumberGzip(raw)
        } else {
            raw
        }
        return sourceTar.buffer()
    }

    private suspend fun salinSumberKeTemp(
        lokasi: LokasiBerkas,
        fileSystemLokal: FileSystem,
        pathTempIsi: Path
    ): HasilOperasi<Long> {
        val reader = when (val buka = repositoriBerkas.bukaUntukBaca(lokasi)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return buka
        }
        var total = 0L
        try {
            fileSystemLokal.sink(pathTempIsi, mustCreate = false).buffer().use { dst ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val pot = reader.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val data = pot.potongan.data
                            if (data.isNotEmpty()) {
                                dst.write(data)
                                total += data.size
                            }
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            return HasilOperasi.Gagal(pot.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                    }
                }
                dst.flush()
            }
            return HasilOperasi.Sukses(total)
        } finally {
            withContext(NonCancellable) { reader.tutup() }
        }
    }

    private suspend fun kumpulkanItemRekursif(
        daftarSumber: List<ItemSumberKompresi>
    ): HasilOperasi<List<ItemSiapTar>> {
        val hasil = mutableListOf<ItemSiapTar>()
        val jalurSudahAda = HashSet<String>()
        var galatBaca: HasilOperasi.Gagal? = null

        suspend fun telusuri(lokasi: LokasiBerkas, jalurRelatif: String, adalahFolder: Boolean, waktuMs: Long?) {
            currentCoroutineContext().ensureActive()
            if (galatBaca != null) return
            val validasi = ValidasiPathArsip.validasi(jalurRelatif, adalahFolder)
            if (validasi !is HasilValidasiPathArsip.Sah) return
            val kunci = validasi.jalurNormal
            if (!jalurSudahAda.add(kunci)) return

            if (!adalahFolder) {
                hasil.add(ItemSiapTar(lokasi, kunci, false, waktuMs))
                return
            }
            hasil.add(ItemSiapTar(lokasi, kunci, true, waktuMs))
            val anakHasil = repositoriBerkas.daftarIsi(lokasi).first()
            val daftarAnak = when (anakHasil) {
                is HasilOperasi.Sukses -> anakHasil.nilai
                is HasilOperasi.Gagal -> {
                    // Jangan diam-diam membuang isi folder: gagalkan seluruh kompresi.
                    galatBaca = HasilOperasi.Gagal(
                        "Gagal membaca isi folder '$kunci': ${anakHasil.pesan}",
                        anakHasil.penyebab
                    )
                    return
                }
            }
            for (anak in daftarAnak) {
                if (anak.jenis == JenisBerkas.TAUTAN) continue
                telusuri(
                    lokasi = anak.lokasi,
                    jalurRelatif = "$kunci/${anak.nama}",
                    adalahFolder = anak.jenis == JenisBerkas.FOLDER,
                    waktuMs = anak.waktuUbahEpochMs
                )
            }
        }

        for (s in daftarSumber) {
            telusuri(s.lokasi, s.namaDalamArsip, s.adalahFolder, null)
        }
        galatBaca?.let { return it }
        return if (hasil.isEmpty()) {
            HasilOperasi.Gagal("Tidak ada berkas/folder sah untuk diarsipkan", PenyebabGalat.VALIDASI)
        } else {
            HasilOperasi.Sukses(hasil)
        }
    }

    private suspend fun bacaEntriBerikutnya(
        src: BufferedSource
    ): HasilBacaHeaderTar {
        var paxPath: String? = null
        var paxSize: Long? = null
        var gnuLongName: String? = null
        var headerNolBerturut = 0

        while (true) {
            val blok = bacaBlokHeaderOrNull(src) ?: return if (headerNolBerturut > 0) {
                HasilBacaHeaderTar.Akhir
            } else {
                HasilBacaHeaderTar.Galat("Aliran TAR terpotong sebelum penanda akhir arsip")
            }

            if (blok.all { it == 0.toByte() }) {
                headerNolBerturut++
                if (headerNolBerturut >= 2 || src.exhausted()) {
                    return HasilBacaHeaderTar.Akhir
                }
                continue
            }
            headerNolBerturut = 0

            if (!verifikasiChecksumHeaderTar(blok)) {
                return HasilBacaHeaderTar.Galat("Header TAR tidak sah atau checksum rusak")
            }

            val tipe = blok[156].toInt().toChar()
            val ukuran = paxSize ?: parseUkuranTar(blok)
                ?: return HasilBacaHeaderTar.Galat("Ukuran entri pada header TAR tidak valid")

            if (tipe == 'x' || tipe == 'g') {
                if (ukuran > BATAS_PAX_HEADER_BYTE) {
                    return HasilBacaHeaderTar.Galat("Header PAX TAR terlalu besar")
                }
                val paxBytes = if (ukuran > 0L) src.readByteArray(ukuran) else ByteArray(0)
                val pad = hitungPaddingBlok(ukuran)
                if (pad > 0L) lewatiTepat(src, pad)
                if (tipe == 'x') {
                    val parsedPax = parseAtributPax(paxBytes.decodeToString())
                    paxPath = parsedPax["path"] ?: paxPath
                    parsedPax["size"]?.toLongOrNull()?.takeIf { it >= 0L }?.let { paxSize = it }
                }
                continue
            }

            if (tipe == 'L') {
                if (ukuran > BATAS_PAX_HEADER_BYTE) {
                    return HasilBacaHeaderTar.Galat("Nama jalur GNU TAR terlalu panjang")
                }
                val nameBytes = if (ukuran > 0L) src.readByteArray(ukuran) else ByteArray(0)
                val pad = hitungPaddingBlok(ukuran)
                if (pad > 0L) lewatiTepat(src, pad)
                gnuLongName = nameBytes.decodeToString().trimEnd('\u0000')
                continue
            }

            val namaDasar = bacaStringNul(blok, 0, 100)
            val prefix = bacaStringNul(blok, 345, 155)
            val jalurGabung = when {
                !paxPath.isNullOrEmpty() -> paxPath
                !gnuLongName.isNullOrEmpty() -> gnuLongName
                prefix.isNotEmpty() && namaDasar.isNotEmpty() -> "$prefix/$namaDasar"
                prefix.isNotEmpty() -> prefix
                else -> namaDasar
            }

            val mtimeDetik = parseOktalTar(blok, 136, 12)
            val waktuMs = if (mtimeDetik != null && mtimeDetik > 0L) mtimeDetik * 1000L else null
            val adalahFolder = tipe == '5' || (tipe == '\u0000' && jalurGabung.endsWith('/'))
            val adalahTautan = tipe == '2' || tipe == '1'

            return HasilBacaHeaderTar.Entri(
                HeaderTar(
                    jalurMentah = jalurGabung,
                    adalahFolder = adalahFolder,
                    adalahTautan = adalahTautan,
                    ukuranByte = if (adalahFolder) 0L else ukuran,
                    waktuUbahEpochMs = waktuMs
                )
            )
        }
    }

    private fun bacaBlokHeaderOrNull(src: BufferedSource): ByteArray? {
        if (src.exhausted()) return null
        val blok = ByteArray(UKURAN_BLOK_TAR)
        var offset = 0
        while (offset < UKURAN_BLOK_TAR) {
            val n = src.read(blok, offset, UKURAN_BLOK_TAR - offset)
            if (n == -1) {
                return if (offset == 0) null else throw EOFException("Blok header TAR tidak lengkap")
            }
            offset += n
        }
        return blok
    }

    private fun verifikasiChecksumHeaderTar(blok: ByteArray): Boolean {
        val checksumTersimpan = parseOktalTar(blok, 148, 8) ?: return false
        var sumUnsigned = 0L
        var sumSigned = 0L
        for (i in 0 until UKURAN_BLOK_TAR) {
            if (i in 148..155) {
                sumUnsigned += 32L
                sumSigned += 32L
            } else {
                sumUnsigned += (blok[i].toInt() and 0xFF).toLong()
                sumSigned += blok[i].toLong()
            }
        }
        return checksumTersimpan == sumUnsigned || checksumTersimpan == sumSigned
    }

    private fun parseUkuranTar(blok: ByteArray): Long? {
        val pertama = blok[124].toInt() and 0xFF
        if ((pertama and 0x80) != 0) {
            if ((pertama and 0x40) != 0) return null
            var nilai = (pertama and 0x7F).toLong()
            for (i in 125 until 136) {
                if (nilai > (Long.MAX_VALUE ushr 8)) return null
                nilai = (nilai shl 8) or (blok[i].toInt() and 0xFF).toLong()
            }
            return nilai
        }
        return parseOktalTar(blok, 124, 12)
    }

    private fun parseOktalTar(blok: ByteArray, offset: Int, panjang: Int): Long? {
        var i = offset
        val batas = offset + panjang
        while (i < batas && (blok[i] == ' '.code.toByte() || blok[i] == 0.toByte())) {
            i++
        }
        if (i >= batas) return 0L
        var hasil = 0L
        var adaDigit = false
        while (i < batas) {
            val b = blok[i]
            if (b == 0.toByte() || b == ' '.code.toByte()) break
            val ch = b.toInt().toChar()
            if (ch !in '0'..'7') return null
            adaDigit = true
            hasil = (hasil shl 3) or (ch - '0').toLong()
            i++
        }
        return if (adaDigit) hasil else 0L
    }

    private fun bacaStringNul(blok: ByteArray, offset: Int, panjang: Int): String {
        var akhir = offset
        val batas = offset + panjang
        while (akhir < batas && blok[akhir] != 0.toByte()) {
            akhir++
        }
        return blok.decodeToString(offset, akhir)
    }

    private fun parseAtributPax(teks: String): Map<String, String> {
        val hasil = HashMap<String, String>()
        for (baris in teks.split('\n')) {
            if (baris.isEmpty()) continue
            val spasi = baris.indexOf(' ')
            if (spasi <= 0) continue
            val pasangan = baris.substring(spasi + 1)
            val eq = pasangan.indexOf('=')
            if (eq > 0) {
                hasil[pasangan.substring(0, eq)] = pasangan.substring(eq + 1)
            }
        }
        return hasil
    }

    private fun hitungPaddingBlok(ukuran: Long): Long {
        val sisa = ukuran % UKURAN_BLOK_TAR
        return if (sisa == 0L) 0L else UKURAN_BLOK_TAR - sisa
    }

    private suspend fun lewatiTepat(src: BufferedSource, jumlahByte: Long) {
        var sisa = jumlahByte
        while (sisa > 0L) {
            currentCoroutineContext().ensureActive()
            val langkah = minOf(sisa, 8192L)
            src.skip(langkah)
            sisa -= langkah
        }
    }

    private fun tulisEntriHeaderTar(
        out: okio.BufferedSink,
        jalur: String,
        ukuranByte: Long,
        adalahFolder: Boolean,
        waktuUbahEpochMs: Long?
    ) {
        val jalurBytes = jalur.encodeToByteArray()
        var namaUntukHeader = jalur
        var prefixUntukHeader = ""

        if (jalurBytes.size > 100) {
            val split = pisahUstarPrefixDanNama(jalur)
            if (split != null) {
                prefixUntukHeader = split.first
                namaUntukHeader = split.second
            } else {
                val dataPax = bentukRecordPax("path", jalur)
                val paxHeader = bangunBlokUstar(
                    nama = "PaxHeaders.0/${jalur.substringAfterLast('/').take(48)}",
                    prefix = "",
                    ukuranByte = dataPax.size.toLong(),
                    adalahFolder = false,
                    waktuDetik = 0L,
                    tipeFlag = 'x'.code.toByte()
                )
                out.write(paxHeader)
                out.write(dataPax)
                val padPax = hitungPaddingBlok(dataPax.size.toLong())
                if (padPax > 0L) out.write(ByteArray(padPax.toInt()))

                namaUntukHeader = StringBuilder().apply {
                    val potong = jalur.take(96)
                    append(potong)
                }.toString()
                while (namaUntukHeader.encodeToByteArray().size > 99) {
                    namaUntukHeader = namaUntukHeader.dropLast(1)
                }
            }
        }

        val mtimeDetik = if (waktuUbahEpochMs != null && waktuUbahEpochMs > 0L) {
            waktuUbahEpochMs / 1000L
        } else {
            0L
        }
        val header = bangunBlokUstar(
            nama = namaUntukHeader,
            prefix = prefixUntukHeader,
            ukuranByte = ukuranByte,
            adalahFolder = adalahFolder,
            waktuDetik = mtimeDetik,
            tipeFlag = if (adalahFolder) '5'.code.toByte() else '0'.code.toByte()
        )
        out.write(header)
    }

    private fun pisahUstarPrefixDanNama(jalur: String): Pair<String, String>? {
        var idx = jalur.indexOf('/')
        while (idx > 0) {
            val prefix = jalur.substring(0, idx)
            val nama = jalur.substring(idx + 1)
            if (prefix.encodeToByteArray().size <= 155 && nama.encodeToByteArray().size <= 100) {
                return prefix to nama
            }
            idx = jalur.indexOf('/', idx + 1)
        }
        return null
    }

    private fun bentukRecordPax(kunci: String, nilai: String): ByteArray {
        val tubuh = " $kunci=$nilai\n"
        val panjangTubuh = tubuh.encodeToByteArray().size
        var total = panjangTubuh + 1
        while (true) {
            val digitLen = total.toString().length
            if (digitLen + panjangTubuh == total) {
                return "$total$tubuh".encodeToByteArray()
            }
            total = digitLen + panjangTubuh
        }
    }

    private fun bangunBlokUstar(
        nama: String,
        prefix: String,
        ukuranByte: Long,
        adalahFolder: Boolean,
        waktuDetik: Long,
        tipeFlag: Byte
    ): ByteArray {
        val blok = ByteArray(UKURAN_BLOK_TAR)
        val namaBytes = nama.encodeToByteArray()
        namaBytes.copyInto(blok, 0, 0, minOf(namaBytes.size, 100))

        tulisOktal(blok, 100, 8, if (adalahFolder) 0b111_101_101L else 0b110_100_100L)
        tulisOktal(blok, 108, 8, 0L)
        tulisOktal(blok, 116, 8, 0L)
        tulisUkuranTar(blok, 124, 12, ukuranByte)
        tulisOktal(blok, 136, 12, waktuDetik.coerceAtLeast(0L))

        for (i in 148..155) {
            blok[i] = ' '.code.toByte()
        }
        blok[156] = tipeFlag

        "ustar\u0000".encodeToByteArray().copyInto(blok, 257)
        "00".encodeToByteArray().copyInto(blok, 263)

        if (prefix.isNotEmpty()) {
            val prefBytes = prefix.encodeToByteArray()
            prefBytes.copyInto(blok, 345, 0, minOf(prefBytes.size, 155))
        }

        var sum = 0L
        for (b in blok) {
            sum += (b.toInt() and 0xFF).toLong()
        }
        val oktalSum = sum.toString(8).padStart(6, '0')
        oktalSum.encodeToByteArray().copyInto(blok, 148, 0, 6)
        blok[154] = 0
        blok[155] = ' '.code.toByte()
        return blok
    }

    private fun tulisUkuranTar(blok: ByteArray, offset: Int, panjang: Int, nilai: Long) {
        if (nilai <= 0x1FFFFFFFFL) {
            tulisOktal(blok, offset, panjang, nilai)
        } else {
            var v = nilai
            for (i in (offset + panjang - 1) downTo (offset + 1)) {
                blok[i] = (v and 0xFFL).toByte()
                v = v ushr 8
            }
            blok[offset] = 0x80.toByte()
        }
    }

    private fun tulisOktal(blok: ByteArray, offset: Int, panjang: Int, nilai: Long) {
        val digit = nilai.toString(8).padStart(panjang - 1, '0')
        val potong = if (digit.length > panjang - 1) digit.takeLast(panjang - 1) else digit
        potong.encodeToByteArray().copyInto(blok, offset)
        blok[offset + panjang - 1] = 0
    }

    private data class ItemSiapTar(
        val lokasi: LokasiBerkas,
        val jalurDalamArsip: String,
        val adalahFolder: Boolean,
        val waktuUbahEpochMs: Long?
    )

    private data class HeaderTar(
        val jalurMentah: String,
        val adalahFolder: Boolean,
        val adalahTautan: Boolean,
        val ukuranByte: Long,
        val waktuUbahEpochMs: Long?
    )

    private sealed interface HasilBacaHeaderTar {
        data class Entri(val data: HeaderTar) : HasilBacaHeaderTar
        data object Akhir : HasilBacaHeaderTar
        data class Galat(val pesan: String) : HasilBacaHeaderTar
    }
}

private class SumberTerbatasOkio(
    private val sumber: BufferedSource,
    private val totalBatas: Long
) : Source {
    private var sisa = totalBatas
    val sisaByte: Long get() = sisa

    override fun timeout(): Timeout = sumber.timeout()

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        if (sisa <= 0L) return -1L
        val dibaca = sumber.read(sink, minOf(byteCount, sisa))
        if (dibaca == -1L) {
            throw EOFException("Isi entri TAR terpotong sebelum selesai")
        }
        sisa -= dibaca
        return dibaca
    }

    override fun close() {
        // Sumber TAR dikelola oleh pemanggil (tidak menutup stream utama saat masih ada sisa entri).
    }
}