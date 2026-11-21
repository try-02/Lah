package org.fm.com.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fm.com.domain.diff.AlgoritmaMyers
import org.fm.com.domain.diff.HasilDiffLengkap
import org.fm.com.domain.diff.OpsiDiff
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.repository.RepositoriBerkas

sealed interface HasilBandingkanBerkas {
    data class Sukses(
        val teksA: String,
        val teksB: String,
        val sidikA: SidikIsiBerkas,
        val sidikB: SidikIsiBerkas,
        val hasilDiff: HasilDiffLengkap
    ) : HasilBandingkanBerkas

    data class BinerDitolak(
        val namaBerkas: String,
        val alasan: String
    ) : HasilBandingkanBerkas

    data class TerlaluBesar(
        val namaBerkas: String,
        val ukuranByte: Long?,
        val batasMaksimalByte: Long
    ) : HasilBandingkanBerkas

    data class Gagal(
        val pesan: String,
        val penyebab: PenyebabGalat
    ) : HasilBandingkanBerkas
}

class BandingkanBerkasUseCase(
    private val repositoriBerkas: RepositoriBerkas,
    private val bukaBerkasTeksUseCase: BukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repositoriBerkas),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun muatDanBandingkan(
        lokasiA: LokasiBerkas,
        namaA: String,
        lokasiB: LokasiBerkas,
        namaB: String,
        opsi: OpsiDiff = OpsiDiff()
    ): HasilBandingkanBerkas = coroutineScope {
        val defA = async { bukaBerkasTeksUseCase(lokasiA) }
        val defB = async { bukaBerkasTeksUseCase(lokasiB) }
        val hasilA = defA.await()
        val hasilB = defB.await()

        val dataA = when (hasilA) {
            is HasilBukaBerkasTeks.Sukses -> hasilA.data
            is HasilBukaBerkasTeks.Biner -> return@coroutineScope HasilBandingkanBerkas.BinerDitolak(
                namaA,
                "Berkas '$namaA' terdeteksi sebagai file biner: ${hasilA.alasan}"
            )
            is HasilBukaBerkasTeks.TerlaluBesar -> return@coroutineScope HasilBandingkanBerkas.TerlaluBesar(
                namaA,
                hasilA.ukuranByte,
                hasilA.batasMaksimalByte
            )
            is HasilBukaBerkasTeks.Gagal -> return@coroutineScope HasilBandingkanBerkas.Gagal(
                "Gagal membuka '$namaA': ${hasilA.pesan}",
                hasilA.penyebab
            )
        }

        val dataB = when (hasilB) {
            is HasilBukaBerkasTeks.Sukses -> hasilB.data
            is HasilBukaBerkasTeks.Biner -> return@coroutineScope HasilBandingkanBerkas.BinerDitolak(
                namaB,
                "Berkas '$namaB' terdeteksi sebagai file biner: ${hasilB.alasan}"
            )
            is HasilBukaBerkasTeks.TerlaluBesar -> return@coroutineScope HasilBandingkanBerkas.TerlaluBesar(
                namaB,
                hasilB.ukuranByte,
                hasilB.batasMaksimalByte
            )
            is HasilBukaBerkasTeks.Gagal -> return@coroutineScope HasilBandingkanBerkas.Gagal(
                "Gagal membuka '$namaB': ${hasilB.pesan}",
                hasilB.penyebab
            )
        }

        val diff = hitungDiffDariMemori(dataA.isi, dataB.isi, opsi)
        HasilBandingkanBerkas.Sukses(
            teksA = dataA.isi,
            teksB = dataB.isi,
            sidikA = dataA.sidikIsiByte,
            sidikB = dataB.sidikIsiByte,
            hasilDiff = diff
        )
    }

    suspend fun hitungDiffDariMemori(
        teksA: String,
        teksB: String,
        opsi: OpsiDiff
    ): HasilDiffLengkap = withContext(computeDispatcher) {
        val ctx = currentCoroutineContext()
        ctx.ensureActive()
        AlgoritmaMyers.bandingkanTeks(
            teksA = teksA,
            teksB = teksB,
            opsi = opsi,
            cekBatal = { ctx.ensureActive() }
        )
    }

    suspend fun eksporPatch(
        namaA: String,
        namaB: String,
        teksA: String,
        teksB: String,
        opsi: OpsiDiff
    ): String = withContext(computeDispatcher) {
        currentCoroutineContext().ensureActive()
        AlgoritmaMyers.bentukUnifiedPatch(namaA, namaB, teksA, teksB, opsi)
    }
}