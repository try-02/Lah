package org.fm.com.data.sftp

import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fm.com.data.DataBerkas
import org.fm.com.data.PemetaanDataDomain.keDomain
import org.fm.com.data.SumberDataPenyimpanan
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.sftp.EntriSftp
import org.fm.com.domain.sftp.GagalSftp
import org.fm.com.domain.sftp.LokasiSftp
import org.fm.com.domain.sftp.PengenalSftp

private const val UKURAN_BUFFER_SFTP = 32 * 1024
private const val BATAS_KEDALAMAN_SALIN_SFTP = 64

class SftpSumberDataPenyimpanan(
    private val pengelolaKoneksi: PengelolaKoneksiSftp,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberDataPenyimpanan {

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.SFTP) {
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SFTP)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
        emit(muatDaftarIsi(lokasi))
    }

    private suspend fun muatDaftarIsi(lokasi: LokasiBerkas): HasilOperasi<List<EntitasBerkas>> {
        val (lokSftp, koneksi) = when (val siap = siapkan(lokasi)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        return jalankanDenganPemetaan(lokSftp.idProfil) {
            val stat = koneksi.stat(lokSftp.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Folder tidak ditemukan: ${lokSftp.path}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (!stat.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Bukan folder: ${lokSftp.path}",
                    PenyebabGalat.VALIDASI
                )
            }
            val entriList = koneksi.daftar(lokSftp.path)
                .map { it.keEntitasDomain(lokSftp.idProfil) }
                .sortedBy { it.nama }
            HasilOperasi.Sukses(entriList)
        }
    }

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
        val (lokSftp, koneksi) = when (val siap = siapkan(lokasi)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        return jalankanDenganPemetaan(lokSftp.idProfil) {
            val stat = koneksi.stat(lokSftp.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Berkas tidak ditemukan: ${lokSftp.path}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (stat.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Tidak bisa membaca folder sebagai berkas",
                    PenyebabGalat.VALIDASI
                )
            }
            HasilOperasi.Sukses(SftpAliranIsi(koneksi, lokSftp.path, stat.ukuran.takeIf { it >= 0L }, ioDispatcher))
        }
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val (lokSftp, koneksi) = when (val siap = siapkan(lokasi)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        if (lokSftp.path == "/") {
            return HasilOperasi.Gagal("Tidak dapat menulis isi ke root", PenyebabGalat.VALIDASI)
        }
        return jalankanDenganPemetaan(lokSftp.idProfil) {
            val stat = koneksi.stat(lokSftp.path)
            if (stat != null && stat.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Tidak bisa menulis isi ke folder",
                    PenyebabGalat.VALIDASI
                )
            }
            if (stat != null && !timpa) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa: ${lokSftp.path}",
                    PenyebabGalat.KONFLIK
                )
            }
            val nama = PengenalSftp.nama(lokSftp.path).ifBlank { lokasi.label ?: "berkas" }
            val targetLok = LokasiBerkas(PengenalSftp.buat(lokSftp.idProfil, lokSftp.path), JenisLokasi.SFTP, nama)
            HasilOperasi.Sukses(SftpAliranTulis(koneksi, lokSftp.path, targetLok, timpa, ioDispatcher = ioDispatcher))
        }
    }

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas,
        nama: String,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> {
        if (!ValidasiNama.sah(nama)) {
            return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        val (lokInduk, koneksi) = when (val siap = siapkan(induk)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        return jalankanDenganPemetaan(lokInduk.idProfil) {
            val statInduk = koneksi.stat(lokInduk.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Folder induk tidak ditemukan: ${lokInduk.path}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (!statInduk.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Induk bukan folder: ${lokInduk.path}",
                    PenyebabGalat.VALIDASI
                )
            }
            val targetPath = PengenalSftp.gabung(lokInduk.path, nama)
            val statTarget = koneksi.stat(targetPath)
            if (statTarget != null && statTarget.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Tidak bisa menulis isi ke folder",
                    PenyebabGalat.VALIDASI
                )
            }
            if (statTarget != null && !timpa) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            val targetLok = LokasiBerkas(PengenalSftp.buat(lokInduk.idProfil, targetPath), JenisLokasi.SFTP, nama)
            HasilOperasi.Sukses(SftpAliranTulis(koneksi, targetPath, targetLok, timpa, ioDispatcher = ioDispatcher))
        }
    }

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, folder = true)

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, folder = false)

    private suspend fun buat(permintaan: PermintaanOperasi, folder: Boolean): HasilOperasi<EntitasBerkas> {
        val nama = permintaan.namaBaru
            ?: return HasilOperasi.Gagal("namaBaru wajib diisi", PenyebabGalat.VALIDASI)
        if (!ValidasiNama.sah(nama)) {
            return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        val (lokInduk, koneksi) = when (val siap = siapkan(permintaan.sumber)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        return jalankanDenganPemetaan(lokInduk.idProfil) {
            val statInduk = koneksi.stat(lokInduk.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Folder induk tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            if (!statInduk.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal("Induk bukan folder", PenyebabGalat.VALIDASI)
            }
            val targetPath = PengenalSftp.gabung(lokInduk.path, nama)
            val statAda = koneksi.stat(targetPath)
            if (statAda != null) {
                if (!permintaan.konfirmasi) {
                    return@jalankanDenganPemetaan HasilOperasi.Gagal(
                        "Target sudah ada, perlu konfirmasi timpa: $nama",
                        PenyebabGalat.KONFLIK
                    )
                }
                val cadangan = PengenalSftp.gabung(lokInduk.path, ".~bak-${UUID.randomUUID()}")
                koneksi.gantiNama(targetPath, cadangan, timpa = true)
                try {
                    if (folder) koneksi.buatFolder(targetPath, rekursif = false)
                    else koneksi.buatBerkasKosong(targetPath, timpa = false)
                    runCatching { koneksi.hapus(cadangan) }
                } catch (t: Throwable) {
                    runCatching { koneksi.gantiNama(cadangan, targetPath, timpa = true) }
                    throw t
                }
            } else {
                if (folder) koneksi.buatFolder(targetPath, rekursif = false)
                else koneksi.buatBerkasKosong(targetPath, timpa = false)
            }
            val hasil = koneksi.stat(targetPath)?.keEntitasDomain(lokInduk.idProfil)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Gagal membaca entitas baru", PenyebabGalat.TIDAK_DITEMUKAN)
            HasilOperasi.Sukses(hasil)
        }
    }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> {
        val namaBaru = permintaan.namaBaru
            ?: return HasilOperasi.Gagal("namaBaru wajib diisi untuk ganti nama", PenyebabGalat.VALIDASI)
        if (!ValidasiNama.sah(namaBaru)) {
            return HasilOperasi.Gagal("Nama tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
        }
        val (lokSumber, koneksi) = when (val siap = siapkan(permintaan.sumber)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        if (lokSumber.path == "/") {
            return HasilOperasi.Gagal("Root tidak bisa diganti nama", PenyebabGalat.VALIDASI)
        }
        return jalankanDenganPemetaan(lokSumber.idProfil) {
            val statSumber = koneksi.stat(lokSumber.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            val indukPath = PengenalSftp.induk(lokSumber.path)
            val targetPath = PengenalSftp.gabung(indukPath, namaBaru)
            if (targetPath == lokSumber.path) {
                return@jalankanDenganPemetaan HasilOperasi.Sukses(statSumber.keEntitasDomain(lokSumber.idProfil))
            }
            renameDenganCadanganFolderJikaPerlu(
                koneksi = koneksi,
                dari = lokSumber.path,
                ke = targetPath,
                indukTujuan = indukPath,
                sumberAdalahFolder = statSumber.adalahFolder,
                konfirmasi = permintaan.konfirmasi
            )
            val baru = koneksi.stat(targetPath)?.keEntitasDomain(lokSumber.idProfil)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Gagal membaca hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
            HasilOperasi.Sukses(baru)
        }
    }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> {
        if (!permintaan.konfirmasi) {
            return HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
        }
        val (lokSumber, koneksi) = when (val siap = siapkan(permintaan.sumber)) {
            is HasilOperasi.Sukses -> siap.nilai
            is HasilOperasi.Gagal -> return siap
        }
        if (lokSumber.path == "/") {
            return HasilOperasi.Gagal("Root tidak dapat dihapus", PenyebabGalat.VALIDASI)
        }
        return jalankanDenganPemetaan(lokSumber.idProfil) {
            koneksi.hapus(lokSumber.path)
            HasilOperasi.Sukses(Unit)
        }
    }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = false)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = true)

    private suspend fun salinAtauPindah(
        permintaan: PermintaanOperasi,
        pindah: Boolean
    ): HasilOperasi<EntitasBerkas> {
        val tujuanInduk = permintaan.tujuan
            ?: return HasilOperasi.Gagal("Tujuan (folder induk) wajib diisi", PenyebabGalat.VALIDASI)
        if (permintaan.sumber.jenis != JenisLokasi.SFTP || tujuanInduk.jenis != JenisLokasi.SFTP) {
            return HasilOperasi.Gagal(
                "Salin/pindah lintas jenis lokasi ditangani use case streaming",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        permintaan.namaBaru?.let {
            if (!ValidasiNama.sah(it)) {
                return HasilOperasi.Gagal("Nama tidak sah: $it", PenyebabGalat.VALIDASI)
            }
        }
        val lokSumber = PengenalSftp.urai(permintaan.sumber.pengenal)
            ?: return HasilOperasi.Gagal("Pengenal SFTP sumber tidak valid", PenyebabGalat.VALIDASI)
        val lokTujuan = PengenalSftp.urai(tujuanInduk.pengenal)
            ?: return HasilOperasi.Gagal("Pengenal SFTP tujuan tidak valid", PenyebabGalat.VALIDASI)
        if (lokSumber.idProfil != lokTujuan.idProfil) {
            return HasilOperasi.Gagal(
                "Salin/pindah antar profil SFTP berbeda ditangani via streaming",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        val koneksi = when (val k = pengelolaKoneksi.ambilAtauBuatKoneksi(lokSumber.idProfil)) {
            is HasilOperasi.Sukses -> k.nilai
            is HasilOperasi.Gagal -> return k
        }
        return jalankanDenganPemetaan(lokSumber.idProfil) {
            val statSumber = koneksi.stat(lokSumber.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            val statInduk = koneksi.stat(lokTujuan.path)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Folder tujuan tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            if (!statInduk.adalahFolder) {
                return@jalankanDenganPemetaan HasilOperasi.Gagal("Tujuan bukan folder", PenyebabGalat.VALIDASI)
            }
            val namaAkhir = permintaan.namaBaru ?: PengenalSftp.nama(lokSumber.path)
            val targetPath = PengenalSftp.gabung(lokTujuan.path, namaAkhir)
            if (targetPath == lokSumber.path) {
                return@jalankanDenganPemetaan HasilOperasi.Sukses(statSumber.keEntitasDomain(lokSumber.idProfil))
            }
            if (statSumber.adalahFolder) {
                val awalan = if (lokSumber.path == "/") "/" else "${lokSumber.path}/"
                if (lokTujuan.path == lokSumber.path || lokTujuan.path.startsWith(awalan)) {
                    return@jalankanDenganPemetaan HasilOperasi.Gagal(
                        "Tidak bisa menyalin/memindah folder ke dalam dirinya sendiri",
                        PenyebabGalat.VALIDASI
                    )
                }
            }

            if (pindah) {
                try {
                    renameDenganCadanganFolderJikaPerlu(
                        koneksi = koneksi,
                        dari = lokSumber.path,
                        ke = targetPath,
                        indukTujuan = lokTujuan.path,
                        sumberAdalahFolder = statSumber.adalahFolder,
                        konfirmasi = permintaan.konfirmasi
                    )
                } catch (e: GagalSftp.Lainnya) {
                    // Fallback salin + hapus jika rename server gagal karena lintas mount point (EXDEV)
                    salinDenganCadanganTimpa(
                        koneksi = koneksi,
                        sumber = statSumber,
                        targetPath = targetPath,
                        indukTujuan = lokTujuan.path,
                        konfirmasi = permintaan.konfirmasi
                    )
                    koneksi.hapus(lokSumber.path)
                }
            } else {
                salinDenganCadanganTimpa(
                    koneksi = koneksi,
                    sumber = statSumber,
                    targetPath = targetPath,
                    indukTujuan = lokTujuan.path,
                    konfirmasi = permintaan.konfirmasi
                )
            }

            val hasil = koneksi.stat(targetPath)?.keEntitasDomain(lokSumber.idProfil)
                ?: return@jalankanDenganPemetaan HasilOperasi.Gagal("Gagal membaca entitas tujuan", PenyebabGalat.TIDAK_DITEMUKAN)
            HasilOperasi.Sukses(hasil)
        }
    }

    private suspend fun renameDenganCadanganFolderJikaPerlu(
        koneksi: KoneksiSftp,
        dari: String,
        ke: String,
        indukTujuan: String,
        sumberAdalahFolder: Boolean,
        konfirmasi: Boolean
    ) {
        val statAda = koneksi.stat(ke)
        if (statAda != null) {
            if (!konfirmasi) throw GagalSftp.SudahAda(ke)
            if (statAda.adalahFolder || sumberAdalahFolder) {
                val cadangan = PengenalSftp.gabung(indukTujuan, ".~bak-${UUID.randomUUID()}")
                koneksi.gantiNama(ke, cadangan, timpa = true)
                try {
                    koneksi.gantiNama(dari, ke, timpa = true)
                } catch (t: Throwable) {
                    runCatching { koneksi.gantiNama(cadangan, ke, timpa = true) }
                    throw t
                }
                runCatching { koneksi.hapus(cadangan) }
                return
            }
        }
        koneksi.gantiNama(dari, ke, timpa = konfirmasi)
    }

    private suspend fun salinDenganCadanganTimpa(
        koneksi: KoneksiSftp,
        sumber: EntriSftp,
        targetPath: String,
        indukTujuan: String,
        konfirmasi: Boolean
    ) {
        val statAda = koneksi.stat(targetPath)
        if (statAda != null && !konfirmasi) {
            throw GagalSftp.SudahAda(targetPath)
        }
        if (sumber.adalahFolder) {
            val cadangan = if (statAda != null) {
                PengenalSftp.gabung(indukTujuan, ".~bak-${UUID.randomUUID()}").also {
                    koneksi.gantiNama(targetPath, it, timpa = true)
                }
            } else {
                null
            }
            try {
                salinFolderRekursifSftp(koneksi, sumber.path, targetPath)
                if (cadangan != null) runCatching { koneksi.hapus(cadangan) }
            } catch (t: Throwable) {
                runCatching { koneksi.hapus(targetPath) }
                if (cadangan != null) runCatching { koneksi.gantiNama(cadangan, targetPath, timpa = true) }
                throw t
            }
        } else {
            salinBerkasStreamingSftp(koneksi, sumber.path, targetPath, timpa = konfirmasi, izin = sumber.izin)
        }
    }

    private suspend fun salinFolderRekursifSftp(
        koneksi: KoneksiSftp,
        sumberRoot: String,
        tujuanRoot: String
    ) {
        val antrean = ArrayDeque<Triple<String, String, Int>>()
        val statAkar = koneksi.stat(sumberRoot) ?: throw GagalSftp.TidakAda(sumberRoot)
        koneksi.buatFolder(tujuanRoot, rekursif = false)
        runCatching { koneksi.chmod(tujuanRoot, statAkar.izin) }
        antrean.addLast(Triple(sumberRoot, tujuanRoot, 0))

        while (antrean.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (dirAsal, dirTujuan, kedalaman) = antrean.removeFirst()
            if (kedalaman >= BATAS_KEDALAMAN_SALIN_SFTP) {
                throw GagalSftp.TautanBerputar(dirAsal)
            }
            val daftarAnak = koneksi.daftar(dirAsal)
            for (anak in daftarAnak) {
                currentCoroutineContext().ensureActive()
                if (anak.adalahTautan) continue
                val tujuanAnak = PengenalSftp.gabung(dirTujuan, anak.nama)
                if (anak.adalahFolder) {
                    koneksi.buatFolder(tujuanAnak, rekursif = false)
                    runCatching { koneksi.chmod(tujuanAnak, anak.izin) }
                    antrean.addLast(Triple(anak.path, tujuanAnak, kedalaman + 1))
                } else {
                    salinBerkasStreamingSftp(koneksi, anak.path, tujuanAnak, timpa = false, izin = anak.izin)
                }
            }
        }
    }

    private suspend fun salinBerkasStreamingSftp(
        koneksi: KoneksiSftp,
        dariPath: String,
        kePath: String,
        timpa: Boolean,
        izin: Int
    ) {
        val inStream = koneksi.bukaBaca(dariPath)
        val outStream = try {
            koneksi.bukaTulisAtomik(kePath, timpa = timpa, izinEksplisit = izin)
        } catch (t: Throwable) {
            runCatching { inStream.close() }
            throw t
        }
        val buffer = ByteArray(UKURAN_BUFFER_SFTP)
        var selesaiSukses = false
        try {
            withContext(ioDispatcher) {
                inStream.use { src ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buffer)
                        if (n == -1) break
                        if (n > 0) outStream.write(buffer, 0, n)
                    }
                    outStream.close()
                    selesaiSukses = true
                }
            }
        } finally {
            if (!selesaiSukses) {
                withContext(NonCancellable) {
                    runCatching { inStream.close() }
                    outStream.batalkan()
                }
            }
        }
    }

    private suspend fun siapkan(lokasi: LokasiBerkas): HasilOperasi<Pair<LokasiSftp, KoneksiSftp>> {
        if (lokasi.jenis != JenisLokasi.SFTP) {
            return HasilOperasi.Gagal("Lokasi bukan bertipe SFTP: ${lokasi.jenis}", PenyebabGalat.VALIDASI)
        }
        val lokSftp = PengenalSftp.urai(lokasi.pengenal)
            ?: return HasilOperasi.Gagal("Pengenal lokasi SFTP tidak valid: ${lokasi.pengenal}", PenyebabGalat.VALIDASI)
        return when (val k = pengelolaKoneksi.ambilAtauBuatKoneksi(lokSftp.idProfil)) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(lokSftp to k.nilai)
            is HasilOperasi.Gagal -> k
        }
    }

    private suspend fun <T> jalankanDenganPemetaan(
        idProfil: String,
        blok: suspend () -> HasilOperasi<T>
    ): HasilOperasi<T> {
        return try {
            blok()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GagalSftp) {
            val profil = runCatching { pengelolaKoneksi.ambilProfil(idProfil) }.getOrNull()
            val (pesan, penyebab) = PengelolaKoneksiSftp.petakanPesanRamah(e, profil)
            HasilOperasi.Gagal(pesan, penyebab)
        } catch (e: IOException) {
            HasilOperasi.Gagal(e.message ?: "Kegagalan I/O pada operasi SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Kegagalan tidak terduga pada SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private fun EntriSftp.keEntitasDomain(idProfil: String): EntitasBerkas =
        DataBerkas(
            pengenal = PengenalSftp.buat(idProfil, path),
            nama = nama,
            jenisLokasi = JenisLokasi.SFTP,
            adalahFolder = adalahFolder,
            adalahTautan = adalahTautan,
            ukuranByte = if (adalahFolder) null else ukuran.takeIf { it >= 0L },
            waktuUbahEpochMs = diubahMs.takeIf { it > 0L },
            tersembunyi = nama.startsWith("."),
            dapatDibaca = true,
            dapatDitulis = true
        ).keDomain()
}

private class SftpAliranIsi(
    private val koneksi: KoneksiSftp,
    private val path: String,
    override val ukuranByte: Long?,
    private val ioDispatcher: CoroutineDispatcher
) : SumberIsiBerkas {
    private val mutex = Mutex()
    private var stream: InputStream? = null
    private var buffer: ByteArray? = null
    private var tertutup = false
    private var sudahEof = false

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (tertutup) return@withLock SumberIsiBerkas.HasilPotongan.Gagal("Sumber baca SFTP sudah ditutup")
        if (sudahEof) return@withLock SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
        try {
            val src = stream ?: koneksi.bukaBaca(path).also { stream = it }
            val buf = buffer ?: ByteArray(UKURAN_BUFFER_SFTP).also { buffer = it }
            val dibaca = withContext(ioDispatcher) { src.read(buf) }
            if (dibaca == -1) {
                sudahEof = true
                tutupInternal()
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
            } else {
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(buf.copyOf(dibaca), selesai = false))
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { tutupInternal() }
            throw e
        } catch (e: GagalSftp) {
            tutupInternal()
            SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Gagal membaca berkas SFTP")
        } catch (e: IOException) {
            tutupInternal()
            SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Kegagalan I/O saat membaca SFTP")
        }
    }

    override suspend fun tutup() {
        mutex.withLock {
            withContext(ioDispatcher + NonCancellable) { tutupInternal() }
        }
    }

    private fun tutupInternal() {
        tertutup = true
        try {
            stream?.close()
        } catch (_: IOException) {
        } finally {
            stream = null
            buffer = null
        }
    }
}

private class SftpAliranTulis(
    private val koneksi: KoneksiSftp,
    private val path: String,
    override val lokasiTarget: LokasiBerkas,
    private val timpa: Boolean,
    private val izinEksplisit: Int? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberTulisBerkas {
    private enum class Status { AKTIF, SELESAI, BATAL }

    private val mutex = Mutex()
    private var status = Status.AKTIF
    private var stream: StreamTulisAtomik? = null

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withLock HasilOperasi.Gagal("Sumber tulis SFTP sudah tidak aktif", PenyebabGalat.VALIDASI)
        }
        try {
            val s = stream ?: koneksi.bukaTulisAtomik(path, timpa = timpa, izinEksplisit = izinEksplisit).also {
                stream = it
            }
            if (potongan.data.isNotEmpty()) {
                withContext(ioDispatcher) { s.write(potongan.data) }
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                batalkanInternal()
                status = Status.BATAL
            }
            throw e
        } catch (e: GagalSftp) {
            batalkanInternal()
            status = Status.BATAL
            val (_, penyebab) = PengelolaKoneksiSftp.petakanPesanRamah(e)
            HasilOperasi.Gagal(e.message ?: "Gagal menulis ke berkas SFTP", penyebab)
        } catch (e: IOException) {
            batalkanInternal()
            status = Status.BATAL
            HasilOperasi.Gagal(e.message ?: "Kegagalan I/O saat menulis SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (status != Status.AKTIF) {
            return@withLock HasilOperasi.Gagal("Sumber tulis SFTP sudah tidak aktif", PenyebabGalat.VALIDASI)
        }
        try {
            val s = stream ?: koneksi.bukaTulisAtomik(path, timpa = timpa, izinEksplisit = izinEksplisit).also {
                stream = it
            }
            withContext(ioDispatcher) { s.close() }
            stream = null
            status = Status.SELESAI
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                batalkanInternal()
                status = Status.BATAL
            }
            throw e
        } catch (e: GagalSftp) {
            batalkanInternal()
            status = Status.BATAL
            val (_, penyebab) = PengelolaKoneksiSftp.petakanPesanRamah(e)
            HasilOperasi.Gagal(e.message ?: "Gagal menyelesaikan penulisan SFTP", penyebab)
        } catch (e: IOException) {
            batalkanInternal()
            status = Status.BATAL
            HasilOperasi.Gagal(e.message ?: "Gagal menyelesaikan penulisan SFTP", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            if (status == Status.SELESAI) {
                return@withContext HasilOperasi.Sukses(Unit)
            }
            status = Status.BATAL
            batalkanInternal()
            HasilOperasi.Sukses(Unit)
        }
    }

    private fun batalkanInternal() {
        runCatching { stream?.batalkan() }
        stream = null
    }
}