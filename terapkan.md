### A. Pembaruan Provider Arsip (Tulis Ulang Aman per Entri)

#### 1. `shared/src/commonMain/kotlin/org/fm/com/data/arsip/ProviderZip.kt`

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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

    internal suspend fun tulisUlangDenganPengganti(
        pathArsipLama: Path,
        format: FormatArsip,
        jalurTarget: String,
        pathFilePengganti: Path,
        pathTempOut: Path,
        waktuBaruEpochMs: Long? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<Unit> = withContext(ioDispatcher) {
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
        val pathSumberLama = SumberPathLokal.Tersedia(pathArsipLama, fs, harusDihapusSetelahnya = false)
        val ringkasan = when (val meta = bacaMetadataZipDariPath(pathSumberLama, format, batas, tolakZipSlipFatal = true)) {
            is HasilOperasi.Sukses -> meta.nilai
            is HasilOperasi.Gagal -> return@withContext meta
        }

        if (ringkasan.daftarEntri.any { it.terenkripsi }) {
            return@withContext HasilOperasi.Gagal(
                "Arsip mengandung entri terenkripsi sehingga tidak dapat ditulis ulang dengan aman",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        if (ringkasan.daftarEntri.any { it.adalahTautanSimbolik }) {
            return@withContext HasilOperasi.Gagal(
                "Arsip mengandung tautan simbolik sehingga penulisan ulang dibatalkan demi keamanan",
                PenyebabGalat.VALIDASI
            )
        }
        if (ringkasan.daftarEntri.none { !it.adalahFolder && it.jalurInternal == jalurNormal }) {
            return@withContext HasilOperasi.Gagal(
                "Entri '$jalurNormal' tidak ditemukan dalam arsip untuk diperbarui",
                PenyebabGalat.TIDAK_DITEMUKAN
            )
        }

        val zipLama = adapterPlatform.bukaZipDariPath(fs, pathArsipLama)
        val penulis = adapterPlatform.bukaPenulisZip(fs, pathTempOut)
        var selesai = false
        val buf = ByteArray(8 * 1024)
        try {
            for (entri in ringkasan.daftarEntri) {
                currentCoroutineContext().ensureActive()
                if (entri.adalahFolder) {
                    penulis.mulaiEntri(entri.jalurInternal, adalahFolder = true, waktuUbahEpochMs = entri.waktuUbahEpochMs)
                    penulis.tutupEntri()
                    continue
                }

                val adalahTarget = entri.jalurInternal == jalurNormal
                val waktu = if (adalahTarget) (waktuBaruEpochMs ?: entri.waktuUbahEpochMs) else entri.waktuUbahEpochMs
                penulis.mulaiEntri(entri.jalurInternal, adalahFolder = false, waktuUbahEpochMs = waktu)

                val source = if (adalahTarget) {
                    fs.source(pathFilePengganti)
                } else {
                    zipLama.bukaSumberEntri(entri.jalurInternal)
                }
                source.buffer().use { src ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buf)
                        if (n == -1) break
                        penulis.tulisPotongan(buf, 0, n)
                    }
                }
                penulis.tutupEntri()
            }
            penulis.selesaikanDanTutup()
            selesai = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext HasilOperasi.Gagal(
                petakanPesanErrorZip(e, "Gagal menulis ulang arsip ZIP"),
                PenyebabGalat.TIDAK_DIKETAHUI
            )
        } finally {
            withContext(NonCancellable) {
                runCatching { zipLama.tutup() }
                if (!selesai) {
                    penulis.batalkanDanTutup()
                }
            }
        }

        when (
            val verifikasi = bacaMetadataZipDariPath(
                SumberPathLokal.Tersedia(pathTempOut, fs, harusDihapusSetelahnya = false),
                format,
                batas,
                tolakZipSlipFatal = true
            )
        ) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(Unit)
            is HasilOperasi.Gagal -> {
                runCatching { fs.delete(pathTempOut, mustExist = false) }
                HasilOperasi.Gagal("Verifikasi arsip hasil pembaruan gagal: ${verifikasi.pesan}", verifikasi.penyebab)
            }
        }
    }
```

---

#### 2. `shared/src/commonMain/kotlin/org/fm/com/data/arsip/ProviderTar.kt`

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

---

#### 3. `shared/src/commonMain/kotlin/org/fm/com/data/arsip/ProviderGzip.kt`

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

---

### B. Pembaruan Domain & Ekstraktor Arsip (Konteks Edit + Perbarui Arsip Aman)

#### 4. `shared/src/commonMain/kotlin/org/fm/com/domain/usecase/EkstrakArsipUseCase.kt`

ganti kode ini:
```kotlin
data class StatusLapisanArsip(
    val jejakLapisan: List<LapisanJejakArsip>,
    val ringkasan: RingkasanIsiArsip,
    val kedalamanSaatIni: Int
) {
    val lapisanAktif: LapisanJejakArsip get() = jejakLapisan.last()
}
```
dengan kode ini:
```kotlin
data class StatusLapisanArsip(
    val jejakLapisan: List<LapisanJejakArsip>,
    val ringkasan: RingkasanIsiArsip,
    val kedalamanSaatIni: Int
) {
    val lapisanAktif: LapisanJejakArsip get() = jejakLapisan.last()
}

data class KonteksEditArsip(
    val sumberArsipAkar: LokasiBerkas,
    val namaArsipTampilan: String,
    val formatArsipAktif: FormatArsip,
    val rantaiJalurArsipBersarang: List<String> = emptyList(),
    val jalurEntriTarget: String,
    val idSesiArsip: String? = null
)

data class HasilSiapkanEditArsip(
    val entitasSementara: EntitasBerkas,
    val konteks: KonteksEditArsip
)
```

ganti kode ini:
```kotlin
    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<EntitasBerkas>

    suspend fun tutupSesi(idSesi: String)
}
```
dengan kode ini:
```kotlin
    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        untukEdit: Boolean = false,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<HasilSiapkanEditArsip>

    suspend fun perbaruiBerkasDalamArsip(
        konteks: KonteksEditArsip,
        lokasiBerkasEdit: LokasiBerkas,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip?>

    suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip?

    suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas)

    suspend fun tutupSesi(idSesi: String)
}
```

ganti kode ini:
```kotlin
    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<EntitasBerkas> =
        pelaksana.siapkanPratinjauBerkas(idSesi, jalurInternal, kataSandi, batas)

    suspend fun tutupSesi(idSesi: String) {
        pelaksana.tutupSesi(idSesi)
    }
