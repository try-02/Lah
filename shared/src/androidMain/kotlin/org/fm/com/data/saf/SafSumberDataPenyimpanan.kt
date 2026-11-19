package org.fm.com.data.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.fm.com.data.RepositoriRouterBerkas
import org.fm.com.data.SumberDataPenyimpanan
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
import org.fm.com.domain.repository.RepositoriOperasiBerkas

class SafSumberDataPenyimpanan internal constructor(
    private val appContext: Context?,
    private val daftarIsiPenyedia: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>,
    private val operasiPenyedia: RepositoriOperasiBerkas?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberDataPenyimpanan {
    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        appContext = context.applicationContext,
        daftarIsiPenyedia = { SafDaftarIsi(context, ioDispatcher).daftarIsi(it) },
        operasiPenyedia = SafOperasiBerkas(context, ioDispatcher),
        ioDispatcher = ioDispatcher
    )

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.SAF) {
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        daftarIsiPenyedia(lokasi)

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
        val context = appContext ?: return tidakDidukungTanpaKonteks()
        val uri = when (val hasil = SafPemetaanLokasi.keUriDokumen(context, lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        val info = when (val hasilInfo = bacaInfoDokumen(context, uri)) {
            is HasilOperasi.Sukses -> hasilInfo.nilai
            is HasilOperasi.Gagal -> return hasilInfo
        }
        if (info.adalahFolder) {
            return HasilOperasi.Gagal("Tidak bisa membaca folder sebagai berkas", PenyebabGalat.VALIDASI)
        }
        return HasilOperasi.Sukses(SafAliranIsi.buka(context, uri, info.ukuranByte, ioDispatcher))
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val context = appContext ?: return tidakDidukungTanpaKonteks()
        val uri = when (val hasil = SafPemetaanLokasi.keUriDokumen(context, lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        val info = when (val hasilInfo = bacaInfoDokumen(context, uri)) {
            is HasilOperasi.Sukses -> hasilInfo.nilai
            is HasilOperasi.Gagal -> return hasilInfo
        }
        if (info.adalahFolder) {
            return HasilOperasi.Gagal("Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI)
        }
        if (!timpa) {
            return HasilOperasi.Gagal(
                "Berkas sudah ada, perlu konfirmasi timpa: ${lokasi.pengenal}",
                PenyebabGalat.KONFLIK
            )
        }
        return HasilOperasi.Sukses(
            SafAliranTulis(
                appContext = context,
                targetDocUri = uri,
                apakahDokumenBaru = false,
                lokasiTarget = lokasi.copy(label = info.nama ?: lokasi.label),
                ioDispatcher = ioDispatcher
            )
        )
    }

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas,
        nama: String,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> {
        if (!ValidasiNama.sah(nama)) {
            return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        val context = appContext ?: return tidakDidukungTanpaKonteks()
        val parentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(context, induk)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        val infoInduk = when (val hasilInduk = bacaInfoDokumen(context, parentUri)) {
            is HasilOperasi.Sukses -> hasilInduk.nilai
            is HasilOperasi.Gagal -> return hasilInduk
        }
        if (!infoInduk.adalahFolder) {
            return HasilOperasi.Gagal("Induk bukan folder: ${induk.pengenal}", PenyebabGalat.VALIDASI)
        }

        val opSaf = (operasiPenyedia as? SafOperasiBerkas) ?: SafOperasiBerkas(context, ioDispatcher)
        val existingDocUri = opSaf.cariAnakBernama(parentUri, nama, ignoreCase = true)
        if (existingDocUri != null) {
            val infoAda = bacaInfoDokumen(context, existingDocUri)
            if (infoAda is HasilOperasi.Sukses && infoAda.nilai.adalahFolder) {
                return HasilOperasi.Gagal("Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI)
            }
            if (!timpa) {
                return HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            return HasilOperasi.Sukses(
                SafAliranTulis(
                    appContext = context,
                    targetDocUri = existingDocUri,
                    apakahDokumenBaru = false,
                    lokasiTarget = LokasiBerkas(existingDocUri.toString(), JenisLokasi.SAF, nama),
                    ioDispatcher = ioDispatcher
                )
            )
        }

        val createdUri = try {
            val mime = SafOperasiBerkas.tentukanMimeType(nama)
            DocumentsContract.createDocument(context.contentResolver, parentUri, mime, nama)
                ?: return HasilOperasi.Gagal("Penyedia SAF gagal membuat dokumen baru", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin membuat dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            return HasilOperasi.Gagal(e.message ?: "Folder induk SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat dokumen baru di SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        val entitasBaru = opSaf.bacaSatuEntitas(createdUri)
        if (entitasBaru != null && entitasBaru.nama != nama && !timpa) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, createdUri) }
            return HasilOperasi.Gagal("Berkas sudah ada, perlu konfirmasi timpa: $nama", PenyebabGalat.KONFLIK)
        }

        return HasilOperasi.Sukses(
            SafAliranTulis(
                appContext = context,
                targetDocUri = createdUri,
                apakahDokumenBaru = true,
                lokasiTarget = LokasiBerkas(createdUri.toString(), JenisLokasi.SAF, entitasBaru?.nama ?: nama),
                ioDispatcher = ioDispatcher
            )
        )
    }

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia?.buatFolder(permintaan) ?: tidakDidukungTanpaKonteks()

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia?.buatBerkasKosong(permintaan) ?: tidakDidukungTanpaKonteks()

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia?.gantiNama(permintaan) ?: tidakDidukungTanpaKonteks()

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        operasiPenyedia?.hapus(permintaan) ?: tidakDidukungTanpaKonteks()

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia?.salin(permintaan) ?: tidakDidukungTanpaKonteks()

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia?.pindah(permintaan) ?: tidakDidukungTanpaKonteks()

    private fun tidakDidukungTanpaKonteks(): HasilOperasi.Gagal =
        HasilOperasi.Gagal("Operasi tidak didukung pada instance uji tanpa Context", PenyebabGalat.TIDAK_DIDUKUNG)

    private data class InfoDokumenSingkat(
        val adalahFolder: Boolean,
        val ukuranByte: Long?,
        val nama: String?
    )

    private fun bacaInfoDokumen(context: Context, docUri: Uri): HasilOperasi<InfoDokumenSingkat> {
        return try {
            val cursor = context.contentResolver.query(
                docUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            ) ?: return HasilOperasi.Gagal("Dokumen SAF tidak ditemukan: $docUri", PenyebabGalat.TIDAK_DITEMUKAN)

            cursor.use { c ->
                if (!c.moveToFirst()) {
                    HasilOperasi.Gagal("Dokumen SAF tidak ditemukan: $docUri", PenyebabGalat.TIDAK_DITEMUKAN)
                } else {
                    val mime = if (!c.isNull(0)) c.getString(0) else null
                    val ukuran = if (!c.isNull(1)) c.getLong(1) else null
                    val nama = if (!c.isNull(2)) c.getString(2) else null
                    HasilOperasi.Sukses(
                        InfoDokumenSingkat(
                            adalahFolder = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                            ukuranByte = ukuran?.takeIf { it >= 0L },
                            nama = nama?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin akses dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            HasilOperasi.Gagal(e.message ?: "Dokumen SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: IllegalArgumentException) {
            HasilOperasi.Gagal(e.message ?: "URI dokumen SAF tidak valid", PenyebabGalat.VALIDASI)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal membaca metadata dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    internal companion object {
        fun untukUji(
            pemuatDaftarIsi: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>
        ): SafSumberDataPenyimpanan = SafSumberDataPenyimpanan(
            appContext = null,
            daftarIsiPenyedia = pemuatDaftarIsi,
            operasiPenyedia = null
        )
    }
}

fun buatRouterBerkasSaf(
    context: Context,
    sumberLain: Map<JenisLokasi, SumberDataPenyimpanan> = emptyMap(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): RepositoriRouterBerkas = RepositoriRouterBerkas(
    sumber = buildMap {
        putAll(sumberLain)
        put(JenisLokasi.SAF, SafSumberDataPenyimpanan(context, ioDispatcher))
    }
)