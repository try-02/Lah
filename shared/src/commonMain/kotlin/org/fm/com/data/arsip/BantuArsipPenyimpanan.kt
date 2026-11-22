package org.fm.com.data.arsip

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.use
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KebijakanKonflikEkstrak
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas

private const val UKURAN_POTONGAN_STREAM_ARSIP = 8 * 1024

internal sealed interface SumberPathLokal {
    data class Tersedia(
        val path: Path,
        val fileSystem: FileSystem,
        val harusDihapusSetelahnya: Boolean
    ) : SumberPathLokal

    data class Gagal(
        val pesan: String,
        val penyebab: PenyebabGalat
    ) : SumberPathLokal
}

internal object BantuArsipPenyimpanan {

    fun buatPathCacheSementara(
        awalan: String,
        ekstensi: String = ".tmp",
        direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    ): Path {
        val acak = Random.nextLong().toString(16).removePrefix("-")
        return direktoriTemp / ".fm_${awalan}_$acak$ekstensi"
    }

    suspend fun siapkanPathLokalUntukBaca(
        sumber: LokasiBerkas,
        repositoriBerkas: RepositoriBerkas,
        fileSystemLokal: FileSystem,
        direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
    ): SumberPathLokal {
        if (sumber.jenis == JenisLokasi.POSIX) {
            val pathPosix = sumber.pengenal.toPath(normalize = true)
            val meta = runCatching { fileSystemLokal.metadataOrNull(pathPosix) }.getOrNull()
            if (meta != null && meta.isRegularFile) {
                return SumberPathLokal.Tersedia(
                    path = pathPosix,
                    fileSystem = fileSystemLokal,
                    harusDihapusSetelahnya = false
                )
            }
        }

        val sumberBaca = when (val hasil = repositoriBerkas.bukaUntukBaca(sumber)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return SumberPathLokal.Gagal(hasil.pesan, hasil.penyebab)
        }

        val pathTemp = buatPathCacheSementara("arsip_in", ".bin", direktoriTemp)
        var berhasil = false
        try {
            fileSystemLokal.sink(pathTemp, mustCreate = false).buffer().use { dst ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    when (val pot = sumberBaca.potongan()) {
                        is SumberIsiBerkas.HasilPotongan.Isi -> {
                            val data = pot.potongan.data
                            if (data.isNotEmpty()) {
                                dst.write(data)
                            }
                            if (pot.potongan.selesai) break
                        }
                        is SumberIsiBerkas.HasilPotongan.Gagal -> {
                            return SumberPathLokal.Gagal(pot.pesan, PenyebabGalat.TIDAK_DIKETAHUI)
                        }
                    }
                }
                dst.flush()
            }
            berhasil = true
            return SumberPathLokal.Tersedia(
                path = pathTemp,
                fileSystem = fileSystemLokal,
                harusDihapusSetelahnya = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SumberPathLokal.Gagal(
                e.message ?: "Gagal menyalin arsip ke cache sementara",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                sumberBaca.tutup()
                if (!berhasil) {
                    runCatching { fileSystemLokal.delete(pathTemp, mustExist = false) }
                }
            }
        }
    }

