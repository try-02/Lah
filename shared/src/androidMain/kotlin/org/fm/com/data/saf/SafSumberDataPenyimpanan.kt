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
import org.fm.com.domain.repository.RepositoriOperasiBerkas

/**
 * Adapter [SumberDataPenyimpanan] penuh untuk [JenisLokasi.SAF] (keputusan K9, K10, T9, T10).
 */
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
        val ukuran = bacaUkuran(context, uri)
        return HasilOperasi.Sukses(SafAliranIsi.buka(context, uri, ukuran, ioDispatcher))
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val context = appContext ?: return tidakDidukungTanpaKonteks()
        val uri = when (val hasil = SafPemetaanLokasi.keUriDokumen(context, lokasi)) {
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
                appContext = context,
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
        val context = appContext ?: return tidakDidukungTanpaKonteks()
        val parentUri = when (val hasil = SafPemetaanLokasi.keUriDokumen(context, induk)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }

        val opSaf = operasiPenyedia as? SafOperasiBerkas
        val existingDocUri = opSaf?.cariAnakBernama(parentUri, nama)
        if (existingDocUri != null) {
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
                    ioDispatcher = ioDispatcher
                )
            )
        }

        val createdUri = try {
            val mime = "application/octet-stream"
            DocumentsContract.createDocument(context.contentResolver, parentUri, mime, nama)
                ?: return HasilOperasi.Gagal("Penyedia SAF gagal membuat dokumen baru", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat dokumen baru di SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        return HasilOperasi.Sukses(
            SafAliranTulis(
                appContext = context,
                targetDocUri = createdUri,
                apakahDokumenBaru = true,
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

    private fun bacaUkuran(context: Context, docUri: Uri): Long? {
        return try {
            context.contentResolver.query(
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