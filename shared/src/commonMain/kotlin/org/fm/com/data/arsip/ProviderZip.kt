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
import okio.FileSystem
import okio.Path
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
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class ProviderZip(
    private val repositoriBerkas: RepositoriBerkas,
    private val repositoriOperasi: RepositoriOperasiBerkas,
    private val adapterPlatform: AdapterArsipPlatform = buatAdapterArsipPlatform(),
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ArsipProvider {

    override val formatDidukung: Set<FormatArsip> = setOf(FormatArsip.ZIP)

    override fun kapabilitas(format: FormatArsip): KapabilitasArsip? =
        if (format in formatDidukung) MatriksArsip.untuk(format) else null

    override suspend fun daftarEntri(
        sumber: LokasiBerkas,
        format: FormatArsip,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<RingkasanIsiArsip> = withContext(ioDispatcher) {
        if (format != FormatArsip.ZIP && format != FormatArsip.JAR && format != FormatArsip.ZIP_LAIN) {
            return@withContext HasilOperasi.Gagal("Format bukan ZIP: ${format.namaTampilan}", PenyebabGalat.VALIDASI)
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
            bacaMetadataZipDariPath(pathSumber, format, batas)
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
        val validasiTarget = ValidasiPathArsip.validasi(jalurInternal, adalahFolder = false)
        val jalurAman = when (validasiTarget) {
            is HasilValidasiPathArsip.Sah -> validasiTarget.jalurNormal
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Tidak dapat membaca akar arsip sebagai berkas", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(validasiTarget.alasan, PenyebabGalat.VALIDASI)
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
            val hasilMeta = bacaMetadataZipDariPath(pathSumber, format, batas)
            val ringkasan = when (hasilMeta) {
                is HasilOperasi.Sukses -> hasilMeta.nilai
                is HasilOperasi.Gagal -> {
                    cleanupTemp()
                    return@withContext hasilMeta
                }
            }

            val entriTarget = ringkasan.daftarEntri.firstOrNull { it.jalurInternal == jalurAman }
                ?: run {
                    cleanupTemp()
                    return@withContext HasilOperasi.Gagal(
                        "Entri '$jalurAman' tidak ditemukan dalam arsip",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                }

            if (entriTarget.adalahFolder) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal("Entri '$jalurAman' adalah folder", PenyebabGalat.VALIDASI)
            }
            if (entriTarget.terenkripsi) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    "Entri '$jalurAman' terenkripsi dan membutuhkan kata sandi (belum didukung)",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }
            if (entriTarget.adalahTautanSimbolik) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    "Entri '$jalurAman' adalah tautan simbolik dan ditolak demi keamanan",
                    PenyebabGalat.VALIDASI
                )
            }
            val ukuran = entriTarget.ukuranTidakTerkompresiByte
            if (ukuran != null && ukuran > batas.batasUkuranSatuEntriByte) {
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    "Ukuran entri '$jalurAman' melebihi batas baca (${batas.batasUkuranSatuEntriByte} byte)",
                    PenyebabGalat.VALIDASI
                )
            }

            val zipTerbuka = adapterPlatform.bukaZipDariPath(pathSumber.fileSystem, pathSumber.path)
            val sourceEntri = try {
                zipTerbuka.bukaSumberEntri(jalurAman)
            } catch (e: CancellationException) {
                zipTerbuka.tutup()
                cleanupTemp()
                throw e
            } catch (e: Exception) {
                zipTerbuka.tutup()
                cleanupTemp()
                return@withContext HasilOperasi.Gagal(
                    petakanPesanErrorZip(e, "Gagal membuka entri '$jalurAman'"),
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            }

            HasilOperasi.Sukses(
                SumberIsiOkioTerkelola(
                    ukuranByte = ukuran,
                    source = sourceEntri,
                    batasUkuranByte = batas.batasUkuranSatuEntriByte,
                    pembersihAkhir = {
                        zipTerbuka.tutup()
                        cleanupTemp()
                    }
                )
            )
        } catch (e: CancellationException) {
            cleanupTemp()
            throw e
        } catch (e: Exception) {
            cleanupTemp()
            HasilOperasi.Gagal(
                petakanPesanErrorZip(e, "Gagal membaca entri '$jalurAman' dari arsip ZIP"),
                PenyebabGalat.TIDAK_DIKETAHUI
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

        var zipTerbuka: ArsipZipTerbuka? = null
        try {
            val hasilMeta = bacaMetadataZipDariPath(pathSumber, format, opsi.batas, tolakZipSlipFatal = true)
            val ringkasan = when (hasilMeta) {
                is HasilOperasi.Sukses -> hasilMeta.nilai
                is HasilOperasi.Gagal -> return@withContext hasilMeta
            }

            val targetEkstrak = ringkasan.daftarEntri.filter { entri ->
                ValidasiPathArsip.apakahCocokFilterEkstrak(entri.jalurInternal, opsi.jalurTerpilih)
            }

            zipTerbuka = adapterPlatform.bukaZipDariPath(pathSumber.fileSystem, pathSumber.path)

            val cacheFolder = HashMap<String, LokasiBerkas>()
            val cacheIsiFolder = HashMap<LokasiBerkas, MutableMap<String, EntitasBerkas>>()
            val daftarBerkasHasil = mutableListOf<EntitasBerkas>()
            val daftarKonflik = mutableListOf<String>()
            val daftarGagal = mutableListOf<ItemGagalArsip>()

            var jumlahDiekstrak = 0
            var jumlahDilewati = 0
            var totalByte = 0L
            val totalItem = targetEkstrak.size

            for ((idx, entri) in targetEkstrak.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgres(idx + 1, totalItem, entri.jalurInternal)

                val validasi = ValidasiPathArsip.validasi(entri.jalurInternal, entri.adalahFolder)
                val pathSah = when (validasi) {
                    is HasilValidasiPathArsip.Sah -> validasi
                    is HasilValidasiPathArsip.AbaikanRoot -> continue
                    is HasilValidasiPathArsip.TidakSah -> {
                        daftarGagal.add(ItemGagalArsip(entri.jalurInternal, validasi.alasan, PenyebabGalat.VALIDASI))
                        continue
                    }
                }

                if (entri.adalahTautanSimbolik) {
                    daftarGagal.add(
                        ItemGagalArsip(
                            entri.jalurInternal,
                            "Tautan simbolik dalam arsip dilewati demi keamanan",
                            PenyebabGalat.VALIDASI
                        )
                    )
                    continue
                }

                if (entri.terenkripsi) {
                    daftarGagal.add(
                        ItemGagalArsip(
                            entri.jalurInternal,
                            "Entri terenkripsi memerlukan kata sandi (belum didukung)",
                            PenyebabGalat.TIDAK_DIDUKUNG
                        )
                    )
                    continue
                }

                if (entri.adalahFolder) {
                    when (
                        val buatDir = BantuArsipPenyimpanan.pastikanFolderTujuan(
                            daftarSegmenFolder = pathSah.daftarSegmen,
                            folderAkarTujuan = folderTujuan,
                            repositoriBerkas = repositoriBerkas,
                            repositoriOperasi = repositoriOperasi,
                            cacheFolder = cacheFolder,
                            cacheIsiFolder = cacheIsiFolder
                        )
                    ) {
                        is HasilOperasi.Sukses -> Unit
                        is HasilOperasi.Gagal -> {
                            daftarGagal.add(
                                ItemGagalArsip(entri.jalurInternal, buatDir.pesan, buatDir.penyebab)
                            )
                        }
                    }
                    continue
                }

                val ukuranPerkiraan = entri.ukuranTidakTerkompresiByte
                if (ukuranPerkiraan != null && ukuranPerkiraan > opsi.batas.batasUkuranSatuEntriByte) {
                    daftarGagal.add(
                        ItemGagalArsip(
                            entri.jalurInternal,
                            "Ukuran berkas melebihi batas satu entri (${opsi.batas.batasUkuranSatuEntriByte} byte)",
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

                val folderIndukTujuan = when (
                    val resFolder = BantuArsipPenyimpanan.pastikanFolderTujuan(
                        daftarSegmenFolder = segmenFolder,
                        folderAkarTujuan = folderTujuan,
                        repositoriBerkas = repositoriBerkas,
                        repositoriOperasi = repositoriOperasi,
                        cacheFolder = cacheFolder,
                        cacheIsiFolder = cacheIsiFolder
                    )
                ) {
                    is HasilOperasi.Sukses -> resFolder.nilai
                    is HasilOperasi.Gagal -> {
                        daftarGagal.add(
                            ItemGagalArsip(entri.jalurInternal, resFolder.pesan, resFolder.penyebab)
                        )
                        continue
                    }
                }

                val sisaBatasTotal = (opsi.batas.batasUkuranTotalEkstrakByte - totalByte).coerceAtLeast(0L)
                val hasilTulis = try {
                    zipTerbuka.bukaSumberEntri(pathSah.jalurNormal).use { sumberEntri ->
                        BantuArsipPenyimpanan.tulisEntriHasilKeRepositori(
                            source = sumberEntri,
                            indukTujuan = folderIndukTujuan,
                            namaBerkas = pathSah.namaSegmenAkhir,
                            kebijakanKonflik = opsi.kebijakanKonflik,
                            batasSatuEntriByte = opsi.batas.batasUkuranSatuEntriByte,
                            sisaBatasTotalByte = sisaBatasTotal,
                            repositoriBerkas = repositoriBerkas,
                            cacheIsiFolder = cacheIsiFolder
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HasilTulisEntriEkstrak.Gagal(
                        petakanPesanErrorZip(e, "Gagal mengekstrak '${entri.jalurInternal}'"),
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }

                when (hasilTulis) {
                    is HasilTulisEntriEkstrak.Sukses -> {
                        jumlahDiekstrak++
                        totalByte += hasilTulis.byteDitulis
                        daftarBerkasHasil.add(hasilTulis.entitas)
                    }
                    is HasilTulisEntriEkstrak.Dilewati -> jumlahDilewati++
                    is HasilTulisEntriEkstrak.Konflik -> daftarKonflik.add(entri.jalurInternal)
                    is HasilTulisEntriEkstrak.Gagal -> daftarGagal.add(
                        ItemGagalArsip(entri.jalurInternal, hasilTulis.pesan, hasilTulis.penyebab)
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
                petakanPesanErrorZip(e, "Gagal mengekstrak arsip ZIP"),
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { zipTerbuka?.tutup() }
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
        if (!ValidasiNama.sah(namaArsip)) {
            return@withContext HasilOperasi.Gagal("Nama arsip tidak sah: $namaArsip", PenyebabGalat.VALIDASI)
        }
        if (kataSandi != null) {
            return@withContext HasilOperasi.Gagal(
                "Pembuatan arsip terenkripsi kata sandi belum didukung",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        if (daftarSumber.isEmpty()) {
            return@withContext HasilOperasi.Gagal("Pilih minimal satu item untuk diarsipkan", PenyebabGalat.VALIDASI)
        }

        val itemDiarsipkan = kumpulkanItemRekursifUntukArsip(daftarSumber)
        val daftarItemSiap = when (itemDiarsipkan) {
            is HasilOperasi.Sukses -> itemDiarsipkan.nilai
            is HasilOperasi.Gagal -> return@withContext itemDiarsipkan
        }

        val fileSystemLokal = adapterPlatform.sistemBerkasLokal
        val pathTempZip = BantuArsipPenyimpanan.buatPathCacheSementara("zip_out", ".zip", direktoriTemp)
        val penulis = adapterPlatform.bukaPenulisZip(fileSystemLokal, pathTempZip)
        var penulisanSukses = false

        try {
            val total = daftarItemSiap.size
            for ((idx, item) in daftarItemSiap.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgres(idx + 1, total, item.jalurDalamArsip)

                penulis.mulaiEntri(
                    jalurInternal = item.jalurDalamArsip,
                    adalahFolder = item.adalahFolder,
                    waktuUbahEpochMs = item.waktuUbahEpochMs
                )
                if (!item.adalahFolder) {
                    val sumberBaca = when (val buka = repositoriBerkas.bukaUntukBaca(item.lokasi)) {
                        is HasilOperasi.Sukses -> buka.nilai
                        is HasilOperasi.Gagal -> return@withContext buka
                    }
                    try {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            when (val pot = sumberBaca.potongan()) {
                                is SumberIsiBerkas.HasilPotongan.Isi -> {
                                    val data = pot.potongan.data
                                    if (data.isNotEmpty()) {
                                        penulis.tulisPotongan(data, 0, data.size)
                                    }
                                    if (pot.potongan.selesai) break
                                }
                                is SumberIsiBerkas.HasilPotongan.Gagal -> {
                                    return@withContext HasilOperasi.Gagal(pot.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) { sumberBaca.tutup() }
                    }
                }
                penulis.tutupEntri()
            }

            // Verifikasi jumlah byte tertulis minimal & selesaikan EOCD
            penulis.selesaikanDanTutup()
            penulisanSukses = true

            // Verifikasi arsip ZIP hasil bisa dibuka kembali sebelum menyalin ke tujuan akhir
            val cekBaca = runCatching {
                val cek = adapterPlatform.bukaZipDariPath(fileSystemLokal, pathTempZip)
                cek.daftarJalurRekursif()
                cek.tutup()
            }
            if (cekBaca.isFailure) {
                return@withContext HasilOperasi.Gagal(
                    cekBaca.exceptionOrNull()?.message ?: "Verifikasi arsip ZIP baru gagal",
                    PenyebabGalat.TIDAK_DIKETAHUI
                )
            }

            BantuArsipPenyimpanan.tulisFileTempKeRepositori(
                fileSystemLokal = fileSystemLokal,
                pathTempSumber = pathTempZip,
                tujuanInduk = tujuanInduk,
                namaArsip = namaArsip,
                timpa = konfirmasiTimpa,
                repositoriBerkas = repositoriBerkas
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membuat arsip ZIP", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                if (!penulisanSukses) {
                    penulis.batalkanDanTutup()
                }
                runCatching { fileSystemLokal.delete(pathTempZip, mustExist = false) }
            }
        }
    }

    private suspend fun kumpulkanItemRekursifUntukArsip(
        daftarSumber: List<ItemSumberKompresi>
    ): HasilOperasi<List<ItemSiapArsip>> {
        val hasil = mutableListOf<ItemSiapArsip>()
        val jalurSudahAda = HashSet<String>()

        suspend fun telusuri(lokasi: LokasiBerkas, jalurRelatif: String, adalahFolder: Boolean, waktuMs: Long?) {
            currentCoroutineContext().ensureActive()
            val validasi = ValidasiPathArsip.validasi(jalurRelatif, adalahFolder)
            if (validasi !is HasilValidasiPathArsip.Sah) return
            val kunci = validasi.jalurNormal
            if (!jalurSudahAda.add(kunci)) return

            if (!adalahFolder) {
                hasil.add(ItemSiapArsip(lokasi, kunci, false, waktuMs))
                return
            }

            hasil.add(ItemSiapArsip(lokasi, kunci, true, waktuMs))
            val anakHasil = repositoriBerkas.daftarIsi(lokasi).first()
            if (anakHasil is HasilOperasi.Sukses) {
                for (anak in anakHasil.nilai) {
                    if (anak.jenis == org.fm.com.domain.model.JenisBerkas.TAUTAN) continue
                    telusuri(
                        lokasi = anak.lokasi,
                        jalurRelatif = "$kunci/${anak.nama}",
                        adalahFolder = anak.jenis == org.fm.com.domain.model.JenisBerkas.FOLDER,
                        waktuMs = anak.waktuUbahEpochMs
                    )
                }
            }
        }

        for (item in daftarSumber) {
            telusuri(item.lokasi, item.namaDalamArsip, item.adalahFolder, null)
        }
        return if (hasil.isEmpty()) {
            HasilOperasi.Gagal("Tidak ada berkas/folder sah untuk diarsipkan", PenyebabGalat.VALIDASI)
        } else {
            HasilOperasi.Sukses(hasil)
        }
    }

    private fun bacaMetadataZipDariPath(
        pathSumber: SumberPathLokal.Tersedia,
        format: FormatArsip,
        batas: KebijakanBatasArsip,
        tolakZipSlipFatal: Boolean = true
    ): HasilOperasi<RingkasanIsiArsip> {
        val cdInfo = scanCentralDirectoryZip(pathSumber.fileSystem, pathSumber.path, batas.batasJumlahEntriMaks)
        when (cdInfo) {
            is HasilScanCd.Rusak -> return HasilOperasi.Gagal(cdInfo.pesan, PenyebabGalat.VALIDASI)
            is HasilScanCd.Valid -> {
                if (tolakZipSlipFatal) {
                    for (entry in cdInfo.entri) {
                        val valPath = ValidasiPathArsip.validasi(entry.rawName, entry.adalahFolder)
                        if (valPath is HasilValidasiPathArsip.TidakSah) {
                            return HasilOperasi.Gagal(valPath.alasan, PenyebabGalat.VALIDASI)
                        }
                    }
                }
            }
            HasilScanCd.Zip64GunakanOkio -> Unit
        }

        val petaCd = if (cdInfo is HasilScanCd.Valid) {
            cdInfo.entri.associateBy {
                val valP = ValidasiPathArsip.validasi(it.rawName, it.adalahFolder)
                if (valP is HasilValidasiPathArsip.Sah) valP.jalurNormal else it.rawName
            }
        } else {
            emptyMap()
        }

        val zipTerbuka = try {
            adapterPlatform.bukaZipDariPath(pathSumber.fileSystem, pathSumber.path)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(
                petakanPesanErrorZip(e, "Berkas bukan arsip ZIP yang sah atau arsip rusak"),
                PenyebabGalat.VALIDASI
            )
        }

        return try {
            val mentah = zipTerbuka.daftarJalurRekursif()
            if (mentah.size > batas.batasJumlahEntriMaks) {
                return HasilOperasi.Gagal(
                    "Jumlah entri arsip melebihi batas maksimal (${batas.batasJumlahEntriMaks})",
                    PenyebabGalat.VALIDASI
                )
            }

            val daftarEntri = ArrayList<EntriArsip>(mentah.size)
            val sudahAda = HashSet<String>(mentah.size)
            var totalUkuran = 0L
            var adaTidakDiketahui = false

            for (item in mentah) {
                val valPath = ValidasiPathArsip.validasi(item.jalurMentah, item.adalahFolder)
                when (valPath) {
                    is HasilValidasiPathArsip.AbaikanRoot -> continue
                    is HasilValidasiPathArsip.TidakSah -> {
                        if (tolakZipSlipFatal) {
                            return HasilOperasi.Gagal(valPath.alasan, PenyebabGalat.VALIDASI)
                        }
                        continue
                    }
                    is HasilValidasiPathArsip.Sah -> {
                        if (!sudahAda.add(valPath.jalurNormal)) continue
                        val infoCd = petaCd[valPath.jalurNormal]
                        val ukuranAsli = if (item.adalahFolder) null else (infoCd?.uncompressedSize ?: item.ukuranByte)
                        if (!item.adalahFolder) {
                            if (ukuranAsli != null) totalUkuran += ukuranAsli else adaTidakDiketahui = true
                        }
                        daftarEntri.add(
                            EntriArsip(
                                jalurInternal = valPath.jalurNormal,
                                nama = valPath.namaSegmenAkhir,
                                adalahFolder = item.adalahFolder,
                                ukuranTidakTerkompresiByte = ukuranAsli,
                                ukuranTerkompresiByte = if (item.adalahFolder) null else infoCd?.compressedSize,
                                waktuUbahEpochMs = item.waktuUbahEpochMs,
                                terenkripsi = infoCd?.terenkripsi ?: false,
                                metodeKompresi = infoCd?.metodeKompresi,
                                adalahTautanSimbolik = infoCd?.adalahTautan ?: false
                            )
                        )
                    }
                }
            }

            // Tambahkan entri CD terenkripsi atau tidak muncul di Okio jika ada
            if (cdInfo is HasilScanCd.Valid) {
                for (entry in cdInfo.entri) {
                    val valPath = ValidasiPathArsip.validasi(entry.rawName, entry.adalahFolder)
                    if (valPath is HasilValidasiPathArsip.Sah && sudahAda.add(valPath.jalurNormal)) {
                        val ukuranAsli = if (entry.adalahFolder) null else entry.uncompressedSize
                        if (!entry.adalahFolder && ukuranAsli != null) totalUkuran += ukuranAsli
                        daftarEntri.add(
                            EntriArsip(
                                jalurInternal = valPath.jalurNormal,
                                nama = valPath.namaSegmenAkhir,
                                adalahFolder = entry.adalahFolder,
                                ukuranTidakTerkompresiByte = ukuranAsli,
                                ukuranTerkompresiByte = if (entry.adalahFolder) null else entry.compressedSize,
                                waktuUbahEpochMs = null,
                                terenkripsi = entry.terenkripsi,
                                metodeKompresi = entry.metodeKompresi,
                                adalahTautanSimbolik = entry.adalahTautan
                            )
                        )
                    }
                }
            }

            HasilOperasi.Sukses(
                RingkasanIsiArsip(
                    format = format,
                    daftarEntri = daftarEntri.sortedBy { it.jalurInternal.lowercase() },
                    totalUkuranTidakTerkompresiByte = totalUkuran,
                    adaUkuranTidakDiketahui = adaTidakDiketahui
                )
            )
        } catch (e: Exception) {
            HasilOperasi.Gagal(
                petakanPesanErrorZip(e, "Gagal membaca daftar isi arsip ZIP"),
                PenyebabGalat.VALIDASI
            )
        } finally {
            zipTerbuka.tutup()
        }
    }

    private fun praPeriksaZipSlip(
        pathSumber: SumberPathLokal.Tersedia,
        batasEntriMaks: Int
    ): List<Pair<String, String>> {
        val scan = scanCentralDirectoryZip(pathSumber.fileSystem, pathSumber.path, batasEntriMaks)
        if (scan !is HasilScanCd.Valid) return emptyList()
        val ditolak = mutableListOf<Pair<String, String>>()
        for (entry in scan.entri) {
            val res = ValidasiPathArsip.validasi(entry.rawName, entry.adalahFolder)
            if (res is HasilValidasiPathArsip.TidakSah) {
                ditolak.add(entry.rawName to res.alasan)
            }
        }
        return ditolak
    }

    private fun petakanPesanErrorZip(e: Exception, defaultPesan: String): String {
        val msg = e.message?.lowercase().orEmpty()
        return when {
            "encrypt" in msg || "password" in msg || "aes" in msg ->
                "Arsip ZIP terenkripsi dan membutuhkan kata sandi (belum didukung)"
            "unsupported compression" in msg ->
                "Metode kompresi di dalam arsip ZIP ini belum didukung"
            "not a zip" in msg || "end of central directory" in msg || "truncated" in msg ->
                "Berkas bukan arsip ZIP yang sah atau arsip rusak"
            else -> e.message ?: defaultPesan
        }
    }

    private data class ItemSiapArsip(
        val lokasi: LokasiBerkas,
        val jalurDalamArsip: String,
        val adalahFolder: Boolean,
        val waktuUbahEpochMs: Long?
    )

    private data class InfoCdZip(
        val rawName: String,
        val adalahFolder: Boolean,
        val uncompressedSize: Long,
        val compressedSize: Long,
        val terenkripsi: Boolean,
        val adalahTautan: Boolean,
        val metodeKompresi: String
    )

    private sealed interface HasilScanCd {
        data class Valid(val entri: List<InfoCdZip>) : HasilScanCd
        data object Zip64GunakanOkio : HasilScanCd
        data class Rusak(val pesan: String) : HasilScanCd
    }

    private fun scanCentralDirectoryZip(
        fileSystem: FileSystem,
        pathZip: Path,
        batasEntriMaks: Int
    ): HasilScanCd {
        return try {
            fileSystem.openReadOnly(pathZip).use { handle ->
                val ukuran = handle.size()
                if (ukuran < 22L) {
                    return HasilScanCd.Rusak("Berkas bukan arsip ZIP yang sah atau arsip rusak")
                }

                val panjangTail = minOf(ukuran, 65_557L).toInt()
                val offsetTail = ukuran - panjangTail
                val tail = handle.source(offsetTail).buffer().use { it.readByteArray(panjangTail.toLong()) }

                var posisiEocd = -1
                for (i in (tail.size - 22) downTo 0) {
                    if (tail[i] == 0x50.toByte() &&
                        tail[i + 1] == 0x4B.toByte() &&
                        tail[i + 2] == 0x05.toByte() &&
                        tail[i + 3] == 0x06.toByte()
                    ) {
                        val commentLen = u16Le(tail, i + 20)
                        if (i + 22 + commentLen == tail.size) {
                            posisiEocd = i
                            break
                        }
                    }
                }

                if (posisiEocd < 0) {
                    return HasilScanCd.Rusak("Penanda End of Central Directory ZIP tidak ditemukan (arsip rusak)")
                }

                val jumlahEntri = u16Le(tail, posisiEocd + 10)
                val cdSize = u32Le(tail, posisiEocd + 12)
                val cdOffset = u32Le(tail, posisiEocd + 16)

                if (jumlahEntri == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
                    return HasilScanCd.Zip64GunakanOkio
                }
                if (jumlahEntri > batasEntriMaks) {
                    return HasilScanCd.Rusak("Jumlah entri arsip melebihi batas maksimal ($batasEntriMaks)")
                }
                val posisiEocdAbsolut = offsetTail + posisiEocd
                if (cdOffset + cdSize > posisiEocdAbsolut) {
                    return HasilScanCd.Rusak("Struktur Central Directory ZIP tidak konsisten (arsip rusak)")
                }

                val entri = ArrayList<InfoCdZip>(jumlahEntri)
                handle.source(cdOffset).buffer().use { src ->
                    val hdr = Buffer()
                    repeat(jumlahEntri) {
                        src.readFully(hdr, 46L)
                        val sig = hdr.readIntLe()
                        if (sig != 0x02014B50) {
                            return HasilScanCd.Rusak("Header entri ZIP rusak (signature Central Directory tidak cocok)")
                        }
                        hdr.skip(4) // versionMadeBy, versionNeeded
                        val bitFlag = hdr.readShortLe().toInt() and 0xFFFF
                        val method = hdr.readShortLe().toInt() and 0xFFFF
                        hdr.skip(8) // time, date, crc32
                        val compressedSize = hdr.readIntLe().toLong() and 0xFFFFFFFFL
                        val uncompressedSize = hdr.readIntLe().toLong() and 0xFFFFFFFFL
                        val nameLen = hdr.readShortLe().toInt() and 0xFFFF
                        val extraLen = hdr.readShortLe().toInt() and 0xFFFF
                        val commentLen = hdr.readShortLe().toInt() and 0xFFFF
                        hdr.skip(4) // diskStart, internalAttr
                        val externalAttr = hdr.readIntLe().toLong() and 0xFFFFFFFFL
                        hdr.skip(4) // localHeaderOffset

                        val nameBytes = src.readByteArray(nameLen.toLong())
                        if (extraLen > 0) src.skip(extraLen.toLong())
                        if (commentLen > 0) src.skip(commentLen.toLong())

                        val rawName = nameBytes.decodeToString()
                        val adalahFolder = rawName.endsWith("/") ||
                            rawName.endsWith("\\") ||
                            ((externalAttr and 0x10L) != 0L && uncompressedSize == 0L)
                        val adalahTautan = ((externalAttr ushr 16) and 0xF000L) == 0xA000L
                        val terenkripsi = (bitFlag and 0x0001) != 0
                        val namaMetode = when (method) {
                            0 -> "STORED"
                            8 -> "DEFLATED"
                            99 -> "AES"
                            else -> "M$method"
                        }
                        entri.add(
                            InfoCdZip(
                                rawName = rawName,
                                adalahFolder = adalahFolder,
                                uncompressedSize = uncompressedSize,
                                compressedSize = compressedSize,
                                terenkripsi = terenkripsi,
                                adalahTautan = adalahTautan,
                                metodeKompresi = namaMetode
                            )
                        )
                    }
                }
                HasilScanCd.Valid(entri)
            }
        } catch (e: Exception) {
            HasilScanCd.Rusak(e.message ?: "Gagal memvalidasi struktur ZIP (arsip rusak)")
        }
    }

    private fun u16Le(buf: ByteArray, off: Int): Int =
        (buf[off].toInt() and 0xFF) or ((buf[off + 1].toInt() and 0xFF) shl 8)

    private fun u32Le(buf: ByteArray, off: Int): Long =
        (buf[off].toLong() and 0xFFL) or
            ((buf[off + 1].toLong() and 0xFFL) shl 8) or
            ((buf[off + 2].toLong() and 0xFFL) shl 16) or
            ((buf[off + 3].toLong() and 0xFFL) shl 24)
}