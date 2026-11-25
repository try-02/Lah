package org.fm.com.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.fm.com.data.PemetaanDataDomain.keDomain
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
import org.fm.com.domain.repository.RepositoriBerkas

private const val UKURAN_POTONGAN_BAWAAN = 8 * 1024

private fun awalanAnak(pengenal: String): String =
    if (pengenal.endsWith("/")) pengenal else "$pengenal/"

private fun indukDari(pengenal: String): String {
    val i = pengenal.lastIndexOf('/')
    return if (i <= 0) "/" else pengenal.substring(0, i)
}

private fun gabung(induk: String, nama: String): String =
    if (induk.endsWith("/")) induk + nama else "$induk/$nama"

/**
 * Fake in-memory [RepositoriBerkas] untuk commonTest & pratinjau UI pra-DI —
 * BUKAN adapter nyata; tetap di commonMain (lihat K10). Semua akses peta dilindungi
 * [Mutex]; operasi folder rekursif mempertahankan flag (termasuk tautan).
 * Pengenal = kunci opaque bersekat '/' — adapter SAF/iOS memakai API platform,
 * bukan matematika path ini.
 *
 * Parameter khusus uji: [ukuranPotongan] kecil memaksa loop multi-potongan;
 * [gagalBacaSetelahPotongan]/[gagalTulisSetelahPotongan] menyuntik kegagalan I/O
 * mid-stream (untuk uji T22/T35/T44). Produksi memakai bawaan (tanpa hook).
 */
