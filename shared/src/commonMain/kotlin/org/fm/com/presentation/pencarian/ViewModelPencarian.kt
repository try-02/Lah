@file:OptIn(kotlin.time.ExperimentalTime::class)

package org.fm.com.presentation.pencarian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.FilterCariIsi
import org.fm.com.domain.model.FilterCariNama
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.domain.model.UtilitasFilterPencarian
import org.fm.com.domain.usecase.CariIsiBerkasUseCase
import org.fm.com.domain.usecase.CariNamaBerkasUseCase

class ViewModelPencarian(
    private val cariNamaUseCase: CariNamaBerkasUseCase,
    private val cariIsiUseCase: CariIsiBerkasUseCase,
    private val clock: Clock = Clock.System
) : ViewModel() {
    private val _state = MutableStateFlow(StatePencarian())
    val state: StateFlow<StatePencarian> = _state.asStateFlow()

    private var pekerjaanCari: Job? = null

    fun prosesIntent(intent: IntentPencarian) {
        when (intent) {
            is IntentPencarian.SetFolderAkar -> _state.update { it.copy(folderAkar = intent.lokasi) }
            is IntentPencarian.UbahModePencarian -> {
                pekerjaanCari?.cancel()
                _state.update {
                    it.copy(
                        modePencarian = intent.mode,
                        sedangMencari = false,
                        pesanGalat = null
                    )
                }
            }
            is IntentPencarian.UbahKueri -> _state.update { it.copy(kueri = intent.kueri, pesanGalat = null) }
            is IntentPencarian.ToggleFilterTerbuka -> _state.update { it.copy(filterTerbuka = !it.filterTerbuka) }
            is IntentPencarian.ToggleRekursif -> _state.update { it.copy(rekursif = !it.rekursif) }
            is IntentPencarian.ToggleTersembunyi -> _state.update { it.copy(sertakanTersembunyi = !it.sertakanTersembunyi) }
            is IntentPencarian.UbahFilterEkstensi -> _state.update { it.copy(filterEkstensiInput = intent.input) }
            is IntentPencarian.UbahUkuranMin -> _state.update { it.copy(ukuranMinInput = intent.input) }
            is IntentPencarian.UbahUkuranMaks -> _state.update { it.copy(ukuranMaksInput = intent.input) }
            is IntentPencarian.UbahSatuanUkuran -> _state.update { it.copy(satuanUkuran = intent.satuan) }
            is IntentPencarian.UbahModeCocokNama -> _state.update { it.copy(modeCocokNama = intent.mode, pesanGalat = null) }
            is IntentPencarian.TogglePekaHurufNama -> _state.update { it.copy(pekaHurufNama = !it.pekaHurufNama) }
            is IntentPencarian.UbahFilterJenisNama -> _state.update { it.copy(filterJenisNama = intent.jenis) }
            is IntentPencarian.UbahRentangTanggal -> _state.update { it.copy(rentangTanggal = intent.rentang) }
            is IntentPencarian.TogglePekaHurufIsi -> _state.update { it.copy(pekaHurufIsi = !it.pekaHurufIsi) }
            is IntentPencarian.ToggleKataUtuhIsi -> _state.update { it.copy(kataUtuhIsi = !it.kataUtuhIsi) }
            is IntentPencarian.ToggleRegexIsi -> _state.update { it.copy(gunakanRegexIsi = !it.gunakanRegexIsi, pesanGalat = null) }
            is IntentPencarian.ToggleHanyaFormatTeksIsi -> _state.update { it.copy(hanyaFormatTeksIsi = !it.hanyaFormatTeksIsi) }
            is IntentPencarian.UbahBatasUkuranIsi -> _state.update { it.copy(batasUkuranIsi = intent.batas) }
            is IntentPencarian.ResetFilter -> _state.update {
                it.copy(
                    rekursif = true,
                    sertakanTersembunyi = false,
                    filterEkstensiInput = "",
                    ukuranMinInput = "",
                    ukuranMaksInput = "",
                    satuanUkuran = SatuanUkuranFilter.KIB,
                    modeCocokNama = ModeCocokNama.SEBAGIAN,
                    pekaHurufNama = false,
                    filterJenisNama = FilterJenisEntitas.SEMUA,
                    rentangTanggal = RentangTanggalFilter.SEMUA,
                    pekaHurufIsi = false,
                    kataUtuhIsi = false,
                    gunakanRegexIsi = false,
                    hanyaFormatTeksIsi = true,
                    batasUkuranIsi = OpsiBatasUkuranIsi.DUA_MIB,
                    pesanGalat = null
                )
            }
            is IntentPencarian.MulaiPencarian -> mulaiPencarian()
            is IntentPencarian.BatalkanPencarian -> batalkanPencarian()
            is IntentPencarian.HapusGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun mulaiPencarian() {
        val st = _state.value
        val akar = st.folderAkar ?: run {
            _state.update { it.copy(pesanGalat = "Folder target pencarian belum dipilih") }
            return
        }

        val ukuranMin = parseUkuranInput(st.ukuranMinInput, st.satuanUkuran)
        if (ukuranMin.galat != null) {
            _state.update { it.copy(pesanGalat = ukuranMin.galat) }
            return
        }
        val ukuranMaks = parseUkuranInput(st.ukuranMaksInput, st.satuanUkuran)
        if (ukuranMaks.galat != null) {
            _state.update { it.copy(pesanGalat = ukuranMaks.galat) }
            return
        }

        val ekstensi = UtilitasFilterPencarian.normalisasiEkstensi(st.filterEkstensiInput)
        pekerjaanCari?.cancel()

        when (st.modePencarian) {
            ModePencarian.NAMA -> {
                val durasi = st.rentangTanggal.durasiMs
                val waktuMin = if (durasi != null) {
                    (clock.now().toEpochMilliseconds() - durasi).coerceAtLeast(0L)
                } else {
                    null
                }
                val filterNama = FilterCariNama(
                    kueri = st.kueri,
                    modeCocok = st.modeCocokNama,
                    pekaHuruf = st.pekaHurufNama,
                    rekursif = st.rekursif,
                    sertakanTersembunyi = st.sertakanTersembunyi,
                    ekstensiDiizinkan = ekstensi,
                    ukuranMinByte = ukuranMin.nilai,
                    ukuranMaksByte = ukuranMaks.nilai,
                    filterJenis = st.filterJenisNama,
                    waktuUbahMinEpochMs = waktuMin
                )

                _state.update {
                    it.copy(
                        sedangMencari = true,
                        pernahMencari = true,
                        hasilNama = emptyList(),
                        jumlahBerkasDipindai = 0,
                        jumlahFolderDipindai = 0,
                        jumlahBinerDilewati = 0,
                        jumlahTerlaluBesarDilewati = 0,
                        jumlahPeringatanFolder = 0,
                        daftarGalatBerkas = emptyList(),
                        itemDiprosesSaatIni = "",
                        terpotongBatasHasil = false,
                        pesanGalat = null
                    )
                }

                pekerjaanCari = viewModelScope.launch {
                    cariNamaUseCase(akar, filterNama)
                        .catch { e ->
                            _state.update {
                                it.copy(
                                    sedangMencari = false,
                                    pesanGalat = e.message ?: "Gagal melakukan pencarian nama"
                                )
                            }
                        }
                        .collect { progres ->
                            _state.update {
                                it.copy(
                                    hasilNama = progres.keUiList(),
                                    jumlahFolderDipindai = progres.jumlahFolderDipindai,
                                    jumlahBerkasDipindai = progres.jumlahItemDiperiksa,
                                    jumlahPeringatanFolder = progres.jumlahPeringatanFolder,
                                    itemDiprosesSaatIni = progres.namaItemSaatIni,
                                    terpotongBatasHasil = progres.terpotongBatasHasil,
                                    sedangMencari = !progres.selesai,
                                    pesanGalat = progres.galatFatal
                                )
                            }
                        }
                }
            }
            ModePencarian.ISI -> {
                val filterIsi = FilterCariIsi(
                    kueri = st.kueri,
                    pekaHuruf = st.pekaHurufIsi,
                    kataUtuh = st.kataUtuhIsi,
                    gunakanRegex = st.gunakanRegexIsi,
                    rekursif = st.rekursif,
                    sertakanTersembunyi = st.sertakanTersembunyi,
                    ekstensiDiizinkan = ekstensi,
                    hanyaFormatTeksDikenal = st.hanyaFormatTeksIsi,
                    ukuranMinByte = ukuranMin.nilai,
                    ukuranMaksByte = ukuranMaks.nilai,
                    batasUkuranPerFileByte = st.batasUkuranIsi.byte
                )

                _state.update {
                    it.copy(
                        sedangMencari = true,
                        pernahMencari = true,
                        hasilIsi = emptyList(),
                        jumlahBerkasDipindai = 0,
                        jumlahFolderDipindai = 0,
                        jumlahBinerDilewati = 0,
                        jumlahTerlaluBesarDilewati = 0,
                        jumlahPeringatanFolder = 0,
                        daftarGalatBerkas = emptyList(),
                        itemDiprosesSaatIni = "",
                        terpotongBatasHasil = false,
                        pesanGalat = null
                    )
                }

                pekerjaanCari = viewModelScope.launch {
                    cariIsiUseCase(akar, filterIsi)
                        .catch { e ->
                            _state.update {
                                it.copy(
                                    sedangMencari = false,
                                    pesanGalat = e.message ?: "Gagal melakukan pencarian isi berkas"
                                )
                            }
                        }
                        .collect { progres ->
                            _state.update {
                                it.copy(
                                    hasilIsi = progres.keUiList(),
                                    jumlahBerkasDipindai = progres.jumlahBerkasDipindai,
                                    jumlahFolderDipindai = progres.jumlahFolderDipindai,
                                    jumlahBinerDilewati = progres.jumlahBinerDilewati,
                                    jumlahTerlaluBesarDilewati = progres.jumlahTerlaluBesarDilewati,
                                    daftarGalatBerkas = progres.daftarGalatBerkas,
                                    itemDiprosesSaatIni = progres.namaBerkasSaatIni,
                                    terpotongBatasHasil = progres.terpotongBatasHasil,
                                    sedangMencari = !progres.selesai,
                                    pesanGalat = progres.galatFatal
                                )
                            }
                        }
                }
            }
        }
    }

    private fun batalkanPencarian() {
        pekerjaanCari?.cancel()
        pekerjaanCari = null
        _state.update {
            it.copy(
                sedangMencari = false,
                itemDiprosesSaatIni = ""
            )
        }
    }

    private data class HasilParseUkuran(
        val nilai: Long? = null,
        val galat: String? = null
    )

    private fun parseUkuranInput(input: String, satuan: SatuanUkuranFilter): HasilParseUkuran {
        val bersih = input.trim()
        if (bersih.isEmpty()) return HasilParseUkuran(null)
        val angka = bersih.toLongOrNull()
            ?: return HasilParseUkuran(galat = "Format ukuran tidak valid: '$bersih'")
        if (angka < 0L) {
            return HasilParseUkuran(galat = "Ukuran tidak boleh negatif")
        }
        val nilaiByte = if (angka > Long.MAX_VALUE / satuan.pengaliByte) {
            return HasilParseUkuran(galat = "Nilai ukuran terlalu besar")
        } else {
            angka * satuan.pengaliByte
        }
        return HasilParseUkuran(nilaiByte)
    }

    override fun onCleared() {
        pekerjaanCari?.cancel()
        super.onCleared()
    }
}