```
dengan kode ini:
```kotlin
    suspend fun siapkanPratinjauBerkas(
        idSesi: String,
        jalurInternal: String,
        untukEdit: Boolean = false,
        kataSandi: String? = null,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<HasilSiapkanEditArsip> =
        pelaksana.siapkanPratinjauBerkas(idSesi, jalurInternal, untukEdit, kataSandi, batas)

    suspend fun perbaruiBerkasDalamArsip(
        konteks: KonteksEditArsip,
        lokasiBerkasEdit: LokasiBerkas,
        batas: KebijakanBatasArsip = KebijakanBatasArsip()
    ): HasilOperasi<StatusLapisanArsip?> =
        pelaksana.perbaruiBerkasDalamArsip(konteks, lokasiBerkasEdit, batas)

    suspend fun ambilStatusSesi(idSesi: String): StatusLapisanArsip? =
        pelaksana.ambilStatusSesi(idSesi)

    suspend fun bersihkanBerkasSementara(lokasi: LokasiBerkas) {
        pelaksana.bersihkanBerkasSementara(lokasi)
    }

    suspend fun tutupSesi(idSesi: String) {
        pelaksana.tutupSesi(idSesi)
    }
```

---

#### 5. `shared/src/commonMain/kotlin/org/fm/com/data/arsip/EkstraktorArsip.kt`

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

ganti kode ini:
```kotlin
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
```
dengan kode ini:
```kotlin
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
```

---

### C. Editor Konfirmasi Pembaruan Arsip & Pembatalan Aman

#### 6. `shared/src/commonMain/kotlin/org/fm/com/presentation/editor/ModelTabEditor.kt`

ganti kode ini:
```kotlin
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.usecase.EncodingTeks
import org.fm.com.domain.usecase.JenisAkhirBaris
```
dengan kode ini:
```kotlin
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.usecase.EncodingTeks
import org.fm.com.domain.usecase.JenisAkhirBaris
import org.fm.com.domain.usecase.KonteksEditArsip
```

ganti kode ini:
```kotlin
data class ModelTabEditor(
    val idTab: String,
    val lokasi: LokasiBerkas?,
    val namaBerkas: String,
    val statusMuat: StatusMuatTab = StatusMuatTab.MEMUAT,
```
dengan kode ini:
```kotlin
data class ModelTabEditor(
    val idTab: String,
    val lokasi: LokasiBerkas?,
    val namaBerkas: String,
    val konteksArsip: KonteksEditArsip? = null,
    val statusMuat: StatusMuatTab = StatusMuatTab.MEMUAT,
```

---

#### 7. `shared/src/commonMain/kotlin/org/fm/com/presentation/editor/StateEditor.kt`

ganti kode ini:
```kotlin
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.RentangKecocokan
```
dengan kode ini:
```kotlin
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.RentangKecocokan
```

ganti kode ini:
```kotlin
@Immutable
data class StateEditor(
    val daftarTab: List<ModelTabEditor> = emptyList(),
    val idTabAktif: String? = null,
    val wordWrapUmum: Boolean = true,
    val lokasiFolderSimpanDefault: LokasiBerkas? = null,
    val sedangMenyimpan: Boolean = false,
    val tabMenungguKonfirmasiTutup: ModelTabEditor? = null,
    val konfirmasiKonflikSimpan: PermintaanKonfirmasiKonflikSimpan? = null,
    val dialogSimpanSebagai: PermintaanSimpanSebagai? = null,
```
dengan kode ini:
```kotlin
@Immutable
data class PermintaanKonfirmasiPerbaruiArsip(
    val idTab: String,
    val namaBerkas: String,
    val namaArsip: String,
    val tutupSetelahPerbarui: Boolean,
    val kembaliKeArsipSetelahSelesai: Boolean = false
)

@Immutable
data class AcaraArsipDiperbaruiDariEditor(
    val id: Long,
    val kembaliKeArsip: Boolean
)

@Immutable
data class StateEditor(
    val daftarTab: List<ModelTabEditor> = emptyList(),
    val idTabAktif: String? = null,
    val wordWrapUmum: Boolean = true,
    val lokasiFolderSimpanDefault: LokasiBerkas? = null,
    val sedangMenyimpan: Boolean = false,
    val tabMenungguKonfirmasiTutup: ModelTabEditor? = null,
    val konfirmasiKonflikSimpan: PermintaanKonfirmasiKonflikSimpan? = null,
    val konfirmasiPerbaruiArsip: PermintaanKonfirmasiPerbaruiArsip? = null,
    val acaraArsipDiperbarui: AcaraArsipDiperbaruiDariEditor? = null,
    val dialogSimpanSebagai: PermintaanSimpanSebagai? = null,
```

ganti kode ini:
```kotlin
    data class BukaBerkas(
        val entitas: EntitasBerkas,
        val barisTujuan: Int? = null
    ) : IntentEditor
```
dengan kode ini:
```kotlin
    data class BukaBerkas(
        val entitas: EntitasBerkas,
        val barisTujuan: Int? = null,
        val konteksArsip: KonteksEditArsip? = null
    ) : IntentEditor
```

ganti kode ini:
```kotlin
    data object KonfirmasiTimpaKonflikSimpan : IntentEditor
    data object BatalKonflikSimpan : IntentEditor
```
dengan kode ini:
```kotlin
    data object KonfirmasiTimpaKonflikSimpan : IntentEditor
    data object BatalKonflikSimpan : IntentEditor
    data class MintaKeluarDariTabArsip(val idTab: String) : IntentEditor
    data object KonfirmasiPerbaruiArsip : IntentEditor
    data object BatalkanEditDalamArsip : IntentEditor
    data object TutupDialogPerbaruiArsip : IntentEditor
    data object KonsumsiAcaraArsipDiperbarui : IntentEditor
```

---

#### 8. `shared/src/commonMain/kotlin/org/fm/com/presentation/editor/ViewModelEditor.kt`

ganti kode ini:
```kotlin
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.CariGantiUseCase
import org.fm.com.domain.usecase.HasilBukaBerkasTeks
import org.fm.com.domain.usecase.HasilCariEditor
import org.fm.com.domain.usecase.HasilOperasiGanti
import org.fm.com.domain.usecase.HasilSimpanBerkas
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.OpsiCariEditor
import org.fm.com.domain.usecase.RentangKecocokan
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.domain.usecase.SimpanBerkasUseCase
import org.fm.com.domain.usecase.StatistikTeksUseCase

class ViewModelEditor(
    private val repositoriBerkas: RepositoriBerkas,
    private val bukaBerkasTeksUseCase: BukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repositoriBerkas),
    private val cariGantiUseCase: CariGantiUseCase = CariGantiUseCase(),
    private val riwayatPencarianUseCase: RiwayatPencarianUseCase? = null,
    private val statistikTeksUseCase: StatistikTeksUseCase = StatistikTeksUseCase(),
    private val simpanBerkasUseCase: SimpanBerkasUseCase? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
```
dengan kode ini:
```kotlin
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.CariGantiUseCase
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.HasilBukaBerkasTeks
import org.fm.com.domain.usecase.HasilCariEditor
import org.fm.com.domain.usecase.HasilOperasiGanti
import org.fm.com.domain.usecase.HasilSimpanBerkas
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.OpsiCariEditor
import org.fm.com.domain.usecase.RentangKecocokan
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.domain.usecase.SimpanBerkasUseCase
import org.fm.com.domain.usecase.StatistikTeksUseCase

class ViewModelEditor(
    private val repositoriBerkas: RepositoriBerkas,
    private val bukaBerkasTeksUseCase: BukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repositoriBerkas),
    private val cariGantiUseCase: CariGantiUseCase = CariGantiUseCase(),
    private val riwayatPencarianUseCase: RiwayatPencarianUseCase? = null,
    private val statistikTeksUseCase: StatistikTeksUseCase = StatistikTeksUseCase(),
    private val simpanBerkasUseCase: SimpanBerkasUseCase? = null,
    private val ekstrakArsipUseCase: EkstrakArsipUseCase? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
```

ganti kode ini:
```kotlin
    private var penghitungTabKosong = 0L
    private var urutanNavigasi = 0L
```
dengan kode ini:
```kotlin
    private var penghitungTabKosong = 0L
    private var urutanNavigasi = 0L
    private var urutanAcaraArsip = 0L
```

ganti kode ini:
```kotlin
            is IntentEditor.BukaBerkas -> {
                val entitas = intent.entitas
                bukaLokasi(
                    lokasi = entitas.lokasi,
                    nama = entitas.nama,
                    dapatDitulis = entitas.dapatDitulis,
                    ukuranByte = entitas.ukuranByte,
                    waktuUbahMs = entitas.waktuUbahEpochMs,
                    barisTujuan = intent.barisTujuan
                )
            }
```
dengan kode ini:
```kotlin
            is IntentEditor.BukaBerkas -> {
                val entitas = intent.entitas
                bukaLokasi(
                    lokasi = entitas.lokasi,
                    nama = entitas.nama,
                    dapatDitulis = entitas.dapatDitulis,
                    ukuranByte = entitas.ukuranByte,
                    waktuUbahMs = entitas.waktuUbahEpochMs,
                    barisTujuan = intent.barisTujuan,
                    konteksArsip = intent.konteksArsip
                )
            }
```

ganti kode ini:
```kotlin
            is IntentEditor.KonfirmasiTimpaKonflikSimpan -> konfirmasiTimpaKonflikSimpan()
            is IntentEditor.BatalKonflikSimpan -> _state.update { it.copy(konfirmasiKonflikSimpan = null) }
```
dengan kode ini:
```kotlin
            is IntentEditor.KonfirmasiTimpaKonflikSimpan -> konfirmasiTimpaKonflikSimpan()
            is IntentEditor.BatalKonflikSimpan -> _state.update { it.copy(konfirmasiKonflikSimpan = null) }
            is IntentEditor.MintaKeluarDariTabArsip -> mintaKeluarDariTabArsip(intent.idTab)
            is IntentEditor.KonfirmasiPerbaruiArsip -> konfirmasiPerbaruiArsip()
            is IntentEditor.BatalkanEditDalamArsip -> batalkanEditDalamArsip()
            is IntentEditor.TutupDialogPerbaruiArsip -> _state.update { it.copy(konfirmasiPerbaruiArsip = null) }
            is IntentEditor.KonsumsiAcaraArsipDiperbarui -> _state.update { it.copy(acaraArsipDiperbarui = null) }
```

ganti kode ini:
```kotlin
    private fun bukaLokasi(
        lokasi: LokasiBerkas,
        nama: String,
        dapatDitulis: Boolean,
        ukuranByte: Long?,
        waktuUbahMs: Long?,
        barisTujuan: Int?
    ) {
        val idTab = buatIdTabLokasi(lokasi)
        val tabAda = _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        if (tabAda != null) {
            pilihTab(idTab)
            if (tabAda.statusMuat == StatusMuatTab.SIAP && barisTujuan != null) {
                lompatKeBaris(idTab, barisTujuan)
            } else if (tabAda.statusMuat == StatusMuatTab.GAGAL) {
                muatIsiTab(idTab, lokasi, barisTujuan)
            }
            return
        }

        val kapabilitasTulis = repositoriBerkas.kapabilitas(lokasi)?.apakahDidukung(OperasiBerkas.MENULIS) ?: true
        val hanyaBacaPermanen = !dapatDitulis || !kapabilitasTulis
        val tabBaru = ModelTabEditor(
            idTab = idTab,
            lokasi = lokasi,
            namaBerkas = nama.ifBlank { "Berkas" },
            statusMuat = StatusMuatTab.MEMUAT,
            hanyaBacaPermanen = hanyaBacaPermanen,
            modeHanyaBaca = hanyaBacaPermanen,
            ukuranByteAsli = ukuranByte,
            waktuUbahTerakhirMs = waktuUbahMs
        )
```
dengan kode ini:
```kotlin
    private fun bukaLokasi(
        lokasi: LokasiBerkas,
        nama: String,
        dapatDitulis: Boolean,
        ukuranByte: Long?,
        waktuUbahMs: Long?,
        barisTujuan: Int?,
        konteksArsip: KonteksEditArsip? = null
    ) {
        val idTab = buatIdTabLokasi(lokasi)
        val tabAda = if (konteksArsip != null) {
            _state.value.daftarTab.firstOrNull { tab ->
                val k = tab.konteksArsip
                k != null &&
                    k.sumberArsipAkar == konteksArsip.sumberArsipAkar &&
                    k.rantaiJalurArsipBersarang == konteksArsip.rantaiJalurArsipBersarang &&
                    k.jalurEntriTarget == konteksArsip.jalurEntriTarget
            }
        } else {
            _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        }
        if (tabAda != null) {
            if (konteksArsip != null && tabAda.lokasi != null && tabAda.lokasi != lokasi) {
                val lokasiBaru = lokasi
                viewModelScope.launch { ekstrakArsipUseCase?.bersihkanBerkasSementara(lokasiBaru) }
            }
            pilihTab(tabAda.idTab)
            if (tabAda.statusMuat == StatusMuatTab.SIAP && barisTujuan != null) {
                lompatKeBaris(tabAda.idTab, barisTujuan)
            } else if (tabAda.statusMuat == StatusMuatTab.GAGAL) {
                muatIsiTab(tabAda.idTab, tabAda.lokasi ?: lokasi, barisTujuan)
            }
            return
        }

        val kapabilitasTulis = repositoriBerkas.kapabilitas(lokasi)?.apakahDidukung(OperasiBerkas.MENULIS) ?: true
        val hanyaBacaPermanen = !dapatDitulis || !kapabilitasTulis
        val tabBaru = ModelTabEditor(
            idTab = idTab,
            lokasi = lokasi,
            namaBerkas = nama.ifBlank { "Berkas" },
            konteksArsip = konteksArsip,
            statusMuat = StatusMuatTab.MEMUAT,
            hanyaBacaPermanen = hanyaBacaPermanen,
            modeHanyaBaca = hanyaBacaPermanen,
            ukuranByteAsli = ukuranByte,
            waktuUbahTerakhirMs = waktuUbahMs
        )
```

ganti kode ini:
```kotlin
    private fun mintaTutupTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.adaPerubahanBelumTersimpan) {
            _state.update { it.copy(tabMenungguKonfirmasiTutup = tab) }
        } else {
            hapusTabInternal(idTab)
        }
    }
```
dengan kode ini:
```kotlin
    private fun mintaTutupTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val kArsip = tab.konteksArsip
        if (kArsip != null && tab.adaPerubahanBelumTersimpan && !tab.hanyaBacaPermanen) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = tab.idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = true,
                        kembaliKeArsipSetelahSelesai = false
                    )
                )
            }
        } else if (tab.adaPerubahanBelumTersimpan) {
            _state.update { it.copy(tabMenungguKonfirmasiTutup = tab) }
        } else {
            hapusTabInternal(idTab)
        }
    }

    private fun mintaKeluarDariTabArsip(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val kArsip = tab.konteksArsip ?: return
        if (tab.adaPerubahanBelumTersimpan && !tab.hanyaBacaPermanen) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = tab.idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = true,
                        kembaliKeArsipSetelahSelesai = true
                    )
                )
            }
        } else {
            hapusTabInternal(tab.idTab)
            _state.update {
                it.copy(
                    acaraArsipDiperbarui = AcaraArsipDiperbaruiDariEditor(
                        id = ++urutanAcaraArsip,
                        kembaliKeArsip = true
                    )
                )
            }
        }
    }
