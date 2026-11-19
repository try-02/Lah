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
import org.fm.com.domain.model.PenyebabGalat
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
    val daftarKonflik: List<LokasiBerkas> = emptyList(),
    val tujuanKonflik: LokasiBerkas? = null,
    val hasilSebelumKonflik: HasilMassal? = null,
    val tampilkanDialogKonflikTimpa: Boolean = false,
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
                daftarKonflik = emptyList(),
                tujuanKonflik = null,
                hasilSebelumKonflik = null,
                tampilkanDialogKonflikTimpa = false,
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
                daftarKonflik = emptyList(),
                tujuanKonflik = null,
                hasilSebelumKonflik = null,
                tampilkanDialogKonflikTimpa = false,
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
                    timpaKonflik = false,
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
                val hasil = hasilEksekusi
                if (hasil == null) {
                    _state.update {
                        it.copy(
                            sedangBerjalan = false,
                            daftarSumber = emptyList(),
                            daftarKonflik = emptyList(),
                            tujuanKonflik = null,
                            hasilSebelumKonflik = null,
                            tampilkanDialogKonflikTimpa = false
                        )
                    }
                    onSelesai(null)
                } else {
                    val itemKonflik = hasil.gagal.filter { it.penyebab == PenyebabGalat.KONFLIK }
                    val gagalNonKonflik = hasil.gagal.filter { it.penyebab != PenyebabGalat.KONFLIK }
                    if (itemKonflik.isNotEmpty()) {
                        val hasilSementara = HasilMassal(berhasil = hasil.berhasil, gagal = gagalNonKonflik)
                        _state.update {
                            it.copy(
                                sedangBerjalan = false,
                                daftarSumber = emptyList(),
                                daftarKonflik = itemKonflik.map { k -> k.sumber },
                                tujuanKonflik = tujuan,
                                hasilSebelumKonflik = hasilSementara,
                                tampilkanDialogKonflikTimpa = true,
                                hasilTerakhir = hasilSementara,
                                tampilkanDialogHasil = false
                            )
                        }
                        onSelesai(hasilSementara)
                    } else {
                        _state.update {
                            it.copy(
                                sedangBerjalan = false,
                                daftarSumber = emptyList(),
                                hasilTerakhir = hasil,
                                tampilkanDialogHasil = hasil.adaGagal
                            )
                        }
                        onSelesai(hasil)
                    }
                }
            }
        }
    }

    fun eksekusiTimpaKonflik(onSelesai: (HasilMassal?) -> Unit) {
        val konflik = _state.value.daftarKonflik
        val tujuan = _state.value.tujuanKonflik ?: return
        val pindah = _state.value.apakahPindah
        val hasilAwal = _state.value.hasilSebelumKonflik ?: HasilMassal.KOSONG
        if (konflik.isEmpty()) return

        _state.update {
            it.copy(
                tampilkanDialogKonflikTimpa = false,
                sedangBerjalan = true,
                totalItem = konflik.size,
                itemDiproses = 0,
                namaItemSaatIni = ""
            )
        }
        pekerjaanOperasi?.cancel()
        pekerjaanOperasi = viewModelScope.launch {
            var hasilTimpa: HasilMassal? = null
            try {
                hasilTimpa = salinPindahkanUseCase(
                    daftarSumber = konflik,
                    tujuanInduk = tujuan,
                    apakahPindah = pindah,
                    timpaKonflik = true,
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
                val gabungan = if (hasilTimpa != null) {
                    HasilMassal(
                        berhasil = hasilAwal.berhasil + hasilTimpa.berhasil,
                        gagal = hasilAwal.gagal + hasilTimpa.gagal
                    )
                } else {
                    null
                }
                _state.update {
                    it.copy(
                        sedangBerjalan = false,
                        daftarKonflik = emptyList(),
                        tujuanKonflik = null,
                        hasilSebelumKonflik = null,
                        tampilkanDialogKonflikTimpa = false,
                        hasilTerakhir = gabungan,
                        tampilkanDialogHasil = gabungan?.adaGagal == true
                    )
                }
                onSelesai(gabungan)
            }
        }
    }

    fun lewatiKonflik(onSelesai: (HasilMassal) -> Unit) {
        val hasilAkhir = _state.value.hasilSebelumKonflik ?: HasilMassal.KOSONG
        _state.update {
            it.copy(
                daftarKonflik = emptyList(),
                tujuanKonflik = null,
                hasilSebelumKonflik = null,
                tampilkanDialogKonflikTimpa = false,
                hasilTerakhir = hasilAkhir,
                tampilkanDialogHasil = hasilAkhir.adaGagal
            )
        }
        onSelesai(hasilAkhir)
    }

    fun batalkanOperasi() {
        pekerjaanOperasi?.cancel()
    }
}