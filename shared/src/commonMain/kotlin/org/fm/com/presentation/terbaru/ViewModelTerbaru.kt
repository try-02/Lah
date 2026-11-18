package org.fm.com.presentation.terbaru

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.RiwayatLokasiUseCase

class ViewModelTerbaru(
    private val riwayatLokasiUseCase: RiwayatLokasiUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StateTerbaru(sedangMemuat = true))
    val state: StateFlow<StateTerbaru> = _state.asStateFlow()

    private var pekerjaanMuat: Job? = null

    init {
        muatRiwayat()
    }

    fun prosesIntent(intent: IntentTerbaru) {
        when (intent) {
            is IntentTerbaru.Segarkan -> muatRiwayat()
            is IntentTerbaru.HapusItem -> hapusItem(intent.lokasi)
            is IntentTerbaru.SetBukaDialogHapusSemua -> _state.update { it.copy(dialogHapusSemuaTerbuka = intent.buka) }
            is IntentTerbaru.KonfirmasiHapusSemua -> hapusSemua()
            is IntentTerbaru.HapusPesanGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun muatRiwayat() {
        pekerjaanMuat?.cancel()
        pekerjaanMuat = viewModelScope.launch {
            _state.update { it.copy(sedangMemuat = true) }
            riwayatLokasiUseCase.ambilRiwayat().collect { list ->
                val uiList = list.map { it.keUi() }
                _state.update {
                    it.copy(
                        daftarItem = uiList,
                        sedangMemuat = false
                    )
                }
            }
        }
    }

    private fun hapusItem(lokasi: LokasiBerkas) {
        viewModelScope.launch {
            when (val hasil = riwayatLokasiUseCase.hapusRiwayat(lokasi)) {
                is HasilOperasi.Sukses -> {
                    _state.update { st ->
                        st.copy(daftarItem = st.daftarItem.filterNot { it.lokasi == lokasi })
                    }
                }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanGalat = hasil.pesan) }
            }
        }
    }

    private fun hapusSemua() {
        viewModelScope.launch {
            _state.update { it.copy(dialogHapusSemuaTerbuka = false) }
            when (val hasil = riwayatLokasiUseCase.bersihkanSemuaRiwayat()) {
                is HasilOperasi.Sukses -> {
                    _state.update { it.copy(daftarItem = emptyList()) }
                }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanGalat = hasil.pesan) }
            }
        }
    }
}