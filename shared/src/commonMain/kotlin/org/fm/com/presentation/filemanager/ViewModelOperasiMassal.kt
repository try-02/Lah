package org.fm.com.presentation.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.HasilMassal
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.usecase.SalinPindahkanBerkasUseCase

data class StateOperasiMassal(
    val sedangBerjalan: Boolean = false,
    val apakahPindah: Boolean = false,
    val totalItem: Int = 0,
    val itemDiproses: Int = 0,
    val namaItemSaatIni: String = "",
    val daftarSumber: List<LokasiBerkas> = emptyList(),
    val sedangMemilihTujuan: Boolean = false,
    val dialogPilihTujuanTerbuka: Boolean = false,
    val hasilTerakhir: HasilMassal? = null,
    val tampilkanDialogHasil: Boolean = false
) {
    val progresFloat: Float
        get() = if (totalItem > 0) itemDiproses.toFloat() / totalItem.toFloat() else 0f
}

class ViewModelOperasiMassal(
    repositoriBerkas: RepositoriBerkas,
    repositoriOperasi: RepositoriOperasiBerkas,
    private val salinPindahkanUseCase: SalinPindahkanBerkasUseCase = SalinPindahkanBerkasUseCase(
        repositoriBerkas,
        repositoriOperasi
    )
) : ViewModel() {
    private val _state = MutableStateFlow(StateOperasiMassal())
    val state: StateFlow<StateOperasiMassal> = _state.asStateFlow()
    private var pekerjaanOperasi: Job? = null

    fun inisiasiSalin(sumber: List<LokasiBerkas>) {
        if (sumber.isEmpty()) return
        _state.update {
            it.copy(
                daftarSumber = sumber,
                apakahPindah = false,
                sedangMemilihTujuan = true,
                dialogPilihTujuanTerbuka = false,
                hasilTerakhir = null,
                tampilkanDialogHasil = false
            )
        }
    }

    fun inisiasiPindah(sumber: List<LokasiBerkas>) {
        if (sumber.isEmpty()) return
        _state.update {
            it.copy(
                daftarSumber = sumber,
                apakahPindah = true,
                sedangMemilihTujuan = true,
                dialogPilihTujuanTerbuka = false,
                hasilTerakhir = null,
                tampilkanDialogHasil = false
            )
        }
    }

    fun bukaKonfirmasiTempel() {
        if (_state.value.daftarSumber.isEmpty()) return
        _state.update { it.copy(dialogPilihTujuanTerbuka = true) }
    }

    fun batalModeTempel() {
        _state.update {
            it.copy(
                sedangMemilihTujuan = false,
                dialogPilihTujuanTerbuka = false,
                daftarSumber = emptyList()
            )
        }
    }

    fun tutupDialogTujuan() {
        _state.update { it.copy(dialogPilihTujuanTerbuka = false) }
    }

    fun tutupDialogHasil() {
        _state.update { it.copy(tampilkanDialogHasil = false, hasilTerakhir = null) }
    }

    fun eksekusiKeTujuan(tujuan: LokasiBerkas, onSelesai: (HasilMassal?) -> Unit) {
        val sumber = _state.value.daftarSumber
        val pindah = _state.value.apakahPindah
        if (sumber.isEmpty()) return
        _state.update {
            it.copy(
                sedangMemilihTujuan = false,
                dialogPilihTujuanTerbuka = false,
                sedangBerjalan = true,
                totalItem = sumber.size,
                itemDiproses = 0,
                namaItemSaatIni = ""
            )
        }
        pekerjaanOperasi?.cancel()
        pekerjaanOperasi = viewModelScope.launch {
            var hasilEksekusi: HasilMassal? = null
            try {
                hasilEksekusi = salinPindahkanUseCase(
                    daftarSumber = sumber,
                    tujuanInduk = tujuan,
                    apakahPindah = pindah,
                    onProgres = { indeks, total, item ->
                        _state.update {
                            it.copy(
                                itemDiproses = indeks,
                                totalItem = total,
                                namaItemSaatIni = item.label ?: item.pengenal.substringAfterLast('/')
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                _state.update {
                    it.copy(
                        sedangBerjalan = false,
                        hasilTerakhir = hasilEksekusi,
                        tampilkanDialogHasil = hasilEksekusi?.adaGagal == true,
                        daftarSumber = emptyList()
                    )
                }
                onSelesai(hasilEksekusi)
            }
        }
    }

    fun batalkanOperasi() {
        pekerjaanOperasi?.cancel()
    }
}