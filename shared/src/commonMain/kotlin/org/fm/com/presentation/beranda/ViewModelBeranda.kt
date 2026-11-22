package org.fm.com.presentation.beranda

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.usecase.BerandaUseCase
import org.fm.com.domain.usecase.HasilResolusiBeranda
import org.fm.com.presentation.komponen.PemformatLokasiRamah

@Immutable
data class StateBeranda(
    val lokasiBawaan: LokasiBerkas,
    val lokasiKhusus: LokasiBerkas? = null,
    val lokasiTerselesaikan: LokasiBerkas = lokasiBawaan,
    val peringatanIzinAtauHilang: String? = null,
    val sedangMemuat: Boolean = false,
    val pesanInfo: String? = null
) {
    val memakaiLokasiKhusus: Boolean
        get() = lokasiKhusus != null

    val lokasiAktifUntukTampil: LokasiBerkas
        get() = lokasiKhusus ?: lokasiBawaan

    val ringkasanLokasiAktif: String
        get() = PemformatLokasiRamah.formatRingkas(lokasiAktifUntukTampil)
}

sealed interface IntentBeranda {
    data class TetapkanBeranda(val lokasi: LokasiBerkas) : IntentBeranda
    data object ResetKeBawaan : IntentBeranda
    data object SegarkanResolusi : IntentBeranda
    data class SetPesanInfo(val pesan: String?) : IntentBeranda
}

class ViewModelBeranda(
    private val berandaUseCase: BerandaUseCase,
    private val siapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(StateBeranda(lokasiBawaan = berandaUseCase.lokasiBawaan))
    val state: StateFlow<StateBeranda> = _state.asStateFlow()

    private var pekerjaanAmati: Job? = null

    init {
        amatiKonfigurasi()
    }

    fun prosesIntent(intent: IntentBeranda) {
        when (intent) {
            is IntentBeranda.TetapkanBeranda -> tetapkanBeranda(intent.lokasi)
            is IntentBeranda.ResetKeBawaan -> resetKeBawaan()
            is IntentBeranda.SegarkanResolusi -> periksaResolusi()
            is IntentBeranda.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
        }
    }

    private fun amatiKonfigurasi() {
        pekerjaanAmati?.cancel()
        pekerjaanAmati = viewModelScope.launch {
            berandaUseCase.amatiKonfigurasi()
                .catch { /* Abaikan galat sementara */ }
                .collect { konfig ->
                    _state.update { it.copy(lokasiKhusus = konfig.lokasiKhusus) }
                    periksaResolusi()
                }
        }
    }

    private fun periksaResolusi() {
        viewModelScope.launch {
            when (val res = berandaUseCase.selesaikanLokasiAwal(siapkanAksesLokasi)) {
                is HasilResolusiBeranda.GunakanBawaan -> _state.update {
                    it.copy(
                        lokasiTerselesaikan = res.lokasiDigunakan,
                        peringatanIzinAtauHilang = null
                    )
                }
                is HasilResolusiBeranda.GunakanKhusus -> _state.update {
                    it.copy(
                        lokasiTerselesaikan = res.lokasiDigunakan,
                        peringatanIzinAtauHilang = null
                    )
                }
                is HasilResolusiBeranda.IzinAtauLokasiHilang -> _state.update {
                    it.copy(
                        lokasiTerselesaikan = res.lokasiDigunakan,
                        peringatanIzinAtauHilang = "Akses ke folder beranda tidak tersedia (${res.pesan}). Memakai folder bawaan."
                    )
                }
            }
        }
    }

    private fun tetapkanBeranda(lokasi: LokasiBerkas) {
        viewModelScope.launch {
            _state.update { it.copy(sedangMemuat = true) }
            val lokasiSiap = runCatching { siapkanAksesLokasi?.invoke(lokasi) ?: lokasi }.getOrDefault(lokasi)
            when (val hasil = berandaUseCase.tetapkanBeranda(lokasiSiap)) {
                is HasilOperasi.Sukses -> _state.update {
                    it.copy(
                        sedangMemuat = false,
                        lokasiKhusus = hasil.nilai,
                        lokasiTerselesaikan = hasil.nilai,
                        peringatanIzinAtauHilang = null,
                        pesanInfo = "Beranda ditetapkan ke '${hasil.nilai.label ?: PemformatLokasiRamah.formatRingkas(hasil.nilai)}'"
                    )
                }
                is HasilOperasi.Gagal -> _state.update {
                    it.copy(
                        sedangMemuat = false,
                        pesanInfo = "Gagal menetapkan beranda: ${hasil.pesan}"
                    )
                }
            }
        }
    }

    private fun resetKeBawaan() {
        viewModelScope.launch {
            _state.update { it.copy(sedangMemuat = true) }
            when (val hasil = berandaUseCase.resetKeBawaan()) {
                is HasilOperasi.Sukses -> _state.update {
                    it.copy(
                        sedangMemuat = false,
                        lokasiKhusus = null,
                        lokasiTerselesaikan = hasil.nilai,
                        peringatanIzinAtauHilang = null,
                        pesanInfo = "Beranda dikembalikan ke bawaan"
                    )
                }
                is HasilOperasi.Gagal -> _state.update {
                    it.copy(
                        sedangMemuat = false,
                        pesanInfo = hasil.pesan
                    )
                }
            }
        }
    }

    override fun onCleared() {
        pekerjaanAmati?.cancel()
    }
}