class ImplementasiRepositoriBerkas(
    private val ukuranPotongan: Int = UKURAN_POTONGAN_BAWAAN,
    private val gagalBacaSetelahPotongan: Int? = null,
    private val gagalTulisSetelahPotongan: Int? = null
) : RepositoriBerkas {
    init { require(ukuranPotongan > 0) { "ukuranPotongan harus > 0" } }

    private val mutex = Mutex()
    private val kaset: MutableMap<String, DataBerkas> = mutableMapOf()
    private val isiBerkas: MutableMap<String, ByteArray> = mutableMapOf()

    override fun kapabilitas(lokasi: LokasiBerkas): KemampuanPenyimpanan? =
        MatriksKemampuan.untuk(jenisKePlatform(lokasi.jenis), lokasi.jenis)

    override fun daftarIsi(lokasi: LokasiBerkas): Flow<HasilOperasi<List<EntitasBerkas>>> = flow {
        val hasil = mutex.withLock {
            val folder = kaset[lokasi.pengenal]
            when {
                folder == null -> HasilOperasi.Gagal(
                    "Folder tidak ditemukan: ${lokasi.pengenal}", PenyebabGalat.TIDAK_DITEMUKAN
                )
                !folder.adalahFolder -> HasilOperasi.Gagal(
                    "Bukan folder: ${lokasi.pengenal}", PenyebabGalat.VALIDASI
                )
                else -> {
                    val awalan = awalanAnak(lokasi.pengenal)
                    HasilOperasi.Sukses(
                        kaset.values
                            .filter {
                                it.pengenal.startsWith(awalan) &&
                                    it.pengenal.length > awalan.length && // sisa kosong = folder itu sendiri (root "/")
                                    '/' !in it.pengenal.substring(awalan.length)
                            }
                            .sortedBy { it.nama }
                            .map { it.keDomain() }
                    )
                }
            }
        }
        emit(hasil)
    }

    override suspend fun bukaUntukBaca(lokasi: LokasiBerkas): HasilOperasi<SumberIsiBerkas> =
        mutex.withLock {
            val entitas = kaset[lokasi.pengenal]
            when {
                entitas == null -> HasilOperasi.Gagal(
                    "Berkas tidak ditemukan: ${lokasi.pengenal}", PenyebabGalat.TIDAK_DITEMUKAN
                )
                entitas.adalahFolder -> HasilOperasi.Gagal(
                    "Tidak bisa membaca folder sebagai berkas", PenyebabGalat.VALIDASI
                )
                else -> HasilOperasi.Sukses(
                    SumberMemoriBaca(
                        isiBerkas[lokasi.pengenal]?.copyOf() ?: ByteArray(0),
                        ukuranPotongan, gagalBacaSetelahPotongan
                    )
                )
            }
        }

    // Perbarui bukaUntukTulis di ImplementasiRepositoriBerkas:
    override suspend fun bukaUntukTulis(lokasi: LokasiBerkas, timpa: Boolean): HasilOperasi<SumberTulisBerkas> {
        val namaSegmen = lokasi.label?.takeIf { it.isNotBlank() }
            ?: lokasi.pengenal.trimEnd('/').substringAfterLast('/')
        if (lokasi.pengenal == "/" || !ValidasiNama.sah(namaSegmen)) {
            return HasilOperasi.Gagal("Lokasi tulis tidak sah: ${lokasi.pengenal}", PenyebabGalat.VALIDASI)
        }
        return mutex.withLock {
            val lama = kaset[lokasi.pengenal]
            when {
                lama != null && lama.adalahFolder -> HasilOperasi.Gagal(
                    "Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI
                )
                lama != null && !timpa -> HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa", PenyebabGalat.KONFLIK
                )
                else -> HasilOperasi.Sukses(SumberMemoriTulis(this, lokasi, timpa, gagalTulisSetelahPotongan))
            }
        }
    }

    override suspend fun bukaUntukTulisDi(
        induk: LokasiBerkas, nama: String, timpa: Boolean
    ): HasilOperasi<SumberTulisBerkas> {
        if (!ValidasiNama.sah(nama)) {
            return HasilOperasi.Gagal("Nama tidak sah: $nama", PenyebabGalat.VALIDASI)
        }
        return mutex.withLock {
            val dataInduk = kaset[induk.pengenal]
                ?: return@withLock HasilOperasi.Gagal(
                    "Folder induk tidak ditemukan: ${induk.pengenal}", PenyebabGalat.TIDAK_DITEMUKAN
                )
            if (!dataInduk.adalahFolder) {
                return@withLock HasilOperasi.Gagal("Induk bukan folder: ${induk.pengenal}", PenyebabGalat.VALIDASI)
            }
            val id = gabung(induk.pengenal, nama)
            val lama = kaset[id]
            when {
                lama != null && lama.adalahFolder -> HasilOperasi.Gagal(
                    "Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI
                )
                lama != null && !timpa -> HasilOperasi.Gagal(
                    "Berkas sudah ada, perlu konfirmasi timpa", PenyebabGalat.KONFLIK
                )
                else -> HasilOperasi.Sukses(
                    SumberMemoriTulis(this, LokasiBerkas(id, induk.jenis, nama), timpa, gagalTulisSetelahPotongan)
                )
            }
        }
    }

    private fun jenisKePlatform(jenis: JenisLokasi): PlatformPenyimpanan = when (jenis) {
        JenisLokasi.POSIX, JenisLokasi.SAF, JenisLokasi.SFTP -> PlatformPenyimpanan.ANDROID
        JenisLokasi.IOS -> PlatformPenyimpanan.IOS
    }

    // ── API internal untuk operasi, sumber tulis, dan test (semua terkunci mutex) ──

    internal suspend fun simpan(entitas: DataBerkas, isi: ByteArray? = null): Unit = mutex.withLock {
        kaset[entitas.pengenal] = entitas
        if (isi != null) isiBerkas[entitas.pengenal] = isi.copyOf()
    }

    internal suspend fun data(pengenal: String): DataBerkas? = mutex.withLock { kaset[pengenal] }

    internal suspend fun isiDari(pengenal: String): ByteArray? = mutex.withLock { isiBerkas[pengenal]?.copyOf() }

    internal suspend fun hapusPohon(pengenal: String): Boolean =
        mutex.withLock { hapusSubpohonTerkunci(pengenal) }

    internal suspend fun simpanIsi(lokasi: LokasiBerkas, isi: ByteArray, timpa: Boolean): HasilOperasi<Unit> =
        mutex.withLock {
            val lama = kaset[lokasi.pengenal]
            if (lama != null && lama.adalahFolder) {
                return@withLock HasilOperasi.Gagal("Tidak bisa menulis isi ke folder", PenyebabGalat.VALIDASI)
            }
            if (lama != null && !timpa) {
                return@withLock HasilOperasi.Gagal("Berkas sudah ada, perlu konfirmasi timpa", PenyebabGalat.KONFLIK)
            }
            val entitas = lama?.copy(ukuranByte = isi.size.toLong())
                ?: DataBerkas(
                    pengenal = lokasi.pengenal,
                    nama = lokasi.label ?: lokasi.pengenal.substringAfterLast('/'),
                    jenisLokasi = lokasi.jenis,
                    adalahFolder = false,
                    ukuranByte = isi.size.toLong()
                )
            kaset[lokasi.pengenal] = entitas
            isiBerkas[lokasi.pengenal] = isi
            HasilOperasi.Sukses(Unit)
        }

    internal suspend fun buatEntitas(
        induk: LokasiBerkas, nama: String, folder: Boolean, konfirmasi: Boolean
    ): HasilOperasi<EntitasBerkas> = mutex.withLock {
        val dataInduk = kaset[induk.pengenal]
            ?: return@withLock HasilOperasi.Gagal(
                "Folder induk tidak ditemukan: ${induk.pengenal}", PenyebabGalat.TIDAK_DITEMUKAN
            )
        if (!dataInduk.adalahFolder) {
            return@withLock HasilOperasi.Gagal("Induk bukan folder: ${induk.pengenal}", PenyebabGalat.VALIDASI)
        }
        val id = gabung(induk.pengenal, nama)
        if (kaset.containsKey(id)) {
            if (!konfirmasi) return@withLock HasilOperasi.Gagal(
                "Target sudah ada, perlu konfirmasi timpa: $id", PenyebabGalat.KONFLIK
            )
            hapusSubpohonTerkunci(id)
        }
        val entitas = DataBerkas(
            pengenal = id, nama = nama, jenisLokasi = induk.jenis,
            adalahFolder = folder, ukuranByte = if (folder) null else 0L
        )
        kaset[id] = entitas
        if (!folder) isiBerkas[id] = ByteArray(0)
        HasilOperasi.Sukses(entitas.keDomain())
    }

    internal suspend fun gantiNamaPohon(
        sumber: LokasiBerkas, namaBaru: String, konfirmasi: Boolean
    ): HasilOperasi<EntitasBerkas> = mutex.withLock {
        val asal = kaset[sumber.pengenal]
            ?: return@withLock HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        if (sumber.pengenal == "/") {
            return@withLock HasilOperasi.Gagal("Root tidak bisa diganti nama", PenyebabGalat.VALIDASI)
        }
        val idTujuan = gabung(indukDari(sumber.pengenal), namaBaru)
        if (idTujuan == sumber.pengenal) return@withLock HasilOperasi.Sukses(asal.keDomain())
        if (kaset.containsKey(idTujuan)) {
            if (!konfirmasi) return@withLock HasilOperasi.Gagal(
                "Target sudah ada, perlu konfirmasi timpa: $idTujuan", PenyebabGalat.KONFLIK
            )
            hapusSubpohonTerkunci(idTujuan)
        }
        val baru = salinSubpohonTerkunci(sumber.pengenal, idTujuan, sumber.jenis)
        hapusSubpohonTerkunci(sumber.pengenal)
        HasilOperasi.Sukses(baru.keDomain())
    }

    internal suspend fun salinAtauPindahPohon(
        sumber: LokasiBerkas, indukTujuan: LokasiBerkas, namaBaru: String?,
        konfirmasi: Boolean, pindah: Boolean
    ): HasilOperasi<EntitasBerkas> = mutex.withLock {
        val asal = kaset[sumber.pengenal]
            ?: return@withLock HasilOperasi.Gagal("Sumber tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        val dataInduk = kaset[indukTujuan.pengenal]
            ?: return@withLock HasilOperasi.Gagal(
                "Folder tujuan tidak ditemukan: ${indukTujuan.pengenal}", PenyebabGalat.TIDAK_DITEMUKAN
            )
        if (!dataInduk.adalahFolder) {
            return@withLock HasilOperasi.Gagal("Tujuan bukan folder: ${indukTujuan.pengenal}", PenyebabGalat.VALIDASI)
        }
        val idTujuan = gabung(indukTujuan.pengenal, namaBaru ?: asal.nama)
        if (idTujuan == sumber.pengenal) return@withLock HasilOperasi.Sukses(asal.keDomain())
        if (asal.adalahFolder && idTujuan.startsWith(awalanAnak(sumber.pengenal))) {
            return@withLock HasilOperasi.Gagal(
                "Tidak bisa menyalin/memindah folder ke dalam dirinya sendiri", PenyebabGalat.VALIDASI
            )
        }
        if (kaset.containsKey(idTujuan)) {
            if (!konfirmasi) return@withLock HasilOperasi.Gagal(
                "Target sudah ada, perlu konfirmasi timpa: $idTujuan", PenyebabGalat.KONFLIK
            )
            hapusSubpohonTerkunci(idTujuan)
        }
        val baru = salinSubpohonTerkunci(sumber.pengenal, idTujuan, indukTujuan.jenis)
        if (pindah) hapusSubpohonTerkunci(sumber.pengenal)
        HasilOperasi.Sukses(baru.keDomain())
    }

    /** Salin akar + seluruh descendan beserta isi; flag (termasuk tautan) dipertahankan. */
    private fun salinSubpohonTerkunci(idAsal: String, idTujuan: String, jenisTujuan: JenisLokasi): DataBerkas {
        val awalan = awalanAnak(idAsal)
        val akar = kaset.getValue(idAsal)
        val pasangan = mutableListOf<Pair<DataBerkas, ByteArray?>>()
        pasangan += akar.copy(
            pengenal = idTujuan, nama = idTujuan.substringAfterLast('/'), jenisLokasi = jenisTujuan
        ) to isiBerkas[idAsal]?.copyOf()
        for ((id, ent) in kaset.toMap()) {
            if (id != idAsal && id.startsWith(awalan)) {
                val idBaru = idTujuan + id.substring(idAsal.length)
                pasangan += ent.copy(pengenal = idBaru, jenisLokasi = jenisTujuan) to isiBerkas[id]?.copyOf()
            }
        }
        for ((ent, isi) in pasangan) {
            kaset[ent.pengenal] = ent
            if (isi != null) isiBerkas[ent.pengenal] = isi else isiBerkas.remove(ent.pengenal)
        }
        return kaset.getValue(idTujuan)
    }

    private fun hapusSubpohonTerkunci(id: String): Boolean {
        val ada = kaset.remove(id) != null
        isiBerkas.remove(id)
        val awalan = awalanAnak(id)
        for (k in kaset.keys.filter { it.startsWith(awalan) }) {
            kaset.remove(k)
            isiBerkas.remove(k)
        }
        return ada
    }
}

