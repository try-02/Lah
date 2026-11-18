package org.fm.com.presentation.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.DaftarFolderUseCase
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.UrutkanBerkasUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi.keUi

/**
 * ViewModel untuk layar file manager mengikuti arsitektur UDF (Unidirectional Data Flow).
 */
class ViewModelFilemanager(
    private val repositoriBerkas: RepositoriBerkas,
    private val daftarFolderUseCase: DaftarFolderUseCase = DaftarFolderUseCase(repositoriBerkas),
    private val urutkanBerkasUseCase: UrutkanBerkasUseCase = UrutkanBerkasUseCase()
) : ViewModel() {

    private val _state = MutableStateFlow(StateFilemanager())
    val state: StateFlow<StateFilemanager> = _state.asStateFlow()

    private var pekerjaanMuatFolder: Job? = null
    private var daftarEntitasMentah: List<EntitasBerkas> = emptyList()

    fun prosesIntent(intent: IntentFilemanager) {
        when (intent) {
            is IntentFilemanager.BukaLokasi -> bukaLokasi(intent.lokasi)
            is IntentFilemanager.Segarkan -> segarkan()
            is IntentFilemanager.UbahPengurutan -> ubahPengurutan(intent.kriteria, intent.arah)
            is IntentFilemanager.ToggleFolderDiAtas -> toggleFolderDiAtas()
            is IntentFilemanager.ToggleFileTersembunyi -> toggleFileTersembunyi()
            is IntentFilemanager.UbahModeTampilan -> ubahModeTampilan(intent.mode)
            is IntentFilemanager.TogglePilihItem -> togglePilihItem(intent.lokasi)
            is IntentFilemanager.PilihSemua -> pilihSemua()
            is IntentFilemanager.BersihkanPilihan -> bersihkanPilihan()
            is IntentFilemanager.HapusPesanGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun bukaLokasi(lokasi: LokasiBerkas) {
        pekerjaanMuatFolder?.cancel()
        val kemampuan = repositoriBerkas.kapabilitas(lokasi)

        _state.update {
            it.copy(
                lokasiSaatIni = lokasi,
                sedangMemuat = true,
                pesanGalat = null,
                itemTerpilih = emptySet(),
                kemampuanLokasi = kemampuan
            )
        }

        pekerjaanMuatFolder = viewModelScope.launch {
            daftarFolderUseCase(lokasi, _state.value.tampilkanTersembunyi).collect { hasil ->
                when (hasil) {
                    is HasilOperasi.Sukses -> {
                        daftarEntitasMentah = hasil.nilai
                        perbaruiDaftarTampilan()
                    }
                    is HasilOperasi.Gagal -> {
                        daftarEntitasMentah = emptyList()
                        _state.update {
                            it.copy(
                                daftarItem = emptyList(),
                                sedangMemuat = false,
                                pesanGalat = hasil.pesan
                            )
                        }
                    }
                }
            }
        }
    }

    private fun segarkan() {
        val lokasi = _state.value.lokasiSaatIni ?: return
        bukaLokasi(lokasi)
    }

    private fun ubahPengurutan(kriteria: KriteriaUrutan, arahDiminta: ArahUrutan?) {
        val arahBaru = arahDiminta ?: run {
            if (_state.value.kriteriaUrutan == kriteria) {
                if (_state.value.arahUrutan == ArahUrutan.NAIK) ArahUrutan.TURUN else ArahUrutan.NAIK
            } else {
                ArahUrutan.NAIK
            }
        }
        _state.update { it.copy(kriteriaUrutan = kriteria, arahUrutan = arahBaru) }
        perbaruiDaftarTampilan()
    }

    private fun toggleFolderDiAtas() {
        _state.update { it.copy(folderDiAtas = !it.folderDiAtas) }
        perbaruiDaftarTampilan()
    }

    private fun toggleFileTersembunyi() {
        val baru = !_state.value.tampilkanTersembunyi
        _state.update { it.copy(tampilkanTersembunyi = baru) }
        val lokasi = _state.value.lokasiSaatIni ?: return
        bukaLokasi(lokasi)
    }

    private fun ubahModeTampilan(mode: ModeTampilan) {
        _state.update { it.copy(modeTampilan = mode) }
    }

    private fun togglePilihItem(lokasi: LokasiBerkas) {
        _state.update { st ->
            val setBaru = if (lokasi in st.itemTerpilih) {
                st.itemTerpilih - lokasi
            } else {
                st.itemTerpilih + lokasi
            }
            st.copy(itemTerpilih = setBaru)
        }
    }

    private fun pilihSemua() {
        _state.update { st ->
            val semua = st.daftarItem.map { it.lokasi }.toSet()
            st.copy(itemTerpilih = semua)
        }
    }

    private fun bersihkanPilihan() {
        _state.update { it.copy(itemTerpilih = emptySet()) }
    }

    private fun perbaruiDaftarTampilan() {
        val st = _state.value
        val terurut = urutkanBerkasUseCase(
            daftar = daftarEntitasMentah,
            kriteria = st.kriteriaUrutan,
            arah = st.arahUrutan,
            folderDiAtas = st.folderDiAtas
        )
        val uiList = terurut.map { it.keUi() }
        _state.update {
            it.copy(
                daftarItem = uiList,
                sedangMemuat = false
            )
        }
    }
}