```

ganti kode ini:
```kotlin
        pekerjaanMuat.values.forEach { it.cancel() }
        pekerjaanMuat.clear()
        pekerjaanCari?.cancel()
        pekerjaanStatistik?.cancel()
        pekerjaanSimpan?.cancel()
        sejarahPerTab.clear()
        _state.update {
            it.copy(
                daftarTab = emptyList(),
                idTabAktif = null,
                sedangMenyimpan = false,
                tabMenungguKonfirmasiTutup = null,
                konfirmasiKonflikSimpan = null,
                dialogSimpanSebagai = null,
```
dengan kode ini:
```kotlin
        val lokasiArsipUntukDibersihkan = _state.value.daftarTab
            .filter { it.konteksArsip != null && it.lokasi != null }
            .mapNotNull { it.lokasi }
        if (lokasiArsipUntukDibersihkan.isNotEmpty() && ekstrakArsipUseCase != null) {
            viewModelScope.launch {
                for (lok in lokasiArsipUntukDibersihkan) {
                    ekstrakArsipUseCase.bersihkanBerkasSementara(lok)
                }
            }
        }
        pekerjaanMuat.values.forEach { it.cancel() }
        pekerjaanMuat.clear()
        pekerjaanCari?.cancel()
        pekerjaanStatistik?.cancel()
        pekerjaanSimpan?.cancel()
        sejarahPerTab.clear()
        _state.update {
            it.copy(
                daftarTab = emptyList(),
                idTabAktif = null,
                sedangMenyimpan = false,
                tabMenungguKonfirmasiTutup = null,
                konfirmasiKonflikSimpan = null,
                konfirmasiPerbaruiArsip = null,
                dialogSimpanSebagai = null,
```

ganti kode ini:
```kotlin
    private fun hapusTabInternal(idTab: String) {
        pekerjaanMuat.remove(idTab)?.cancel()
        sejarahPerTab.remove(idTab)
        if (_state.value.idTabAktif == idTab) {
            pekerjaanStatistik?.cancel()
        }
        _state.update { st ->
            val idx = st.daftarTab.indexOfFirst { it.idTab == idTab }
```
dengan kode ini:
```kotlin
    private fun hapusTabInternal(idTab: String) {
        val tabTarget = _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        if (tabTarget?.konteksArsip != null && tabTarget.lokasi != null && ekstrakArsipUseCase != null) {
            val lok = tabTarget.lokasi
            viewModelScope.launch { ekstrakArsipUseCase.bersihkanBerkasSementara(lok) }
        }
        pekerjaanMuat.remove(idTab)?.cancel()
        sejarahPerTab.remove(idTab)
        if (_state.value.idTabAktif == idTab) {
            pekerjaanStatistik?.cancel()
        }
        _state.update { st ->
            val idx = st.daftarTab.indexOfFirst { it.idTab == idTab }
```

ganti kode ini:
```kotlin
            val menungguBaru = if (st.tabMenungguKonfirmasiTutup?.idTab == idTab) null else st.tabMenungguKonfirmasiTutup
            val konflikBaru = if (st.konfirmasiKonflikSimpan?.idTab == idTab) null else st.konfirmasiKonflikSimpan
            val simpanSebagaiBaru = if (st.dialogSimpanSebagai?.idTab == idTab) null else st.dialogSimpanSebagai
```
dengan kode ini:
```kotlin
            val menungguBaru = if (st.tabMenungguKonfirmasiTutup?.idTab == idTab) null else st.tabMenungguKonfirmasiTutup
            val konflikBaru = if (st.konfirmasiKonflikSimpan?.idTab == idTab) null else st.konfirmasiKonflikSimpan
            val arsipKonfBaru = if (st.konfirmasiPerbaruiArsip?.idTab == idTab) null else st.konfirmasiPerbaruiArsip
            val simpanSebagaiBaru = if (st.dialogSimpanSebagai?.idTab == idTab) null else st.dialogSimpanSebagai
```

ganti kode ini:
```kotlin
            st.copy(
                daftarTab = daftarBaru,
                idTabAktif = aktifBaru,
                tabMenungguKonfirmasiTutup = menungguBaru,
                konfirmasiKonflikSimpan = konflikBaru,
                dialogSimpanSebagai = simpanSebagaiBaru,
```
dengan kode ini:
```kotlin
            st.copy(
                daftarTab = daftarBaru,
                idTabAktif = aktifBaru,
                tabMenungguKonfirmasiTutup = menungguBaru,
                konfirmasiKonflikSimpan = konflikBaru,
                konfirmasiPerbaruiArsip = arsipKonfBaru,
                dialogSimpanSebagai = simpanSebagaiBaru,
```

ganti kode ini:
```kotlin
    private fun simpanTab(
        idTab: String,
        paksaTimpaKonflik: Boolean,
        tutupSetelahSimpan: Boolean
    ) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.hanyaBacaPermanen) {
            _state.update { it.copy(pesanInfo = "Lokasi berkas tidak mendukung penulisan. Gunakan Simpan Sebagai.") }
            return
        }
        val lokasi = tab.lokasi
        if (lokasi == null) {
            bukaDialogSimpanSebagai(idTab, tutupSetelahSimpan = tutupSetelahSimpan)
            return
        }
```
dengan kode ini:
```kotlin
    private fun simpanTab(
        idTab: String,
        paksaTimpaKonflik: Boolean,
        tutupSetelahSimpan: Boolean
    ) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.hanyaBacaPermanen) {
            _state.update { it.copy(pesanInfo = "Lokasi berkas tidak mendukung penulisan. Gunakan Simpan Sebagai.") }
            return
        }
        val kArsip = tab.konteksArsip
        if (kArsip != null) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = tutupSetelahSimpan,
                        kembaliKeArsipSetelahSelesai = false
                    )
                )
            }
            return
        }
        val lokasi = tab.lokasi
        if (lokasi == null) {
            bukaDialogSimpanSebagai(idTab, tutupSetelahSimpan = tutupSetelahSimpan)
            return
        }
```

ganti kode ini:
```kotlin
    private fun konfirmasiTimpaKonflikSimpan() {
        val permintaan = _state.value.konfirmasiKonflikSimpan ?: return
        simpanTab(
            idTab = permintaan.idTab,
            paksaTimpaKonflik = true,
            tutupSetelahSimpan = permintaan.tutupSetelahSimpan
        )
    }
```
dengan kode ini:
```kotlin
    private fun konfirmasiTimpaKonflikSimpan() {
        val permintaan = _state.value.konfirmasiKonflikSimpan ?: return
        simpanTab(
            idTab = permintaan.idTab,
            paksaTimpaKonflik = true,
            tutupSetelahSimpan = permintaan.tutupSetelahSimpan
        )
    }

    private fun konfirmasiPerbaruiArsip() {
        val permintaan = _state.value.konfirmasiPerbaruiArsip ?: return
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == permintaan.idTab } ?: return
        val kArsip = tab.konteksArsip ?: return
        val lokasiTemp = tab.lokasi ?: return
        val simpanUc = simpanBerkasUseCase ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null, pesanInfo = "Layanan simpan belum tersedia") }
            return
        }
        val arsipUc = ekstrakArsipUseCase ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null, pesanInfo = "Layanan pembaruan arsip belum tersedia") }
            return
        }

        val snapshotIsi = tab.isiSaatIni
        pekerjaanSimpan?.cancel()
        _state.update { it.copy(konfirmasiPerbaruiArsip = null, sedangMenyimpan = true) }

        val job = viewModelScope.launch {
            // 1. Tulis ke berkas kerja sementara dengan verifikasi
            val hasilTemp = simpanUc.simpan(
                lokasi = lokasiTemp,
                isiTeks = snapshotIsi,
                encoding = tab.encoding,
                akhirBaris = tab.akhirBaris,
                sidikDiharapkan = null,
                paksaTimpaKonflik = true
            )
            if (hasilTemp !is HasilSimpanBerkas.Sukses) {
                val pesan = when (hasilTemp) {
                    is HasilSimpanBerkas.Gagal -> hasilTemp.pesan
                    is HasilSimpanBerkas.KonflikEksternal -> hasilTemp.pesan
                    is HasilSimpanBerkas.KonflikTujuan -> hasilTemp.pesan
                    is HasilSimpanBerkas.Sukses -> ""
                }
                _state.update {
                    it.copy(
                        sedangMenyimpan = false,
                        pesanInfo = "Gagal menyimpan salinan kerja: $pesan"
                    )
                }
                return@launch
            }

            // 2. Bangun ulang & verifikasi arsip target
            when (val hasilArsip = arsipUc.perbaruiBerkasDalamArsip(kArsip, hasilTemp.lokasiHasil)) {
                is HasilOperasi.Sukses -> {
                    perbaruiTab(tab.idTab) {
                        it.copy(
                            isiTersimpan = snapshotIsi,
                            ukuranByteAsli = hasilTemp.sidikBaru.ukuranByte,
                            waktuUbahTerakhirMs = hasilTemp.waktuSimpanEpochMs,
                            sidikIsiTersimpan = hasilTemp.sidikBaru
                        )
                    }
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Arsip '${kArsip.namaArsipTampilan}' berhasil diperbarui",
                            acaraArsipDiperbarui = AcaraArsipDiperbaruiDariEditor(
                                id = ++urutanAcaraArsip,
                                kembaliKeArsip = permintaan.kembaliKeArsipSetelahSelesai
                            )
                        )
                    }
                    if (permintaan.tutupSetelahPerbarui) {
                        hapusTabInternal(tab.idTab)
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Gagal memperbarui arsip (arsip asli dipertahankan): ${hasilArsip.pesan}"
                        )
                    }
                }
            }
        }
        pekerjaanSimpan = job
        job.invokeOnCompletion {
            if (pekerjaanSimpan === job) pekerjaanSimpan = null
        }
    }

    private fun batalkanEditDalamArsip() {
        val permintaan = _state.value.konfirmasiPerbaruiArsip ?: return
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == permintaan.idTab } ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null) }
            return
        }
        _state.update { it.copy(konfirmasiPerbaruiArsip = null) }

        sejarahPerTab[tab.idTab]?.bersihkan()
        if (permintaan.tutupSetelahPerbarui) {
            hapusTabInternal(tab.idTab)
            _state.update {
                it.copy(
                    pesanInfo = "Edit berkas dibatalkan; arsip tetap seperti semula",
                    acaraArsipDiperbarui = if (permintaan.kembaliKeArsipSetelahSelesai) {
                        AcaraArsipDiperbaruiDariEditor(
                            id = ++urutanAcaraArsip,
                            kembaliKeArsip = true
                        )
                    } else {
                        it.acaraArsipDiperbarui
                    }
                )
            }
        } else {
            val jumlahBarisAsli = hitungJumlahBaris(tab.isiTersimpan)
            perbaruiTab(tab.idTab) {
                it.copy(
                    isiSaatIni = it.isiTersimpan,
                    jumlahBaris = jumlahBarisAsli,
                    posisiKursor = PosisiKursor(),
                    dapatUndo = false,
                    dapatRedo = false,
                    urutanNavigasiKursor = ++urutanNavigasi,
                    urutanSinkronTeks = it.urutanSinkronTeks + 1L
                )
            }
            _state.update {
                it.copy(pesanInfo = "Edit berkas dibatalkan; arsip tetap seperti semula")
            }
        }
    }