/** Sumber baca chunked; EOF idempoten; kegagalan mid-stream bersifat terminal. */
private class SumberMemoriBaca(
    private val isi: ByteArray,
    private val ukuranPotongan: Int,
    private val gagalSetelahPotongan: Int?
) : SumberIsiBerkas {
    private var posisi = 0
    private var tertutup = false
    private var potonganBerhasil = 0

    override val ukuranByte: Long = isi.size.toLong()

    override suspend fun potongan(): SumberIsiBerkas.HasilPotongan {
        if (tertutup) return SumberIsiBerkas.HasilPotongan.Gagal("Sumber baca sudah ditutup")
        if (posisi >= isi.size) return SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi.KOSONG)
        if (gagalSetelahPotongan != null && potonganBerhasil >= gagalSetelahPotongan) {
            tertutup = true
            return SumberIsiBerkas.HasilPotongan.Gagal("Simulasi kegagalan I/O saat baca")
        }
        val akhir = minOf(posisi + ukuranPotongan, isi.size)
        val data = isi.copyOfRange(posisi, akhir)
        posisi = akhir
        potonganBerhasil++
        return SumberIsiBerkas.HasilPotongan.Isi(PotonganIsi(data, selesai = posisi >= isi.size))
    }

    override suspend fun tutup() { tertutup = true }
}

