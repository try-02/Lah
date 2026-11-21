package org.fm.com.presentation.diff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.diff.ModeWhitespaceDiff
import org.fm.com.domain.diff.OpsiDiff
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.BandingkanBerkasUseCase
import org.fm.com.domain.usecase.HasilBandingkanBerkas

class ViewModelDiff(
    private val bandingkanUseCase: BandingkanBerkasUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(StateDiff())
    val state: StateFlow<StateDiff> = _state.asStateFlow()

    private var pekerjaanDiff: Job? = null
    private var teksCacheA: String? = null
    private var teksCacheB: String? = null
    private var urutanNav = 0L

    fun prosesIntent(intent: IntentDiff) {
        when (intent) {
            is IntentDiff.Bandingkan -> mulaiBandingkanDariDisk(
                lokasiA = intent.lokasiA,
                namaA = intent.namaA,
                lokasiB = intent.lokasiB,
                namaB = intent.namaB
            )
            is IntentDiff.SegarkanDariDisk -> segarkanDariDisk()
            is IntentDiff.BatalkanProses -> batalkanProses()
            is IntentDiff.UbahModeTampilan -> _state.update { it.copy(modeTampilan = intent.mode) }
            is IntentDiff.TogglePekaHuruf -> perbaruiOpsiDanHitungUlang { it.copy(pekaHuruf = !it.pekaHuruf) }
            is IntentDiff.UbahModeWhitespace -> perbaruiOpsiDanHitungUlang { it.copy(modeWhitespace = intent.mode) }
            is IntentDiff.ToggleTampilkanBarisSama -> perbaruiOpsiDanHitungUlang {
                it.copy(tampilkanBarisSama = !it.tampilkanBarisSama)
            }
            is IntentDiff.UbahJumlahBarisKonteks -> perbaruiOpsiDanHitungUlang {
                it.copy(jumlahBarisKonteks = intent.jumlah.coerceAtLeast(0))
            }
            is IntentDiff.SetBukaDialogOpsi -> _state.update { it.copy(dialogOpsiTerbuka = intent.buka) }
            is IntentDiff.LompatPerubahanBerikutnya -> lompatPerubahan(maju = true)
            is IntentDiff.LompatPerubahanSebelumnya -> lompatPerubahan(maju = false)
            is IntentDiff.LompatBarisBedaPertama -> lompatBarisBedaPertama()
            is IntentDiff.SiapkanEksporPatch -> siapkanEksporPatch()
            is IntentDiff.HapusPatchSiapSalin -> _state.update { it.copy(patchUntukDisalin = null) }
            is IntentDiff.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
        }
    }

    private fun mulaiBandingkanDariDisk(
        lokasiA: LokasiBerkas,
        namaA: String,
        lokasiB: LokasiBerkas,
        namaB: String
    ) {
        pekerjaanDiff?.cancel()
        teksCacheA = null
        teksCacheB = null
        val opsiSaatIni = _state.value.opsi
        _state.update {
            it.copy(
                berkasA = InfoBerkasDiff(lokasiA, namaA),
                berkasB = InfoBerkasDiff(lokasiB, namaB),
                sedangMemuat = true,
                sedangMenghitung = true,
                barisBerdampingan = emptyList(),
                barisUnified = emptyList(),
                daftarPerubahan = emptyList(),
                indeksPerubahanAktif = -1,
                statistik = null,
                sidikA = null,
                sidikB = null,
                pesanGalat = null
            )
        }

        val job = viewModelScope.launch {
            when (val hasil = bandingkanUseCase.muatDanBandingkan(lokasiA, namaA, lokasiB, namaB, opsiSaatIni)) {
                is HasilBandingkanBerkas.Sukses -> {
                    teksCacheA = hasil.teksA
                    teksCacheB = hasil.teksB
                    val diff = hasil.hasilDiff
                    val idxAwal = if (diff.daftarPerubahan.isNotEmpty()) 0 else -1
                    _state.update {
                        it.copy(
                            sedangMemuat = false,
                            sedangMenghitung = false,
                            barisBerdampingan = diff.barisBerdampingan,
                            barisUnified = diff.barisUnified,
                            daftarPerubahan = diff.daftarPerubahan,
                            indeksPerubahanAktif = idxAwal,
                            statistik = diff.statistik,
                            sidikA = hasil.sidikA,
                            sidikB = hasil.sidikB,
                            terpotongBatasKompleksitas = diff.terpotongBatasKompleksitas,
                            pesanGalat = null
                        )
                    }
                }
                is HasilBandingkanBerkas.BinerDitolak -> {
                    _state.update {
                        it.copy(
                            sedangMemuat = false,
                            sedangMenghitung = false,
                            pesanGalat = hasil.alasan
                        )
                    }
                }
                is HasilBandingkanBerkas.TerlaluBesar -> {
                    _state.update {
                        it.copy(
                            sedangMemuat = false,
                            sedangMenghitung = false,
                            pesanGalat = "Berkas '${hasil.namaBerkas}' melebihi batas ukuran perbandingan (${hasil.batasMaksimalByte / (1024 * 1024)} MiB)"
                        )
                    }
                }
                is HasilBandingkanBerkas.Gagal -> {
                    _state.update {
                        it.copy(
                            sedangMemuat = false,
                            sedangMenghitung = false,
                            pesanGalat = hasil.pesan
                        )
                    }
                }
            }
        }
        pekerjaanDiff = job
        job.invokeOnCompletion {
            if (pekerjaanDiff === job) pekerjaanDiff = null
        }
    }

    private fun segarkanDariDisk() {
        val a = _state.value.berkasA ?: return
        val b = _state.value.berkasB ?: return
        mulaiBandingkanDariDisk(a.lokasi, a.nama, b.lokasi, b.nama)
    }

    private fun perbaruiOpsiDanHitungUlang(transformasi: (OpsiDiff) -> OpsiDiff) {
        val opsiBaru = transformasi(_state.value.opsi)
        if (opsiBaru == _state.value.opsi) return
        _state.update { it.copy(opsi = opsiBaru) }

        val a = teksCacheA
        val b = teksCacheB
        if (a == null || b == null) {
            segarkanDariDisk()
            return
        }

        pekerjaanDiff?.cancel()
        _state.update { it.copy(sedangMenghitung = true) }
        val job = viewModelScope.launch {
            val diff = bandingkanUseCase.hitungDiffDariMemori(a, b, opsiBaru)
            val idxAwal = if (diff.daftarPerubahan.isNotEmpty()) 0 else -1
            _state.update {
                it.copy(
                    sedangMenghitung = false,
                    barisBerdampingan = diff.barisBerdampingan,
                    barisUnified = diff.barisUnified,
                    daftarPerubahan = diff.daftarPerubahan,
                    indeksPerubahanAktif = idxAwal,
                    statistik = diff.statistik,
                    terpotongBatasKompleksitas = diff.terpotongBatasKompleksitas
                )
            }
        }
        pekerjaanDiff = job
        job.invokeOnCompletion {
            if (pekerjaanDiff === job) pekerjaanDiff = null
        }
    }

    private fun batalkanProses() {
        pekerjaanDiff?.cancel()
        pekerjaanDiff = null
        _state.update {
            it.copy(
                sedangMemuat = false,
                sedangMenghitung = false,
                pesanInfo = "Perbandingan dibatalkan"
            )
        }
    }

    private fun lompatPerubahan(maju: Boolean) {
        val daftar = _state.value.daftarPerubahan
        if (daftar.isEmpty()) return
        val saatIni = _state.value.indeksPerubahanAktif
        val indeksBaru = if (maju) {
            if (saatIni < 0) 0 else (saatIni + 1) % daftar.size
        } else {
            if (saatIni <= 0) daftar.lastIndex else saatIni - 1
        }
        _state.update {
            it.copy(
                indeksPerubahanAktif = indeksBaru,
                urutanNavigasi = ++urutanNav
            )
        }
    }

    private fun lompatBarisBedaPertama() {
        val daftar = _state.value.daftarPerubahan
        if (daftar.isEmpty()) return
        _state.update {
            it.copy(
                indeksPerubahanAktif = 0,
                urutanNavigasi = ++urutanNav
            )
        }
    }

    private fun siapkanEksporPatch() {
        val st = _state.value
        val a = teksCacheA ?: return
        val b = teksCacheB ?: return
        val namaA = st.berkasA?.nama ?: "a"
        val namaB = st.berkasB?.nama ?: "b"
        viewModelScope.launch {
            val patch = bandingkanUseCase.eksporPatch(namaA, namaB, a, b, st.opsi)
            _state.update {
                it.copy(
                    patchUntukDisalin = patch,
                    pesanInfo = "Patch diff disalin ke clipboard"
                )
            }
        }
    }

    override fun onCleared() {
        pekerjaanDiff?.cancel()
        super.onCleared()
    }
}