package org.fm.com.presentation.diff

import androidx.compose.runtime.Immutable
import org.fm.com.domain.diff.BarisDiffBerdampingan
import org.fm.com.domain.diff.BarisDiffUnified
import org.fm.com.domain.diff.KelompokPerubahanDiff
import org.fm.com.domain.diff.ModeWhitespaceDiff
import org.fm.com.domain.diff.OpsiDiff
import org.fm.com.domain.diff.StatistikDiff
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas

enum class ModeTampilanDiff {
    BERDAMPINGAN,
    UNIFIED
}

data class InfoBerkasDiff(
    val lokasi: LokasiBerkas,
    val nama: String
)

@Immutable
data class StateDiff(
    val berkasA: InfoBerkasDiff? = null,
    val berkasB: InfoBerkasDiff? = null,
    val modeTampilan: ModeTampilanDiff = ModeTampilanDiff.BERDAMPINGAN,
    val opsi: OpsiDiff = OpsiDiff(),
    val sedangMemuat: Boolean = false,
    val sedangMenghitung: Boolean = false,
    val barisBerdampingan: List<BarisDiffBerdampingan> = emptyList(),
    val barisUnified: List<BarisDiffUnified> = emptyList(),
    val daftarPerubahan: List<KelompokPerubahanDiff> = emptyList(),
    val indeksPerubahanAktif: Int = -1,
    val statistik: StatistikDiff? = null,
    val sidikA: SidikIsiBerkas? = null,
    val sidikB: SidikIsiBerkas? = null,
    val terpotongBatasKompleksitas: Boolean = false,
    val urutanNavigasi: Long = 0L,
    val dialogOpsiTerbuka: Boolean = false,
    val patchUntukDisalin: String? = null,
    val pesanGalat: String? = null,
    val pesanInfo: String? = null
) {
    val adaPerubahan: Boolean
        get() = daftarPerubahan.isNotEmpty()

    val perubahanAktif: KelompokPerubahanDiff?
        get() = daftarPerubahan.getOrNull(indeksPerubahanAktif)

    val pesanPengabaianAktif: String?
        get() = buildList {
            if (!opsi.pekaHuruf) add("huruf besar/kecil diabaikan")
            when (opsi.modeWhitespace) {
                ModeWhitespaceDiff.PERSIS -> Unit
                ModeWhitespaceDiff.ABAIKAN_TEPI -> add("spasi awal/akhir baris diabaikan")
                ModeWhitespaceDiff.ABAIKAN_SEMUA -> add("seluruh whitespace diabaikan")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

sealed interface IntentDiff {
    data class Bandingkan(
        val lokasiA: LokasiBerkas,
        val namaA: String,
        val lokasiB: LokasiBerkas,
        val namaB: String
    ) : IntentDiff

    data object SegarkanDariDisk : IntentDiff
    data object BatalkanProses : IntentDiff
    data class UbahModeTampilan(val mode: ModeTampilanDiff) : IntentDiff
    data object TogglePekaHuruf : IntentDiff
    data class UbahModeWhitespace(val mode: ModeWhitespaceDiff) : IntentDiff
    data object ToggleTampilkanBarisSama : IntentDiff
    data class UbahJumlahBarisKonteks(val jumlah: Int) : IntentDiff
    data class SetBukaDialogOpsi(val buka: Boolean) : IntentDiff
    data object LompatPerubahanBerikutnya : IntentDiff
    data object LompatPerubahanSebelumnya : IntentDiff
    data object LompatBarisBedaPertama : IntentDiff
    data object SiapkanEksporPatch : IntentDiff
    data object HapusPatchSiapSalin : IntentDiff
    data class SetPesanInfo(val pesan: String?) : IntentDiff
}