```

---

#### 9. `shared/src/commonMain/kotlin/org/fm/com/presentation/editor/DialogTutupTab.kt`

ganti kode ini:
```kotlin
        dismissButton = {
            TextButton(onClick = onTutup, enabled = !sedangMenyimpan) {
                Text("Batal")
            }
        }
    )
}
```
dengan kode ini:
```kotlin
        dismissButton = {
            TextButton(onClick = onTutup, enabled = !sedangMenyimpan) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DialogKonfirmasiPerbaruiArsip(
    permintaan: PermintaanKonfirmasiPerbaruiArsip?,
    sedangMenyimpan: Boolean,
    onPerbaruiArsip: () -> Unit,
    onBatalkanEdit: () -> Unit,
    onKembali: () -> Unit
) {
    if (permintaan == null) return

    AlertDialog(
        onDismissRequest = { if (!sedangMenyimpan) onKembali() },
        title = { Text("Perbarui Arsip?") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Berkas '${permintaan.namaBerkas}' telah diedit. Perbarui isi arsip '${permintaan.namaArsip}' sekarang?"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Jika Anda memilih 'Batalkan Edit', perubahan pada berkas akan dibatalkan dan arsip tetap seperti semula.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onPerbaruiArsip,
                enabled = !sedangMenyimpan
            ) {
                Text(if (sedangMenyimpan) "Memperbarui..." else "Perbarui Arsip")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onKembali,
                    enabled = !sedangMenyimpan
                ) {
                    Text("Kembali")
                }
                TextButton(
                    onClick = onBatalkanEdit,
                    enabled = !sedangMenyimpan
                ) {
                    Text("Batalkan Edit", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}
```

---

#### 10. `shared/src/commonMain/kotlin/org/fm/com/presentation/editor/LayarEditor.kt`

ganti kode ini:
```kotlin
@Composable
fun LayarEditor(
    viewModel: ViewModelEditor,
    modifier: Modifier = Modifier,
    onKembali: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentEditor.SetPesanInfo(null))
        }
    }
```
dengan kode ini:
```kotlin
@Composable
fun LayarEditor(
    viewModel: ViewModelEditor,
    modifier: Modifier = Modifier,
    onKembali: (() -> Unit)? = null,
    onArsipDiperbarui: ((kembaliKeArsip: Boolean) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentEditor.SetPesanInfo(null))
        }
    }

    LaunchedEffect(state.acaraArsipDiperbarui?.id) {
        val acara = state.acaraArsipDiperbarui ?: return@LaunchedEffect
        onArsipDiperbarui?.invoke(acara.kembaliKeArsip)
        viewModel.prosesIntent(IntentEditor.KonsumsiAcaraArsipDiperbarui)
    }
```

ganti kode ini:
```kotlin
    if (state.dialogSimpanSebagai != null) {
        DialogSimpanSebagai(
            permintaan = state.dialogSimpanSebagai,
            sedangMenyimpan = state.sedangMenyimpan,
            onSimpan = { nama, timpa ->
                viewModel.prosesIntent(IntentEditor.EksekusiSimpanSebagai(nama, timpa))
            },
            onTutup = { viewModel.prosesIntent(IntentEditor.TutupDialogSimpanSebagai) }
        )
    }
}
```
dengan kode ini:
```kotlin
    if (state.dialogSimpanSebagai != null) {
        DialogSimpanSebagai(
            permintaan = state.dialogSimpanSebagai,
            sedangMenyimpan = state.sedangMenyimpan,
            onSimpan = { nama, timpa ->
                viewModel.prosesIntent(IntentEditor.EksekusiSimpanSebagai(nama, timpa))
            },
            onTutup = { viewModel.prosesIntent(IntentEditor.TutupDialogSimpanSebagai) }
        )
    }

    if (state.konfirmasiPerbaruiArsip != null) {
        DialogKonfirmasiPerbaruiArsip(
            permintaan = state.konfirmasiPerbaruiArsip,
            sedangMenyimpan = state.sedangMenyimpan,
            onPerbaruiArsip = { viewModel.prosesIntent(IntentEditor.KonfirmasiPerbaruiArsip) },
            onBatalkanEdit = { viewModel.prosesIntent(IntentEditor.BatalkanEditDalamArsip) },
            onKembali = { viewModel.prosesIntent(IntentEditor.TutupDialogPerbaruiArsip) }
        )
    }
}
```

---

### D. Bebas Pilih Lokasi Ekstraksi & Edit Berkas dari Browser Arsip

#### 11. `shared/src/commonMain/kotlin/org/fm/com/presentation/arsip/ViewModelArsip.kt`

ganti kode ini:
```kotlin
import org.fm.com.domain.model.RingkasanIsiArsip
```
*(catatan: jika baris ini sudah dihapus di perbaikan sebelumnya, langsung lanjut ke blok di bawah)*

ganti kode ini:
```kotlin
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.KompresArsipUseCase
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.StatusLapisanArsip
import org.fm.com.presentation.filemanager.PemetaanDomainUi
```
dengan kode ini:
```kotlin
import kotlinx.coroutines.flow.first
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.usecase.BuatBerkasUseCase
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.KompresArsipUseCase
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.domain.usecase.StatusLapisanArsip
import org.fm.com.domain.usecase.TelusuriLokasiUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi
```

ganti kode ini:
```kotlin
@Immutable
data class AcaraBukaEditorArsip(
    val id: Long,
    val entitas: EntitasBerkas
)
```
dengan kode ini:
```kotlin
@Immutable
data class AcaraBukaEditorArsip(
    val id: Long,
    val entitas: EntitasBerkas,
    val konteks: KonteksEditArsip
)

