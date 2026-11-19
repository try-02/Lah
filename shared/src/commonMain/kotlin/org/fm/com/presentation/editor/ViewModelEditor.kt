package org.fm.com.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.HasilBukaBerkasTeks

class ViewModelEditor(
    private val repositoriBerkas: RepositoriBerkas,
    private val bukaBerkasTeksUseCase: BukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repositoriBerkas)
) : ViewModel() {
    private val _state = MutableStateFlow(StateEditor())
    val state: StateFlow<StateEditor> = _state.asStateFlow()

    private val pekerjaanMuat = mutableMapOf<String, Job>()
    private var penghitungTabKosong = 0L

    fun prosesIntent(intent: IntentEditor) {
        when (intent) {
            is IntentEditor.BukaBerkas -> {
                val entitas = intent.entitas
                bukaLokasi(
                    lokasi = entitas.lokasi,
                    nama = entitas.nama,
                    dapatDitulis = entitas.dapatDitulis,
                    ukuranByte = entitas.ukuranByte,
                    waktuUbahMs = entitas.waktuUbahEpochMs,
                    barisTujuan = intent.barisTujuan
                )
            }
            is IntentEditor.BukaLokasi -> {
                bukaLokasi(
                    lokasi = intent.lokasi,
                    nama = intent.nama ?: intent.lokasi.label ?: intent.lokasi.pengenal.substringAfterLast('/'),
                    dapatDitulis = intent.dapatDitulis,
                    ukuranByte = intent.ukuranByte,
                    waktuUbahMs = intent.waktuUbahMs,
                    barisTujuan = intent.barisTujuan
                )
            }
            is IntentEditor.BuatTabKosong -> buatTabKosong(intent.nama)
            is IntentEditor.PilihTab -> _state.update { st ->
                if (st.daftarTab.any { it.idTab == intent.idTab }) st.copy(idTabAktif = intent.idTab) else st
            }
            is IntentEditor.MintaTutupTab -> mintaTutupTab(intent.idTab)
            is IntentEditor.KonfirmasiBuangDanTutupTab -> konfirmasiBuangDanTutup()
            is IntentEditor.BatalTutupTab -> _state.update { it.copy(tabMenungguKonfirmasiTutup = null) }
            is IntentEditor.TutupSemuaTab -> tutupSemuaTab(intent.paksa)
            is IntentEditor.UbahTeks -> ubahTeks(intent.idTab, intent.teksBaru, intent.indeksKursor)
            is IntentEditor.UbahPosisiKursor -> ubahKursor(intent.idTab, intent.indeksKarakter)
            is IntentEditor.LompatKeBaris -> lompatKeBaris(intent.idTab, intent.nomorBaris)
            is IntentEditor.ToggleModeHanyaBaca -> toggleModeHanyaBaca(intent.idTab)
            is IntentEditor.ToggleWordWrapTab -> toggleWordWrapTab(intent.idTab)
            is IntentEditor.SetWordWrapUmum -> _state.update { it.copy(wordWrapUmum = intent.aktif) }
            is IntentEditor.MuatUlangTab -> muatUlangTab(intent.idTab)
            is IntentEditor.TandaiTersimpan -> tandaiTersimpan(
                idTab = intent.idTab,
                lokasiBaru = intent.lokasiBaru,
                namaBaru = intent.namaBaru,
                waktuUbahBaruMs = intent.waktuUbahBaruMs
            )
            is IntentEditor.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
        }
    }

    private fun buatIdTabLokasi(lokasi: LokasiBerkas): String =
        "${lokasi.jenis.name}:${lokasi.pengenal}"

    private fun bukaLokasi(
        lokasi: LokasiBerkas,
        nama: String,
        dapatDitulis: Boolean,
        ukuranByte: Long?,
        waktuUbahMs: Long?,
        barisTujuan: Int?
    ) {
        val idTab = buatIdTabLokasi(lokasi)
        val tabAda = _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        if (tabAda != null) {
            _state.update { it.copy(idTabAktif = idTab) }
            if (tabAda.statusMuat == StatusMuatTab.SIAP && barisTujuan != null) {
                lompatKeBaris(idTab, barisTujuan)
            } else if (tabAda.statusMuat == StatusMuatTab.GAGAL) {
                muatIsiTab(idTab, lokasi, barisTujuan)
            }
            return
        }

        val kapabilitasTulis = repositoriBerkas.kapabilitas(lokasi)?.apakahDidukung(OperasiBerkas.MENULIS) ?: true
        val hanyaBacaPermanen = !dapatDitulis || !kapabilitasTulis
        val tabBaru = ModelTabEditor(
            idTab = idTab,
            lokasi = lokasi,
            namaBerkas = nama.ifBlank { "Berkas" },
            statusMuat = StatusMuatTab.MEMUAT,
            hanyaBacaPermanen = hanyaBacaPermanen,
            modeHanyaBaca = hanyaBacaPermanen,
            ukuranByteAsli = ukuranByte,
            waktuUbahTerakhirMs = waktuUbahMs
        )
        _state.update { st ->
            st.copy(
                daftarTab = st.daftarTab + tabBaru,
                idTabAktif = idTab
            )
        }
        muatIsiTab(idTab, lokasi, barisTujuan)
    }

    private fun muatUlangTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val lokasi = tab.lokasi ?: return
        perbaruiTab(idTab) { it.copy(statusMuat = StatusMuatTab.MEMUAT, pesanStatus = null) }
        muatIsiTab(idTab, lokasi, barisTujuan = null)
    }

    private fun muatIsiTab(idTab: String, lokasi: LokasiBerkas, barisTujuan: Int?) {
        pekerjaanMuat.remove(idTab)?.cancel()
        val job = viewModelScope.launch {
            when (val hasil = bukaBerkasTeksUseCase(lokasi)) {
                is HasilBukaBerkasTeks.Sukses -> {
                    val data = hasil.data
                    val jumlahBaris = hitungJumlahBaris(data.isi)
                    val kursor = if (barisTujuan != null) {
                        hitungKursorUntukBaris(data.isi, barisTujuan)
                    } else {
                        PosisiKursor()
                    }
                    perbaruiTab(idTab) { tab ->
                        tab.copy(
                            statusMuat = StatusMuatTab.SIAP,
                            pesanStatus = null,
                            isiTersimpan = data.isi,
                            isiSaatIni = data.isi,
                            encoding = data.encoding,
                            akhirBaris = data.akhirBaris,
                            ukuranByteAsli = data.ukuranByteAsli,
                            jumlahBaris = jumlahBaris,
                            posisiKursor = kursor
                        )
                    }
                }
                is HasilBukaBerkasTeks.Biner -> {
                    perbaruiTab(idTab) { tab ->
                        tab.copy(
                            statusMuat = StatusMuatTab.BINER,
                            pesanStatus = hasil.alasan,
                            hanyaBacaPermanen = true,
                            modeHanyaBaca = true
                        )
                    }
                }
                is HasilBukaBerkasTeks.TerlaluBesar -> {
                    perbaruiTab(idTab) { tab ->
                        tab.copy(
                            statusMuat = StatusMuatTab.TERLALU_BESAR,
                            pesanStatus = "Ukuran berkas melebihi batas pembukaan editor (${hasil.batasMaksimalByte / (1024 * 1024)} MiB)",
                            ukuranByteAsli = hasil.ukuranByte ?: tab.ukuranByteAsli,
                            hanyaBacaPermanen = true,
                            modeHanyaBaca = true
                        )
                    }
                }
                is HasilBukaBerkasTeks.Gagal -> {
                    perbaruiTab(idTab) { tab ->
                        tab.copy(
                            statusMuat = StatusMuatTab.GAGAL,
                            pesanStatus = hasil.pesan
                        )
                    }
                }
            }
        }
        pekerjaanMuat[idTab] = job
        job.invokeOnCompletion {
            if (pekerjaanMuat[idTab] === job) {
                pekerjaanMuat.remove(idTab)
            }
        }
    }

    private fun buatTabKosong(nama: String) {
        penghitungTabKosong++
        val idTab = "baru:$penghitungTabKosong"
        val tab = ModelTabEditor(
            idTab = idTab,
            lokasi = null,
            namaBerkas = nama.ifBlank { "Tanpa Judul.txt" },
            statusMuat = StatusMuatTab.SIAP,
            isiTersimpan = "",
            isiSaatIni = "",
            hanyaBacaPermanen = false,
            modeHanyaBaca = false,
            jumlahBaris = 1
        )
        _state.update { st ->
            st.copy(
                daftarTab = st.daftarTab + tab,
                idTabAktif = idTab
            )
        }
    }

    private fun mintaTutupTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.adaPerubahanBelumTersimpan) {
            _state.update { it.copy(tabMenungguKonfirmasiTutup = tab) }
        } else {
            hapusTabInternal(idTab)
        }
    }

    private fun konfirmasiBuangDanTutup() {
        val tab = _state.value.tabMenungguKonfirmasiTutup ?: return
        _state.update { it.copy(tabMenungguKonfirmasiTutup = null) }
        hapusTabInternal(tab.idTab)
    }

    private fun tutupSemuaTab(paksa: Boolean) {
        if (!paksa) {
            val kotorPertama = _state.value.daftarTab.firstOrNull { it.adaPerubahanBelumTersimpan }
            if (kotorPertama != null) {
                _state.update { it.copy(idTabAktif = kotorPertama.idTab, tabMenungguKonfirmasiTutup = kotorPertama) }
                return
            }
        }
        pekerjaanMuat.values.forEach { it.cancel() }
        pekerjaanMuat.clear()
        _state.update {
            it.copy(
                daftarTab = emptyList(),
                idTabAktif = null,
                tabMenungguKonfirmasiTutup = null
            )
        }
    }

    private fun hapusTabInternal(idTab: String) {
        pekerjaanMuat.remove(idTab)?.cancel()
        _state.update { st ->
            val idx = st.daftarTab.indexOfFirst { it.idTab == idTab }
            if (idx < 0) return@update st
            val daftarBaru = st.daftarTab.filterNot { it.idTab == idTab }
            val aktifBaru = when {
                st.idTabAktif != idTab -> st.idTabAktif
                daftarBaru.isEmpty() -> null
                else -> daftarBaru[idx.coerceAtMost(daftarBaru.lastIndex)].idTab
            }
            val menungguBaru = if (st.tabMenungguKonfirmasiTutup?.idTab == idTab) null else st.tabMenungguKonfirmasiTutup
            st.copy(
                daftarTab = daftarBaru,
                idTabAktif = aktifBaru,
                tabMenungguKonfirmasiTutup = menungguBaru
            )
        }
    }

    private fun ubahTeks(idTab: String, teksBaru: String, indeksKursor: Int?) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update { it.copy(pesanInfo = "Berkas sedang dalam mode hanya baca") }
            return
        }
        if (teksBaru == tab.isiSaatIni) return
        val jumlahBaris = hitungJumlahBaris(teksBaru)
        val kursorBaru = if (indeksKursor != null) {
            hitungPosisiKursor(teksBaru, indeksKursor)
        } else {
            hitungPosisiKursor(teksBaru, teksBaru.length)
        }
        perbaruiTab(idTab) {
            it.copy(
                isiSaatIni = teksBaru,
                jumlahBaris = jumlahBaris,
                posisiKursor = kursorBaru
            )
        }
    }

    private fun ubahKursor(idTab: String, indeksKarakter: Int) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        val kursorBaru = hitungPosisiKursor(tab.isiSaatIni, indeksKarakter)
        if (kursorBaru != tab.posisiKursor) {
            perbaruiTab(idTab) { it.copy(posisiKursor = kursorBaru) }
        }
    }

    private fun lompatKeBaris(idTab: String, nomorBaris: Int) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (nomorBaris < 1 || nomorBaris > tab.jumlahBaris) {
            _state.update { it.copy(pesanInfo = "Nomor baris harus antara 1 dan ${tab.jumlahBaris}") }
            return
        }
        val kursor = hitungKursorUntukBaris(tab.isiSaatIni, nomorBaris)
        perbaruiTab(idTab) { it.copy(posisiKursor = kursor) }
    }

    private fun toggleModeHanyaBaca(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.hanyaBacaPermanen) {
            _state.update { it.copy(pesanInfo = "Penyimpanan atau berkas ini tidak memiliki izin tulis") }
            return
        }
        perbaruiTab(idTab) { it.copy(modeHanyaBaca = !it.modeHanyaBaca) }
    }

    private fun toggleWordWrapTab(idTab: String) {
        perbaruiTab(idTab) { tab ->
            val aktifSekarang = tab.wordWrapEfektif(_state.value.wordWrapUmum)
            tab.copy(wordWrapOverride = !aktifSekarang)
        }
    }

    private fun tandaiTersimpan(
        idTab: String,
        lokasiBaru: LokasiBerkas?,
        namaBaru: String?,
        waktuUbahBaruMs: Long?
    ) {
        perbaruiTab(idTab) { tab ->
            tab.copy(
                lokasi = lokasiBaru ?: tab.lokasi,
                namaBerkas = namaBaru ?: tab.namaBerkas,
                isiTersimpan = tab.isiSaatIni,
                waktuUbahTerakhirMs = waktuUbahBaruMs ?: tab.waktuUbahTerakhirMs
            )
        }
    }

    private fun perbaruiTab(idTab: String, transformasi: (ModelTabEditor) -> ModelTabEditor) {
        _state.update { st ->
            var berubah = false
            val daftarBaru = st.daftarTab.map { tab ->
                if (tab.idTab == idTab) {
                    berubah = true
                    transformasi(tab)
                } else {
                    tab
                }
            }
            if (!berubah) st else st.copy(daftarTab = daftarBaru)
        }
    }

    override fun onCleared() {
        pekerjaanMuat.values.forEach { it.cancel() }
        pekerjaanMuat.clear()
        super.onCleared()
    }

    internal companion object {
        fun hitungJumlahBaris(teks: String): Int {
            var baris = 1
            for (i in 0 until teks.length) {
                if (teks[i] == '\n') baris++
            }
            return baris
        }

        fun hitungPosisiKursor(teks: String, indeksKarakter: Int): PosisiKursor {
            val batas = indeksKarakter.coerceIn(0, teks.length)
            var baris = 1
            var kolom = 1
            for (i in 0 until batas) {
                if (teks[i] == '\n') {
                    baris++
                    kolom = 1
                } else {
                    kolom++
                }
            }
            return PosisiKursor(baris = baris, kolom = kolom, indeksKarakter = batas)
        }

        fun hitungKursorUntukBaris(teks: String, targetBaris: Int): PosisiKursor {
            val barisAman = targetBaris.coerceAtLeast(1)
            if (barisAman == 1) return PosisiKursor(baris = 1, kolom = 1, indeksKarakter = 0)
            var barisSaatIni = 1
            for (i in 0 until teks.length) {
                if (teks[i] == '\n') {
                    barisSaatIni++
                    if (barisSaatIni == barisAman) {
                        return PosisiKursor(baris = barisSaatIni, kolom = 1, indeksKarakter = i + 1)
                    }
                }
            }
            return hitungPosisiKursor(teks, teks.length)
        }
    }
}