/**
 * Sumber tulis fake: menampung potongan di memori (adapter nyata WAJIB streaming ke temp).
 * Konflik diperiksa ulang atomik saat [selesai]. Gagal tulis → BATAL + bersih (terminal).
 * [batalkan] idempoten; tidak pernah mengubah hasil SELESAI.
 */
// Perbarui SumberMemoriTulis di bagian bawah ImplementasiRepositoriBerkas.kt:
private class SumberMemoriTulis(
    private val repo: ImplementasiRepositoriBerkas,
    private val lokasi: LokasiBerkas,
    private val timpa: Boolean,
    private val gagalSetelahPotongan: Int?
) : SumberTulisBerkas {
    private enum class Status { AKTIF, SELESAI, BATAL }
    private val penampung = mutableListOf<ByteArray>()
    private var status = Status.AKTIF

    override val lokasiTarget: LokasiBerkas = lokasi

    override suspend fun tulis(potongan: PotonganIsi): HasilOperasi<Unit> {
        if (status != Status.AKTIF) {
            return HasilOperasi.Gagal("Sumber tulis sudah tidak aktif", PenyebabGalat.VALIDASI)
        }
        if (gagalSetelahPotongan != null && penampung.size >= gagalSetelahPotongan) {
            penampung.clear()
            status = Status.BATAL
            return HasilOperasi.Gagal("Simulasi kegagalan I/O saat tulis", PenyebabGalat.TIDAK_DIKETAHUI)
        }
        penampung.add(potongan.data.copyOf())
        return HasilOperasi.Sukses(Unit)
    }

    override suspend fun selesai(): HasilOperasi<Unit> {
        if (status != Status.AKTIF) {
            return HasilOperasi.Gagal("Sumber tulis sudah tidak aktif", PenyebabGalat.VALIDASI)
        }
        val gabungan = ByteArray(penampung.sumOf { it.size })
        var posisi = 0
        for (b in penampung) { b.copyInto(gabungan, posisi); posisi += b.size }
        val hasil = repo.simpanIsi(lokasi, gabungan, timpa)
        status = if (hasil is HasilOperasi.Sukses) Status.SELESAI else Status.BATAL
        penampung.clear()
        return hasil
    }

    override suspend fun batalkan(): HasilOperasi<Unit> {
        if (status == Status.AKTIF) status = Status.BATAL
        penampung.clear()
        return HasilOperasi.Sukses(Unit)
    }
}