@Immutable
data class StatePemilihFolderTujuan(
    val terbuka: Boolean = false,
    val lokasiSaatIni: LokasiBerkas? = null,
    val segmenBreadcrumb: List<SegmenLokasi> = emptyList(),
    val daftarSubfolder: List<EntitasBerkas> = emptyList(),
    val sedangMemuat: Boolean = false,
    val dapatMembuatFolder: Boolean = true,
    val dialogBuatFolderTerbuka: Boolean = false,
    val pesanGalat: String? = null
)
```

ganti kode ini:
```kotlin
@Immutable
data class StateArsip(
    val browser: StateBrowserArsip = StateBrowserArsip(),
    val kompres: StateKompresArsip = StateKompresArsip(),
    val folderTujuanBawaan: LokasiBerkas? = null,
    val lokasiPintasan: List<LokasiBerkas> = emptyList(),
    val acaraBukaEditor: AcaraBukaEditorArsip? = null,
    val acaraSuksesKompres: AcaraSuksesKompres? = null,
    val acaraSuksesEkstrak: AcaraSuksesEkstrak? = null
)
```
dengan kode ini:
```kotlin
@Immutable
data class StateArsip(
    val browser: StateBrowserArsip = StateBrowserArsip(),
    val kompres: StateKompresArsip = StateKompresArsip(),
    val pemilihFolder: StatePemilihFolderTujuan = StatePemilihFolderTujuan(),
    val folderTujuanBawaan: LokasiBerkas? = null,
    val lokasiPintasan: List<LokasiBerkas> = emptyList(),
    val acaraBukaEditor: AcaraBukaEditorArsip? = null,
    val acaraSuksesKompres: AcaraSuksesKompres? = null,
    val acaraSuksesEkstrak: AcaraSuksesEkstrak? = null
)
```

ganti kode ini:
```kotlin
    data class UbahFolderTujuanEkstrak(val lokasi: LokasiBerkas) : IntentArsip
    data class UbahKebijakanKonflikEkstrak(val kebijakan: KebijakanKonflikEkstrak) : IntentArsip
    data object BatalDialogEkstrak : IntentArsip
    data object KonfirmasiDanEkstrak : IntentArsip
    data object BatalkanEkstraksi : IntentArsip
    data object TutupLaporanEkstrak : IntentArsip
    data class SetDialogInfoKapabilitas(val buka: Boolean) : IntentArsip
    data class SetPesanInfoBrowser(val pesan: String?) : IntentArsip
```
dengan kode ini:
```kotlin
    data class UbahFolderTujuanEkstrak(val lokasi: LokasiBerkas) : IntentArsip
    data class UbahKebijakanKonflikEkstrak(val kebijakan: KebijakanKonflikEkstrak) : IntentArsip
    data object BatalDialogEkstrak : IntentArsip
    data object KonfirmasiDanEkstrak : IntentArsip
    data object BatalkanEkstraksi : IntentArsip
    data object TutupLaporanEkstrak : IntentArsip
    data class SetDialogInfoKapabilitas(val buka: Boolean) : IntentArsip
    data class SetPesanInfoBrowser(val pesan: String?) : IntentArsip
    data object SegarkanDariSesi : IntentArsip

    // Pemilih folder tujuan bebas
    data object BukaPemilihFolderTujuan : IntentArsip
    data class BukaLokasiDiPemilihFolder(val lokasi: LokasiBerkas) : IntentArsip
    data class NavigasiSegmenPemilihFolder(val segmen: SegmenLokasi) : IntentArsip
    data class SetDialogBuatFolderTujuan(val buka: Boolean) : IntentArsip
    data class BuatFolderDiPemilihTujuan(val nama: String) : IntentArsip
    data object KonfirmasiPemilihFolderTujuan : IntentArsip
    data object TutupPemilihFolderTujuan : IntentArsip
    data class TerimaFolderSistemTujuan(val lokasi: LokasiBerkas) : IntentArsip