    suspend fun tulisFileTempKeRepositori(
        fileSystemLokal: FileSystem,
        pathTempSumber: Path,
        tujuanInduk: LokasiBerkas,
        namaArsip: String,
        timpa: Boolean,
        repositoriBerkas: RepositoriBerkas
    ): HasilOperasi<EntitasBerkas> {
        val writer = when (val buka = repositoriBerkas.bukaUntukTulisDi(tujuanInduk, namaArsip, timpa = timpa)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> return buka
        }

        var selesaiSukses = false
        var ukuranTotal = 0L
        try {
            val buf = ByteArray(UKURAN_POTONGAN_STREAM_ARSIP)
            fileSystemLokal.source(pathTempSumber).buffer().use { src ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val dibaca = src.read(buf)
                    if (dibaca == -1) break
                    ukuranTotal += dibaca
                    val data = if (dibaca == buf.size) buf else buf.copyOf(dibaca)
                    when (val tulis = writer.tulis(PotonganIsi(data, selesai = false))) {
                        is HasilOperasi.Sukses -> Unit
                        is HasilOperasi.Gagal -> return tulis
                    }
                }
            }
            when (val hasilSelesai = writer.selesai()) {
                is HasilOperasi.Sukses -> selesaiSukses = true
                is HasilOperasi.Gagal -> return hasilSelesai
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menulis arsip ke tujuan", PenyebabGalat.TIDAK_DIKETAHUI)
        } finally {
            withContext(NonCancellable) {
                if (!selesaiSukses) {
                    writer.batalkan()
                }
            }
        }

        val lokasiHasil = writer.lokasiTarget
            ?: LokasiBerkas(
                pengenal = if (tujuanInduk.pengenal.endsWith("/")) {
                    "${tujuanInduk.pengenal}$namaArsip"
                } else {
                    "${tujuanInduk.pengenal}/$namaArsip"
                },
                jenis = tujuanInduk.jenis,
                label = namaArsip
            )

        return HasilOperasi.Sukses(
            EntitasBerkas(
                lokasi = lokasiHasil,
                nama = lokasiHasil.label ?: namaArsip,
                jenis = JenisBerkas.ARSIP,
                ukuranByte = ukuranTotal
            )
        )
    }

    suspend fun muatAnakFolderTerindeks(
        repositoriBerkas: RepositoriBerkas,
        folder: LokasiBerkas
    ): HasilOperasi<MutableMap<String, EntitasBerkas>> {
        val hasil = repositoriBerkas.daftarIsi(folder).first()
        return when (hasil) {
            is HasilOperasi.Sukses -> {
                val peta = HashMap<String, EntitasBerkas>(hasil.nilai.size)
                for (e in hasil.nilai) {
                    peta[e.nama.lowercase()] = e
                }
                HasilOperasi.Sukses(peta)
            }
            is HasilOperasi.Gagal -> hasil
        }
    }

    suspend fun pastikanFolderTujuan(
        daftarSegmenFolder: List<String>,
        folderAkarTujuan: LokasiBerkas,
        repositoriBerkas: RepositoriBerkas,
        repositoriOperasi: RepositoriOperasiBerkas,
        cacheFolder: MutableMap<String, LokasiBerkas>,
        cacheIsiFolder: MutableMap<LokasiBerkas, MutableMap<String, EntitasBerkas>>
    ): HasilOperasi<LokasiBerkas> {
        var indukSekarang = folderAkarTujuan
        if (daftarSegmenFolder.isEmpty()) return HasilOperasi.Sukses(indukSekarang)

        val sbJalur = StringBuilder()
        for (segmen in daftarSegmenFolder) {
            currentCoroutineContext().ensureActive()
            if (sbJalur.isNotEmpty()) sbJalur.append('/')
            sbJalur.append(segmen)
            val kunciJalur = sbJalur.toString()

            val cached = cacheFolder[kunciJalur]
            if (cached != null) {
                indukSekarang = cached
                continue
            }

            val isiInduk = cacheIsiFolder.getOrPut(indukSekarang) {
                when (val muat = muatAnakFolderTerindeks(repositoriBerkas, indukSekarang)) {
                    is HasilOperasi.Sukses -> muat.nilai
                    is HasilOperasi.Gagal -> return muat
                }
            }

            val existing = isiInduk[segmen.lowercase()]
            val entitasFolder = if (existing != null) {
                if (existing.jenis != JenisBerkas.FOLDER) {
                    return HasilOperasi.Gagal(
                        "Konflik: '$kunciJalur' sudah ada sebagai berkas, bukan folder",
                        PenyebabGalat.KONFLIK
                    )
                }
                existing
            } else {
                val buat = repositoriOperasi.buatFolder(
                    PermintaanOperasi(
                        sumber = indukSekarang,
                        namaBaru = segmen,
                        konfirmasi = false
                    )
                )
                when (buat) {
                    is HasilOperasi.Sukses -> {
                        isiInduk[segmen.lowercase()] = buat.nilai
                        buat.nilai
                    }
                    is HasilOperasi.Gagal -> return buat
                }
            }

            cacheFolder[kunciJalur] = entitasFolder.lokasi
            indukSekarang = entitasFolder.lokasi
        }
        return HasilOperasi.Sukses(indukSekarang)
    }

