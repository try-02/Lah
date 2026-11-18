package org.fm.com.presentation.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val dialogPilihTujuanTerbuka: Boolean = false,
    val hasilTerakhir: HasilMassal? = null
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
        _state.update {
            it.copy(
                daftarSumber = sumber,
                apakahPindah = false,
                dialogPilihTujuanTerbuka = true,
                hasilTerakhir = null
            )
        }
    }

    fun inisiasiPindah(sumber: List<LokasiBerkas>) {
        _state.update {
            it.copy(
                daftarSumber = sumber,
                apakahPindah = true,
                dialogPilihTujuanTerbuka = true,
                hasilTerakhir = null
            )
        }
    }

    fun tutupDialogTujuan() {
        _state.update { it.copy(dialogPilihTujuanTerbuka = false) }
    }

    fun eksekusiKeTujuan(tujuan: LokasiBerkas, onSelesai: () -> Unit) {
        val sumber = _state.value.daftarSumber
        val pindah = _state.value.apakahPindah
        if (sumber.isEmpty()) return

        _state.update {
            it.copy(
                dialogPilihTujuanTerbuka = false,
                sedangBerjalan = true,
                totalItem = sumber.size,
                itemDiproses = 0,
                namaItemSaatIni = ""
            )
        }

        pekerjaanOperasi?.cancel()
        pekerjaanOperasi = viewModelScope.launch {
            val hasil = salinPindahkanUseCase(
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

            _state.update {
                it.copy(
                    sedangBerjalan = false,
                    hasilTerakhir = hasil,
                    daftarSumber = emptyList()
                )
            }
            onSelesai()
        }
    }

    fun batalkanOperasi() {
        pekerjaanOperasi?.cancel()
        _state.update {
            it.copy(
                sedangBerjalan = false,
                daftarSumber = emptyList()
            )
        }
    }
}