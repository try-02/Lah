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
import org.fm.com.domain.arsip.RegistriArsipProvider
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
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.PelaksanaPenjelajahanArsip
import org.fm.com.domain.usecase.StatusLapisanArsip

private const val NAMA_DIR_PRATINJAU_ARSIP = "fm_arsip_pratinjau"

class EkstraktorArsip(
    private val registriArsipProvider: RegistriArsipProvider,
    private val adapterPlatform: AdapterArsipPlatform = buatAdapterArsipPlatform(),
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PelaksanaPenjelajahanArsip {

    private data class LapisanInternal(
        val jejak: LapisanJejakArsip,
        val lokasiFisik: LokasiBerkas,
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
        kataSandi: String?,
        batas: KebijakanBatasArsip
    ): HasilOperasi<EntitasBerkas> = withContext(ioDispatcher) {
        val lapisanAktif = mutex.withLock { sesiAktif[idSesi]?.lastOrNull() }
            ?: return@withContext HasilOperasi.Gagal(
                "Sesi arsip belum dibuka",
                PenyebabGalat.TIDAK_DITEMUKAN
            )

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

        val ukuran = (salin as HasilOperasi.Sukses).nilai
        HasilOperasi.Sukses(
            EntitasBerkas(
                lokasi = LokasiBerkas(
                    pengenal = pathTemp.toString(),
                    jenis = JenisLokasi.POSIX,
                    label = jalurAman.namaSegmenAkhir
                ),
                nama = jalurAman.namaSegmenAkhir,
                jenis = JenisBerkas.BERKAS_TEKS,
                ukuranByte = ukuran,
                dapatDibaca = true,
                dapatDitulis = false
            )
        )
    }

    override suspend fun tutupSesi(idSesi: String) {
        val stack = mutex.withLock { sesiAktif.remove(idSesi) } ?: return
        withContext(ioDispatcher + NonCancellable) {
            bersihkanDaftarLapisan(stack)
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