    suspend fun tulisEntriHasilKeRepositori(
        source: Source,
        indukTujuan: LokasiBerkas,
        namaBerkas: String,
        kebijakanKonflik: KebijakanKonflikEkstrak,
        batasSatuEntriByte: Long,
        sisaBatasTotalByte: Long,
        repositoriBerkas: RepositoriBerkas,
        cacheIsiFolder: MutableMap<LokasiBerkas, MutableMap<String, EntitasBerkas>>
    ): HasilTulisEntriEkstrak {
        val isiInduk = cacheIsiFolder.getOrPut(indukTujuan) {
            when (val muat = muatAnakFolderTerindeks(repositoriBerkas, indukTujuan)) {
                is HasilOperasi.Sukses -> muat.nilai
                is HasilOperasi.Gagal -> return HasilTulisEntriEkstrak.Gagal(muat.pesan, muat.penyebab)
            }
        }

        val adaDiDisk = isiInduk[namaBerkas.lowercase()]
        if (adaDiDisk != null) {
            if (adaDiDisk.jenis == JenisBerkas.FOLDER) {
                return HasilTulisEntriEkstrak.Gagal(
                    "Tidak dapat mengekstrak berkas '$namaBerkas' karena nama sama sudah ada sebagai folder",
                    PenyebabGalat.KONFLIK
                )
            }
            when (kebijakanKonflik) {
                KebijakanKonflikEkstrak.LEWATI -> return HasilTulisEntriEkstrak.Dilewati
                KebijakanKonflikEkstrak.TOLAK_KONFLIK -> return HasilTulisEntriEkstrak.Konflik(namaBerkas)
                KebijakanKonflikEkstrak.TIMPA -> Unit
            }
        }

        val timpa = kebijakanKonflik == KebijakanKonflikEkstrak.TIMPA
        val writer = when (val buka = repositoriBerkas.bukaUntukTulisDi(indukTujuan, namaBerkas, timpa = timpa)) {
            is HasilOperasi.Sukses -> buka.nilai
            is HasilOperasi.Gagal -> {
                return if (buka.penyebab == PenyebabGalat.KONFLIK &&
                    kebijakanKonflik == KebijakanKonflikEkstrak.LEWATI
                ) {
                    HasilTulisEntriEkstrak.Dilewati
                } else if (buka.penyebab == PenyebabGalat.KONFLIK) {
                    HasilTulisEntriEkstrak.Konflik(namaBerkas)
                } else {
                    HasilTulisEntriEkstrak.Gagal(buka.pesan, buka.penyebab)
                }
            }
        }

        var selesaiSukses = false
        var totalEntri = 0L
        try {
            val buffered = source.buffer()
            val buf = ByteArray(UKURAN_POTONGAN_STREAM_ARSIP)
            while (true) {
                currentCoroutineContext().ensureActive()
                val dibaca = buffered.read(buf)
                if (dibaca == -1) break
                totalEntri += dibaca
                if (totalEntri > batasSatuEntriByte) {
                    return HasilTulisEntriEkstrak.Gagal(
                        "Ukuran hasil ekstraksi '$namaBerkas' melebihi batas per berkas ($batasSatuEntriByte byte)",
                        PenyebabGalat.VALIDASI
                    )
                }
                if (totalEntri > sisaBatasTotalByte) {
                    return HasilTulisEntriEkstrak.Gagal(
                        "Total ukuran hasil ekstraksi melebihi batas maksimal yang diizinkan",
                        PenyebabGalat.VALIDASI
                    )
                }
                val potongan = if (dibaca == buf.size) buf else buf.copyOf(dibaca)
                when (val tulis = writer.tulis(PotonganIsi(potongan, selesai = false))) {
                    is HasilOperasi.Sukses -> Unit
                    is HasilOperasi.Gagal -> return HasilTulisEntriEkstrak.Gagal(tulis.pesan, tulis.penyebab)
                }
            }

            when (val hasilSelesai = writer.selesai()) {
                is HasilOperasi.Sukses -> selesaiSukses = true
                is HasilOperasi.Gagal -> return HasilTulisEntriEkstrak.Gagal(
                    hasilSelesai.pesan,
                    hasilSelesai.penyebab
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HasilTulisEntriEkstrak.Gagal(
                e.message ?: "Gagal mengekstrak entri '$namaBerkas' (arsip mungkin rusak)",
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                if (!selesaiSukses) {
                    writer.batalkan()
                }
            }
        }

        val lokasiHasil = writer.lokasiTarget
            ?: LokasiBerkas(
                pengenal = if (indukTujuan.pengenal.endsWith("/")) {
                    "${indukTujuan.pengenal}$namaBerkas"
                } else {
                    "${indukTujuan.pengenal}/$namaBerkas"
                },
                jenis = indukTujuan.jenis,
                label = namaBerkas
            )
        val entitas = EntitasBerkas(
            lokasi = lokasiHasil,
            nama = lokasiHasil.label ?: namaBerkas,
            jenis = JenisBerkas.LAIN,
            ukuranByte = totalEntri
        )
        isiInduk[namaBerkas.lowercase()] = entitas
        return HasilTulisEntriEkstrak.Sukses(entitas, totalEntri)
    }
}

internal sealed interface HasilTulisEntriEkstrak {
    data class Sukses(val entitas: EntitasBerkas, val byteDitulis: Long) : HasilTulisEntriEkstrak
    data object Dilewati : HasilTulisEntriEkstrak
    data class Konflik(val nama: String) : HasilTulisEntriEkstrak
    data class Gagal(val pesan: String, val penyebab: PenyebabGalat) : HasilTulisEntriEkstrak
}

internal class SumberIsiOkioTerkelola(
    override val ukuranByte: Long?,
    private val source: Source,
    private val batasUkuranByte: Long,
    private val pembersihAkhir: () -> Unit = {}
) : SumberIsiBerkas {
    private val buffered: BufferedSource = source.buffer()
    private val bufferBaca = ByteArray(UKURAN_POTONGAN_STREAM_ARSIP)
    private var totalDibaca = 0L
    private var tertutup = false
    private var selesai = false

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan {
        currentCoroutineContext().ensureActive()
        if (tertutup) return SumberIsiBerkas.HasilPotongan.Gagal("Sumber entri arsip sudah ditutup")
        if (selesai) return SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
        return try {
            val n = buffered.read(bufferBaca)
            if (n == -1) {
                selesai = true
                tutup()
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
            } else {
                totalDibaca += n
                if (totalDibaca > batasUkuranByte) {
                    tutup()
                    return SumberIsiBerkas.HasilPotongan.Gagal(
                        "Ukuran entri arsip melebihi batas baca aman ($batasUkuranByte byte)"
                    )
                }
                val data = if (n == bufferBaca.size) bufferBaca.copyOf() else bufferBaca.copyOf(n)
                SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(data, selesai = false))
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { tutup() }
            throw e
        } catch (e: IOException) {
            tutup()
            SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Gagal membaca isi entri arsip (arsip rusak)")
        } catch (e: Exception) {
            tutup()
            SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Kegagalan saat membaca isi entri arsip")
        }
    }

    override suspend fun tutup() {
        if (tertutup) return
        tertutup = true
        runCatching { buffered.close() }
        runCatching { pembersihAkhir() }
    }
}