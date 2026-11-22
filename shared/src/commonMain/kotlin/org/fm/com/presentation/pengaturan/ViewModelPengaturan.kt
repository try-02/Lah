package org.fm.com.presentation.pengaturan

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
import org.fm.com.domain.usecase.PreferensiUseCase
import org.fm.com.domain.usecase.RiwayatLokasiUseCase
import org.fm.com.domain.usecase.RiwayatPencarianUseCase

@Immutable
data class StatePengaturan(
    val wordWrapUmum: Boolean = true,
    val jumlahRiwayatPencarian: Int = 0,
    val jumlahRiwayatLokasi: Int = 0,
    val dialogKonfirmasiHapusRiwayatPencarian: Boolean = false,
    val dialogKonfirmasiHapusRiwayatLokasi: Boolean = false,
    val pesanInfo: String? = null
)

sealed interface IntentPengaturan {
    data class SetWordWrapUmum(val aktif: Boolean) : IntentPengaturan
    data class SetDialogHapusRiwayatPencarian(val buka: Boolean) : IntentPengaturan
    data object KonfirmasiHapusRiwayatPencarian : IntentPengaturan
    data class SetDialogHapusRiwayatLokasi(val buka: Boolean) : IntentPengaturan
    data object KonfirmasiHapusRiwayatLokasi : IntentPengaturan
    data class SetPesanInfo(val pesan: String?) : IntentPengaturan
}

class ViewModelPengaturan(
    private val preferensiUseCase: PreferensiUseCase,
    private val riwayatPencarianUseCase: RiwayatPencarianUseCase,
    private val riwayatLokasiUseCase: RiwayatLokasiUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StatePengaturan())
    val state: StateFlow<StatePengaturan> = _state.asStateFlow()

    private var pekerjaanPreferensi: Job? = null
    private var pekerjaanRiwayatCari: Job? = null
    private var pekerjaanRiwayatLokasi: Job? = null

    init {
        amatiSemua()
    }

    fun prosesIntent(intent: IntentPengaturan) {
        when (intent) {
            is IntentPengaturan.SetWordWrapUmum -> simpanWordWrapUmum(intent.aktif)
            is IntentPengaturan.SetDialogHapusRiwayatPencarian -> _state.update {
                it.copy(dialogKonfirmasiHapusRiwayatPencarian = intent.buka)
            }
            is IntentPengaturan.KonfirmasiHapusRiwayatPencarian -> bersihkanRiwayatPencarian()
            is IntentPengaturan.SetDialogHapusRiwayatLokasi -> _state.update {
                it.copy(dialogKonfirmasiHapusRiwayatLokasi = intent.buka)
            }
            is IntentPengaturan.KonfirmasiHapusRiwayatLokasi -> bersihkanRiwayatLokasi()
            is IntentPengaturan.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
        }
    }

    private fun amatiSemua() {
        pekerjaanPreferensi?.cancel()
        pekerjaanPreferensi = viewModelScope.launch {
            preferensiUseCase.amatiPreferensi()
                .catch { /* Abaikan galat aliran */ }
                .collect { pref ->
                    _state.update { it.copy(wordWrapUmum = pref.wordWrapUmum) }
                }
        }

        pekerjaanRiwayatCari?.cancel()
        pekerjaanRiwayatCari = viewModelScope.launch {
            riwayatPencarianUseCase.ambilRiwayat(batas = 100)
                .catch { /* Abaikan */ }
                .collect { daftar ->
                    _state.update { it.copy(jumlahRiwayatPencarian = daftar.size) }
                }
        }

        pekerjaanRiwayatLokasi?.cancel()
        pekerjaanRiwayatLokasi = viewModelScope.launch {
            riwayatLokasiUseCase.ambilRiwayat(batas = 100)
                .catch { /* Abaikan */ }
                .collect { daftar ->
                    _state.update { it.copy(jumlahRiwayatLokasi = daftar.size) }
                }
        }
    }

    private fun simpanWordWrapUmum(aktif: Boolean) {
        _state.update { it.copy(wordWrapUmum = aktif) }
        viewModelScope.launch {
            when (val res = preferensiUseCase.setWordWrapUmum(aktif)) {
                is HasilOperasi.Sukses -> Unit
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanInfo = res.pesan) }
            }
        }
    }

    private fun bersihkanRiwayatPencarian() {
        _state.update { it.copy(dialogKonfirmasiHapusRiwayatPencarian = false) }
        viewModelScope.launch {
            when (val res = riwayatPencarianUseCase.bersihkanSemua()) {
                is HasilOperasi.Sukses -> _state.update {
                    it.copy(pesanInfo = "Riwayat pencarian lokal dihapus")
                }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanInfo = res.pesan) }
            }
        }
    }

    private fun bersihkanRiwayatLokasi() {
        _state.update { it.copy(dialogKonfirmasiHapusRiwayatLokasi = false) }
        viewModelScope.launch {
            when (val res = riwayatLokasiUseCase.bersihkanSemuaRiwayat()) {
                is HasilOperasi.Sukses -> _state.update {
                    it.copy(pesanInfo = "Riwayat lokasi terbaru dihapus")
                }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanInfo = res.pesan) }
            }
        }
    }

    override fun onCleared() {
        pekerjaanPreferensi?.cancel()
        pekerjaanRiwayatCari?.cancel()
        pekerjaanRiwayatLokasi?.cancel()
    }
}