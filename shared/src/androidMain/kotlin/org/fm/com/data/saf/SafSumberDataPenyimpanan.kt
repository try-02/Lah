package org.fm.com.data.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

/**
 * Adapter [SumberDataPenyimpanan] penuh untuk [JenisLokasi.SAF] (keputusan K9, K10, T9, T10).
 */
class SafSumberDataPenyimpanan internal constructor(
    private val appContext: Context,
    private val daftarIsiSaf: SafDaftarIsi,
    private val operasiSaf: SafOperasiBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberDataPenyimpanan {

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        appContext = context.applicationContext,
        daftarIsiSaf = SafDaftarIsi(context, ioDispatcher),
        operasiSaf = SafOperasiBerkas(context, ioDispatcher),
        ioDispatcher = ioDispatcher
    )

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.SAF) {
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        daftarIsiSaf.daftarIsi(lokasi)

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
        val uri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        val ukuran = bacaUkuran(uri)
        return HasilOperasi.Sukses(SafAliranIsi.buka(appContext, uri, ukuran, ioDispatcher))
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val uri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        if (!timpa) {
            return HasilOperasi.Gagal(
                "Berkas sudah ada, perlu konfirmasi timpa: ${lokasi.pengenal}",
                PenyebabGalat.KONFLIK
            )
        }
        return HasilOperasi.Sukses(
            SafAliranTulis(
                appContext = appContext,
                targetDocUri = uri,
                apakahDokumenBaru = false,
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
        val parentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(appContext, induk)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }

        // Cek tabrakan nama sebelum membuat dokumen
        val existingDocUri = operasiSaf.cariAnakBernama(parentUri, nama)
        if (existingDocUri != null) {
            if (!timpa) {
                return HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa: $nama",
                    PenyebabGalat.KONFLIK
                )
            }
            return HasilOperasi.Sukses(
                SafAliranTulis(
                    appContext = appContext,
                    targetDocUri = existingDocUri,
                    apakahDokumenBaru = false,
                    ioDispatcher = ioDispatcher
                )
            )
        }

        // Dokumen baru: buat di SAF lalu kembalikan writer yang siap menghapusnya jika dibatalkan
        val createdUri = try {
            val mime = "application/octet-stream"
            DocumentsContract.createDocument(appContext.contentResolver, parentUri, mime, nama)
                ?: return HasilOperasi.Gagal("Penyedia SAF gagal membuat dokumen baru", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat dokumen baru di SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        return HasilOperasi.Sukses(
            SafAliranTulis(
                appContext = appContext,
                targetDocUri = createdUri,
                apakahDokumenBaru = true,
                ioDispatcher = ioDispatcher
            )
        )
    }

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiSaf.buatFolder(permintaan)

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiSaf.buatBerkasKosong(permintaan)

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiSaf.gantiNama(permintaan)

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        operasiSaf.hapus(permintaan)

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiSaf.salin(permintaan)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiSaf.pindah(permintaan)

    private fun bacaUkuran(docUri: Uri): Long? {
        return try {
            appContext.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Mendaftarkan adapter SAF untuk [JenisLokasi.SAF] ke [RepositoriRouterBerkas]. */
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