```

ganti kode ini:
```kotlin
class ViewModelArsip(
    private val ekstrakArsipUseCase: EkstrakArsipUseCase,
    private val kompresArsipUseCase: KompresArsipUseCase,
    lokasiPintasanAwal: List<LokasiBerkas> = emptyList()
) : ViewModel() {

    private val _state = MutableStateFlow(StateArsip(lokasiPintasan = lokasiPintasanAwal))
    val state: StateFlow<StateArsip> = _state.asStateFlow()

    private var pekerjaanMuat: Job? = null
    private var pekerjaanEkstrak: Job? = null
    private var pekerjaanKompres: Job? = null
    private var urutanAcara = 0L
```
dengan kode ini:
```kotlin
class ViewModelArsip(
    private val ekstrakArsipUseCase: EkstrakArsipUseCase,
    private val kompresArsipUseCase: KompresArsipUseCase,
    private val repositoriBerkas: RepositoriBerkas? = null,
    repositoriOperasi: RepositoriOperasiBerkas? = repositoriBerkas as? RepositoriOperasiBerkas,
    private val telusuriLokasiUseCase: TelusuriLokasiUseCase = TelusuriLokasiUseCase(),
    private val buatBerkasUseCase: BuatBerkasUseCase? = repositoriOperasi?.let { BuatBerkasUseCase(it) },
    lokasiPintasanAwal: List<LokasiBerkas> = emptyList()
) : ViewModel() {

    private val _state = MutableStateFlow(StateArsip(lokasiPintasan = lokasiPintasanAwal))
    val state: StateFlow<StateArsip> = _state.asStateFlow()

    private var pekerjaanMuat: Job? = null
    private var pekerjaanMuatFolderTujuan: Job? = null
    private var pekerjaanEkstrak: Job? = null
    private var pekerjaanKompres: Job? = null
    private var urutanAcara = 0L
    private val riwayatHierarkiPemilih = mutableListOf<LokasiBerkas>()
```

ganti kode ini:
```kotlin
            is IntentArsip.SetDialogInfoKapabilitas -> _state.update {
                it.copy(browser = it.browser.copy(dialogInfoKapabilitasTerbuka = intent.buka))
            }
            is IntentArsip.SetPesanInfoBrowser -> _state.update {
                it.copy(browser = it.browser.copy(pesanInfo = intent.pesan))
            }
```
dengan kode ini:
```kotlin
            is IntentArsip.SetDialogInfoKapabilitas -> _state.update {
                it.copy(browser = it.browser.copy(dialogInfoKapabilitasTerbuka = intent.buka))
            }
            is IntentArsip.SetPesanInfoBrowser -> _state.update {
                it.copy(browser = it.browser.copy(pesanInfo = intent.pesan))
            }
            is IntentArsip.SegarkanDariSesi -> segarkanDariSesi()
            is IntentArsip.BukaPemilihFolderTujuan -> bukaPemilihFolderTujuan()
            is IntentArsip.BukaLokasiDiPemilihFolder -> muatPemilihFolder(intent.lokasi, dariSegmen = false)
            is IntentArsip.NavigasiSegmenPemilihFolder -> navigasiSegmenPemilih(intent.segmen)
            is IntentArsip.SetDialogBuatFolderTujuan -> _state.update {
                it.copy(pemilihFolder = it.pemilihFolder.copy(dialogBuatFolderTerbuka = intent.buka))
            }
            is IntentArsip.BuatFolderDiPemilihTujuan -> buatFolderDiPemilihTujuan(intent.nama)
            is IntentArsip.KonfirmasiPemilihFolderTujuan -> konfirmasiPemilihFolderTujuan()
            is IntentArsip.TutupPemilihFolderTujuan -> {
                pekerjaanMuatFolderTujuan?.cancel()
                _state.update { it.copy(pemilihFolder = StatePemilihFolderTujuan()) }
            }
            is IntentArsip.TerimaFolderSistemTujuan -> terimaFolderSistemTujuan(intent.lokasi)
```

ganti kode ini:
```kotlin
        val stBrowser = _state.value.browser
        if (stBrowser.sedangMemuat || stBrowser.sedangMengekstrak) return
```
*(untuk `kembaliKeLapisan` dan `tutupBrowser` — cari fungsi `tutupBrowser()`)*

ganti kode ini:
```kotlin
    private fun tutupBrowser() {
        pekerjaanMuat?.cancel()
        pekerjaanEkstrak?.cancel()
        viewModelScope.launch {
            ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA)
        }
        _state.update { it.copy(browser = StateBrowserArsip()) }
    }
```
dengan kode ini:
```kotlin
    private fun tutupBrowser() {
        pekerjaanMuat?.cancel()
        pekerjaanMuatFolderTujuan?.cancel()
        pekerjaanEkstrak?.cancel()
        viewModelScope.launch {
            ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA)
        }
        _state.update {
            it.copy(
                browser = StateBrowserArsip(),
                pemilihFolder = StatePemilihFolderTujuan()
            )
        }
    }

    private fun segarkanDariSesi() {
        viewModelScope.launch {
            val status = ekstrakArsipUseCase.ambilStatusSesi(ID_SESI_UTAMA) ?: return@launch
            val folderTetap = _state.value.browser.folderDalamLapisan
            terapkanStatusLapisan(status, folderDalamBaru = folderTetap)
        }
    }

    private fun bukaPemilihFolderTujuan() {
        val br = _state.value.browser
        val tujuanAwal = br.dialogKonfirmasiEkstrak?.folderTujuan
            ?: _state.value.folderTujuanBawaan
            ?: _state.value.lokasiPintasan.firstOrNull()
            ?: return
        riwayatHierarkiPemilih.clear()
        if (tujuanAwal.jenis != org.fm.com.domain.model.JenisLokasi.POSIX) {
            riwayatHierarkiPemilih.add(tujuanAwal)
        }
        muatPemilihFolder(tujuanAwal, dariSegmen = false)
    }

    private fun navigasiSegmenPemilih(segmen: SegmenLokasi) {
        val idx = riwayatHierarkiPemilih.indexOfFirst { it == segmen.lokasi }
        if (idx >= 0) {
            while (riwayatHierarkiPemilih.size > idx + 1) {
                riwayatHierarkiPemilih.removeAt(riwayatHierarkiPemilih.lastIndex)
            }
        }
        muatPemilihFolder(segmen.lokasi, dariSegmen = true)
    }

    private fun muatPemilihFolder(lokasi: LokasiBerkas, dariSegmen: Boolean) {
        val repo = repositoriBerkas ?: return
        pekerjaanMuatFolderTujuan?.cancel()

        if (!dariSegmen) {
            if (lokasi.jenis == org.fm.com.domain.model.JenisLokasi.POSIX) {
                riwayatHierarkiPemilih.clear()
            } else {
                val adalahAnak = _state.value.pemilihFolder.daftarSubfolder.any { it.lokasi == lokasi }
                if (adalahAnak && riwayatHierarkiPemilih.isNotEmpty() && riwayatHierarkiPemilih.last().jenis == lokasi.jenis) {
                    if (riwayatHierarkiPemilih.last() != lokasi) riwayatHierarkiPemilih.add(lokasi)
                } else {
                    val idxAda = riwayatHierarkiPemilih.indexOfFirst { it == lokasi }
                    if (idxAda >= 0) {
                        while (riwayatHierarkiPemilih.size > idxAda + 1) {
                            riwayatHierarkiPemilih.removeAt(riwayatHierarkiPemilih.lastIndex)
                        }
                    } else {
                        riwayatHierarkiPemilih.clear()
                        riwayatHierarkiPemilih.add(lokasi)
                    }
                }
            }
        }

        val segmen = telusuriLokasiUseCase.buatSegmenBreadcrumb(lokasi, riwayatHierarkiPemilih)
        val dapatBuat = repo.kapabilitas(lokasi)?.apakahDidukung(OperasiBerkas.MEMBUAT) ?: true

        _state.update {
            it.copy(
                pemilihFolder = StatePemilihFolderTujuan(
                    terbuka = true,
                    lokasiSaatIni = lokasi,
                    segmenBreadcrumb = segmen,
                    daftarSubfolder = emptyList(),
                    sedangMemuat = true,
                    dapatMembuatFolder = dapatBuat && buatBerkasUseCase != null,
                    dialogBuatFolderTerbuka = false,
                    pesanGalat = null
                )
            )
        }

        pekerjaanMuatFolderTujuan = viewModelScope.launch {
            val hasil = runCatching { repo.daftarIsi(lokasi).first() }.getOrNull()
            when (hasil) {
                is HasilOperasi.Sukses -> {
                    val folderSaja = hasil.nilai
                        .filter { it.jenis == JenisBerkas.FOLDER }
                        .sortedBy { it.nama.lowercase() }
                    _state.update { st ->
                        st.copy(
                            pemilihFolder = st.pemilihFolder.copy(
                                daftarSubfolder = folderSaja,
                                sedangMemuat = false,
                                pesanGalat = null
                            )
                        )
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update { st ->
                        st.copy(
                            pemilihFolder = st.pemilihFolder.copy(
                                sedangMemuat = false,
                                pesanGalat = hasil.pesan
                            )
                        )
                    }
                }
                null -> {
                    _state.update { st ->
                        st.copy(
                            pemilihFolder = st.pemilihFolder.copy(
                                sedangMemuat = false,
                                pesanGalat = "Gagal memuat daftar folder tujuan"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buatFolderDiPemilihTujuan(nama: String) {
        val pf = _state.value.pemilihFolder
        val induk = pf.lokasiSaatIni ?: return
        val uc = buatBerkasUseCase ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(pemilihFolder = it.pemilihFolder.copy(dialogBuatFolderTerbuka = false, sedangMemuat = true))
            }
            when (val hasil = uc.buatFolder(induk, nama, konfirmasi = false)) {
                is HasilOperasi.Sukses -> {
                    muatPemilihFolder(hasil.nilai.lokasi, dariSegmen = false)
                }
                is HasilOperasi.Gagal -> {
                    _state.update {
                        it.copy(pemilihFolder = it.pemilihFolder.copy(sedangMemuat = false, pesanGalat = hasil.pesan))
                    }
                }
            }
        }
    }

    private fun konfirmasiPemilihFolderTujuan() {
        val lokasiDipilih = _state.value.pemilihFolder.lokasiSaatIni ?: return
        pekerjaanMuatFolderTujuan?.cancel()
        _state.update { st ->
            val dialog = st.browser.dialogKonfirmasiEkstrak
            st.copy(
                pemilihFolder = StatePemilihFolderTujuan(),
                folderTujuanBawaan = lokasiDipilih,
                browser = st.browser.copy(
                    dialogKonfirmasiEkstrak = dialog?.copy(folderTujuan = lokasiDipilih)
                )
            )
        }
    }

    private fun terimaFolderSistemTujuan(lokasi: LokasiBerkas) {
        _state.update { st ->
            val dialog = st.browser.dialogKonfirmasiEkstrak
            val pemilih = st.pemilihFolder
            st.copy(
                folderTujuanBawaan = lokasi,
                browser = st.browser.copy(
                    dialogKonfirmasiEkstrak = dialog?.copy(folderTujuan = lokasi)
                ),
                pemilihFolder = if (pemilih.terbuka) pemilih.copy(lokasiSaatIni = lokasi) else pemilih
            )
        }
        if (_state.value.pemilihFolder.terbuka) {
            muatPemilihFolder(lokasi, dariSegmen = false)
        }
    }
```

ganti kode ini:
```kotlin
        val batas = _state.value.browser.batasKebijakan
        viewModelScope.launch {
            _state.update { it.copy(browser = it.browser.copy(sedangMemuat = true)) }
            when (
                val hasil = ekstrakArsipUseCase.siapkanPratinjauBerkas(
                    idSesi = ID_SESI_UTAMA,
                    jalurInternal = entri.jalurInternal,
                    batas = batas
                )
            ) {
                is HasilOperasi.Sukses -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(sedangMemuat = false),
                            acaraBukaEditor = AcaraBukaEditorArsip(
                                id = ++urutanAcara,
                                entitas = hasil.nilai
                            )
                        )
                    }
                }
```
dengan kode ini:
```kotlin
        val batas = _state.value.browser.batasKebijakan
        val kapAktif = _state.value.browser.kapabilitasAktif
        val bolehEdit = kapAktif?.dapatBuat == true
        viewModelScope.launch {
            _state.update { it.copy(browser = it.browser.copy(sedangMemuat = true)) }
            when (
                val hasil = ekstrakArsipUseCase.siapkanPratinjauBerkas(
                    idSesi = ID_SESI_UTAMA,
                    jalurInternal = entri.jalurInternal,
                    untukEdit = bolehEdit,
                    batas = batas
                )
            ) {
                is HasilOperasi.Sukses -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(sedangMemuat = false),
                            acaraBukaEditor = AcaraBukaEditorArsip(
                                id = ++urutanAcara,
                                entitas = hasil.nilai.entitasSementara,
                                konteks = hasil.nilai.konteks
                            )
                        )
                    }
                }
```

ganti kode ini:
```kotlin
    override fun onCleared() {
        pekerjaanMuat?.cancel()
        pekerjaanEkstrak?.cancel()
        pekerjaanKompres?.cancel()
    }
```
dengan kode ini:
```kotlin
    override fun onCleared() {
        pekerjaanMuat?.cancel()
        pekerjaanMuatFolderTujuan?.cancel()
        pekerjaanEkstrak?.cancel()
        pekerjaanKompres?.cancel()
    }
```

---

#### 12. `shared/src/commonMain/kotlin/org/fm/com/presentation/arsip/LayarArsip.kt`

ganti kode ini:
```kotlin
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
```
dengan kode ini:
```kotlin
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
```

ganti kode ini:
```kotlin
@Composable
fun LayarArsip(
    viewModel: ViewModelArsip,
    onKembali: () -> Unit,
    onBukaBerkasDiEditor: (EntitasBerkas) -> Unit,
    modifier: Modifier = Modifier
) {
```
dengan kode ini:
```kotlin
@Composable
fun LayarArsip(
    viewModel: ViewModelArsip,
    onKembali: () -> Unit,
    onBukaBerkasDiEditor: (EntitasBerkas, KonteksEditArsip) -> Unit,
    modifier: Modifier = Modifier,
    onPilihFolderSistem: (() -> Unit)? = null
) {
```

ganti kode ini:
```kotlin
    LaunchedEffect(state.acaraBukaEditor?.id) {
        val acara = state.acaraBukaEditor ?: return@LaunchedEffect
        onBukaBerkasDiEditor(acara.entitas)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEditor)
    }
```
dengan kode ini:
```kotlin
    LaunchedEffect(state.acaraBukaEditor?.id) {
        val acara = state.acaraBukaEditor ?: return@LaunchedEffect
        onBukaBerkasDiEditor(acara.entitas, acara.konteks)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEditor)
    }
```

ganti kode ini:
```kotlin
                    Text("Buka di Editor (Baca)")
```
dengan kode ini:
```kotlin
                    Text(
                        if (br.kapabilitasAktif?.dapatBuat == true) {
                            "Buka / Edit di Editor"
                        } else {
                            "Buka di Editor (Baca)"
                        }
                    )
```

ganti kode ini:
```kotlin
    OverlayDialogEkstrakArsip(viewModel = viewModel)
}

@Composable
fun OverlayDialogEkstrakArsip(
    viewModel: ViewModelArsip,
    modifier: Modifier = Modifier,
    onEkstrakSelesai: ((HasilEkstrakArsip, LokasiBerkas) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val br = state.browser

    LaunchedEffect(state.acaraSuksesEkstrak?.id) {
        val acara = state.acaraSuksesEkstrak ?: return@LaunchedEffect
        onEkstrakSelesai?.invoke(acara.hasil, acara.folderTujuan)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEkstrak)
    }

    br.dialogKonfirmasiEkstrak?.let { req ->
        DialogKonfirmasiEkstrak(
            permintaan = req,
            lokasiPintasan = state.lokasiPintasan,
            onPilihTujuan = { viewModel.prosesIntent(IntentArsip.UbahFolderTujuanEkstrak(it)) },
            onPilihKebijakan = { viewModel.prosesIntent(IntentArsip.UbahKebijakanKonflikEkstrak(it)) },
            onKonfirmasi = { viewModel.prosesIntent(IntentArsip.KonfirmasiDanEkstrak) },
            onBatal = { viewModel.prosesIntent(IntentArsip.BatalDialogEkstrak) },
            modifier = modifier
        )
    }
```
dengan kode ini:
```kotlin
    OverlayDialogEkstrakArsip(
        viewModel = viewModel,
        onPilihFolderSistem = onPilihFolderSistem
    )
}

@Composable
fun OverlayDialogEkstrakArsip(
    viewModel: ViewModelArsip,
    modifier: Modifier = Modifier,
    onPilihFolderSistem: (() -> Unit)? = null,
    onEkstrakSelesai: ((HasilEkstrakArsip, LokasiBerkas) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val br = state.browser

    LaunchedEffect(state.acaraSuksesEkstrak?.id) {
        val acara = state.acaraSuksesEkstrak ?: return@LaunchedEffect
        onEkstrakSelesai?.invoke(acara.hasil, acara.folderTujuan)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEkstrak)
    }

    br.dialogKonfirmasiEkstrak?.let { req ->
        DialogKonfirmasiEkstrak(
            permintaan = req,
            lokasiPintasan = state.lokasiPintasan,
            onPilihTujuan = { viewModel.prosesIntent(IntentArsip.UbahFolderTujuanEkstrak(it)) },
            onBukaPemilihFolder = { viewModel.prosesIntent(IntentArsip.BukaPemilihFolderTujuan) },
            onPilihFolderSistem = onPilihFolderSistem,
            onPilihKebijakan = { viewModel.prosesIntent(IntentArsip.UbahKebijakanKonflikEkstrak(it)) },
            onKonfirmasi = { viewModel.prosesIntent(IntentArsip.KonfirmasiDanEkstrak) },
            onBatal = { viewModel.prosesIntent(IntentArsip.BatalDialogEkstrak) },
            modifier = modifier
        )
    }

    if (state.pemilihFolder.terbuka) {
        DialogPemilihFolderEkstrak(
            statePemilih = state.pemilihFolder,
            lokasiPintasan = state.lokasiPintasan,
            onBukaLokasi = { viewModel.prosesIntent(IntentArsip.BukaLokasiDiPemilihFolder(it)) },
            onKlikSegmen = { viewModel.prosesIntent(IntentArsip.NavigasiSegmenPemilihFolder(it)) },
            onBukaDialogBuatFolder = { viewModel.prosesIntent(IntentArsip.SetDialogBuatFolderTujuan(true)) },
            onTutupDialogBuatFolder = { viewModel.prosesIntent(IntentArsip.SetDialogBuatFolderTujuan(false)) },
            onBuatFolder = { viewModel.prosesIntent(IntentArsip.BuatFolderDiPemilihTujuan(it)) },
            onPilihFolderSistem = onPilihFolderSistem,
            onPilihFolderIni = { viewModel.prosesIntent(IntentArsip.KonfirmasiPemilihFolderTujuan) },
            onBatal = { viewModel.prosesIntent(IntentArsip.TutupPemilihFolderTujuan) }
        )
    }
```

ganti kode ini:
```kotlin
@Composable
private fun DialogKonfirmasiEkstrak(
    permintaan: PermintaanKonfirmasiEkstrak,
    lokasiPintasan: List<LokasiBerkas>,
    onPilihTujuan: (LokasiBerkas) -> Unit,
    onPilihKebijakan: (KebijakanKonflikEkstrak) -> Unit,
    onKonfirmasi: () -> Unit,
    onBatal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    var menuTujuanTerbuka by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onBatal,
        title = { Text("Konfirmasi Ekstraksi") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Target: ${permintaan.labelTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${permintaan.jumlahItem} item · Perkiraan ${PemformatLokasiRamah.formatRingkasUkuran(permintaan.estimasiUkuranByte)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Folder Tujuan:", style = MaterialTheme.typography.labelLarge)
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                            .clickable(enabled = lokasiPintasan.isNotEmpty()) { menuTujuanTerbuka = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = permintaan.folderTujuan.label ?: "Folder Tujuan",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(permintaan.folderTujuan),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (lokasiPintasan.isNotEmpty()) {
                            Icon(
                                imageVector = IkonAplikasi.TukarLokasi,
                                contentDescription = "Ganti tujuan ekstraksi",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuTujuanTerbuka,
                        onDismissRequest = { menuTujuanTerbuka = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        lokasiPintasan.forEach { lok ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lok.label ?: PemformatLokasiRamah.formatRingkas(lok))
                                        Text(
                                            text = PemformatLokasiRamah.formatRingkas(lok),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    menuTujuanTerbuka = false
                                    onPilihTujuan(lok)
                                }
                            )
                        }
                    }
                }
```
dengan kode ini:
```kotlin
@Composable
private fun DialogKonfirmasiEkstrak(
    permintaan: PermintaanKonfirmasiEkstrak,
    lokasiPintasan: List<LokasiBerkas>,
    onPilihTujuan: (LokasiBerkas) -> Unit,
    onBukaPemilihFolder: () -> Unit,
    onPilihFolderSistem: (() -> Unit)?,
    onPilihKebijakan: (KebijakanKonflikEkstrak) -> Unit,
    onKonfirmasi: () -> Unit,
    onBatal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    var menuTujuanTerbuka by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onBatal,
        title = { Text("Konfirmasi Ekstraksi") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Target: ${permintaan.labelTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${permintaan.jumlahItem} item · Perkiraan ${PemformatLokasiRamah.formatRingkasUkuran(permintaan.estimasiUkuranByte)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Folder Tujuan:", style = MaterialTheme.typography.labelLarge)
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                            .clickable(enabled = lokasiPintasan.isNotEmpty()) { menuTujuanTerbuka = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = permintaan.folderTujuan.label ?: "Folder Tujuan",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(permintaan.folderTujuan),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (lokasiPintasan.isNotEmpty()) {
                            Icon(
                                imageVector = IkonAplikasi.TukarLokasi,
                                contentDescription = "Pintasan folder tujuan",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuTujuanTerbuka,
                        onDismissRequest = { menuTujuanTerbuka = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        lokasiPintasan.forEach { lok ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lok.label ?: PemformatLokasiRamah.formatRingkas(lok))
                                        Text(
                                            text = PemformatLokasiRamah.formatRingkas(lok),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    menuTujuanTerbuka = false
                                    onPilihTujuan(lok)
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBukaPemilihFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Jelajahi Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (onPilihFolderSistem != null) {
                        OutlinedButton(
                            onClick = onPilihFolderSistem,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Folder Sistem", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
```

ganti kode ini:
```kotlin
private fun PemformatLokasiRamah.formatRingkasUkuran(byte: Long): String =
    PemetaanDomainUi.formatUkuran(byte, adalahFolder = false)
```
dengan kode ini:
```kotlin
@Composable
private fun DialogPemilihFolderEkstrak(
    statePemilih: StatePemilihFolderTujuan,
    lokasiPintasan: List<LokasiBerkas>,
    onBukaLokasi: (LokasiBerkas) -> Unit,
    onKlikSegmen: (SegmenLokasi) -> Unit,
    onBukaDialogBuatFolder: () -> Unit,
    onTutupDialogBuatFolder: () -> Unit,
    onBuatFolder: (String) -> Unit,
    onPilihFolderSistem: (() -> Unit)?,
    onPilihFolderIni: () -> Unit,
    onBatal: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val lokasiAktif = statePemilih.lokasiSaatIni

    Dialog(
        onDismissRequest = onBatal,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pilih Folder Tujuan", style = MaterialTheme.typography.titleMedium)
                        lokasiAktif?.let {
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(it),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (statePemilih.dapatMembuatFolder) {
                        IconButton(onClick = onBukaDialogBuatFolder) {
                            Icon(
                                imageVector = IkonAplikasi.Tambah,
                                contentDescription = "Buat folder baru",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onPilihFolderSistem != null) {
                        IconButton(onClick = onPilihFolderSistem) {
                            Icon(
                                imageVector = IkonAplikasi.PilihFolder,
                                contentDescription = "Pilih dari penyimpanan sistem",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (lokasiPintasan.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lokasiPintasan.forEach { lok ->
                            val terpilih = lokasiAktif != null &&
                                lokasiAktif.pengenal == lok.pengenal &&
                                lokasiAktif.jenis == lok.jenis
                            androidx.compose.material3.FilterChip(
                                selected = terpilih,
                                onClick = { onBukaLokasi(lok) },
                                label = {
                                    Text(
                                        text = lok.label ?: PemformatLokasiRamah.formatRingkas(lok),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                if (statePemilih.segmenBreadcrumb.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statePemilih.segmenBreadcrumb.forEachIndexed { idx, seg ->
                            val akhir = idx == statePemilih.segmenBreadcrumb.lastIndex
                            Text(
                                text = seg.namaTampilan,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = fontMono,
                                    fontWeight = if (akhir) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (akhir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(enabled = !akhir) { onKlikSegmen(seg) }
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            )
                            if (!akhir) {
                                Text(
                                    text = "/",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                ) {
                    when {
                        statePemilih.sedangMemuat -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                        statePemilih.pesanGalat != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statePemilih.pesanGalat,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        statePemilih.daftarSubfolder.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "// tidak ada subfolder",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(
                                    items = statePemilih.daftarSubfolder,
                                    key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                                ) { folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBukaLokasi(folder.lokasi) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        KomponenIkonBerkas(jenis = JenisBerkas.FOLDER, ukuran = 20.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = folder.nama,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 48.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onBatal) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPilihFolderIni,
                        enabled = lokasiAktif != null
                    ) {
                        Text("Pilih Folder Ini")
                    }
                }
            }
        }
    }

    if (statePemilih.dialogBuatFolderTerbuka) {
        var namaFolder by remember { mutableStateOf("") }
        val namaSah = ValidasiNama.sah(namaFolder.trim())
        AlertDialog(
            onDismissRequest = onTutupDialogBuatFolder,
            title = { Text("Buat Folder Tujuan") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = namaFolder,
                    onValueChange = { namaFolder = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    isError = namaFolder.isNotEmpty() && !namaSah,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (namaSah) onBuatFolder(namaFolder.trim()) },
                    enabled = namaSah
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(onClick = onTutupDialogBuatFolder) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun PemformatLokasiRamah.formatRingkasUkuran(byte: Long): String =
    PemetaanDomainUi.formatUkuran(byte, adalahFolder = false)
```

---

### E. Penyambungan DI, `App.kt`, `DaftarBerkasLayar.kt`, & Uji Integrasi

#### 13. `shared/src/commonMain/kotlin/org/fm/com/di/KontainerAplikasi.kt`

ganti kode ini:
```kotlin
                single<PelaksanaPenjelajahanArsip> { EkstraktorArsip(get()) }
```
dengan kode ini:
```kotlin
                single<PelaksanaPenjelajahanArsip> { EkstraktorArsip(get(), get()) }
```

---

#### 14. `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt`

ganti kode ini:
```kotlin
        OverlayDialogEkstrakArsip(
            viewModel = viewModelArsip,
            onEkstrakSelesai = { hasil, _ ->
```
dengan kode ini:
```kotlin
        OverlayDialogEkstrakArsip(
            viewModel = viewModelArsip,
            onPilihFolderSistem = onPilihFolder,
            onEkstrakSelesai = { hasil, _ ->
```

---

#### 15. `shared/src/commonMain/kotlin/org/fm/com/App.kt`

ganti kode ini:
```kotlin
        val vmEditor: ViewModelEditor = viewModel(key = "editor_$kunciKontainer") {
            ViewModelEditor(
                repositoriBerkas = kontainer.repositoriBerkas,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
                simpanBerkasUseCase = kontainer.simpanBerkasUseCase
            )
        }
        val vmDiff: ViewModelDiff = viewModel(key = "diff_$kunciKontainer") {
            ViewModelDiff(kontainer.bandingkanBerkasUseCase)
        }
        val vmBulkRename: ViewModelBulkRename = viewModel(key = "rename_$kunciKontainer") {
            ViewModelBulkRename(
                repositoriBerkas = kontainer.repositoriBerkas,
                jalankanRenameMassalUseCase = kontainer.jalankanRenameMassalUseCase
            )
        }
        val vmArsip: ViewModelArsip = viewModel(key = "arsip_$kunciKontainer") {
            ViewModelArsip(
                ekstrakArsipUseCase = kontainer.ekstrakArsipUseCase,
                kompresArsipUseCase = kontainer.kompresArsipUseCase,
                lokasiPintasanAwal = lokasiPintasan
            )
        }
```
dengan kode ini:
```kotlin
        val vmEditor: ViewModelEditor = viewModel(key = "editor_$kunciKontainer") {
            ViewModelEditor(
                repositoriBerkas = kontainer.repositoriBerkas,
                riwayatPencarianUseCase = kontainer.riwayatPencarianUseCase,
                simpanBerkasUseCase = kontainer.simpanBerkasUseCase,
                ekstrakArsipUseCase = kontainer.ekstrakArsipUseCase
            )
        }
        val vmDiff: ViewModelDiff = viewModel(key = "diff_$kunciKontainer") {
            ViewModelDiff(kontainer.bandingkanBerkasUseCase)
        }
        val vmBulkRename: ViewModelBulkRename = viewModel(key = "rename_$kunciKontainer") {
            ViewModelBulkRename(
                repositoriBerkas = kontainer.repositoriBerkas,
                jalankanRenameMassalUseCase = kontainer.jalankanRenameMassalUseCase
            )
        }
        val vmArsip: ViewModelArsip = viewModel(key = "arsip_$kunciKontainer") {
            ViewModelArsip(
                ekstrakArsipUseCase = kontainer.ekstrakArsipUseCase,
                kompresArsipUseCase = kontainer.kompresArsipUseCase,
                repositoriBerkas = kontainer.repositoriBerkas,
                repositoriOperasi = kontainer.repositoriOperasi,
                lokasiPintasanAwal = lokasiPintasan
            )
        }
```

ganti kode ini:
```kotlin
        LaunchedEffect(permintaanBukaEksternal?.id) {
            val lokasi = permintaanBukaEksternal?.lokasi ?: return@LaunchedEffect
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
            ruangEditorTerbuka = false
            ruangArsipTerbuka = false
            vmArsip.prosesIntent(IntentArsip.TutupBrowser)
            tabTerpilih = 0
            vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
        }
```
dengan kode ini:
```kotlin
        LaunchedEffect(permintaanBukaEksternal?.id) {
            val lokasi = permintaanBukaEksternal?.lokasi ?: return@LaunchedEffect
            val lokasiSiap = onSiapkanAksesLokasi?.invoke(lokasi) ?: lokasi
            val arsipState = vmArsip.state.value
            if (arsipState.browser.dialogKonfirmasiEkstrak != null || arsipState.pemilihFolder.terbuka) {
                vmArsip.prosesIntent(IntentArsip.TerimaFolderSistemTujuan(lokasiSiap))
            } else {
                ruangEditorTerbuka = false
                ruangArsipTerbuka = false
                vmArsip.prosesIntent(IntentArsip.TutupBrowser)
                tabTerpilih = 0
                vmFilemanager.prosesIntent(IntentFilemanager.BukaLokasi(lokasiSiap))
            }
        }
```

ganti kode ini:
```kotlin
        } else if (ruangEditorTerbuka) {
            LayarEditor(
                viewModel = vmEditor,
                onKembali = {
                    ruangEditorTerbuka = false
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                }
            )
        } else if (ruangArsipTerbuka) {
            LayarArsip(
                viewModel = vmArsip,
                onKembali = {
                    vmArsip.prosesIntent(IntentArsip.TutupBrowser)
                    ruangArsipTerbuka = false
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                },
                onBukaBerkasDiEditor = { entitas ->
                    vmEditor.prosesIntent(IntentEditor.BukaBerkas(entitas))
                    ruangEditorTerbuka = true
                }
            )
        } else {
```
dengan kode ini:
```kotlin
        } else if (ruangEditorTerbuka) {
            LayarEditor(
                viewModel = vmEditor,
                onKembali = {
                    val tabAktif = vmEditor.state.value.tabAktif
                    if (ruangArsipTerbuka && tabAktif?.konteksArsip != null) {
                        vmEditor.prosesIntent(IntentEditor.MintaKeluarDariTabArsip(tabAktif.idTab))
                    } else {
                        ruangEditorTerbuka = false
                        vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                    }
                },
                onArsipDiperbarui = { kembaliKeArsip ->
                    vmArsip.prosesIntent(IntentArsip.SegarkanDariSesi)
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                    if (kembaliKeArsip) {
                        ruangEditorTerbuka = false
                    }
                }
            )
        } else if (ruangArsipTerbuka) {
            LayarArsip(
                viewModel = vmArsip,
                onKembali = {
                    vmArsip.prosesIntent(IntentArsip.TutupBrowser)
                    ruangArsipTerbuka = false
                    vmFilemanager.prosesIntent(IntentFilemanager.Segarkan)
                },
                onBukaBerkasDiEditor = { entitas, konteksArsip ->
                    vmEditor.prosesIntent(
                        IntentEditor.BukaBerkas(
                            entitas = entitas,
                            konteksArsip = konteksArsip
                        )
                    )
                    ruangEditorTerbuka = true
                },
                onPilihFolderSistem = onPilihFolder
            )
        } else {
```

---

#### 16. `shared/src/commonTest/kotlin/org/fm/com/data/arsip/EkstraktorDanPembuatArsipTest.kt`

ganti kode ini:
```kotlin
        val ekstraktor = EkstraktorArsip(registri, adapter)
```
dengan kode ini:
```kotlin
        val ekstraktor = EkstraktorArsip(registri, storage, adapter)
```

ganti kode ini:
```kotlin
        // Pratinjau file dari kedalaman 3
        val pratinjau = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.siapkanPratinjauBerkas(idSesi, "app.json")
        ).nilai as org.fm.com.domain.model.EntitasBerkas
        assertEquals(false, pratinjau.dapatDitulis)
        val pathPratinjau = pratinjau.lokasi.pengenal.toPath()
        try {
            assertTrue(FileSystem.SYSTEM.exists(pathPratinjau))
        } finally {
            runCatching { FileSystem.SYSTEM.delete(pathPratinjau, mustExist = false) }
        }
```
dengan kode ini:
```kotlin
        // Pratinjau & edit file dari kedalaman 3
        val hasilSiapEdit = assertIs<HasilOperasi.Sukses<*>>(
            ekstrakUseCase.siapkanPratinjauBerkas(idSesi, "app.json", untukEdit = true)
        ).nilai as org.fm.com.domain.usecase.HasilSiapkanEditArsip
        assertTrue(hasilSiapEdit.entitasSementara.dapatDitulis)
        val pathPratinjau = hasilSiapEdit.entitasSementara.lokasi.pengenal.toPath()
        try {
            assertTrue(FileSystem.SYSTEM.exists(pathPratinjau))
            // Simpan suntingan ke file sementara lalu perbarui arsip bertingkat proyek.zip
            FileSystem.SYSTEM.write(pathPratinjau) {
                writeUtf8("{\"versi\":2,\"diedit\":true}")
            }
            val statusSetelahEdit = assertIs<HasilOperasi.Sukses<*>>(
                ekstrakUseCase.perbaruiBerkasDalamArsip(
                    konteks = hasilSiapEdit.konteks,
                    lokasiBerkasEdit = hasilSiapEdit.entitasSementara.lokasi
                )
            )
            assertIs<org.fm.com.domain.usecase.StatusLapisanArsip>(statusSetelahEdit.nilai)
        } finally {
            runCatching { FileSystem.SYSTEM.delete(pathPratinjau, mustExist = false) }
        }
```

ganti kode ini:
```kotlin
        assertEquals(1, hasilEkstrak.jumlahDiekstrak)
        assertEquals("{\"versi\":1}", storage.berkas.isiDari("/keluar/app.json")?.decodeToString())
```
dengan kode ini:
```kotlin
        assertEquals(1, hasilEkstrak.jumlahDiekstrak)
        assertEquals("{\"versi\":2,\"diedit\":true}", storage.berkas.isiDari("/keluar/app.json")?.decodeToString())
```

---