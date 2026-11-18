package org.fm.com.data.saf

import android.content.Context
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
import org.fm.com.domain.repository.PermintaanOperasi

/**
 * Adapter [SumberDataPenyimpanan] untuk [JenisLokasi.SAF] (keputusan K9).
 * Di T9 menyediakan kapabilitas dan [daftarIsi]; operasi streaming & mutasi berkas (T10)
 * melaporkan [PenyebabGalat.TIDAK_DIDUKUNG] secara jujur hingga T10 dipasang.
 */
class SafSumberDataPenyimpanan private constructor(
    private val pemuatDaftarIsi: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>
) : SumberDataPenyimpanan {

    constructor(daftarIsiSaf: SafDaftarIsi) : this(daftarIsiSaf::daftarIsi)

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(SafDaftarIsi(context, ioDispatcher))

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.SAF) {
            MatriksKemampuan.untuk(PlatformPenyimpanan.ANDROID, JenisLokasi.SAF)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        pemuatDaftarIsi(lokasi)

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
        belumDidukungT10()

    override suspend fun bukaUntukTulis(
        lokasi: LokasiBerkas,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> = belumDidukungT10()

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas,
        nama: String,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> = belumDidukungT10()

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        belumDidukungT10()

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        belumDidukungT10()

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        belumDidukungT10()

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        belumDidukungT10()

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        belumDidukungT10()

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        belumDidukungT10()

    private fun belumDidukungT10(): HasilOperasi.Gagal = HasilOperasi.Gagal(
        "Operasi berkas SAF belum diimplementasikan sebelum T10",
        PenyebabGalat.TIDAK_DIDUKUNG
    )

    internal companion object {
        fun untukUji(
            pemuatDaftarIsi: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>
        ): SafSumberDataPenyimpanan = SafSumberDataPenyimpanan(pemuatDaftarIsi)
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