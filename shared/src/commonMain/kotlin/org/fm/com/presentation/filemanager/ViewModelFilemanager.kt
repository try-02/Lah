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
import org.fm.com.domain.usecase.FilterBerkasUseCase
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.domain.usecase.TelusuriLokasiUseCase
import org.fm.com.domain.usecase.UrutkanBerkasUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi.keUi

class ViewModelFilemanager(
    private val repositoriBerkas: RepositoriBerkas,
    private val daftarFolderUseCase: DaftarFolderUseCase = DaftarFolderUseCase(repositoriBerkas),
    private val urutkanBerkasUseCase: UrutkanBerkasUseCase = UrutkanBerkasUseCase(),
    private val filterBerkasUseCase: FilterBerkasUseCase = FilterBerkasUseCase(),
    private val telusuriLokasiUseCase: TelusuriLokasiUseCase = TelusuriLokasiUseCase()
) : ViewModel() {

    private val _state = MutableStateFlow(StateFilemanager())
    val state: StateFlow<StateFilemanager> = _state.asStateFlow()

    private var pekerjaanMuatFolder: Job? = null
    private var daftarEntitasMentah: List<EntitasBerkas> = emptyList()
    private val riwayatNavigasi = mutableListOf<LokasiBerkas>()

    fun prosesIntent(intent: IntentFilemanager) {
        when (intent) {
            is IntentFilemanager.BukaLokasi -> navigasiMasuk(intent.lokasi)
            is IntentFilemanager.NavigasiKeSegmen -> navigasiKeSegmen(intent.segmen)
            is IntentFilemanager.NavigasiKembali -> navigasiKembali()
            is IntentFilemanager.Segarkan -> segarkan()
            is IntentFilemanager.UbahKataKunciSaring -> ubahKataKunciSaring(intent.kataKunci)
            is IntentFilemanager.UbahPengurutan -> ubahPengurutan(intent.kriteria, intent.arah)
            is IntentFilemanager.ToggleFolderDiAtas -> toggleFolderDiAtas()
            is IntentFilemanager.ToggleFileTersembunyi -> toggleFileTersembunyi()
            is IntentFilemanager.UbahModeTampilan -> ubahModeTampilan(intent.mode)
            is IntentFilemanager.TogglePilihItem -> togglePilihItem(intent.lokasi)
            is IntentFilemanager.PilihSemua -> pilihSemua()
            is IntentFilemanager.BersihkanPilihan -> bersihkanPilihan()
            is IntentFilemanager.SetBukaDialogUrutkan -> _state.update { it.copy(dialogUrutkanTerbuka = intent.buka) }
            is IntentFilemanager.HapusPesanGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun navigasiMasuk(lokasi: LokasiBerkas) {
        if (riwayatNavigasi.isEmpty() || riwayatNavigasi.last() != lokasi) {
            riwayatNavigasi.add(lokasi)
        }
        muatFolderInternal(lokasi)
    }

    private fun navigasiKeSegmen(segmen: SegmenLokasi) {
        val idx = riwayatNavigasi.indexOfFirst { it == segmen.lokasi }
        if (idx >= 0) {
            while (riwayatNavigasi.size > idx + 1) {
                riwayatNavigasi.removeAt(riwayatNavigasi.lastIndex)
            }
        } else {
            riwayatNavigasi.clear()
            riwayatNavigasi.add(segmen.lokasi)
        }
        muatFolderInternal(segmen.lokasi)
    }

    private fun navigasiKembali() {
        if (riwayatNavigasi.size > 1) {
            riwayatNavigasi.removeAt(riwayatNavigasi.lastIndex)
            val tujuan = riwayatNavigasi.last()
            muatFolderInternal(tujuan)
        }
    }

    private fun muatFolderInternal(lokasi: LokasiBerkas) {
        pekerjaanMuatFolder?.cancel()
        val kemampuan = repositoriBerkas.kapabilitas(lokasi)
        val breadcrumb = telusuriLokasiUseCase.buatSegmenBreadcrumb(lokasi, riwayatNavigasi)

        _state.update {
            it.copy(
                lokasiSaatIni = lokasi,
                segmenBreadcrumb = breadcrumb,
                sedangMemuat = true,
                pesanGalat = null,
                itemTerpilih = emptySet(),
                kemampuanLokasi = kemampuan,
                dapatNavigasiKembali = riwayatNavigasi.size > 1
            )
        }

        pekerjaanMuatFolder = viewModelScope.launch {
            daftarFolderUseCase(lokasi, tampilkanTersembunyi = true).collect { hasil ->
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
        muatFolderInternal(lokasi)
    }

    private fun ubahKataKunciSaring(kataKunci: String) {
        _state.update { it.copy(kataKunciSaring = kataKunci) }
        perbaruiDaftarTampilan()
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
        perbaruiDaftarTampilan()
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
        val tersaring = filterBerkasUseCase(
            daftar = daftarEntitasMentah,
            tampilkanTersembunyi = st.tampilkanTersembunyi,
            kataKunci = st.kataKunciSaring
        )
        val terurut = urutkanBerkasUseCase(
            daftar = tersaring,
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