package org.fm.com.data.arsip

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import okio.Path.Companion.toPath
import org.fm.com.domain.arsip.ArsipProvider
import org.fm.com.domain.arsip.RegistriArsipProvider
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.RingkasanIsiArsip
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.HasilSiapkanEditArsip
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.PelaksanaPenjelajahanArsip
import org.fm.com.domain.usecase.StatusLapisanArsip

private const val NAMA_DIR_PRATINJAU_ARSIP = "fm_arsip_pratinjau"

class EkstraktorArsip(
    private val registriArsipProvider: RegistriArsipProvider,
    private val repositoriBerkas: RepositoriBerkas? = null,
    private val adapterPlatform: AdapterArsipPlatform = buatAdapterArsipPlatform(),
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PelaksanaPenjelajahanArsip {

    private data class LapisanInternal(
        val jejak: LapisanJejakArsip,
        val lokasiFisik: LokasiBerkas,
        val jalurDalamInduk: String?,
        val ringkasan: RingkasanIsiArsip,
        val pathTempUntukDihapus: Path?
    )

    private val mutex = Mutex()
    private val sesiAktif = HashMap<String, MutableList<LapisanInternal>>()

    override suspend fun bukaArsipAkar(
        idSesi: String,
        sumber: LokasiBerkas,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<StatusLapisanArsip> = withContext(ioDispatcher) {
        val namaArsip = sumber.label?.takeIf { it.isNotBlank() }
            ?: sumber.pengenal.trimEnd('/').substringAfterLast('/').ifBlank { "arsip" }

        val format = when (val deteksi = registriArsipProvider.deteksiDanCekDukungan(namaArsip)) {
            is HasilOperasi.Sukses -> deteksi.nilai
            is HasilOperasi.Gagal -> return@withContext deteksi
        }
        val provider = registriArsipProvider.providerUntuk(format)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip untuk format ${format.namaTampilan} belum tersedia",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val kap = registriArsipProvider.kapabilitas(format)
        if (!kap.dapatBaca) {
            return@withContext HasilOperasi.Gagal(
                kap.catatan ?: "Format ${format.namaTampilan} tidak mendukung pembacaan isi",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }

        val cekKedalaman = EkstrakArsipUseCase.validasiKedalaman(1, batas)
        if (cekKedalaman is HasilOperasi.Gagal) return@withContext cekKedalaman

        val ringkasan = when (val daftar = provider.daftarEntri(sumber, format, kataSandi, batas)) {
            is HasilOperasi.Sukses -> daftar.nilai
            is HasilOperasi.Gagal -> return@withContext daftar
        }

        val lapisan = LapisanInternal(
            jejak = LapisanJejakArsip(
                namaArsip = namaArsip,
                format = format,
                kapabilitas = kap,
                kedalaman = 1
            ),
            lokasiFisik = sumber,
            jalurDalamInduk = null,
            ringkasan = ringkasan,
            pathTempUntukDihapus = null
        )

        val lama = mutex.withLock {
            val prev = sesiAktif.remove(idSesi)
            sesiAktif[idSesi] = mutableListOf(lapisan)
            prev
        }
        if (lama != null) {
            withContext(NonCancellable) { bersihkanDaftarLapisan(lama) }
        }

        HasilOperasi.Sukses(bentukStatus(listOf(lapisan)))
    }

    override suspend fun masukArsipBersarang(
        idSesi: String,
        jalurArsipDalamLapisan: String,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<StatusLapisanArsip> = withContext(ioDispatcher) {
        val lapisanAtas = mutex.withLock { sesiAktif[idSesi]?.lastOrNull() }
            ?: return@withContext HasilOperasi.Gagal(
                "Sesi arsip belum dibuka atau sudah ditutup",
                PenyebabGalat.TIDAK_DITEMUKAN
            )

        val kedalamanBaru = lapisanAtas.jejak.kedalaman + 1
        val cekKedalaman = EkstrakArsipUseCase.validasiKedalaman(kedalamanBaru, batas)
        if (cekKedalaman is HasilOperasi.Gagal) return@withContext cekKedalaman

        val valJalur = ValidasiPathArsip.validasi(jalurArsipDalamLapisan, adalahFolder = false)
        val jalurAman = when (valJalur) {
            is HasilValidasiPathArsip.Sah -> valJalur
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Jalur arsip bersarang tidak valid", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(valJalur.alasan, PenyebabGalat.VALIDASI)
            }
        }

        val formatBaru = when (val deteksi = registriArsipProvider.deteksiDanCekDukungan(jalurAman.namaSegmenAkhir)) {
            is HasilOperasi.Sukses -> deteksi.nilai
            is HasilOperasi.Gagal -> return@withContext deteksi
        }
        val providerAnak = registriArsipProvider.providerUntuk(formatBaru)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip untuk format ${formatBaru.namaTampilan} belum tersedia",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val kapAnak = registriArsipProvider.kapabilitas(formatBaru)
        if (!kapAnak.dapatBaca) {
            return@withContext HasilOperasi.Gagal(
                kapAnak.catatan ?: "Format ${formatBaru.namaTampilan} tidak dapat dibaca",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }

        val providerInduk = registriArsipProvider.providerUntuk(lapisanAtas.jejak.format)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip induk tidak ditemukan",
                PenyebabGalat.TIDAK_DIDUKUNG
            )

        val pathTemp = BantuArsipPenyimpanan.buatPathCacheSementara(
            awalan = "nested_${kedalamanBaru}",
            ekstensi = "_${jalurAman.namaSegmenAkhir}",
            direktoriTemp = direktoriTemp
        )

        val salinHasil = salinEntriKePathTemp(
            providerInduk = providerInduk,
            lokasiArsipInduk = lapisanAtas.lokasiFisik,
            formatInduk = lapisanAtas.jejak.format,
            jalurEntri = jalurAman.jalurNormal,
            kataSandi = kataSandi,
            batas = batas,
            pathTujuan = pathTemp,
            batasByte = batas.batasUkuranSatuEntriByte
        )
        if (salinHasil is HasilOperasi.Gagal) {
            withContext(NonCancellable) { hapusFileTempAman(pathTemp) }
            return@withContext salinHasil
        }

        val lokasiSementara = LokasiBerkas(
            pengenal = pathTemp.toString(),
            jenis = JenisLokasi.POSIX,
            label = jalurAman.namaSegmenAkhir
        )
        val ringkasanAnak = when (
            val daftar = providerAnak.daftarEntri(lokasiSementara, formatBaru, kataSandi, batas)
        ) {
            is HasilOperasi.Sukses -> daftar.nilai
            is HasilOperasi.Gagal -> {
                withContext(NonCancellable) { hapusFileTempAman(pathTemp) }
                return@withContext daftar
            }
        }

        val lapisanBaru = LapisanInternal(
            jejak = LapisanJejakArsip(
                namaArsip = jalurAman.namaSegmenAkhir,
                format = formatBaru,
                kapabilitas = kapAnak,
                kedalaman = kedalamanBaru
            ),
            lokasiFisik = lokasiSementara,
            jalurDalamInduk = jalurAman.jalurNormal,
            ringkasan = ringkasanAnak,
            pathTempUntukDihapus = pathTemp
        )

        val snapshot = mutex.withLock {
            val stack = sesiAktif[idSesi]
            if (stack == null || stack.lastOrNull()?.jejak?.kedalaman != lapisanAtas.jejak.kedalaman) {
                null
            } else {
                stack.add(lapisanBaru)
                bentukStatus(stack.toList())
            }
        }
        if (snapshot == null) {
            withContext(NonCancellable) { hapusFileTempAman(pathTemp) }
            return@withContext HasilOperasi.Gagal(
                "Sesi arsip berubah saat membuka arsip bersarang",
                PenyebabGalat.VALIDASI
            )
        }

        HasilOperasi.Sukses(snapshot)
    }

    override suspend fun kembaliKeLapisan(
        idSesi: String,
        indeksLapisanTujuan: Int
    ): HasilOperasi<StatusLapisanArsip> = withContext(ioDispatcher) {
        val dilepas = mutableListOf<LapisanInternal>()
        val statusHasil = mutex.withLock {
            val stack = sesiAktif[idSesi] ?: return@withLock null
            if (indeksLapisanTujuan !in stack.indices) return@withLock null
            while (stack.size - 1 > indeksLapisanTujuan) {
                dilepas.add(stack.removeAt(stack.lastIndex))
            }
            bentukStatus(stack.toList())
        }
        if (dilepas.isNotEmpty()) {
            withContext(NonCancellable) { bersihkanDaftarLapisan(dilepas) }
        }
        if (statusHasil == null) {
            HasilOperasi.Gagal("Lapisan arsip tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } else {
            HasilOperasi.Sukses(statusHasil)
        }
    }

    override suspend fun ekstrak(
        idSesi: String,
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

        val lapisanAktif = mutex.withLock { sesiAktif[idSesi]?.lastOrNull() }
            ?: return@withContext HasilOperasi.Gagal(
                "Sesi arsip belum dibuka",
                PenyebabGalat.TIDAK_DITEMUKAN
            )

        val kap = lapisanAktif.jejak.kapabilitas
        if (!kap.dapatEkstrak) {
            return@withContext HasilOperasi.Gagal(
                kap.catatan ?: "Ekstraksi untuk format ${lapisanAktif.jejak.format.namaTampilan} tidak didukung",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }

        val provider = registriArsipProvider.providerUntuk(lapisanAktif.jejak.format)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip tidak tersedia",
                PenyebabGalat.TIDAK_DIDUKUNG
            )

        provider.ekstrak(
            sumber = lapisanAktif.lokasiFisik,
            format = lapisanAktif.jejak.format,
            folderTujuan = folderTujuan,
            opsi = opsi,
            onProgres = onProgres
        )
    }

    override suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        untukEdit: Boolean,
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<HasilSiapkanEditArsip> = withContext(ioDispatcher) {
        val stack = mutex.withLock { sesiAktif[idSesi]?.toList() }
            ?: return@withContext HasilOperasi.Gagal(
                "Sesi arsip belum dibuka",
                PenyebabGalat.TIDAK_DITEMUKAN
            )
        val lapisanAktif = stack.lastOrNull()
            ?: return@withContext HasilOperasi.Gagal("Sesi arsip kosong", PenyebabGalat.TIDAK_DITEMUKAN)

        val valJalur = ValidasiPathArsip.validasi(jalurInternal, adalahFolder = false)
        val jalurAman = when (valJalur) {
            is HasilValidasiPathArsip.Sah -> valJalur
            is HasilValidasiPathArsip.AbaikanRoot -> {
                return@withContext HasilOperasi.Gagal("Tidak dapat membuka root arsip sebagai berkas", PenyebabGalat.VALIDASI)
            }
            is HasilValidasiPathArsip.TidakSah -> {
                return@withContext HasilOperasi.Gagal(valJalur.alasan, PenyebabGalat.VALIDASI)
            }
        }

        val provider = registriArsipProvider.providerUntuk(lapisanAktif.jejak.format)
            ?: return@withContext HasilOperasi.Gagal(
                "Penyedia arsip tidak tersedia",
                PenyebabGalat.TIDAK_DIDUKUNG
            )

        val dirPratinjau = direktoriTemp / NAMA_DIR_PRATINJAU_ARSIP
        runCatching { adapterPlatform.sistemBerkasLokal.createDirectory(dirPratinjau, mustCreate = false) }
        val pathTemp = BantuArsipPenyimpanan.buatPathCacheSementara(
            awalan = "prev",
            ekstensi = "_${jalurAman.namaSegmenAkhir}",
            direktoriTemp = dirPratinjau
        )

        val salin = salinEntriKePathTemp(
            providerInduk = provider,
            lokasiArsipInduk = lapisanAktif.lokasiFisik,
            formatInduk = lapisanAktif.jejak.format,
            jalurEntri = jalurAman.jalurNormal,
            kataSandi = kataSandi,
            batas = batas,
            pathTujuan = pathTemp,
            batasByte = batas.batasUkuranSatuEntriByte
        )
        if (salin is HasilOperasi.Gagal) {
            withContext(NonCancellable) { hapusFileTempAman(pathTemp) }
            return@withContext salin
        }

        val bolehEdit = untukEdit && stack.all { it.jejak.kapabilitas.dapatBuat } &&
            !lapisanAktif.ringkasan.daftarEntri.any { it.terenkripsi || it.adalahTautanSimbolik }
        val ukuran = (salin as HasilOperasi.Sukses).nilai
        val entitas = EntitasBerkas(
            lokasi = LokasiBerkas(
                pengenal = pathTemp.toString(),
                jenis = JenisLokasi.POSIX,
                label = jalurAman.namaSegmenAkhir
            ),
            nama = jalurAman.namaSegmenAkhir,
            jenis = JenisBerkas.BERKAS_TEKS,
            ukuranByte = ukuran,
            dapatDibaca = true,
            dapatDitulis = bolehEdit
        )
        val konteks = KonteksEditArsip(
            sumberArsipAkar = stack.first().lokasiFisik,
            namaArsipTampilan = stack.last().jejak.namaArsip,
            formatArsipAktif = lapisanAktif.jejak.format,
            rantaiJalurArsipBersarang = stack.drop(1).mapNotNull { it.jalurDalamInduk },
            jalurEntriTarget = jalurAman.jalurNormal,
            idSesiArsip = idSesi
        )
        HasilOperasi.Sukses(HasilSiapkanEditArsip(entitasSementara = entitas, konteks = konteks))
    }

    override suspend fun perbaruiBerkasDalamArsip(
        konteks: KonteksEditArsip,
        lokasiBerkasEdit: LokasiBerkas,
        batas: KebijakanBatasArsip
    ): HasilOperasi<StatusLapisanArsip?> = withContext(ioDispatcher) {
        val repo = repositoriBerkas
            ?: return@withContext HasilOperasi.Gagal(
                "Repositori berkas belum terhubung untuk menulis ulang arsip",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val fs = adapterPlatform.sistemBerkasLokal

        val sumberEdit = when (
            val siap = BantuArsipPenyimpanan.siapkanPathLokalUntukBaca(
                sumber = lokasiBerkasEdit,
                repositoriBerkas = repo,
                fileSystemLokal = fs,
                direktoriTemp = direktoriTemp
            )
        ) {
            is SumberPathLokal.Tersedia -> siap
            is SumberPathLokal.Gagal -> return@withContext HasilOperasi.Gagal(siap.pesan, siap.penyebab)
        }

        val tempUntukBersih = mutableListOf<Path>()
        if (sumberEdit.harusDihapusSetelahnya) tempUntukBersih.add(sumberEdit.path)

        try {
            // Bangun rantai lapisan dari akar hingga lapisan terdalam tempat berkas diedit
            val namaAkar = konteks.sumberArsipAkar.label?.takeIf { it.isNotBlank() }
                ?: konteks.sumberArsipAkar.pengenal.trimEnd('/').substringAfterLast('/').ifBlank { "arsip" }
            val formatAkar = when (val det = registriArsipProvider.deteksiDanCekDukungan(namaAkar)) {
                is HasilOperasi.Sukses -> det.nilai
                is HasilOperasi.Gagal -> return@withContext det
            }

            val sumberAkarLokal = when (
                val siapAkar = BantuArsipPenyimpanan.siapkanPathLokalUntukBaca(
                    sumber = konteks.sumberArsipAkar,
                    repositoriBerkas = repo,
                    fileSystemLokal = fs,
                    direktoriTemp = direktoriTemp
                )
            ) {
                is SumberPathLokal.Tersedia -> siapAkar
                is SumberPathLokal.Gagal -> return@withContext HasilOperasi.Gagal(siapAkar.pesan, siapAkar.penyebab)
            }
            if (sumberAkarLokal.harusDihapusSetelahnya) tempUntukBersih.add(sumberAkarLokal.path)

            data class InfoLapisanBangun(
                val pathLama: Path,
                val format: FormatArsip,
                val jalurDiganti: String
            )

            val rantai = mutableListOf<InfoLapisanBangun>()
            var pathLapisanSekarang = sumberAkarLokal.path
            var formatLapisanSekarang = formatAkar

            for (jalurNested in konteks.rantaiJalurArsipBersarang) {
                currentCoroutineContext().ensureActive()
                val kap = registriArsipProvider.kapabilitas(formatLapisanSekarang)
                if (!kap.dapatBuat) {
                    return@withContext HasilOperasi.Gagal(
                        kap.catatan ?: "Format ${formatLapisanSekarang.namaTampilan} tidak mendukung penulisan ulang",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                }
                rantai.add(
                    InfoLapisanBangun(
                        pathLama = pathLapisanSekarang,
                        format = formatLapisanSekarang,
                        jalurDiganti = jalurNested
                    )
                )

                val providerInduk = registriArsipProvider.providerUntuk(formatLapisanSekarang)
                    ?: return@withContext HasilOperasi.Gagal("Provider arsip tidak tersedia", PenyebabGalat.TIDAK_DIDUKUNG)
                val namaNested = jalurNested.substringAfterLast('/')
                val formatAnak = when (val det = registriArsipProvider.deteksiDanCekDukungan(namaNested)) {
                    is HasilOperasi.Sukses -> det.nilai
                    is HasilOperasi.Gagal -> return@withContext det
                }
                val pathNestedTemp = BantuArsipPenyimpanan.buatPathCacheSementara(
                    awalan = "edit_nested",
                    ekstensi = "_$namaNested",
                    direktoriTemp = direktoriTemp
                )
                tempUntukBersih.add(pathNestedTemp)

                val lokasiLapisanSekarang = LokasiBerkas(pathLapisanSekarang.toString(), JenisLokasi.POSIX, namaNested)
                when (
                    val salinNested = salinEntriKePathTemp(
                        providerInduk = providerInduk,
                        lokasiArsipInduk = lokasiLapisanSekarang,
                        formatInduk = formatLapisanSekarang,
                        jalurEntri = jalurNested,
                        kataSandi = null,
                        batas = batas,
                        pathTujuan = pathNestedTemp,
                        batasByte = batas.batasUkuranSatuEntriByte
                    )
                ) {
                    is HasilOperasi.Sukses -> Unit
                    is HasilOperasi.Gagal -> return@withContext salinNested
                }
                pathLapisanSekarang = pathNestedTemp
                formatLapisanSekarang = formatAnak
            }

            val kapTerdalam = registriArsipProvider.kapabilitas(formatLapisanSekarang)
            if (!kapTerdalam.dapatBuat) {
                return@withContext HasilOperasi.Gagal(
                    kapTerdalam.catatan ?: "Format ${formatLapisanSekarang.namaTampilan} tidak mendukung penulisan ulang",
                    PenyebabGalat.TIDAK_DIDUKUNG
                )
            }
            rantai.add(
                InfoLapisanBangun(
                    pathLama = pathLapisanSekarang,
                    format = formatLapisanSekarang,
                    jalurDiganti = konteks.jalurEntriTarget
                )
            )

            // Tulis ulang dari lapisan terdalam menuju arsip akar
            var pathPenggantiSekarang = sumberEdit.path
            for (idx in rantai.lastIndex downTo 0) {
                currentCoroutineContext().ensureActive()
                val lapis = rantai[idx]
                val pathOutTemp = BantuArsipPenyimpanan.buatPathCacheSementara(
                    awalan = "rebuild_$idx",
                    ekstensi = ".${lapis.format.ekstensiUtama}",
                    direktoriTemp = direktoriTemp
                )
                tempUntukBersih.add(pathOutTemp)

                val hasilUbah = tulisUlangSatuLapisan(
                    pathArsipLama = lapis.pathLama,
                    format = lapis.format,
                    jalurTarget = lapis.jalurDiganti,
                    pathFilePengganti = pathPenggantiSekarang,
                    pathTempOut = pathOutTemp,
                    batas = batas
                )
                if (hasilUbah is HasilOperasi.Gagal) return@withContext hasilUbah
                pathPenggantiSekarang = pathOutTemp
            }

            // Tulis arsip akar baru yang sudah terverifikasi ke penyimpanan tujuan secara transaksional
            val tulisKeDisk = tulisArsipHasilKeTargetAsli(
                repo = repo,
                fs = fs,
                pathTempArsipBaru = pathPenggantiSekarang,
                lokasiTargetAsli = konteks.sumberArsipAkar
            )
            if (tulisKeDisk is HasilOperasi.Gagal) return@withContext tulisKeDisk

            // Verifikasi akhir arsip di penyimpanan asli
            val providerAkar = registriArsipProvider.providerUntuk(formatAkar)
                ?: return@withContext HasilOperasi.Gagal("Provider arsip tidak tersedia", PenyebabGalat.TIDAK_DIDUKUNG)
            when (val cekAkhir = providerAkar.daftarEntri(konteks.sumberArsipAkar, formatAkar, batas = batas)) {
                is HasilOperasi.Sukses -> Unit
                is HasilOperasi.Gagal -> return@withContext HasilOperasi.Gagal(
                    "Verifikasi arsip setelah pembaruan gagal: ${cekAkhir.pesan}",
                    cekAkhir.penyebab
                )
            }

            // Segarkan sesi browser bila masih terbuka
            val idSesi = konteks.idSesiArsip
            val statusBaru = if (idSesi != null) {
                segarkanSesiSetelahEdit(idSesi, konteks, batas)
            } else {
                null
            }
            HasilOperasi.Sukses(statusBaru)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal memperbarui arsip", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                for (p in tempUntukBersih.asReversed()) {
                    hapusFileTempAman(p)
                }
            }
        }
    }

    override suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip? =
        mutex.withLock { sesiAktif[idSesi]?.let { bentukStatus(it.toList()) } }

    override suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas) {
        if (lokasi.jenis != JenisLokasi.POSIX) return
        val path = runCatching { lokasi.pengenal.toPath(normalize = true) }.getOrNull() ?: return
        val dirPratinjau = (direktoriTemp / NAMA_DIR_PRATINJAU_ARSIP).toString()
        if (path.toString().startsWith(dirPratinjau)) {
            withContext(ioDispatcher + NonCancellable) {
                hapusFileTempAman(path)
            }
        }
    }

    override suspend fun tutupSesi(idSesi: String) {
        val stack = mutex.withLock { sesiAktif.remove(idSesi) } ?: return
        withContext(ioDispatcher + NonCancellable) {
            bersihkanDaftarLapisan(stack)
        }
    }

    private suspend fun tulisUlangSatuLapisan(
        pathArsipLama: Path,
        format: FormatArsip,
        jalurTarget: String,
        pathFilePengganti: Path,
        pathTempOut: Path,
        batas: KebijakanBatasArsip
    ): HasilOperasi<Unit> {
        val provider = registriArsipProvider.providerUntuk(format)
            ?: return HasilOperasi.Gagal(
                "Penyedia arsip untuk format ${format.namaTampilan} belum tersedia",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        return when (format) {
            FormatArsip.ZIP -> {
                val p = provider as? ProviderZip
                    ?: return HasilOperasi.Gagal("Provider ZIP tidak cocok", PenyebabGalat.TIDAK_DIDUKUNG)
                p.tulisUlangDenganPengganti(pathArsipLama, format, jalurTarget, pathFilePengganti, pathTempOut, batas = batas)
            }
            FormatArsip.JAR -> {
                val fs = adapterPlatform.sistemBerkasLokal
                val repo = repositoriBerkas ?: return HasilOperasi.Gagal("Repositori berkas kosong", PenyebabGalat.TIDAK_DIDUKUNG)
                // ProviderZipBased membungkus ProviderZip; gunakan instance ProviderZip langsung atau buat sementara
                val pZip = registriArsipProvider.providerUntuk(FormatArsip.ZIP) as? ProviderZip
                if (pZip != null) {
                    pZip.tulisUlangDenganPengganti(pathArsipLama, format, jalurTarget, pathFilePengganti, pathTempOut, batas = batas)
                } else {
                    HasilOperasi.Gagal("Provider ZIP untuk menulis ulang JAR belum tersedia", PenyebabGalat.TIDAK_DIDUKUNG)
                }
            }
            FormatArsip.TAR, FormatArsip.TGZ -> {
                val p = provider as? ProviderTar
                    ?: return HasilOperasi.Gagal("Provider TAR tidak cocok", PenyebabGalat.TIDAK_DIDUKUNG)
                p.tulisUlangDenganPengganti(pathArsipLama, format, jalurTarget, pathFilePengganti, pathTempOut, batas = batas)
            }
            FormatArsip.GZIP -> {
                val p = provider as? ProviderGzip
                    ?: return HasilOperasi.Gagal("Provider GZIP tidak cocok", PenyebabGalat.TIDAK_DIDUKUNG)
                p.tulisUlangDenganPengganti(pathArsipLama, jalurTarget, pathFilePengganti, pathTempOut)
            }
            FormatArsip.ZIP_LAIN -> HasilOperasi.Gagal(
                "Format ${format.namaTampilan} tidak mendukung penulisan ulang",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
    }

    private suspend fun tulisArsipHasilKeTargetAsli(
        repo: RepositoriBerkas,
        fs: FileSystem,
        pathTempArsipBaru: Path,
        lokasiTargetAsli: LokasiBerkas
    ): HasilOperasi<Unit> {
        val writer = when (val buka = repo.bukaUntukTulis(lokasiTargetAsli, timpa = true)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return buka
        }
        var selesaiSukses = false
        try {
            val buf = ByteArray(8 * 1024)
            fs.source(pathTempArsipBaru).buffer().use { src ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = src.read(buf)
                    if (n == -1) break
                    val data = if (n == buf.size) buf else buf.copyOf(n)
                    when (val tulis = writer.tulis(PotonganIsi(data, selesai = false))) {
                        is HasilOperasi.Sukses -> Unit
                        is HasilOperasi.Gagal -> return tulis
                    }
                }
            }
            when (val hasilSelesai = writer.selesai()) {
                is HasilOperasi.Sukses -> {
                    selesaiSukses = true
                    return HasilOperasi.Sukses(Unit)
                }
                is HasilOperasi.Gagal -> return hasilSelesai
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menyimpan arsip yang diperbarui", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                if (!selesaiSukses) {
                    writer.batalkan()
                }
            }
        }
    }

    private suspend fun segarkanSesiSetelahEdit(
        idSesi: String,
        konteks: KonteksEditArsip,
        batas: KebijakanBatasArsip
    ): StatusLapisanArsip? {
        val stackLama = mutex.withLock { sesiAktif[idSesi]?.toList() } ?: return null
        if (stackLama.isEmpty()) return null

        val akarLama = stackLama.first()
        val providerAkar = registriArsipProvider.providerUntuk(akarLama.jejak.format) ?: return null
        val ringkasanAkarBaru = (providerAkar.daftarEntri(konteks.sumberArsipAkar, akarLama.jejak.format, batas = batas)
            as? HasilOperasi.Sukses)?.nilai ?: return null

        val stackBaru = mutableListOf(
            akarLama.copy(ringkasan = ringkasanAkarBaru)
        )
        val tempBaru = mutableListOf<Path>()

        for (i in 1 until stackLama.size) {
            val lama = stackLama[i]
            val indukBaru = stackBaru[i - 1]
            val jalur = lama.jalurDalamInduk ?: break
            val provInduk = registriArsipProvider.providerUntuk(indukBaru.jejak.format) ?: break
            val provAnak = registriArsipProvider.providerUntuk(lama.jejak.format) ?: break

            val pathTempBaru = BantuArsipPenyimpanan.buatPathCacheSementara(
                awalan = "nested_${lama.jejak.kedalaman}",
                ekstensi = "_${lama.jejak.namaArsip}",
                direktoriTemp = direktoriTemp
            )
            val salin = salinEntriKePathTemp(
                providerInduk = provInduk,
                lokasiArsipInduk = indukBaru.lokasiFisik,
                formatInduk = indukBaru.jejak.format,
                jalurEntri = jalur,
                kataSandi = null,
                batas = batas,
                pathTujuan = pathTempBaru,
                batasByte = batas.batasUkuranSatuEntriByte
            )
            if (salin is HasilOperasi.Gagal) {
                hapusFileTempAman(pathTempBaru)
                break
            }
            val lokBaru = LokasiBerkas(pathTempBaru.toString(), JenisLokasi.POSIX, lama.jejak.namaArsip)
            val ringkasanAnak = (provAnak.daftarEntri(lokBaru, lama.jejak.format, batas = batas)
                as? HasilOperasi.Sukses)?.nilai
            if (ringkasanAnak == null) {
                hapusFileTempAman(pathTempBaru)
                break
            }
            tempBaru.add(pathTempBaru)
            stackBaru.add(
                lama.copy(
                    lokasiFisik = lokBaru,
                    ringkasan = ringkasanAnak,
                    pathTempUntukDihapus = pathTempBaru
                )
            )
        }

        val diganti = mutex.withLock {
            val sekarang = sesiAktif[idSesi]
            if (sekarang == null) {
                false
            } else {
                sesiAktif[idSesi] = stackBaru
                true
            }
        }
        if (diganti) {
            withContext(NonCancellable) {
                for (l in stackLama.asReversed()) {
                    l.pathTempUntukDihapus?.let { hapusFileTempAman(it) }
                }
            }
            return bentukStatus(stackBaru)
        } else {
            withContext(NonCancellable) {
                for (p in tempBaru) hapusFileTempAman(p)
            }
            return null
        }
    }

    private suspend fun salinEntriKePathTemp(
        providerInduk: org.fm.com.domain.arsip.ArsipProvider,
        lokasiArsipInduk: LokasiBerkas,
        formatInduk: FormatArsip,
        jalurEntri: String,
        kataSandi: String?,
        batas: KebijakanBatasArsip,
        pathTujuan: Path,
        batasByte: Long
    ): HasilOperasi<Long> {
        val sumberBaca = when (
            val buka = providerInduk.bukaEntriUntukBaca(
                sumber = lokasiArsipInduk,
                format = formatInduk,
                jalurInternal = jalurEntri,
                kataSandi = kataSandi,
                batas = batas
            )
        ) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return buka
        }

        val fs = adapterPlatform.sistemBerkasLokal
        var totalByte = 0L
        try {
            fs.sink(pathTujuan, mustCreate = false).buffer().use { sink ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val pot = sumberBaca.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val data = pot.potongan.data
                            if (data.isNotEmpty()) {
                                totalByte += data.size
                                if (totalByte > batasByte) {
                                    return HasilOperasi.Gagal(
                                        "Ukuran entri '$jalurEntri' melebihi batas aman ($batasByte byte)",
                                        PenyebabGalat.VALIDASI
                                    )
                                }
                                sink.write(data)
                            }
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            return HasilOperasi.Gagal(pot.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                    }
                }
                sink.flush()
            }
            return HasilOperasi.Sukses(totalByte)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilOperasi.Gagal(
                e.message ?: "Gagal menyiapkan berkas arsip sementara",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                sumberBaca.tutup()
            }
        }
    }

    private fun bentukStatus(stack: List<LapisanInternal>): StatusLapisanArsip = StatusLapisanArsip(
        jejakLapisan = stack.map { it.jejak },
        ringkasan = stack.last().ringkasan,
        kedalamanSaatIni = stack.size
    )

    private fun bersihkanDaftarLapisan(daftar: List<LapisanInternal>) {
        for (lapisan in daftar.asReversed()) {
            lapisan.pathTempUntukDihapus?.let { hapusFileTempAman(it) }
        }
    }

    private fun hapusFileTempAman(path: Path) {
        runCatching { adapterPlatform.sistemBerkasLokal.delete(path, mustExist = false) }
    }
}