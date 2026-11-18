package org.fm.com.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PermintaanOperasi
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas

/**
 * Seam backend penyimpanan per [JenisLokasi] (keputusan K9). Adapter T9–T12
 * (SAF/iOS) mengimplementasikan ini; didaftarkan ke [RepositoriRouterBerkas] via DI.
 * Operasi lintas jenis lokasi (mis. SAF → POSIX) TIDAK lewat router — use case T22
 * menstrimnya via bukaUntukBaca + bukaUntukTulis.
 */
interface SumberDataPenyimpanan : RepositoriBerkas, RepositoriOperasiBerkas

/** Fake in-memory sebagai satu backend utuh; untuk commonTest & pratinjau. */
class SumberMemoriPenyimpanan(
    val berkas: ImplementasiRepositoriBerkas = ImplementasiRepositoriBerkas(),
    val operasi: ImplementasiRepositoriOperasiBerkas = ImplementasiRepositoriOperasiBerkas(berkas)
) : SumberDataPenyimpanan, RepositoriBerkas by berkas, RepositoriOperasiBerkas by operasi

/** Router tipis: merutekan ke backend menurut jenis lokasi; jujur bila tak terdaftar. */
class RepositoriRouterBerkas(
    private val sumber: Map<JenisLokasi, SumberDataPenyimpanan>
) : SumberDataPenyimpanan {

    private fun backend(lokasi: LokasiBerkas): SumberDataPenyimpanan? = sumber[lokasi.jenis]

    private fun tidakTerdaftar(lokasi: LokasiBerkas): HasilOperasi.Gagal =
        HasilOperasi.Gagal("Jenis lokasi tidak terdaftar: ${lokasi.jenis}", PenyebabGalat.TIDAK_DIDUKUNG)

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        backend(lokasi)?.kapabilitas(lokasi)

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        backend(lokasi)?.daftarIsi(lokasi) ?: flowOf(tidakTerdaftar(lokasi))

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
        backend(lokasi)?.bukaUntukBaca(lokasi) ?: tidakTerdaftar(lokasi)

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> =
        backend(lokasi)?.bukaUntukTulis(lokasi, timpa) ?: tidakTerdaftar(lokasi)

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas, nama: String, timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> =
        backend(induk)?.bukaUntukTulisDi(induk, nama, timpa) ?: tidakTerdaftar(induk)

    override suspend fun buatFolder(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        rute(permintaan) { it.buatFolder(permintaan) }

    override suspend fun buatBerkasKosong(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        rute(permintaan) { it.buatBerkasKosong(permintaan) }

    override suspend fun gantiNama(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        rute(permintaan) { it.gantiNama(permintaan) }

    override suspend fun hapus(permintaan: PermintaanOperasi): HasilOperasi<Unit> =
        rute(permintaan) { it.hapus(permintaan) }

    override suspend fun salin(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        rute(permintaan) { it.salin(permintaan) }

    override suspend fun pindah(permintaan: PermintaanOperasi): HasilOperasi<EntitasBerkas> =
        rute(permintaan) { it.pindah(permintaan) }

    private suspend fun <T> rute(
        permintaan: PermintaanOperasi,
        aksi: suspend (SumberDataPenyimpanan) -> HasilOperasi<T>
    ): HasilOperasi<T> {
        val b = backend(permintaan.sumber) ?: return tidakTerdaftar(permintaan.sumber)
        val tujuan = permintaan.tujuan
        if (tujuan != null && tujuan.jenis != permintaan.sumber.jenis) {
            return HasilOperasi.Gagal(
                "Salin/pindah lintas jenis lokasi ditangani use case T22, bukan router",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        }
        return aksi(b)
    }
}