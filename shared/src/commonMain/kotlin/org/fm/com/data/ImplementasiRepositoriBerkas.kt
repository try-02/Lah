package org.fm.com.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.fm.com.domain.io.PotonganIsi
import org.fm.com.domain.io.SumberIsiBerkas
import org.fm.com.domain.io.SumberTulisBerkas
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.PlatformPenyimpanan
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.MatriksKemampuan
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.data.PemetaanDataDomain.keDomain

/**
 * Implementasi RepositoriBerkas di `commonMain` tanpa asumsi platform.
 * Kaset kunci setiap entitas menurut [DataBerkas.pengenal] (path unik), jadi
 * pembacaan, penulisan, dan daftar isi konsisten. Adapter platform (SAF/iOS)
 * menimpa sumber aktual di T9–T12. Kegagalan dilaporkan lewat
 * [HasilOperasi.Gagal], bukan exception.
 */
class ImplementasiRepositoriBerkas : RepositoriBerkas {

    /** Semua entitas, kunci = pengenAL lengkap (path unik). */
    internal val kaset: MutableMap<String, DataBerkas> = mutableMapOf()

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        MatriksKemampuan.untuk(jenisKePlatform(lokasi.jenis), lokasi.jenis)

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> =
        flow {
            val awalan = "${lokasi.pengenal}/"
            val anak = kaset.values
                .filter { it.pengenal.startsWith(awalan) && !it.pengenal.substring(awalan.length).contains('/') }
                .sortedBy { it.nama }
                .map { it.keDomain() }
            emit(HasilOperasi.Sukses(anak))
        }

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> {
        val entitas = kaset[lokasi.pengenal]
        if (entitas == null) {
            return HasilOperasi.Gagal(
                "Berkas tidak ditemukan: ${lokasi.pengenal}",
                PenyebabGalat.TIDAK_DITEMUKAN
            )
        }
        if (entitas.adalahFolder) {
            return HasilOperasi.Gagal(
                "Tidak bisa membaca folder sebagai berkas",
                PenyebabGalat.VALIDASI
            )
        }
        return HasilOperasi.Sukses(SumberMemoriBaca(entitas))
    }

    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        if (kaset.containsKey(lokasi.pengenal) && !timpa) {
            return HasilOperasi.Gagal(
                "Berkas sudah ada, perlu konfirmasi timpa",
                PenyebabGalat.KONFLIK
            )
        }
        return HasilOperasi.Sukses(SumberMemoriTulis(this, lokasi.pengenal))
    }

    private fun jenisKePlatform(jenis: JenisLokasi): PlatformPenyimpanan =
        when (jenis) {
            JenisLokasi.POSIX -> PlatformPenyimpanan.ANDROID
            JenisLokasi.SAF -> PlatformPenyimpanan.ANDROID
            JenisLokasi.IOS -> PlatformPenyimpanan.IOS
        }

    /** Simpan/timpa entitas menurut pengenAL-nya. Digunakan oleh operasi & sumber tulis. */
    internal fun simpan(entitas: DataBerkas) {
        kaset[entitas.pengenal] = entitas
    }

    internal fun hapus(pengenal: String): Boolean = kaset.remove(pengenal) != null
}

/** Sumber baca streaming dari entitas yang ada di kaset. */
private class SumberMemoriBaca(private val entitas: DataBerkas) : SumberIsiBerkas {

    override val ukuranByte: Long? = entitas.ukuranByte

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan =
        SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)

    override suspend fun tutup() {}
}

/** Sumber tulis streaming yang menulis ke kaset setelah [selesai]. */
private class SumberMemoriTulis(
    private val repo: ImplementasiRepositoriBerkas,
    private val pengenAL: String
) : SumberTulisBerkas {

    private val penampung: MutableList<ByteArray> = mutableListOf()
    private var selesai = false

    override suspend fun tulis(potongan: PotonganIsi) {
        if (selesai) throw IllegalStateException("Sudah selesai")
        penampung.add(potongan.data.copyOf())
    }

    override suspend fun selesai() {
        selesai = true
        val gabungan = penampung.fold(ByteArray(0)) { a, b -> a + b }
        repo.simpan(DataBerkas(
            pengenal = pengenAL,
            nama = pengenAL.substringAfterLast("/"),
            jenisLokasi = JenisLokasi.POSIX,
            adalahFolder = false,
            ukuranByte = gabungan.size.toLong()
        ))
    }

    override suspend fun batalkan() {
        selesai = true
        penampung.clear()
    }
}
