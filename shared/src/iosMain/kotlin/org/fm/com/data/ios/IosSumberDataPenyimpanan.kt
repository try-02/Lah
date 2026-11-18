@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.fm.com.data.ios

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileSizeKey

/**
 * Adapter [SumberDataPenyimpanan] untuk penyimpanan sandbox [JenisLokasi.IOS] (PRD §5.1, K9, K10, T11, T12).
 */
class IosSumberDataPenyimpanan internal constructor(
    private val daftarIsiPenyedia: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>,
    private val operasiPenyedia: RepositoriOperasiBerkas,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SumberDataPenyimpanan {

    constructor(
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        daftarIsiPenyedia = { DokumenDaftarIsi(ioDispatcher).daftarIsi(it) },
        operasiPenyedia = DokumenOperasiBerkas(ioDispatcher),
        ioDispatcher = ioDispatcher
    )

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        if (lokasi.jenis == JenisLokasi.IOS) {
            MatriksKemampuan.untuk(PlatformPenyimpanan.IOS, JenisLokasi.IOS)
        } else {
            null
        }

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        daftarIsiPenyedia(lokasi)

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
        val url = when (val hasil = DokumenPemetaanLokasi.keUrl(lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        val ukuran = bacaUkuran(url)
        return HasilOperasi.Sukses(DokumenAliranIsi(url, ukuran, ioDispatcher))
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val url = when (val hasil = DokumenPemetaanLokasi.keUrl(lokasi)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }
        if (!timpa) {
            return HasilOperasi.Gagal(
                "Berkas sudah ada, perlu konfirmasi timpa: ${lokasi.pengenal}",
                PenyebabGalat.KONFLIK
            )
        }
        return HasilOperasi.Sukses(DokumenAliranTulis(url, apakahDokumenBaru = false, ioDispatcher))
    }

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas,
        nama: String,
        timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> {
        if (!ValidasiNama.sah(nama)) {
            return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        val parentUrl = when (val hasil = DokumenPemetaanLokasi.keUrl(induk)) {
            is HasilOperasi.Sukses -> hasil.nilai
            is HasilOperasi.Gagal -> return hasil
        }

        val targetUrl = parentUrl.URLByAppendingPathComponent(nama)
            ?: return HasilOperasi.Gagal("Gagal menyusun URL berkas baru", PenyebabGalat.VALIDASI)

        val targetPath = targetUrl.path
        val targetAda = targetPath != null && NSFileManager.defaultManager.fileExistsAtPath(targetPath)

        if (targetAda && !timpa) {
            return HasilOperasi.Gagal("Berkas sudah ada, perlu konfirmasi timpa: $nama", PenyebabGalat.KONFLIK)
        }

        return HasilOperasi.Sukses(
            DokumenAliranTulis(
                targetUrl = targetUrl,
                apakahDokumenBaru = !targetAda,
                ioDispatcher = ioDispatcher
            )
        )
    }

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia.buatFolder(permintaan)

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia.buatBerkasKosong(permintaan)

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia.gantiNama(permintaan)

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        operasiPenyedia.hapus(permintaan)

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia.salin(permintaan)

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        operasiPenyedia.pindah(permintaan)

    private fun bacaUkuran(url: NSURL): Long? = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val vals = url.resourceValuesForKeys(listOf(NSURLFileSizeKey), errorPtr.ptr)
        (vals?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
    }

    internal companion object {
        fun untukUji(
            pemuatDaftarIsi: (LokasiBerkas) -> Flow<HasilOperasi<List<EntitasBerkas>>>
        ): IosSumberDataPenyimpanan = IosSumberDataPenyimpanan(
            daftarIsiPenyedia = pemuatDaftarIsi,
            operasiPenyedia = DokumenOperasiBerkas()
        )
    }
}

/** Mendaftarkan adapter iOS untuk [JenisLokasi.IOS] ke [RepositoriRouterBerkas]. */
fun buatRouterBerkasIos(
    sumberLain: Map<JenisLokasi, SumberDataPenyimpanan> = emptyMap(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): RepositoriRouterBerkas = RepositoriRouterBerkas(
    sumber = buildMap {
        putAll(sumberLain)
        put(JenisLokasi.IOS, IosSumberDataPenyimpanan(ioDispatcher))
    }
)