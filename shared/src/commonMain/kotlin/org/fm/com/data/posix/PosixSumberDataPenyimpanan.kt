package org.fm.com.data.posix

import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
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

private const val UKURAN_BUFFER_POSIX = 8 * 1024

class PosixSumberDataPenyimpanan(
    private val fileSystem: FileSystem,
    private val platform: PlatformPenyimpanan,
    private val direktoriTemp: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberDataPenyimpanan {

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.POSIX) {
            MatriksKemampuan.untuk(platform, JenisLokasi.POSIX)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
        emit(muatDaftarIsi(lokasi))
    }

    private suspend fun muatDaftarIsi(lokasi: LokasiBerkas): HasilOperasi<List<EntitasBerkas>> =
        withContext(ioDispatcher) {
            if (lokasi.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX: ${lokasi.jenis}", PenyebabGalat.VALIDASI)
            }
            val dirPath = lokasi.pengenal.toPath(normalize = true)
            try {
                val meta = fileSystem.metadataOrNull(dirPath)
                    ?: return@withContext HasilOperasi.Gagal(
                        "Folder tidak ditemukan: ${lokasi.pengenal}",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                if (!meta.isDirectory) {
                    return@withContext HasilOperasi.Gagal(
                        "Bukan folder: ${lokasi.pengenal}",
                        PenyebabGalat.VALIDASI
                    )
                }
                val daftarAnak = fileSystem.list(dirPath)
                val hasil = ArrayList<EntitasBerkas>(daftarAnak.size)
                for (anak in daftarAnak) {
                    currentCoroutineContext().ensureActive()
                    val data = bacaDataBerkas(anak) ?: continue
                    hasil.add(data.keDomain())
                }
                hasil.sortBy { it.nama }
                HasilOperasi.Sukses(hasil)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal membaca folder: ${lokasi.pengenal}")
            } catch (e: Exception) {
                HasilOperasi.Gagal(e.message ?: "Gagal membaca daftar isi POSIX", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
        withContext(ioDispatcher) {
            if (lokasi.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX: ${lokasi.jenis}", PenyebabGalat.VALIDASI)
            }
            val path = lokasi.pengenal.toPath(normalize = true)
            try {
                val meta = fileSystem.metadataOrNull(path)
                    ?: return@withContext HasilOperasi.Gagal(
                        "Berkas tidak ditemukan: ${lokasi.pengenal}",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                if (meta.isDirectory) {
                    return@withContext HasilOperasi.Gagal("Tidak bisa membaca folder sebagai berkas", PenyebabGalat.VALIDASI)
                }
                HasilOperasi.Sukses(SumberOkioBaca(fileSystem, path, meta.size, ioDispatcher))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal membuka berkas untuk baca")
            }
        }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
        withContext(ioDispatcher) {
            if (lokasi.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX: ${lokasi.jenis}", PenyebabGalat.VALIDASI)
            }
            val path = lokasi.pengenal.toPath(normalize = true)
            if (path.name.isBlank() || !ValidasiNama.sah(path.name)) {
                return@withContext HasilOperasi.Gagal("Lokasi tulis tidak sah: ${lokasi.pengenal}", PenyebabGalat.VALIDASI)
            }
            try {
                val meta = fileSystem.metadataOrNull(path)
                if (meta != null && meta.isDirectory) {
                    return@withContext HasilOperasi.Gagal("Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI)
                }
                if (meta != null && !timpa) {
                    return@withContext HasilOperasi.Gagal(
                        "Berkas sudah ada, perlu konfirmasi timpa: ${lokasi.pengenal}",
                        PenyebabGalat.KONFLIK
                    )
                }
                val lok = LokasiBerkas(path.toString(), JenisLokasi.POSIX, path.name)
                HasilOperasi.Sukses(SumberOkioTulis(fileSystem, path, lok, timpa, direktoriTemp, ioDispatcher))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal membuka berkas untuk tulis")
            }
        }

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas,
        nama: String,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> = withContext(ioDispatcher) {
        if (induk.jenis != JenisLokasi.POSIX) {
            return@withContext HasilOperasi.Gagal("Induk bukan bertipe POSIX: ${induk.jenis}", PenyebabGalat.VALIDASI)
        }
        if (!ValidasiNama.sah(nama)) {
            return@withContext HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        val parentPath = induk.pengenal.toPath(normalize = true)
        try {
            val metaInduk = fileSystem.metadataOrNull(parentPath)
                ?: return@withContext HasilOperasi.Gagal(
                    "Folder induk tidak ditemukan: ${induk.pengenal}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (!metaInduk.isDirectory) {
                return@withContext HasilOperasi.Gagal("Induk bukan folder: ${induk.pengenal}", PenyebabGalat.VALIDASI)
            }
            val targetPath = parentPath / nama
            val metaTarget = fileSystem.metadataOrNull(targetPath)
            if (metaTarget != null && metaTarget.isDirectory) {
                return@withContext HasilOperasi.Gagal("Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI)
            }
            if (metaTarget != null && !timpa) {
                return@withContext HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            val lok = LokasiBerkas(targetPath.toString(), JenisLokasi.POSIX, nama)
            HasilOperasi.Sukses(SumberOkioTulis(fileSystem, targetPath, lok, timpa, direktoriTemp, ioDispatcher))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            petakanIoException(e, "Gagal menyiapkan penulisan berkas")
        }
    }

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, adalahFolder = true)

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        buat(permintaan, adalahFolder = false)

    private suspend fun buat(permintaan: PermintaanOperasi, adalahFolder: Boolean): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            if (permintaan.sumber.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX", PenyebabGalat.VALIDASI)
            }
            val nama = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib diisi", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(nama)) {
                return@withContext HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
            }
            val parentPath = permintaan.sumber.pengenal.toPath(normalize = true)
            try {
                val metaInduk = fileSystem.metadataOrNull(parentPath)
                    ?: return@withContext HasilOperasi.Gagal(
                        "Folder induk tidak ditemukan: ${permintaan.sumber.pengenal}",
                        PenyebabGalat.TIDAK_DITEMUKAN
                    )
                if (!metaInduk.isDirectory) {
                    return@withContext HasilOperasi.Gagal("Induk bukan folder", PenyebabGalat.VALIDASI)
                }
                val targetPath = parentPath / nama
                val metaLama = fileSystem.metadataOrNull(targetPath)
                if (metaLama != null) {
                    if (!permintaan.konfirmasi) {
                        return@withContext HasilOperasi.Gagal(
                            "Target sudah ada, perlu konfirmasi timpa: $targetPath",
                            PenyebabGalat.KONFLIK
                        )
                    }
                    fileSystem.deleteRecursively(targetPath, mustExist = false)
                }
                if (adalahFolder) {
                    fileSystem.createDirectory(targetPath, mustCreate = true)
                } else {
                    fileSystem.sink(targetPath, mustCreate = true).buffer().use { it.flush() }
                }
                val entitas = bacaDataBerkas(targetPath)?.keDomain()
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca entitas baru", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal membuat ${if (adalahFolder) "folder" else "berkas"}")
            }
        }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        withContext(ioDispatcher) {
            if (permintaan.sumber.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX", PenyebabGalat.VALIDASI)
            }
            val namaBaru = permintaan.namaBaru
                ?: return@withContext HasilOperasi.Gagal("namaBaru wajib diisi untuk ganti nama", PenyebabGalat.VALIDASI)
            if (!ValidasiNama.sah(namaBaru)) {
                return@withContext HasilOperasi.Gagal("Nama tidak sah: $namaBaru", PenyebabGalat.VALIDASI)
            }
            val sourcePath = permintaan.sumber.pengenal.toPath(normalize = true)
            val parentPath = sourcePath.parent
                ?: return@withContext HasilOperasi.Gagal("Root tidak bisa diganti nama", PenyebabGalat.VALIDASI)
            try {
                if (!fileSystem.exists(sourcePath)) {
                    return@withContext HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                val targetPath = parentPath / namaBaru
                if (targetPath == sourcePath) {
                    val saatIni = bacaDataBerkas(sourcePath)?.keDomain()
                        ?: return@withContext HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                    return@withContext HasilOperasi.Sukses(saatIni)
                }
                val backupPath = siapkanBackupJikaAda(targetPath, parentPath, permintaan.konfirmasi)
                    ?: if (fileSystem.exists(targetPath)) {
                        return@withContext HasilOperasi.Gagal(
                            "Target sudah ada, perlu konfirmasi timpa: $namaBaru",
                            PenyebabGalat.KONFLIK
                        )
                    } else {
                        null
                    }
                try {
                    fileSystem.atomicMove(sourcePath, targetPath)
                    hapusBackupAman(backupPath)
                } catch (e: IOException) {
                    pulihkanBackupAman(backupPath, targetPath)
                    throw e
                }
                val entitas = bacaDataBerkas(targetPath)?.keDomain()
                    ?: return@withContext HasilOperasi.Gagal("Gagal membaca hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                HasilOperasi.Sukses(entitas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal mengganti nama berkas")
            }
        }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        withContext(ioDispatcher) {
            if (permintaan.sumber.jenis != JenisLokasi.POSIX) {
                return@withContext HasilOperasi.Gagal("Lokasi bukan bertipe POSIX", PenyebabGalat.VALIDASI)
            }
            if (!permintaan.konfirmasi) {
                return@withContext HasilOperasi.Gagal("Hapus memerlukan konfirmasi", PenyebabGalat.VALIDASI)
            }
            val path = permintaan.sumber.pengenal.toPath(normalize = true)
            if (path.parent == null) {
                return@withContext HasilOperasi.Gagal("Root tidak dapat dihapus", PenyebabGalat.VALIDASI)
            }
            try {
                if (!fileSystem.exists(path)) {
                    return@withContext HasilOperasi.Gagal("Entitas tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                }
                fileSystem.deleteRecursively(path, mustExist = true)
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                petakanIoException(e, "Gagal menghapus entitas")
            }
        }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = false)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        salinAtauPindah(permintaan, pindah = true)

    private suspend fun salinAtauPindah(
        permintaan: PermintaanOperasi,
        pindah: Boolean
    ): HasilOperasi<EntitasBerkas> = withContext(ioDispatcher) {
        val tujuanInduk = permintaan.tujuan
            ?: return@withContext HasilOperasi.Gagal("Tujuan (folder induk) wajib diisi", PenyebabGalat.VALIDASI)
        if (permintaan.sumber.jenis != JenisLokasi.POSIX || tujuanInduk.jenis != JenisLokasi.POSIX) {
            return@withContext HasilOperasi.Gagal(
                "Salin/pindah lintas jenis lokasi ditangani use case streaming",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        permintaan.namaBaru?.let {
            if (!ValidasiNama.sah(it)) {
                return@withContext HasilOperasi.Gagal("Nama tidak sah: $it", PenyebabGalat.VALIDASI)
            }
        }
        val sourcePath = permintaan.sumber.pengenal.toPath(normalize = true)
        val targetParentPath = tujuanInduk.pengenal.toPath(normalize = true)
        try {
            val metaSource = fileSystem.metadataOrNull(sourcePath)
                ?: return@withContext HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
            val metaInduk = fileSystem.metadataOrNull(targetParentPath)
                ?: return@withContext HasilOperasi.Gagal(
                    "Folder tujuan tidak ditemukan: ${tujuanInduk.pengenal}",
                    PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (!metaInduk.isDirectory) {
                return@withContext HasilOperasi.Gagal("Tujuan bukan folder", PenyebabGalat.VALIDASI)
            }
            val nama = permintaan.namaBaru ?: sourcePath.name
            val targetPath = targetParentPath / nama
            if (targetPath == sourcePath) {
                val saatIni = bacaDataBerkas(sourcePath)?.keDomain()
                    ?: return@withContext HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
                return@withContext HasilOperasi.Sukses(saatIni)
            }
            if (metaSource.isDirectory) {
                val strSource = sourcePath.toString().trimEnd('/')
                val strTargetParent = targetParentPath.toString().trimEnd('/')
                if (strTargetParent == strSource || strTargetParent.startsWith("$strSource/")) {
                    return@withContext HasilOperasi.Gagal(
                        "Tidak bisa menyalin/memindah folder ke dalam dirinya sendiri",
                        PenyebabGalat.VALIDASI
                    )
                }
            }
            val backupPath = siapkanBackupJikaAda(targetPath, targetParentPath, permintaan.konfirmasi)
                ?: if (fileSystem.exists(targetPath)) {
                    return@withContext HasilOperasi.Gagal(
                        "Target sudah ada, perlu konfirmasi timpa: $targetPath",
                        PenyebabGalat.KONFLIK
                    )
                } else {
                    null
                }

            try {
                if (pindah) {
                    try {
                        fileSystem.atomicMove(sourcePath, targetPath)
                    } catch (_: IOException) {
                        salinRekursif(sourcePath, targetPath)
                        fileSystem.deleteRecursively(sourcePath, mustExist = false)
                    }
                } else {
                    salinRekursif(sourcePath, targetPath)
                }
                hapusBackupAman(backupPath)
            } catch (e: CancellationException) {
                pulihkanBackupAman(backupPath, targetPath)
                throw e
            } catch (e: IOException) {
                pulihkanBackupAman(backupPath, targetPath)
                throw e
            }
            val entitas = bacaDataBerkas(targetPath)?.keDomain()
                ?: return@withContext HasilOperasi.Gagal("Gagal membaca entitas tujuan", PenyebabGalat.TIDAK_DITEMUKAN)
            HasilOperasi.Sukses(entitas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            petakanIoException(e, "Gagal ${if (pindah) "memindah" else "menyalin"} berkas")
        }
    }

    private suspend fun salinRekursif(sumber: Path, tujuan: Path) {
        currentCoroutineContext().ensureActive()
        val meta = fileSystem.metadata(sumber)
        if (meta.isDirectory) {
            fileSystem.createDirectory(tujuan, mustCreate = false)
            for (anak in fileSystem.list(sumber)) {
                salinRekursif(anak, tujuan / anak.name)
            }
        } else {
            fileSystem.source(sumber).buffer().use { src ->
                fileSystem.sink(tujuan, mustCreate = false).buffer().use { dst ->
                    dst.writeAll(src)
                    dst.flush()
                }
            }
        }
    }

    private fun siapkanBackupJikaAda(targetPath: Path, parentPath: Path, konfirmasi: Boolean): Path? {
        if (!fileSystem.exists(targetPath)) return null
        if (!konfirmasi) return null
        val backup = parentPath / ".fm_bak_${Random.nextLong().toString(16).removePrefix("-")}"
        return try {
            fileSystem.atomicMove(targetPath, backup)
            backup
        } catch (_: IOException) {
            null
        }
    }

    private fun pulihkanBackupAman(backupPath: Path?, targetPath: Path) {
        if (backupPath == null || !fileSystem.exists(backupPath)) return
        runCatching {
            fileSystem.deleteRecursively(targetPath, mustExist = false)
            fileSystem.atomicMove(backupPath, targetPath)
        }
    }

    private fun hapusBackupAman(backupPath: Path?) {
        if (backupPath == null) return
        runCatching { fileSystem.deleteRecursively(backupPath, mustExist = false) }
    }

    private fun bacaDataBerkas(path: Path): DataBerkas? {
        val meta = fileSystem.metadataOrNull(path) ?: return null
        val nama = path.name.ifBlank { path.toString() }
        val adalahFolder = meta.isDirectory
        val adalahTautan = meta.symlinkTarget != null
        return DataBerkas(
            pengenal = path.toString(),
            nama = nama,
            jenisLokasi = JenisLokasi.POSIX,
            adalahFolder = adalahFolder,
            adalahTautan = adalahTautan,
            ukuranByte = if (adalahFolder) null else meta.size?.takeIf { it >= 0L },
            waktuUbahEpochMs = meta.lastModifiedAtMillis?.takeIf { it >= 0L },
            tersembunyi = nama.startsWith("."),
            dapatDibaca = true,
            dapatDitulis = true
        )
    }

    private fun petakanIoException(e: IOException, pesanDefault: String): HasilOperasi.Gagal {
        val msg = e.message?.lowercase().orEmpty()
        val penyebab = when {
            e is FileNotFoundException || "no such file" in msg -> PenyebabGalat.TIDAK_DITEMUKAN
            "permission denied" in msg || "access is denied" in msg || "eacces" in msg ||
                "eperm" in msg || "read-only" in msg -> PenyebabGalat.IZIN_DITOLAK
            else -> PenyebabGalat.TIDAK_DIKETAHUI
        }
        return HasilOperasi.Gagal(e.message ?: pesanDefault, penyebab)
    }
}

private class SumberOkioBaca(
    private val fileSystem: FileSystem,
    private val path: Path,
    override val ukuranByte: Long?,
    private val ioDispatcher: CoroutineDispatcher
) : SumberIsiBerkas {
    private val mutex = Mutex()
    private var source: BufferedSource? = null
    private var buffer: ByteArray? = null
    private var tertutup = false
    private var sudahEof = false

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (tertutup) return@withContext SumberIsiBerkas.HasilPotongan.Gagal("Sumber baca sudah ditutup")
            if (sudahEof) return@withContext SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
            try {
                val src = source ?: fileSystem.source(path).buffer().also { source = it }
                val buf = buffer ?: ByteArray(UKURAN_BUFFER_POSIX).also { buffer = it }
                val dibaca = src.read(buf)
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
            } catch (e: IOException) {
                tutupInternal()
                SumberIsiBerkas.HasilPotongan.Gagal(e.message ?: "Kegagalan I/O saat membaca berkas")
            }
        }
    }

    override suspend fun tutup() {
        mutex.withLock {
            withContext(ioDispatcher + NonCancellable) {
                tutupInternal()
            }
        }
    }

    private fun tutupInternal() {
        tertutup = true
        try {
            source?.close()
        } catch (_: IOException) {
        } finally {
            source = null
            buffer = null
        }
    }
}

private class SumberOkioTulis(
    private val fileSystem: FileSystem,
    private val targetPath: Path,
    override val lokasiTarget: LokasiBerkas,
    private val timpa: Boolean,
    private val direktoriTemp: Path,
    private val ioDispatcher: CoroutineDispatcher
) : SumberTulisBerkas {
    private enum class Status { AKTIF, SELESAI, BATAL }

    private val mutex = Mutex()
    private var status = Status.AKTIF
    private var tempPath: Path? = null
    private var sink: BufferedSink? = null
    private var totalByte: Long = 0L

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (status != Status.AKTIF) {
                return@withContext HasilOperasi.Gagal("Sumber tulis sudah tidak aktif", PenyebabGalat.VALIDASI)
            }
            try {
                val s = sink ?: siapkanSinkTemp()
                if (potongan.data.isNotEmpty()) {
                    s.write(potongan.data)
                    totalByte += potongan.data.size
                }
                HasilOperasi.Sukses(Unit)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    bersihkanTemp()
                    status = Status.BATAL
                }
                throw e
            } catch (e: IOException) {
                bersihkanTemp()
                status = Status.BATAL
                HasilOperasi.Gagal(e.message ?: "Gagal menulis ke file sementara", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun selesai(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher) {
            currentCoroutineContext().ensureActive()
            if (status != Status.AKTIF) {
                return@withContext HasilOperasi.Gagal("Sumber tulis sudah tidak aktif", PenyebabGalat.VALIDASI)
            }
            try {
                if (sink == null) {
                    siapkanSinkTemp()
                }
                sink?.flush()
                sink?.close()
                sink = null

                val temp = tempPath
                    ?: return@withContext gagalkan("Berkas sementara tidak tersedia", PenyebabGalat.TIDAK_DIKETAHUI)
                val ukuranTemp = fileSystem.metadataOrNull(temp)?.size ?: -1L
                if (ukuranTemp != totalByte) {
                    return@withContext gagalkan(
                        "Verifikasi ukuran gagal: tertulis $totalByte, temp $ukuranTemp",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }

                if (fileSystem.exists(targetPath) && !timpa) {
                    return@withContext gagalkan("Berkas sudah ada, perlu konfirmasi timpa", PenyebabGalat.KONFLIK)
                }

                val backupPath = if (fileSystem.exists(targetPath)) {
                    (targetPath.parent ?: direktoriTemp) / ".fm_bak_${Random.nextLong().toString(16).removePrefix("-")}"
                } else {
                    null
                }
                if (backupPath != null) {
                    runCatching { fileSystem.atomicMove(targetPath, backupPath) }
                }

                try {
                    try {
                        fileSystem.atomicMove(temp, targetPath)
                    } catch (_: IOException) {
                        fileSystem.source(temp).buffer().use { src ->
                            fileSystem.sink(targetPath, mustCreate = false).buffer().use { dst ->
                                dst.writeAll(src)
                                dst.flush()
                            }
                        }
                        runCatching { fileSystem.delete(temp, mustExist = false) }
                    }
                    val ukuranTarget = fileSystem.metadataOrNull(targetPath)?.size ?: -1L
                    if (ukuranTarget != totalByte) {
                        throw IOException("Verifikasi target gagal: tertulis $totalByte, aktual $ukuranTarget")
                    }
                    if (backupPath != null) {
                        runCatching { fileSystem.delete(backupPath, mustExist = false) }
                    }
                    tempPath = null
                    status = Status.SELESAI
                    HasilOperasi.Sukses(Unit)
                } catch (e: Exception) {
                    if (backupPath != null && fileSystem.exists(backupPath)) {
                        runCatching {
                            fileSystem.delete(targetPath, mustExist = false)
                            fileSystem.atomicMove(backupPath, targetPath)
                        }
                    }
                    throw e
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    bersihkanTemp()
                    status = Status.BATAL
                }
                throw e
            } catch (e: IOException) {
                gagalkan(e.message ?: "Gagal menyimpan berkas target", PenyebabGalat.TIDAK_DIKETAHUI)
            }
        }
    }

    override suspend fun batalkan(): HasilOperasi<Unit> = mutex.withLock {
        withContext(ioDispatcher + NonCancellable) {
            if (status == Status.SELESAI) {
                return@withContext HasilOperasi.Sukses(Unit)
            }
            status = Status.BATAL
            bersihkanTemp()
            HasilOperasi.Sukses(Unit)
        }
    }

    private fun siapkanSinkTemp(): BufferedSink {
        val dir = targetPath.parent?.takeIf { fileSystem.exists(it) } ?: direktoriTemp
        val namaTemp = ".fm_tmp_${Random.nextLong().toString(16).removePrefix("-")}"
        val temp = dir / namaTemp
        tempPath = temp
        return fileSystem.sink(temp, mustCreate = false).buffer().also { sink = it }
    }

    private fun gagalkan(pesan: String, penyebab: PenyebabGalat): HasilOperasi.Gagal {
        bersihkanTemp()
        status = Status.BATAL
        return HasilOperasi.Gagal(pesan, penyebab)
    }

    private fun bersihkanTemp() {
        try {
            sink?.close()
        } catch (_: IOException) {
        } finally {
            sink = null
        }
        tempPath?.let { p ->
            runCatching { fileSystem.delete(p, mustExist = false) }
            tempPath = null
        }
    }
}