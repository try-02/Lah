package org.fm.com.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.domain.model.SidikIsiBerkas
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.usecase.BukaBerkasTeksUseCase
import org.fm.com.domain.usecase.CariGantiUseCase
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.HasilBukaBerkasTeks
import org.fm.com.domain.usecase.HasilCariEditor
import org.fm.com.domain.usecase.HasilOperasiGanti
import org.fm.com.domain.usecase.HasilSimpanBerkas
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.OpsiCariEditor
import org.fm.com.domain.usecase.RentangKecocokan
import org.fm.com.domain.usecase.RiwayatPencarianUseCase
import org.fm.com.domain.usecase.SimpanBerkasUseCase
import org.fm.com.domain.usecase.StatistikTeksUseCase

class ViewModelEditor(
    private val repositoriBerkas: RepositoriBerkas,
    private val bukaBerkasTeksUseCase: BukaBerkasTeksUseCase = BukaBerkasTeksUseCase(repositoriBerkas),
    private val cariGantiUseCase: CariGantiUseCase = CariGantiUseCase(),
    private val riwayatPencarianUseCase: RiwayatPencarianUseCase? = null,
    private val statistikTeksUseCase: StatistikTeksUseCase = StatistikTeksUseCase(),
    private val simpanBerkasUseCase: SimpanBerkasUseCase? = null,
    private val ekstrakArsipUseCase: EkstrakArsipUseCase? = null,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _state = MutableStateFlow(StateEditor())
    val state: StateFlow<StateEditor> = _state.asStateFlow()

    private val pekerjaanMuat = mutableMapOf<String, Job>()
    private var pekerjaanCari: Job? = null
    private var pekerjaanRiwayat: Job? = null
    private var pekerjaanStatistik: Job? = null
    private var pekerjaanSimpan: Job? = null
    private val sejarahPerTab = mutableMapOf<String, SejarahUndo>()
    private var penghitungTabKosong = 0L
    private var urutanNavigasi = 0L
    private var urutanAcaraArsip = 0L

    init {
        if (riwayatPencarianUseCase != null) {
            pekerjaanRiwayat = viewModelScope.launch {
                riwayatPencarianUseCase.ambilRiwayat()
                    .catch { /* Abaikan kegagalan aliran riwayat agar editor tetap berjalan */ }
                    .collect { daftar ->
                        val uiList = daftar.map { it.keUi() }
                        _state.update { it.copy(riwayatPencarian = uiList) }
                    }
            }
        }
    }

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
                    barisTujuan = intent.barisTujuan,
                    konteksArsip = intent.konteksArsip
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
            is IntentEditor.PilihTab -> pilihTab(intent.idTab)
            is IntentEditor.MintaTutupTab -> mintaTutupTab(intent.idTab)
            is IntentEditor.KonfirmasiSimpanDanTutupTab -> konfirmasiSimpanDanTutup()
            is IntentEditor.KonfirmasiBuangDanTutupTab -> konfirmasiBuangDanTutup()
            is IntentEditor.BatalTutupTab -> _state.update { it.copy(tabMenungguKonfirmasiTutup = null) }
            is IntentEditor.TutupSemuaTab -> tutupSemuaTab(intent.paksa)
            is IntentEditor.SetLokasiFolderSimpan -> _state.update { it.copy(lokasiFolderSimpanDefault = intent.lokasi) }
            is IntentEditor.SimpanTab -> simpanTab(intent.idTab, paksaTimpaKonflik = false, tutupSetelahSimpan = false)
            is IntentEditor.BukaDialogSimpanSebagai -> bukaDialogSimpanSebagai(intent.idTab, tutupSetelahSimpan = false)
            is IntentEditor.EksekusiSimpanSebagai -> eksekusiSimpanSebagai(intent.namaBerkas, intent.paksaTimpa)
            is IntentEditor.TutupDialogSimpanSebagai -> _state.update { it.copy(dialogSimpanSebagai = null) }
            is IntentEditor.KonfirmasiTimpaKonflikSimpan -> konfirmasiTimpaKonflikSimpan()
            is IntentEditor.BatalKonflikSimpan -> _state.update { it.copy(konfirmasiKonflikSimpan = null) }
            is IntentEditor.MintaKeluarDariTabArsip -> mintaKeluarDariTabArsip(intent.idTab)
            is IntentEditor.KonfirmasiPerbaruiArsip -> konfirmasiPerbaruiArsip()
            is IntentEditor.BatalkanEditDalamArsip -> batalkanEditDalamArsip()
            is IntentEditor.TutupDialogPerbaruiArsip -> _state.update { it.copy(konfirmasiPerbaruiArsip = null) }
            is IntentEditor.KonsumsiAcaraArsipDiperbarui -> _state.update { it.copy(acaraArsipDiperbarui = null) }
            is IntentEditor.HitungStatistikTab -> hitungStatistikTab(intent.idTab)
            is IntentEditor.TutupDialogStatistik -> tutupDialogStatistik()
            is IntentEditor.UbahTeks -> ubahTeks(
                idTab = intent.idTab,
                teksBaru = intent.teksBaru,
                indeksKursor = intent.indeksKursor,
                indeksAkhirSeleksi = intent.indeksAkhirSeleksi
            )
            is IntentEditor.UbahRentangTeks -> ubahRentangTeks(
                idTab = intent.idTab,
                awalGlobal = intent.awalGlobal,
                panjangDihapus = intent.panjangDihapus,
                teksDisisip = intent.teksDisisip,
                indeksKursorGlobal = intent.indeksKursorGlobal,
                indeksAkhirSeleksiGlobal = intent.indeksAkhirSeleksiGlobal
            )
            is IntentEditor.Undo -> eksekusiUndo(intent.idTab)
            is IntentEditor.Redo -> eksekusiRedo(intent.idTab)
            is IntentEditor.UbahPosisiKursor -> ubahKursor(
                idTab = intent.idTab,
                indeksKarakter = intent.indeksKarakter,
                indeksAkhirSeleksi = intent.indeksAkhirSeleksi
            )
            is IntentEditor.SetBukaDialogLompatBaris -> _state.update { it.copy(dialogLompatBarisTerbuka = intent.buka) }
            is IntentEditor.LompatKeBaris -> lompatKeBaris(intent.idTab, intent.nomorBaris)
            is IntentEditor.SetBukaDialogCariGanti -> setBukaDialogCariGanti(intent.buka)
            is IntentEditor.UbahKueriCari -> perbaruiParameterCari { it.copy(kueriCari = intent.kueri) }
            is IntentEditor.UbahTeksPengganti -> _state.update {
                it.copy(
                    cariGanti = it.cariGanti.copy(
                        teksPengganti = intent.teks,
                        konfirmasiGantiSemuaJumlah = null
                    )
                )
            }
            is IntentEditor.TogglePekaHurufCari -> perbaruiParameterCari { it.copy(pekaHuruf = !it.pekaHuruf) }
            is IntentEditor.ToggleKataUtuhCari -> perbaruiParameterCari { it.copy(kataUtuh = !it.kataUtuh) }
            is IntentEditor.ToggleRegexCari -> perbaruiParameterCari { it.copy(gunakanRegex = !it.gunakanRegex) }
            is IntentEditor.CariBerikutnya -> navigasiKecocokan(maju = true)
            is IntentEditor.CariSebelumnya -> navigasiKecocokan(maju = false)
            is IntentEditor.GantiSatu -> eksekusiGantiSatu()
            is IntentEditor.MintaGantiSemua -> mintaGantiSemua()
            is IntentEditor.KonfirmasiGantiSemua -> eksekusiGantiSemua()
            is IntentEditor.BatalGantiSemua -> _state.update {
                it.copy(cariGanti = it.cariGanti.copy(konfirmasiGantiSemuaJumlah = null))
            }
            is IntentEditor.PilihRiwayatPencarian -> pilihRiwayatPencarian(intent.item)
            is IntentEditor.HapusItemRiwayatPencarian -> hapusItemRiwayatPencarian(intent.kueri)
            is IntentEditor.BersihkanRiwayatPencarian -> bersihkanRiwayatPencarian()
            is IntentEditor.ToggleModeHanyaBaca -> toggleModeHanyaBaca(intent.idTab)
            is IntentEditor.ToggleWordWrapTab -> toggleWordWrapTab(intent.idTab)
            is IntentEditor.SetWordWrapTab -> perbaruiTab(intent.idTab) { it.copy(wordWrapOverride = intent.aktif) }
            is IntentEditor.SetWordWrapUmum -> _state.update { it.copy(wordWrapUmum = intent.aktif) }
            is IntentEditor.MuatUlangTab -> muatUlangTab(intent.idTab)
            is IntentEditor.TandaiTersimpan -> tandaiTersimpan(
                idTab = intent.idTab,
                lokasiBaru = intent.lokasiBaru,
                namaBaru = intent.namaBaru,
                waktuUbahBaruMs = intent.waktuUbahBaruMs,
                sidikBaru = intent.sidikBaru
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
        barisTujuan: Int?,
        konteksArsip: KonteksEditArsip? = null
    ) {
        val idTab = buatIdTabLokasi(lokasi)
        val tabAda = if (konteksArsip != null) {
            _state.value.daftarTab.firstOrNull { tab ->
                val k = tab.konteksArsip
                k != null &&
                    k.sumberArsipAkar == konteksArsip.sumberArsipAkar &&
                    k.rantaiJalurArsipBersarang == konteksArsip.rantaiJalurArsipBersarang &&
                    k.jalurEntriTarget == konteksArsip.jalurEntriTarget
            }
        } else {
            _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        }
        if (tabAda != null) {
            if (konteksArsip != null && tabAda.lokasi != null && tabAda.lokasi != lokasi) {
                val lokasiBaru = lokasi
                viewModelScope.launch { ekstrakArsipUseCase?.bersihkanBerkasSementara(lokasiBaru) }
            }
            pilihTab(tabAda.idTab)
            if (tabAda.statusMuat == StatusMuatTab.SIAP && barisTujuan != null) {
                lompatKeBaris(tabAda.idTab, barisTujuan)
            } else if (tabAda.statusMuat == StatusMuatTab.GAGAL) {
                muatIsiTab(tabAda.idTab, tabAda.lokasi ?: lokasi, barisTujuan)
            }
            return
        }

        val kapabilitasTulis = repositoriBerkas.kapabilitas(lokasi)?.apakahDidukung(OperasiBerkas.MENULIS) ?: true
        val hanyaBacaPermanen = !dapatDitulis || !kapabilitasTulis
        val tabBaru = ModelTabEditor(
            idTab = idTab,
            lokasi = lokasi,
            namaBerkas = nama.ifBlank { "Berkas" },
            konteksArsip = konteksArsip,
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

    private fun pilihTab(idTab: String) {
        val berubah = _state.value.idTabAktif != idTab && _state.value.daftarTab.any { it.idTab == idTab }
        if (!berubah) return
        pekerjaanStatistik?.cancel()
        _state.update { st ->
            st.copy(
                idTabAktif = idTab,
                dialogStatistik = StateDialogStatistik()
            )
        }
        if (_state.value.cariGanti.terbuka) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun muatUlangTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val lokasi = tab.lokasi ?: return
        sejarahPerTab[idTab]?.bersihkan()
        perbaruiTab(idTab) {
            it.copy(
                statusMuat = StatusMuatTab.MEMUAT,
                pesanStatus = null,
                dapatUndo = false,
                dapatRedo = false
            )
        }
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
                    sejarahPerTab.getOrPut(idTab) { SejarahUndo() }.bersihkan()
                    val nav = if (barisTujuan != null) ++urutanNavigasi else 0L
                    perbaruiTab(idTab) { tab ->
                        tab.copy(
                            statusMuat = StatusMuatTab.SIAP,
                            pesanStatus = null,
                            isiTersimpan = data.isi,
                            isiSaatIni = data.isi,
                            encoding = data.encoding,
                            akhirBaris = data.akhirBaris,
                            ukuranByteAsli = data.ukuranByteAsli,
                            sidikIsiTersimpan = data.sidikIsiByte,
                            jumlahBaris = jumlahBaris,
                            posisiKursor = kursor,
                            dapatUndo = false,
                            dapatRedo = false,
                            urutanNavigasiKursor = nav,
                            urutanSinkronTeks = tab.urutanSinkronTeks + 1L
                        )
                    }
                    if (_state.value.cariGanti.terbuka && _state.value.idTabAktif == idTab) {
                        jadwalkanPencarian(pilihTerdekat = false)
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
        sejarahPerTab[idTab] = SejarahUndo()
        val tab = ModelTabEditor(
            idTab = idTab,
            lokasi = null,
            namaBerkas = nama.ifBlank { "Tanpa Judul.txt" },
            statusMuat = StatusMuatTab.SIAP,
            isiTersimpan = "",
            isiSaatIni = "",
            hanyaBacaPermanen = false,
            modeHanyaBaca = false,
            jumlahBaris = 1,
            urutanSinkronTeks = 1L
        )
        _state.update { st ->
            st.copy(
                daftarTab = st.daftarTab + tab,
                idTabAktif = idTab
            )
        }
        if (_state.value.cariGanti.terbuka) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun mintaTutupTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val kArsip = tab.konteksArsip
        if (kArsip != null && tab.adaPerubahanBelumTersimpan && !tab.hanyaBacaPermanen) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = tab.idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = true,
                        kembaliKeArsipSetelahSelesai = false
                    )
                )
            }
        } else if (tab.adaPerubahanBelumTersimpan) {
            _state.update { it.copy(tabMenungguKonfirmasiTutup = tab) }
        } else {
            hapusTabInternal(idTab)
        }
    }

    private fun mintaKeluarDariTabArsip(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        val kArsip = tab.konteksArsip ?: return
        if (tab.adaPerubahanBelumTersimpan && !tab.hanyaBacaPermanen) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = tab.idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = true,
                        kembaliKeArsipSetelahSelesai = true
                    )
                )
            }
        } else {
            hapusTabInternal(tab.idTab)
            _state.update {
                it.copy(
                    acaraArsipDiperbarui = AcaraArsipDiperbaruiDariEditor(
                        id = ++urutanAcaraArsip,
                        kembaliKeArsip = true
                    )
                )
            }
        }
    }

    private fun konfirmasiSimpanDanTutup() {
        val tab = _state.value.tabMenungguKonfirmasiTutup ?: return
        _state.update { it.copy(tabMenungguKonfirmasiTutup = null) }
        if (tab.lokasi == null) {
            bukaDialogSimpanSebagai(tab.idTab, tutupSetelahSimpan = true)
        } else {
            simpanTab(tab.idTab, paksaTimpaKonflik = false, tutupSetelahSimpan = true)
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
        val lokasiArsipUntukDibersihkan = _state.value.daftarTab
            .filter { it.konteksArsip != null && it.lokasi != null }
            .mapNotNull { it.lokasi }
        if (lokasiArsipUntukDibersihkan.isNotEmpty() && ekstrakArsipUseCase != null) {
            viewModelScope.launch {
                for (lok in lokasiArsipUntukDibersihkan) {
                    ekstrakArsipUseCase.bersihkanBerkasSementara(lok)
                }
            }
        }
        pekerjaanMuat.values.forEach { it.cancel() }
        pekerjaanMuat.clear()
        pekerjaanCari?.cancel()
        pekerjaanStatistik?.cancel()
        pekerjaanSimpan?.cancel()
        sejarahPerTab.clear()
        _state.update {
            it.copy(
                daftarTab = emptyList(),
                idTabAktif = null,
                sedangMenyimpan = false,
                tabMenungguKonfirmasiTutup = null,
                konfirmasiKonflikSimpan = null,
                konfirmasiPerbaruiArsip = null,
                dialogSimpanSebagai = null,
                dialogLompatBarisTerbuka = false,
                dialogStatistik = StateDialogStatistik(),
                cariGanti = it.cariGanti.copy(
                    terbuka = false,
                    daftarKecocokan = emptyList(),
                    indeksKecocokanAktif = -1,
                    galatPola = null,
                    sedangMencari = false,
                    konfirmasiGantiSemuaJumlah = null
                )
            )
        }
    }

    private fun hapusTabInternal(idTab: String) {
        val tabTarget = _state.value.daftarTab.firstOrNull { it.idTab == idTab }
        if (tabTarget?.konteksArsip != null && tabTarget.lokasi != null && ekstrakArsipUseCase != null) {
            val lok = tabTarget.lokasi
            viewModelScope.launch { ekstrakArsipUseCase.bersihkanBerkasSementara(lok) }
        }
        pekerjaanMuat.remove(idTab)?.cancel()
        sejarahPerTab.remove(idTab)
        if (_state.value.idTabAktif == idTab) {
            pekerjaanStatistik?.cancel()
        }
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
            val konflikBaru = if (st.konfirmasiKonflikSimpan?.idTab == idTab) null else st.konfirmasiKonflikSimpan
            val arsipKonfBaru = if (st.konfirmasiPerbaruiArsip?.idTab == idTab) null else st.konfirmasiPerbaruiArsip
            val simpanSebagaiBaru = if (st.dialogSimpanSebagai?.idTab == idTab) null else st.dialogSimpanSebagai
            val cgBaru = if (daftarBaru.isEmpty()) {
                st.cariGanti.copy(
                    terbuka = false,
                    daftarKecocokan = emptyList(),
                    indeksKecocokanAktif = -1,
                    galatPola = null,
                    sedangMencari = false,
                    konfirmasiGantiSemuaJumlah = null
                )
            } else {
                st.cariGanti
            }
            st.copy(
                daftarTab = daftarBaru,
                idTabAktif = aktifBaru,
                tabMenungguKonfirmasiTutup = menungguBaru,
                konfirmasiKonflikSimpan = konflikBaru,
                konfirmasiPerbaruiArsip = arsipKonfBaru,
                dialogSimpanSebagai = simpanSebagaiBaru,
                dialogStatistik = if (st.idTabAktif == idTab) StateDialogStatistik() else st.dialogStatistik,
                cariGanti = cgBaru
            )
        }
        if (_state.value.cariGanti.terbuka && _state.value.tabAktif != null) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun simpanTab(
        idTab: String,
        paksaTimpaKonflik: Boolean,
        tutupSetelahSimpan: Boolean
    ) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.hanyaBacaPermanen) {
            _state.update { it.copy(pesanInfo = "Lokasi berkas tidak mendukung penulisan. Gunakan Simpan Sebagai.") }
            return
        }
        val kArsip = tab.konteksArsip
        if (kArsip != null) {
            _state.update {
                it.copy(
                    konfirmasiPerbaruiArsip = PermintaanKonfirmasiPerbaruiArsip(
                        idTab = idTab,
                        namaBerkas = tab.namaBerkas,
                        namaArsip = kArsip.namaArsipTampilan,
                        tutupSetelahPerbarui = tutupSetelahSimpan,
                        kembaliKeArsipSetelahSelesai = false
                    )
                )
            }
            return
        }
        val lokasi = tab.lokasi
        if (lokasi == null) {
            bukaDialogSimpanSebagai(idTab, tutupSetelahSimpan = tutupSetelahSimpan)
            return
        }
        val uc = simpanBerkasUseCase ?: run {
            _state.update { it.copy(pesanInfo = "Layanan simpan berkas belum terkonfigurasi") }
            return
        }

        val snapshotIsi = tab.isiSaatIni
        pekerjaanSimpan?.cancel()
        _state.update {
            it.copy(
                sedangMenyimpan = true,
                konfirmasiKonflikSimpan = null
            )
        }
        val job = viewModelScope.launch {
            val hasil = uc.simpan(
                lokasi = lokasi,
                isiTeks = snapshotIsi,
                encoding = tab.encoding,
                akhirBaris = tab.akhirBaris,
                sidikDiharapkan = tab.sidikIsiTersimpan,
                paksaTimpaKonflik = paksaTimpaKonflik
            )
            when (hasil) {
                is HasilSimpanBerkas.Sukses -> {
                    perbaruiTab(idTab) { tabSaatIni ->
                        tabSaatIni.copy(
                            lokasi = hasil.lokasiHasil,
                            namaBerkas = hasil.namaHasil,
                            isiTersimpan = snapshotIsi,
                            ukuranByteAsli = hasil.sidikBaru.ukuranByte,
                            waktuUbahTerakhirMs = hasil.waktuSimpanEpochMs,
                            sidikIsiTersimpan = hasil.sidikBaru
                        )
                    }
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Berkas '${hasil.namaHasil}' berhasil disimpan"
                        )
                    }
                    if (tutupSetelahSimpan) {
                        val tabSesudah = _state.value.daftarTab.firstOrNull { it.idTab == idTab }
                        if (tabSesudah != null && !tabSesudah.adaPerubahanBelumTersimpan) {
                            hapusTabInternal(idTab)
                        }
                    }
                }
                is HasilSimpanBerkas.KonflikEksternal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            konfirmasiKonflikSimpan = PermintaanKonfirmasiKonflikSimpan(
                                idTab = idTab,
                                namaBerkas = tab.namaBerkas,
                                pesan = hasil.pesan,
                                berkasHilangDiDisk = hasil.berkasHilangDiDisk,
                                tutupSetelahSimpan = tutupSetelahSimpan
                            )
                        )
                    }
                }
                is HasilSimpanBerkas.KonflikTujuan -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = hasil.pesan
                        )
                    }
                }
                is HasilSimpanBerkas.Gagal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Gagal menyimpan '${tab.namaBerkas}': ${hasil.pesan}"
                        )
                    }
                }
            }
        }
        pekerjaanSimpan = job
        job.invokeOnCompletion {
            if (pekerjaanSimpan === job) {
                pekerjaanSimpan = null
            }
        }
    }

    private fun konfirmasiTimpaKonflikSimpan() {
        val permintaan = _state.value.konfirmasiKonflikSimpan ?: return
        simpanTab(
            idTab = permintaan.idTab,
            paksaTimpaKonflik = true,
            tutupSetelahSimpan = permintaan.tutupSetelahSimpan
        )
    }

    private fun konfirmasiPerbaruiArsip() {
        val permintaan = _state.value.konfirmasiPerbaruiArsip ?: return
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == permintaan.idTab } ?: return
        val kArsip = tab.konteksArsip ?: return
        val lokasiTemp = tab.lokasi ?: return
        val simpanUc = simpanBerkasUseCase ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null, pesanInfo = "Layanan simpan belum tersedia") }
            return
        }
        val arsipUc = ekstrakArsipUseCase ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null, pesanInfo = "Layanan pembaruan arsip belum tersedia") }
            return
        }

        val snapshotIsi = tab.isiSaatIni
        pekerjaanSimpan?.cancel()
        _state.update { it.copy(konfirmasiPerbaruiArsip = null, sedangMenyimpan = true) }

        val job = viewModelScope.launch {
            // 1. Tulis ke berkas kerja sementara dengan verifikasi
            val hasilTemp = simpanUc.simpan(
                lokasi = lokasiTemp,
                isiTeks = snapshotIsi,
                encoding = tab.encoding,
                akhirBaris = tab.akhirBaris,
                sidikDiharapkan = null,
                paksaTimpaKonflik = true
            )
            if (hasilTemp !is HasilSimpanBerkas.Sukses) {
                val pesan = when (hasilTemp) {
                    is HasilSimpanBerkas.Gagal -> hasilTemp.pesan
                    is HasilSimpanBerkas.KonflikEksternal -> hasilTemp.pesan
                    is HasilSimpanBerkas.KonflikTujuan -> hasilTemp.pesan
                    is HasilSimpanBerkas.Sukses -> ""
                }
                _state.update {
                    it.copy(
                        sedangMenyimpan = false,
                        pesanInfo = "Gagal menyimpan salinan kerja: $pesan"
                    )
                }
                return@launch
            }

            // 2. Bangun ulang & verifikasi arsip target
            when (val hasilArsip = arsipUc.perbaruiBerkasDalamArsip(kArsip, hasilTemp.lokasiHasil)) {
                is HasilOperasi.Sukses -> {
                    perbaruiTab(tab.idTab) {
                        it.copy(
                            isiTersimpan = snapshotIsi,
                            ukuranByteAsli = hasilTemp.sidikBaru.ukuranByte,
                            waktuUbahTerakhirMs = hasilTemp.waktuSimpanEpochMs,
                            sidikIsiTersimpan = hasilTemp.sidikBaru
                        )
                    }
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Arsip '${kArsip.namaArsipTampilan}' berhasil diperbarui",
                            acaraArsipDiperbarui = AcaraArsipDiperbaruiDariEditor(
                                id = ++urutanAcaraArsip,
                                kembaliKeArsip = permintaan.kembaliKeArsipSetelahSelesai
                            )
                        )
                    }
                    if (permintaan.tutupSetelahPerbarui) {
                        hapusTabInternal(tab.idTab)
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Gagal memperbarui arsip (arsip asli dipertahankan): ${hasilArsip.pesan}"
                        )
                    }
                }
            }
        }
        pekerjaanSimpan = job
        job.invokeOnCompletion {
            if (pekerjaanSimpan === job) pekerjaanSimpan = null
        }
    }

    private fun batalkanEditDalamArsip() {
        val permintaan = _state.value.konfirmasiPerbaruiArsip ?: return
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == permintaan.idTab } ?: run {
            _state.update { it.copy(konfirmasiPerbaruiArsip = null) }
            return
        }
        _state.update { it.copy(konfirmasiPerbaruiArsip = null) }

        sejarahPerTab[tab.idTab]?.bersihkan()
        if (permintaan.tutupSetelahPerbarui) {
            hapusTabInternal(tab.idTab)
            _state.update {
                it.copy(
                    pesanInfo = "Edit berkas dibatalkan; arsip tetap seperti semula",
                    acaraArsipDiperbarui = if (permintaan.kembaliKeArsipSetelahSelesai) {
                        AcaraArsipDiperbaruiDariEditor(
                            id = ++urutanAcaraArsip,
                            kembaliKeArsip = true
                        )
                    } else {
                        it.acaraArsipDiperbarui
                    }
                )
            }
        } else {
            val jumlahBarisAsli = hitungJumlahBaris(tab.isiTersimpan)
            perbaruiTab(tab.idTab) {
                it.copy(
                    isiSaatIni = it.isiTersimpan,
                    jumlahBaris = jumlahBarisAsli,
                    posisiKursor = PosisiKursor(),
                    dapatUndo = false,
                    dapatRedo = false,
                    urutanNavigasiKursor = ++urutanNavigasi,
                    urutanSinkronTeks = it.urutanSinkronTeks + 1L
                )
            }
            _state.update {
                it.copy(pesanInfo = "Edit berkas dibatalkan; arsip tetap seperti semula")
            }
        }
    }

    private fun bukaDialogSimpanSebagai(idTab: String, tutupSetelahSimpan: Boolean) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        val folderTujuan = _state.value.lokasiFolderSimpanDefault ?: run {
            _state.update {
                it.copy(
                    pesanInfo = "Pilih atau buka folder terlebih dahulu di tab Berkas sebelum menyimpan berkas baru"
                )
            }
            return
        }
        _state.update {
            it.copy(
                dialogSimpanSebagai = PermintaanSimpanSebagai(
                    idTab = idTab,
                    folderTujuan = folderTujuan,
                    namaAwal = tab.namaBerkas,
                    tutupSetelahSimpan = tutupSetelahSimpan
                )
            )
        }
    }

    private fun eksekusiSimpanSebagai(namaBerkas: String, paksaTimpa: Boolean) {
        val permintaan = _state.value.dialogSimpanSebagai ?: return
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == permintaan.idTab } ?: return
        val uc = simpanBerkasUseCase ?: run {
            _state.update { it.copy(pesanInfo = "Layanan simpan berkas belum terkonfigurasi") }
            return
        }

        val snapshotIsi = tab.isiSaatIni
        pekerjaanSimpan?.cancel()
        _state.update { it.copy(sedangMenyimpan = true) }

        val job = viewModelScope.launch {
            val hasil = uc.simpanSebagai(
                induk = permintaan.folderTujuan,
                namaBaru = namaBerkas,
                isiTeks = snapshotIsi,
                encoding = tab.encoding,
                akhirBaris = tab.akhirBaris,
                paksaTimpaKonflik = paksaTimpa
            )
            when (hasil) {
                is HasilSimpanBerkas.Sukses -> {
                    val idBaru = buatIdTabLokasi(hasil.lokasiHasil)
                    val tabLainAdaIdSama = idBaru != tab.idTab &&
                        _state.value.daftarTab.any { it.idTab == idBaru }

                    if (tabLainAdaIdSama) {
                        perbaruiTab(tab.idTab) {
                            it.copy(
                                lokasi = hasil.lokasiHasil,
                                namaBerkas = hasil.namaHasil,
                                hanyaBacaPermanen = false,
                                isiTersimpan = snapshotIsi,
                                ukuranByteAsli = hasil.sidikBaru.ukuranByte,
                                waktuUbahTerakhirMs = hasil.waktuSimpanEpochMs,
                                sidikIsiTersimpan = hasil.sidikBaru
                            )
                        }
                    } else {
                        sejarahPerTab.remove(tab.idTab)?.let { sej ->
                            sejarahPerTab[idBaru] = sej
                        }
                        pekerjaanMuat.remove(tab.idTab)?.let { j ->
                            pekerjaanMuat[idBaru] = j
                        }
                        _state.update { st ->
                            val daftarBaru = st.daftarTab.map { item ->
                                if (item.idTab == tab.idTab) {
                                    item.copy(
                                        idTab = idBaru,
                                        lokasi = hasil.lokasiHasil,
                                        namaBerkas = hasil.namaHasil,
                                        hanyaBacaPermanen = false,
                                        isiTersimpan = snapshotIsi,
                                        ukuranByteAsli = hasil.sidikBaru.ukuranByte,
                                        waktuUbahTerakhirMs = hasil.waktuSimpanEpochMs,
                                        sidikIsiTersimpan = hasil.sidikBaru
                                    )
                                } else {
                                    item
                                }
                            }
                            st.copy(
                                daftarTab = daftarBaru,
                                idTabAktif = if (st.idTabAktif == tab.idTab) idBaru else st.idTabAktif
                            )
                        }
                    }

                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            dialogSimpanSebagai = null,
                            pesanInfo = "Berkas '${hasil.namaHasil}' berhasil disimpan"
                        )
                    }
                    if (permintaan.tutupSetelahSimpan) {
                        val idTargetTutup = if (tabLainAdaIdSama) tab.idTab else idBaru
                        hapusTabInternal(idTargetTutup)
                    }
                }
                is HasilSimpanBerkas.KonflikTujuan -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            dialogSimpanSebagai = permintaan.copy(
                                namaAwal = hasil.namaBerkas,
                                namaKonflik = hasil.namaBerkas,
                                pesanKonflikTujuan = "Berkas '${hasil.namaBerkas}' sudah ada. Klik Timpa untuk menggantinya."
                            )
                        )
                    }
                }
                is HasilSimpanBerkas.KonflikEksternal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            dialogSimpanSebagai = null,
                            pesanInfo = hasil.pesan
                        )
                    }
                }
                is HasilSimpanBerkas.Gagal -> {
                    _state.update {
                        it.copy(
                            sedangMenyimpan = false,
                            pesanInfo = "Gagal menyimpan berkas: ${hasil.pesan}"
                        )
                    }
                }
            }
        }
        pekerjaanSimpan = job
        job.invokeOnCompletion {
            if (pekerjaanSimpan === job) {
                pekerjaanSimpan = null
            }
        }
    }

    private fun hitungStatistikTab(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        pekerjaanStatistik?.cancel()
        if (tab.statusMuat == StatusMuatTab.BINER) {
            _state.update {
                it.copy(
                    dialogStatistik = StateDialogStatistik(
                        terbuka = true,
                        sedangMenghitung = false,
                        hasil = HasilStatistikTeks.DilewatiBiner(
                            "Statistik teks dilewati karena '${tab.namaBerkas}' adalah berkas biner"
                        )
                    )
                )
            }
            return
        }
        if (tab.statusMuat != StatusMuatTab.SIAP) {
            _state.update {
                it.copy(
                    dialogStatistik = StateDialogStatistik(
                        terbuka = true,
                        sedangMenghitung = false,
                        hasil = HasilStatistikTeks.Gagal("Statistik tidak tersedia saat status berkas: ${tab.statusMuat.name}")
                    )
                )
            }
            return
        }

        _state.update {
            it.copy(
                dialogStatistik = StateDialogStatistik(
                    terbuka = true,
                    sedangMenghitung = true,
                    hasil = null
                )
            )
        }
        val snapshotTeks = tab.isiSaatIni
        val snapshotEncoding = tab.encoding
        val snapshotAkhirBaris = tab.akhirBaris
        val snapshotUkuranAsli = tab.ukuranByteAsli

        val job = viewModelScope.launch {
            val hasil = statistikTeksUseCase(
                teks = snapshotTeks,
                apakahBiner = false,
                encoding = snapshotEncoding,
                akhirBaris = snapshotAkhirBaris,
                ukuranByteAsli = snapshotUkuranAsli
            )
            if (!_state.value.dialogStatistik.terbuka) return@launch
            _state.update {
                it.copy(
                    dialogStatistik = it.dialogStatistik.copy(
                        sedangMenghitung = false,
                        hasil = hasil
                    )
                )
            }
        }
        pekerjaanStatistik = job
        job.invokeOnCompletion {
            if (pekerjaanStatistik === job) {
                pekerjaanStatistik = null
            }
        }
    }

    private fun tutupDialogStatistik() {
        pekerjaanStatistik?.cancel()
        _state.update { it.copy(dialogStatistik = StateDialogStatistik()) }
    }

    private fun ubahTeks(
        idTab: String,
        teksBaru: String,
        indeksKursor: Int?,
        indeksAkhirSeleksi: Int?
    ) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update { it.copy(pesanInfo = "Berkas sedang dalam mode hanya baca") }
            return
        }
        val teksLama = tab.isiSaatIni
        if (teksBaru == teksLama) return

        val kursorSesudahIdx = (indeksKursor ?: teksBaru.length).coerceIn(0, teksBaru.length)
        val akhirSeleksiAman = (indeksAkhirSeleksi ?: kursorSesudahIdx).coerceIn(0, teksBaru.length)

        val minLen = minOf(teksLama.length, teksBaru.length)
        var prefix = 0
        while (prefix < minLen && teksLama[prefix] == teksBaru[prefix]) {
            prefix++
        }
        var suffix = 0
        val sisa = minLen - prefix
        while (
            suffix < sisa &&
            teksLama[teksLama.length - 1 - suffix] == teksBaru[teksBaru.length - 1 - suffix]
        ) {
            suffix++
        }
        val akhirLama = teksLama.length - suffix
        val akhirBaru = teksBaru.length - suffix
        val teksDihapus = teksLama.substring(prefix, akhirLama)
        val teksDisisip = teksBaru.substring(prefix, akhirBaru)

        val sejarah = sejarahPerTab.getOrPut(idTab) { SejarahUndo() }
        sejarah.catatDelta(
            awal = prefix,
            teksDihapus = teksDihapus,
            teksDisisip = teksDisisip,
            kursorSebelum = tab.posisiKursor.indeksKarakter,
            kursorSesudah = kursorSesudahIdx
        )

        val lfDihapus = hitungJumlahLf(teksDihapus)
        val lfDisisip = hitungJumlahLf(teksDisisip)
        val jumlahBaris = (tab.jumlahBaris - lfDihapus + lfDisisip).coerceAtLeast(1)

        val kursorLama = tab.posisiKursor
        val kursorBaru = when {
            kursorSesudahIdx == akhirBaru && kursorLama.indeksKarakter == prefix -> PosisiKursor(
                baris = kursorLama.baris + lfDisisip,
                kolom = hitungKolomPadaIndeks(teksBaru, kursorSesudahIdx),
                indeksKarakter = kursorSesudahIdx,
                indeksAkhirSeleksi = akhirSeleksiAman
            )
            kursorSesudahIdx == prefix && kursorLama.indeksKarakter == akhirLama -> PosisiKursor(
                baris = (kursorLama.baris - lfDihapus).coerceAtLeast(1),
                kolom = hitungKolomPadaIndeks(teksBaru, kursorSesudahIdx),
                indeksKarakter = kursorSesudahIdx,
                indeksAkhirSeleksi = akhirSeleksiAman
            )
            else -> hitungPosisiKursor(teksBaru, kursorSesudahIdx, akhirSeleksiAman)
        }

        perbaruiTab(idTab) {
            it.copy(
                isiSaatIni = teksBaru,
                jumlahBaris = jumlahBaris,
                posisiKursor = kursorBaru,
                dapatUndo = sejarah.dapatUndo,
                dapatRedo = sejarah.dapatRedo
            )
        }
        if (_state.value.cariGanti.terbuka && _state.value.idTabAktif == idTab) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun ubahRentangTeks(
        idTab: String,
        awalGlobal: Int,
        panjangDihapus: Int,
        teksDisisip: String,
        indeksKursorGlobal: Int,
        indeksAkhirSeleksiGlobal: Int
    ) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update { it.copy(pesanInfo = "Berkas sedang dalam mode hanya baca") }
            return
        }

        val teksLama = tab.isiSaatIni
        val awalAman = awalGlobal.coerceIn(0, teksLama.length)
        val akhirAman = (awalAman + panjangDihapus.coerceAtLeast(0)).coerceIn(awalAman, teksLama.length)
        val teksDihapus = teksLama.substring(awalAman, akhirAman)
        if (teksDihapus == teksDisisip) return

        val teksBaru = teksLama.replaceRange(awalAman, akhirAman, teksDisisip)
        val kursorSesudahIdx = indeksKursorGlobal.coerceIn(0, teksBaru.length)
        val akhirSeleksiAman = indeksAkhirSeleksiGlobal.coerceIn(0, teksBaru.length)

        val sejarah = sejarahPerTab.getOrPut(idTab) { SejarahUndo() }
        sejarah.catatDelta(
            awal = awalAman,
            teksDihapus = teksDihapus,
            teksDisisip = teksDisisip,
            kursorSebelum = tab.posisiKursor.indeksKarakter,
            kursorSesudah = kursorSesudahIdx
        )

        val lfDihapus = hitungJumlahLf(teksDihapus)
        val lfDisisip = hitungJumlahLf(teksDisisip)
        val jumlahBaris = (tab.jumlahBaris - lfDihapus + lfDisisip).coerceAtLeast(1)

        val kursorLama = tab.posisiKursor
        val kursorBaru = when {
            kursorSesudahIdx == awalAman + teksDisisip.length && kursorLama.indeksKarakter == awalAman -> PosisiKursor(
                baris = kursorLama.baris + lfDisisip,
                kolom = hitungKolomPadaIndeks(teksBaru, kursorSesudahIdx),
                indeksKarakter = kursorSesudahIdx,
                indeksAkhirSeleksi = akhirSeleksiAman
            )
            kursorSesudahIdx == awalAman && kursorLama.indeksKarakter == akhirAman -> PosisiKursor(
                baris = (kursorLama.baris - lfDihapus).coerceAtLeast(1),
                kolom = hitungKolomPadaIndeks(teksBaru, kursorSesudahIdx),
                indeksKarakter = kursorSesudahIdx,
                indeksAkhirSeleksi = akhirSeleksiAman
            )
            else -> {
                val barisPadaAwal = if (awalAman <= kursorLama.indeksKarakter) {
                    (kursorLama.baris - hitungLfRentang(teksLama, awalAman, kursorLama.indeksKarakter)).coerceAtLeast(1)
                } else {
                    kursorLama.baris + hitungLfRentang(teksLama, kursorLama.indeksKarakter, awalAman)
                }
                val barisBaru = if (kursorSesudahIdx >= awalAman) {
                    barisPadaAwal + hitungLfRentang(teksBaru, awalAman, kursorSesudahIdx)
                } else {
                    (barisPadaAwal - hitungLfRentang(teksBaru, kursorSesudahIdx, awalAman)).coerceAtLeast(1)
                }
                PosisiKursor(
                    baris = barisBaru,
                    kolom = hitungKolomPadaIndeks(teksBaru, kursorSesudahIdx),
                    indeksKarakter = kursorSesudahIdx,
                    indeksAkhirSeleksi = akhirSeleksiAman
                )
            }
        }

        perbaruiTab(idTab) {
            it.copy(
                isiSaatIni = teksBaru,
                jumlahBaris = jumlahBaris,
                posisiKursor = kursorBaru,
                dapatUndo = sejarah.dapatUndo,
                dapatRedo = sejarah.dapatRedo
            )
        }
        if (_state.value.cariGanti.terbuka && _state.value.idTabAktif == idTab) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun eksekusiUndo(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (!tab.dapatDiedit) return
        val sejarah = sejarahPerTab[idTab] ?: return
        val hasil = sejarah.undo(tab.isiSaatIni) ?: return
        val jumlahBaris = hitungJumlahBaris(hasil.teks)
        val kursorBaru = hitungPosisiKursor(hasil.teks, hasil.indeksKursor)
        perbaruiTab(idTab) {
            it.copy(
                isiSaatIni = hasil.teks,
                jumlahBaris = jumlahBaris,
                posisiKursor = kursorBaru,
                dapatUndo = sejarah.dapatUndo,
                dapatRedo = sejarah.dapatRedo,
                urutanNavigasiKursor = ++urutanNavigasi,
                urutanSinkronTeks = it.urutanSinkronTeks + 1L
            )
        }
        if (_state.value.cariGanti.terbuka && _state.value.idTabAktif == idTab) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun eksekusiRedo(idTab: String) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (!tab.dapatDiedit) return
        val sejarah = sejarahPerTab[idTab] ?: return
        val hasil = sejarah.redo(tab.isiSaatIni) ?: return
        val jumlahBaris = hitungJumlahBaris(hasil.teks)
        val kursorBaru = hitungPosisiKursor(hasil.teks, hasil.indeksKursor)
        perbaruiTab(idTab) {
            it.copy(
                isiSaatIni = hasil.teks,
                jumlahBaris = jumlahBaris,
                posisiKursor = kursorBaru,
                dapatUndo = sejarah.dapatUndo,
                dapatRedo = sejarah.dapatRedo,
                urutanNavigasiKursor = ++urutanNavigasi,
                urutanSinkronTeks = it.urutanSinkronTeks + 1L
            )
        }
        if (_state.value.cariGanti.terbuka && _state.value.idTabAktif == idTab) {
            jadwalkanPencarian(pilihTerdekat = false)
        }
    }

    private fun ubahKursor(idTab: String, indeksKarakter: Int, indeksAkhirSeleksi: Int) {
        val tab = _state.value.daftarTab.firstOrNull { it.idTab == idTab } ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        val kursorBaru = hitungPosisiKursor(tab.isiSaatIni, indeksKarakter, indeksAkhirSeleksi)
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
        _state.update { it.copy(dialogLompatBarisTerbuka = false) }
        perbaruiTab(idTab) {
            it.copy(
                posisiKursor = kursor,
                urutanNavigasiKursor = ++urutanNavigasi
            )
        }
    }

    private fun setBukaDialogCariGanti(buka: Boolean) {
        if (!buka) {
            pekerjaanCari?.cancel()
            _state.update {
                it.copy(
                    cariGanti = it.cariGanti.copy(
                        terbuka = false,
                        sedangMencari = false,
                        konfirmasiGantiSemuaJumlah = null
                    )
                )
            }
            return
        }

        val tab = _state.value.tabAktif
        var kueriAwal = _state.value.cariGanti.kueriCari
        if (tab != null && tab.statusMuat == StatusMuatTab.SIAP && tab.posisiKursor.adaSeleksi) {
            val awal = tab.posisiKursor.awalRentang.coerceIn(0, tab.isiSaatIni.length)
            val akhir = tab.posisiKursor.akhirRentang.coerceIn(0, tab.isiSaatIni.length)
            if (akhir > awal && akhir - awal <= 200) {
                val potongan = tab.isiSaatIni.substring(awal, akhir)
                if ('\n' !in potongan) {
                    kueriAwal = potongan
                }
            }
        }

        _state.update {
            it.copy(
                cariGanti = it.cariGanti.copy(
                    terbuka = true,
                    kueriCari = kueriAwal,
                    konfirmasiGantiSemuaJumlah = null
                )
            )
        }
        jadwalkanPencarian(pilihTerdekat = true)
    }

    private fun perbaruiParameterCari(transformasi: (StateCariGanti) -> StateCariGanti) {
        _state.update { st ->
            st.copy(
                cariGanti = transformasi(st.cariGanti).copy(konfirmasiGantiSemuaJumlah = null)
            )
        }
        if (_state.value.cariGanti.terbuka) {
            jadwalkanPencarian(pilihTerdekat = true)
        }
    }

    private fun jadwalkanPencarian(pilihTerdekat: Boolean) {
        pekerjaanCari?.cancel()
        val tab = _state.value.tabAktif
        val cg = _state.value.cariGanti
        if (tab == null || tab.statusMuat != StatusMuatTab.SIAP || cg.kueriCari.isEmpty()) {
            _state.update {
                it.copy(
                    cariGanti = it.cariGanti.copy(
                        daftarKecocokan = emptyList(),
                        indeksKecocokanAktif = -1,
                        galatPola = null,
                        sedangMencari = false
                    )
                )
            }
            return
        }

        val snapshotIdTab = tab.idTab
        val snapshotTeks = tab.isiSaatIni
        val snapshotKueri = cg.kueriCari
        val opsi = OpsiCariEditor(
            pekaHuruf = cg.pekaHuruf,
            kataUtuh = cg.kataUtuh,
            gunakanRegex = cg.gunakanRegex
        )
        val awalSeleksi = tab.posisiKursor.awalRentang
        val akhirSeleksi = tab.posisiKursor.akhirRentang

        _state.update { it.copy(cariGanti = it.cariGanti.copy(sedangMencari = true)) }
        val job = viewModelScope.launch {
            val hasil = withContext(computeDispatcher) {
                cariGantiUseCase.cariSemua(snapshotTeks, snapshotKueri, opsi)
            }
            val tabSekarang = _state.value.tabAktif ?: return@launch
            val cgSekarang = _state.value.cariGanti
            if (tabSekarang.idTab != snapshotIdTab ||
                tabSekarang.isiSaatIni != snapshotTeks ||
                cgSekarang.kueriCari != snapshotKueri
            ) {
                return@launch
            }
            terapkanHasilPencarian(
                idTab = snapshotIdTab,
                teks = snapshotTeks,
                hasil = hasil,
                awalSeleksi = awalSeleksi,
                akhirSeleksi = akhirSeleksi,
                sorotKecocokan = pilihTerdekat
            )
        }
        pekerjaanCari = job
        job.invokeOnCompletion {
            if (pekerjaanCari === job) {
                pekerjaanCari = null
            }
        }
    }

    private fun terapkanHasilPencarian(
        idTab: String,
        teks: String,
        hasil: HasilCariEditor,
        awalSeleksi: Int,
        akhirSeleksi: Int,
        sorotKecocokan: Boolean
    ) {
        when (hasil) {
            is HasilCariEditor.PolaTidakValid -> {
                _state.update {
                    it.copy(
                        cariGanti = it.cariGanti.copy(
                            daftarKecocokan = emptyList(),
                            indeksKecocokanAktif = -1,
                            galatPola = hasil.pesan,
                            sedangMencari = false
                        )
                    )
                }
            }
            is HasilCariEditor.Sukses -> {
                val daftar = hasil.daftarKecocokan
                val indeksAktif = if (daftar.isEmpty()) {
                    -1
                } else {
                    val pas = daftar.indexOfFirst { it.awal == awalSeleksi && it.akhirEksklusif == akhirSeleksi }
                    if (pas >= 0) {
                        pas
                    } else if (sorotKecocokan) {
                        val terdekat = daftar.indexOfFirst { it.awal >= awalSeleksi }
                        if (terdekat >= 0) terdekat else 0
                    } else {
                        -1
                    }
                }
                _state.update {
                    it.copy(
                        cariGanti = it.cariGanti.copy(
                            daftarKecocokan = daftar,
                            indeksKecocokanAktif = indeksAktif,
                            galatPola = null,
                            sedangMencari = false
                        )
                    )
                }
                if (sorotKecocokan && indeksAktif >= 0) {
                    sorotRentangDiTab(idTab, teks, daftar[indeksAktif])
                }
            }
        }
    }

    private fun navigasiKecocokan(maju: Boolean) {
        val tab = _state.value.tabAktif ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        val cg = _state.value.cariGanti
        if (cg.kueriCari.isEmpty()) return

        pekerjaanCari?.cancel()
        val opsi = OpsiCariEditor(cg.pekaHuruf, cg.kataUtuh, cg.gunakanRegex)
        catatRiwayatPencarian(cg.kueriCari, opsi)

        viewModelScope.launch {
            val hasil = withContext(computeDispatcher) {
                cariGantiUseCase.cariSemua(tab.isiSaatIni, cg.kueriCari, opsi)
            }
            val tabSekarang = _state.value.tabAktif ?: return@launch
            if (tabSekarang.idTab != tab.idTab) return@launch

            when (hasil) {
                is HasilCariEditor.PolaTidakValid -> {
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                daftarKecocokan = emptyList(),
                                indeksKecocokanAktif = -1,
                                galatPola = hasil.pesan,
                                sedangMencari = false
                            ),
                            pesanInfo = hasil.pesan
                        )
                    }
                }
                is HasilCariEditor.Sukses -> {
                    val daftar = hasil.daftarKecocokan
                    if (daftar.isEmpty()) {
                        _state.update {
                            it.copy(
                                cariGanti = it.cariGanti.copy(
                                    daftarKecocokan = emptyList(),
                                    indeksKecocokanAktif = -1,
                                    galatPola = null,
                                    sedangMencari = false
                                ),
                                pesanInfo = "Tidak ditemukan kecocokan"
                            )
                        }
                        return@launch
                    }
                    val awal = tabSekarang.posisiKursor.awalRentang
                    val akhir = tabSekarang.posisiKursor.akhirRentang
                    val indeksBaru = if (maju) {
                        cariGantiUseCase.pilihKecocokanBerikutnya(daftar, awal, akhir)
                    } else {
                        cariGantiUseCase.pilihKecocokanSebelumnya(daftar, awal, akhir)
                    }
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                daftarKecocokan = daftar,
                                indeksKecocokanAktif = indeksBaru,
                                galatPola = null,
                                sedangMencari = false
                            )
                        )
                    }
                    if (indeksBaru >= 0) {
                        sorotRentangDiTab(tabSekarang.idTab, tabSekarang.isiSaatIni, daftar[indeksBaru])
                    }
                }
            }
        }
    }

    private fun eksekusiGantiSatu() {
        val tab = _state.value.tabAktif ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update { it.copy(pesanInfo = "Berkas sedang dalam mode hanya baca") }
            return
        }
        val cg = _state.value.cariGanti
        if (cg.kueriCari.isEmpty()) return

        pekerjaanCari?.cancel()
        val opsi = OpsiCariEditor(cg.pekaHuruf, cg.kataUtuh, cg.gunakanRegex)
        catatRiwayatPencarian(cg.kueriCari, opsi)

        viewModelScope.launch {
            val hasilGanti = withContext(computeDispatcher) {
                cariGantiUseCase.gantiSatu(
                    teks = tab.isiSaatIni,
                    kueri = cg.kueriCari,
                    teksPengganti = cg.teksPengganti,
                    awalSeleksi = tab.posisiKursor.awalRentang,
                    akhirSeleksi = tab.posisiKursor.akhirRentang,
                    opsi = opsi
                )
            }
            val tabSekarang = _state.value.tabAktif ?: return@launch
            if (tabSekarang.idTab != tab.idTab || tabSekarang.isiSaatIni != tab.isiSaatIni) return@launch

            when (hasilGanti) {
                is HasilOperasiGanti.PolaTidakValid -> {
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                galatPola = hasilGanti.pesan,
                                sedangMencari = false
                            ),
                            pesanInfo = hasilGanti.pesan
                        )
                    }
                }
                is HasilOperasiGanti.Sukses -> {
                    if (hasilGanti.jumlahUbahan == 0) {
                        _state.update {
                            it.copy(
                                cariGanti = it.cariGanti.copy(
                                    daftarKecocokan = emptyList(),
                                    indeksKecocokanAktif = -1,
                                    galatPola = null,
                                    sedangMencari = false
                                ),
                                pesanInfo = "Tidak ada kecocokan untuk diganti"
                            )
                        }
                        return@launch
                    }

                    val sejarah = sejarahPerTab.getOrPut(tabSekarang.idTab) { SejarahUndo() }
                    sejarah.catatPerubahan(
                        teksSebelum = tabSekarang.isiSaatIni,
                        teksSesudah = hasilGanti.teksBaru,
                        kursorSebelum = tabSekarang.posisiKursor.indeksKarakter,
                        kursorSesudah = hasilGanti.posisiKursorSetelahnya
                    )
                    val rentangBerikut = hasilGanti.rentangBerikutnya
                    val awalBaru = rentangBerikut?.awal ?: hasilGanti.posisiKursorSetelahnya
                    val akhirBaru = rentangBerikut?.akhirEksklusif ?: hasilGanti.posisiKursorSetelahnya
                    val kursorBaru = hitungPosisiKursor(hasilGanti.teksBaru, awalBaru, akhirBaru)

                    perbaruiTab(tabSekarang.idTab) {
                        it.copy(
                            isiSaatIni = hasilGanti.teksBaru,
                            jumlahBaris = hitungJumlahBaris(hasilGanti.teksBaru),
                            posisiKursor = kursorBaru,
                            dapatUndo = sejarah.dapatUndo,
                            dapatRedo = sejarah.dapatRedo,
                            urutanNavigasiKursor = ++urutanNavigasi,
                            urutanSinkronTeks = it.urutanSinkronTeks + 1L
                        )
                    }

                    val hasilCariSesudah = withContext(computeDispatcher) {
                        cariGantiUseCase.cariSemua(hasilGanti.teksBaru, cg.kueriCari, opsi)
                    }
                    val daftarSesudah = (hasilCariSesudah as? HasilCariEditor.Sukses)
                        ?.daftarKecocokan
                        .orEmpty()
                    val indeksAktif = if (rentangBerikut != null) {
                        daftarSesudah.indexOf(rentangBerikut)
                    } else {
                        -1
                    }
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                daftarKecocokan = daftarSesudah,
                                indeksKecocokanAktif = indeksAktif,
                                galatPola = null,
                                sedangMencari = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun mintaGantiSemua() {
        val tab = _state.value.tabAktif ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update { it.copy(pesanInfo = "Berkas sedang dalam mode hanya baca") }
            return
        }
        val cg = _state.value.cariGanti
        if (cg.kueriCari.isEmpty()) return

        pekerjaanCari?.cancel()
        val opsi = OpsiCariEditor(cg.pekaHuruf, cg.kataUtuh, cg.gunakanRegex)
        catatRiwayatPencarian(cg.kueriCari, opsi)

        viewModelScope.launch {
            val hasil = withContext(computeDispatcher) {
                cariGantiUseCase.cariSemua(tab.isiSaatIni, cg.kueriCari, opsi)
            }
            val tabSekarang = _state.value.tabAktif ?: return@launch
            if (tabSekarang.idTab != tab.idTab) return@launch

            when (hasil) {
                is HasilCariEditor.PolaTidakValid -> {
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                daftarKecocokan = emptyList(),
                                indeksKecocokanAktif = -1,
                                galatPola = hasil.pesan,
                                sedangMencari = false,
                                konfirmasiGantiSemuaJumlah = null
                            ),
                            pesanInfo = hasil.pesan
                        )
                    }
                }
                is HasilCariEditor.Sukses -> {
                    if (hasil.daftarKecocokan.isEmpty()) {
                        _state.update {
                            it.copy(
                                cariGanti = it.cariGanti.copy(
                                    daftarKecocokan = emptyList(),
                                    indeksKecocokanAktif = -1,
                                    galatPola = null,
                                    sedangMencari = false,
                                    konfirmasiGantiSemuaJumlah = null
                                ),
                                pesanInfo = "Tidak ada kecocokan untuk diganti"
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                cariGanti = it.cariGanti.copy(
                                    daftarKecocokan = hasil.daftarKecocokan,
                                    galatPola = null,
                                    sedangMencari = false,
                                    konfirmasiGantiSemuaJumlah = hasil.daftarKecocokan.size
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun eksekusiGantiSemua() {
        val tab = _state.value.tabAktif ?: return
        if (tab.statusMuat != StatusMuatTab.SIAP) return
        if (tab.modeHanyaBaca) {
            _state.update {
                it.copy(
                    cariGanti = it.cariGanti.copy(konfirmasiGantiSemuaJumlah = null),
                    pesanInfo = "Berkas sedang dalam mode hanya baca"
                )
            }
            return
        }
        val cg = _state.value.cariGanti
        val opsi = OpsiCariEditor(cg.pekaHuruf, cg.kataUtuh, cg.gunakanRegex)

        viewModelScope.launch {
            val hasilGanti = withContext(computeDispatcher) {
                cariGantiUseCase.gantiSemua(
                    teks = tab.isiSaatIni,
                    kueri = cg.kueriCari,
                    teksPengganti = cg.teksPengganti,
                    opsi = opsi
                )
            }
            val tabSekarang = _state.value.tabAktif ?: return@launch
            if (tabSekarang.idTab != tab.idTab || tabSekarang.isiSaatIni != tab.isiSaatIni) return@launch

            when (hasilGanti) {
                is HasilOperasiGanti.PolaTidakValid -> {
                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                galatPola = hasilGanti.pesan,
                                konfirmasiGantiSemuaJumlah = null
                            ),
                            pesanInfo = hasilGanti.pesan
                        )
                    }
                }
                is HasilOperasiGanti.Sukses -> {
                    if (hasilGanti.jumlahUbahan > 0) {
                        val sejarah = sejarahPerTab.getOrPut(tabSekarang.idTab) { SejarahUndo() }
                        sejarah.catatPerubahan(
                            teksSebelum = tabSekarang.isiSaatIni,
                            teksSesudah = hasilGanti.teksBaru,
                            kursorSebelum = tabSekarang.posisiKursor.indeksKarakter,
                            kursorSesudah = hasilGanti.posisiKursorSetelahnya
                        )
                        val kursorBaru = hitungPosisiKursor(
                            hasilGanti.teksBaru,
                            hasilGanti.posisiKursorSetelahnya
                        )
                        perbaruiTab(tabSekarang.idTab) {
                            it.copy(
                                isiSaatIni = hasilGanti.teksBaru,
                                jumlahBaris = hitungJumlahBaris(hasilGanti.teksBaru),
                                posisiKursor = kursorBaru,
                                dapatUndo = sejarah.dapatUndo,
                                dapatRedo = sejarah.dapatRedo,
                                urutanNavigasiKursor = ++urutanNavigasi,
                                urutanSinkronTeks = it.urutanSinkronTeks + 1L
                            )
                        }
                    }

                    val hasilCariSesudah = withContext(computeDispatcher) {
                        cariGantiUseCase.cariSemua(hasilGanti.teksBaru, cg.kueriCari, opsi)
                    }
                    val sisaKecocokan = (hasilCariSesudah as? HasilCariEditor.Sukses)
                        ?.daftarKecocokan
                        .orEmpty()

                    _state.update {
                        it.copy(
                            cariGanti = it.cariGanti.copy(
                                daftarKecocokan = sisaKecocokan,
                                indeksKecocokanAktif = -1,
                                galatPola = null,
                                sedangMencari = false,
                                konfirmasiGantiSemuaJumlah = null
                            ),
                            pesanInfo = "Berhasil mengganti ${hasilGanti.jumlahUbahan} kecocokan"
                        )
                    }
                }
            }
        }
    }

    private fun pilihRiwayatPencarian(item: ItemRiwayatPencarianUi) {
        _state.update { st ->
            st.copy(
                cariGanti = st.cariGanti.copy(
                    kueriCari = item.kueri,
                    pekaHuruf = item.pekaHuruf,
                    kataUtuh = item.kataUtuh,
                    gunakanRegex = item.gunakanRegex,
                    konfirmasiGantiSemuaJumlah = null
                )
            )
        }
        if (_state.value.cariGanti.terbuka) {
            jadwalkanPencarian(pilihTerdekat = true)
        }
    }

    private fun catatRiwayatPencarian(kueri: String, opsi: OpsiCariEditor) {
        val uc = riwayatPencarianUseCase ?: return
        if (kueri.isEmpty()) return
        viewModelScope.launch {
            uc.catat(
                kueri = kueri,
                pekaHuruf = opsi.pekaHuruf,
                kataUtuh = opsi.kataUtuh,
                gunakanRegex = opsi.gunakanRegex
            )
        }
    }

    private fun hapusItemRiwayatPencarian(kueri: String) {
        val uc = riwayatPencarianUseCase ?: return
        viewModelScope.launch {
            uc.hapusItem(kueri)
        }
    }

    private fun bersihkanRiwayatPencarian() {
        val uc = riwayatPencarianUseCase ?: return
        viewModelScope.launch {
            uc.bersihkanSemua()
        }
    }

    private fun sorotRentangDiTab(idTab: String, teks: String, rentang: RentangKecocokan) {
        val kursorBaru = hitungPosisiKursor(teks, rentang.awal, rentang.akhirEksklusif)
        perbaruiTab(idTab) {
            it.copy(
                posisiKursor = kursorBaru,
                urutanNavigasiKursor = ++urutanNavigasi
            )
        }
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
        waktuUbahBaruMs: Long?,
        sidikBaru: SidikIsiBerkas?
    ) {
        perbaruiTab(idTab) { tab ->
            tab.copy(
                lokasi = lokasiBaru ?: tab.lokasi,
                namaBerkas = namaBaru ?: tab.namaBerkas,
                isiTersimpan = tab.isiSaatIni,
                waktuUbahTerakhirMs = waktuUbahBaruMs ?: tab.waktuUbahTerakhirMs,
                sidikIsiTersimpan = sidikBaru ?: tab.sidikIsiTersimpan
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
        pekerjaanCari?.cancel()
        pekerjaanRiwayat?.cancel()
        pekerjaanStatistik?.cancel()
        pekerjaanSimpan?.cancel()
        sejarahPerTab.clear()
    }

    internal companion object {
        fun hitungJumlahBaris(teks: String): Int {
            var baris = 1
            for (i in 0 until teks.length) {
                if (teks[i] == '\n') baris++
            }
            return baris
        }

        private fun hitungJumlahLf(teks: String): Int {
            var count = 0
            for (i in 0 until teks.length) {
                if (teks[i] == '\n') count++
            }
            return count
        }

        private fun hitungLfRentang(teks: String, awal: Int, akhirEksklusif: Int): Int {
            var count = 0
            val a = awal.coerceIn(0, teks.length)
            val b = akhirEksklusif.coerceIn(a, teks.length)
            for (i in a until b) {
                if (teks[i] == '\n') count++
            }
            return count
        }

        private fun hitungKolomPadaIndeks(teks: String, indeksKarakter: Int): Int {
            val batas = indeksKarakter.coerceIn(0, teks.length)
            if (batas == 0) return 1
            val lastNl = teks.lastIndexOf('\n', startIndex = batas - 1)
            return if (lastNl < 0) batas + 1 else batas - lastNl
        }

        fun hitungPosisiKursor(
            teks: String,
            indeksKarakter: Int,
            indeksAkhirSeleksi: Int = indeksKarakter
        ): PosisiKursor {
            val batas = indeksKarakter.coerceIn(0, teks.length)
            val akhirAman = indeksAkhirSeleksi.coerceIn(0, teks.length)
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
            return PosisiKursor(
                baris = baris,
                kolom = kolom,
                indeksKarakter = batas,
                indeksAkhirSeleksi = akhirAman
            )
        }

        fun hitungKursorUntukBaris(teks: String, targetBaris: Int): PosisiKursor {
            val barisAman = targetBaris.coerceAtLeast(1)
            if (barisAman == 1) return PosisiKursor(baris = 1, kolom = 1, indeksKarakter = 0)
            var barisSaatIni = 1
            for (i in 0 until teks.length) {
                if (teks[i] == '\n') {
                    barisSaatIni++
                    if (barisSaatIni == barisAman) {
                        return PosisiKursor(
                            baris = barisSaatIni,
                            kolom = 1,
                            indeksKarakter = i + 1,
                            indeksAkhirSeleksi = i + 1
                        )
                    }
                }
            }
            return hitungPosisiKursor(teks, teks.length)
        }
    }
}