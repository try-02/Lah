package org.fm.com.presentation.arsip

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.arsip.KapabilitasArsip
import org.fm.com.domain.arsip.MatriksArsip
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntriArsip
import org.fm.com.domain.model.FormatArsip
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.ItemSumberKompresi
import org.fm.com.domain.model.KebijakanBatasArsip
import org.fm.com.domain.model.KebijakanKonflikEkstrak
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.OpsiEkstrakArsip
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.usecase.EkstrakArsipUseCase
import org.fm.com.domain.usecase.KompresArsipUseCase
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.StatusLapisanArsip
import org.fm.com.presentation.filemanager.PemetaanDomainUi

private const val ID_SESI_UTAMA = "sesi_browser_arsip"

@Immutable
data class EntriArsipUi(
    val jalurInternal: String,
    val nama: String,
    val adalahFolder: Boolean,
    val ukuranAsliFormat: String,
    val ukuranAsliByte: Long?,
    val ukuranKompresFormat: String,
    val adalahArsipBersarang: Boolean,
    val terenkripsi: Boolean,
    val adalahTautanSimbolik: Boolean,
    val metodeKompresi: String?
)

@Immutable
data class PermintaanKonfirmasiEkstrak(
    val jalurTerpilih: Set<String>?,
    val labelTarget: String,
    val jumlahItem: Int,
    val estimasiUkuranByte: Long,
    val folderTujuan: LokasiBerkas,
    val kebijakanKonflik: KebijakanKonflikEkstrak = KebijakanKonflikEkstrak.TOLAK_KONFLIK,
    val kataSandi: String? = null
)

@Immutable
data class PermintaanKonfirmasiTimpaKompres(
    val namaArsip: String,
    val pesan: String
)

@Immutable
data class AcaraBukaEditorArsip(
    val id: Long,
    val entitas: EntitasBerkas
)

@Immutable
data class AcaraSuksesKompres(
    val id: Long,
    val entitasHasil: EntitasBerkas
)

@Immutable
data class AcaraSuksesEkstrak(
    val id: Long,
    val hasil: HasilEkstrakArsip,
    val folderTujuan: LokasiBerkas
)

@Immutable
data class StateBrowserArsip(
    val aktif: Boolean = false,
    val sumberAkar: LokasiBerkas? = null,
    val sedangMemuat: Boolean = false,
    val jejakLapisan: List<LapisanJejakArsip> = emptyList(),
    val folderDalamLapisan: String = "",
    val kataKunciSaring: String = "",
    val semuaEntriLapisan: List<EntriArsip> = emptyList(),
    val entriDitampilkan: List<EntriArsipUi> = emptyList(),
    val jalurDipilih: Set<String> = emptySet(),
    val kapabilitasAktif: KapabilitasArsip? = null,
    val totalUkuranLapisanByte: Long = 0L,
    val adaUkuranTidakDiketahui: Boolean = false,
    val batasKebijakan: KebijakanBatasArsip = KebijakanBatasArsip(),
    val dialogInfoKapabilitasTerbuka: Boolean = false,
    val dialogKonfirmasiEkstrak: PermintaanKonfirmasiEkstrak? = null,
    val sedangMengekstrak: Boolean = false,
    val progresEkstrakDiproses: Int = 0,
    val progresEkstrakTotal: Int = 0,
    val progresEkstrakNama: String = "",
    val hasilEkstrakTerakhir: HasilEkstrakArsip? = null,
    val tutupBrowserSetelahEkstrak: Boolean = false,
    val pesanGalat: String? = null,
    val pesanInfo: String? = null
) {
    val sedangMultiSelect: Boolean get() = jalurDipilih.isNotEmpty()
    val segmenFolderDalam: List<String>
        get() = folderDalamLapisan.split('/').filter { it.isNotEmpty() }
}

@Immutable
data class StateKompresArsip(
    val terbuka: Boolean = false,
    val tujuanInduk: LokasiBerkas? = null,
    val daftarSumber: List<ItemSumberKompresi> = emptyList(),
    val formatTerpilih: FormatArsip = FormatArsip.ZIP,
    val namaArsipInput: String = "arsip.zip",
    val namaKustomOlehPengguna: Boolean = false,
    val sedangMengompres: Boolean = false,
    val progresDiproses: Int = 0,
    val progresTotal: Int = 0,
    val namaItemSaatIni: String = "",
    val konfirmasiTimpa: PermintaanKonfirmasiTimpaKompres? = null,
    val pesanGalat: String? = null
) {
    val dapatPilihGzip: Boolean
        get() = daftarSumber.size == 1 && !daftarSumber.first().adalahFolder

    val namaValid: Boolean
        get() = ValidasiNama.sah(namaArsipInput.trim())
}

@Immutable
data class StateArsip(
    val browser: StateBrowserArsip = StateBrowserArsip(),
    val kompres: StateKompresArsip = StateKompresArsip(),
    val folderTujuanBawaan: LokasiBerkas? = null,
    val lokasiPintasan: List<LokasiBerkas> = emptyList(),
    val acaraBukaEditor: AcaraBukaEditorArsip? = null,
    val acaraSuksesKompres: AcaraSuksesKompres? = null,
    val acaraSuksesEkstrak: AcaraSuksesEkstrak? = null
)

sealed interface IntentArsip {
    data class SetLokasiPintasan(val daftar: List<LokasiBerkas>) : IntentArsip
    data class BukaArsip(
        val sumber: LokasiBerkas,
        val folderTujuanDefault: LokasiBerkas? = null
    ) : IntentArsip

    data class EkstrakLangsungDariFileManager(
        val sumber: LokasiBerkas,
        val folderTujuan: LokasiBerkas
    ) : IntentArsip

    data object TutupBrowser : IntentArsip
    data class MasukFolderDalamArsip(val jalurFolder: String) : IntentArsip
    data class NavigasiKeFolderDalam(val jalurFolder: String) : IntentArsip
    data class MasukArsipBersarang(val jalurArsip: String) : IntentArsip
    data class KembaliKeLapisan(val indeksLapisan: Int) : IntentArsip
    data object NavigasiNaik : IntentArsip
    data class UbahKataKunciSaring(val kueri: String) : IntentArsip
    data class TogglePilihEntri(val jalurInternal: String) : IntentArsip
    data object PilihSemuaEntriTampil : IntentArsip
    data object BersihkanPilihanEntri : IntentArsip
    data class BukaEntriUntukPratinjau(val entri: EntriArsipUi) : IntentArsip
    data class MintaEkstrak(val jalurTerpilih: Set<String>?) : IntentArsip
    data class UbahFolderTujuanEkstrak(val lokasi: LokasiBerkas) : IntentArsip
    data class UbahKebijakanKonflikEkstrak(val kebijakan: KebijakanKonflikEkstrak) : IntentArsip
    data object BatalDialogEkstrak : IntentArsip
    data object KonfirmasiDanEkstrak : IntentArsip
    data object BatalkanEkstraksi : IntentArsip
    data object TutupLaporanEkstrak : IntentArsip
    data class SetDialogInfoKapabilitas(val buka: Boolean) : IntentArsip
    data class SetPesanInfoBrowser(val pesan: String?) : IntentArsip

    // T47 Kompres
    data class BukaDialogKompres(
        val daftarSumber: List<ItemSumberKompresi>,
        val tujuanInduk: LokasiBerkas
    ) : IntentArsip

    data class UbahFormatKompres(val format: FormatArsip) : IntentArsip
    data class UbahNamaArsipKompres(val nama: String) : IntentArsip
    data object MulaiKompres : IntentArsip
    data object KonfirmasiTimpaKompres : IntentArsip
    data object BatalTimpaKompres : IntentArsip
    data object BatalkanKompres : IntentArsip
    data object TutupDialogKompres : IntentArsip

    data object KonsumsiAcaraEditor : IntentArsip
    data object KonsumsiAcaraKompres : IntentArsip
    data object KonsumsiAcaraEkstrak : IntentArsip
}

class ViewModelArsip(
    private val ekstrakArsipUseCase: EkstrakArsipUseCase,
    private val kompresArsipUseCase: KompresArsipUseCase,
    lokasiPintasanAwal: List<LokasiBerkas> = emptyList()
) : ViewModel() {

    private val _state = MutableStateFlow(StateArsip(lokasiPintasan = lokasiPintasanAwal))
    val state: StateFlow<StateArsip> = _state.asStateFlow()

    private var pekerjaanMuat: Job? = null
    private var pekerjaanEkstrak: Job? = null
    private var pekerjaanKompres: Job? = null
    private var urutanAcara = 0L

    fun prosesIntent(intent: IntentArsip) {
        when (intent) {
            is IntentArsip.SetLokasiPintasan -> _state.update { it.copy(lokasiPintasan = intent.daftar) }
            is IntentArsip.BukaArsip -> bukaArsip(
                sumber = intent.sumber,
                folderTujuanDefault = intent.folderTujuanDefault,
                langsungMintaEkstrak = false
            )
            is IntentArsip.EkstrakLangsungDariFileManager -> bukaArsip(
                sumber = intent.sumber,
                folderTujuanDefault = intent.folderTujuan,
                langsungMintaEkstrak = true
            )
            is IntentArsip.TutupBrowser -> tutupBrowser()
            is IntentArsip.MasukFolderDalamArsip -> ubahFolderDalam(intent.jalurFolder)
            is IntentArsip.NavigasiKeFolderDalam -> ubahFolderDalam(intent.jalurFolder)
            is IntentArsip.MasukArsipBersarang -> masukArsipBersarang(intent.jalurArsip)
            is IntentArsip.KembaliKeLapisan -> kembaliKeLapisan(intent.indeksLapisan)
            is IntentArsip.NavigasiNaik -> navigasiNaik()
            is IntentArsip.UbahKataKunciSaring -> perbaruiSaringan(intent.kueri)
            is IntentArsip.TogglePilihEntri -> togglePilihEntri(intent.jalurInternal)
            is IntentArsip.PilihSemuaEntriTampil -> pilihSemuaEntriTampil()
            is IntentArsip.BersihkanPilihanEntri -> _state.update {
                it.copy(browser = it.browser.copy(jalurDipilih = emptySet()))
            }
            is IntentArsip.BukaEntriUntukPratinjau -> bukaPratinjauBerkas(intent.entri)
            is IntentArsip.MintaEkstrak -> mintaEkstrak(intent.jalurTerpilih)
            is IntentArsip.UbahFolderTujuanEkstrak -> _state.update { st ->
                val dialog = st.browser.dialogKonfirmasiEkstrak ?: return@update st
                st.copy(
                    browser = st.browser.copy(
                        dialogKonfirmasiEkstrak = dialog.copy(folderTujuan = intent.lokasi)
                    )
                )
            }
            is IntentArsip.UbahKebijakanKonflikEkstrak -> _state.update { st ->
                val dialog = st.browser.dialogKonfirmasiEkstrak ?: return@update st
                st.copy(
                    browser = st.browser.copy(
                        dialogKonfirmasiEkstrak = dialog.copy(kebijakanKonflik = intent.kebijakan)
                    )
                )
            }
            is IntentArsip.BatalDialogEkstrak -> _state.update { st ->
                val tutupSetelah = st.browser.tutupBrowserSetelahEkstrak
                if (tutupSetelah) {
                    viewModelScope.launch { ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA) }
                    st.copy(browser = StateBrowserArsip())
                } else {
                    st.copy(browser = st.browser.copy(dialogKonfirmasiEkstrak = null))
                }
            }
            is IntentArsip.KonfirmasiDanEkstrak -> jalankanEkstraksi()
            is IntentArsip.BatalkanEkstraksi -> pekerjaanEkstrak?.cancel()
            is IntentArsip.TutupLaporanEkstrak -> _state.update { st ->
                if (st.browser.tutupBrowserSetelahEkstrak) {
                    viewModelScope.launch { ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA) }
                    st.copy(browser = StateBrowserArsip())
                } else {
                    st.copy(browser = st.browser.copy(hasilEkstrakTerakhir = null))
                }
            }
            is IntentArsip.SetDialogInfoKapabilitas -> _state.update {
                it.copy(browser = it.browser.copy(dialogInfoKapabilitasTerbuka = intent.buka))
            }
            is IntentArsip.SetPesanInfoBrowser -> _state.update {
                it.copy(browser = it.browser.copy(pesanInfo = intent.pesan))
            }

            is IntentArsip.BukaDialogKompres -> bukaDialogKompres(intent.daftarSumber, intent.tujuanInduk)
            is IntentArsip.UbahFormatKompres -> ubahFormatKompres(intent.format)
            is IntentArsip.UbahNamaArsipKompres -> _state.update {
                it.copy(
                    kompres = it.kompres.copy(
                        namaArsipInput = intent.nama,
                        namaKustomOlehPengguna = true,
                        konfirmasiTimpa = null,
                        pesanGalat = null
                    )
                )
            }
            is IntentArsip.MulaiKompres -> jalankanKompres(konfirmasiTimpa = false)
            is IntentArsip.KonfirmasiTimpaKompres -> jalankanKompres(konfirmasiTimpa = true)
            is IntentArsip.BatalTimpaKompres -> _state.update {
                it.copy(kompres = it.kompres.copy(konfirmasiTimpa = null))
            }
            is IntentArsip.BatalkanKompres -> pekerjaanKompres?.cancel()
            is IntentArsip.TutupDialogKompres -> tutupDialogKompres()

            is IntentArsip.KonsumsiAcaraEditor -> _state.update { it.copy(acaraBukaEditor = null) }
            is IntentArsip.KonsumsiAcaraKompres -> _state.update { it.copy(acaraSuksesKompres = null) }
            is IntentArsip.KonsumsiAcaraEkstrak -> _state.update { it.copy(acaraSuksesEkstrak = null) }
        }
    }

    private fun bukaArsip(
        sumber: LokasiBerkas,
        folderTujuanDefault: LokasiBerkas?,
        langsungMintaEkstrak: Boolean
    ) {
        pekerjaanMuat?.cancel()
        pekerjaanEkstrak?.cancel()

        val batas = _state.value.browser.batasKebijakan
        val tujuanDefault = folderTujuanDefault
            ?: _state.value.folderTujuanBawaan
            ?: _state.value.lokasiPintasan.firstOrNull()

        _state.update { st ->
            st.copy(
                folderTujuanBawaan = tujuanDefault ?: st.folderTujuanBawaan,
                browser = StateBrowserArsip(
                    aktif = !langsungMintaEkstrak,
                    sumberAkar = sumber,
                    sedangMemuat = true,
                    batasKebijakan = batas,
                    tutupBrowserSetelahEkstrak = langsungMintaEkstrak
                )
            )
        }

        pekerjaanMuat = viewModelScope.launch {
            when (val hasil = ekstrakArsipUseCase.bukaAkar(ID_SESI_UTAMA, sumber, batas = batas)) {
                is HasilOperasi.Sukses -> {
                    terapkanStatusLapisan(hasil.nilai, folderDalamBaru = "")
                    if (langsungMintaEkstrak) {
                        mintaEkstrak(jalurTerpilih = null)
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(
                                sedangMemuat = false,
                                pesanGalat = hasil.pesan,
                                pesanInfo = if (langsungMintaEkstrak) hasil.pesan else null,
                                tutupBrowserSetelahEkstrak = false
                            )
                        )
                    }
                }
            }
        }
    }

    private fun masukArsipBersarang(jalurArsip: String) {
        val stBrowser = _state.value.browser
        if (stBrowser.sedangMemuat || stBrowser.sedangMengekstrak) return

        val kedalamanSaatIni = stBrowser.jejakLapisan.size.coerceAtLeast(1)
        pekerjaanMuat?.cancel()
        _state.update { st ->
            st.copy(browser = st.browser.copy(sedangMemuat = true, pesanGalat = null))
        }

        pekerjaanMuat = viewModelScope.launch {
            val hasil = ekstrakArsipUseCase.masukArsipBersarang(
                idSesi = ID_SESI_UTAMA,
                jalurArsipDalamLapisan = jalurArsip,
                kedalamanSaatIni = kedalamanSaatIni,
                batas = stBrowser.batasKebijakan
            )
            when (hasil) {
                is HasilOperasi.Sukses -> terapkanStatusLapisan(hasil.nilai, folderDalamBaru = "")
                is HasilOperasi.Gagal -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(
                                sedangMemuat = false,
                                pesanInfo = hasil.pesan
                            )
                        )
                    }
                }
            }
        }
    }

    private fun kembaliKeLapisan(indeksLapisan: Int) {
        val stBrowser = _state.value.browser
        if (stBrowser.sedangMemuat || stBrowser.sedangMengekstrak) return
        if (indeksLapisan !in stBrowser.jejakLapisan.indices) return
        if (indeksLapisan == stBrowser.jejakLapisan.lastIndex) {
            ubahFolderDalam("")
            return
        }

        pekerjaanMuat?.cancel()
        _state.update { st ->
            st.copy(browser = st.browser.copy(sedangMemuat = true))
        }
        pekerjaanMuat = viewModelScope.launch {
            when (val hasil = ekstrakArsipUseCase.kembaliKeLapisan(ID_SESI_UTAMA, indeksLapisan)) {
                is HasilOperasi.Sukses -> terapkanStatusLapisan(hasil.nilai, folderDalamBaru = "")
                is HasilOperasi.Gagal -> _state.update { st ->
                    st.copy(
                        browser = st.browser.copy(
                            sedangMemuat = false,
                            pesanInfo = hasil.pesan
                        )
                    )
                }
            }
        }
    }

    private fun navigasiNaik() {
        val stBrowser = _state.value.browser
        if (stBrowser.folderDalamLapisan.isNotEmpty()) {
            val induk = stBrowser.folderDalamLapisan.substringBeforeLast('/', "")
            ubahFolderDalam(induk)
        } else if (stBrowser.jejakLapisan.size > 1) {
            kembaliKeLapisan(stBrowser.jejakLapisan.lastIndex - 1)
        }
    }

    private fun tutupBrowser() {
        pekerjaanMuat?.cancel()
        pekerjaanEkstrak?.cancel()
        viewModelScope.launch {
            ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA)
        }
        _state.update { it.copy(browser = StateBrowserArsip()) }
    }

    private fun terapkanStatusLapisan(status: StatusLapisanArsip, folderDalamBaru: String) {
        val entriFolder = olahEntriTampil(
            semuaEntri = status.ringkasan.daftarEntri,
            folderDalam = folderDalamBaru,
            kataKunci = ""
        )
        _state.update { st ->
            st.copy(
                browser = st.browser.copy(
                    sedangMemuat = false,
                    jejakLapisan = status.jejakLapisan,
                    folderDalamLapisan = folderDalamBaru,
                    kataKunciSaring = "",
                    semuaEntriLapisan = status.ringkasan.daftarEntri,
                    entriDitampilkan = entriFolder,
                    jalurDipilih = emptySet(),
                    kapabilitasAktif = status.lapisanAktif.kapabilitas,
                    totalUkuranLapisanByte = status.ringkasan.totalUkuranTidakTerkompresiByte,
                    adaUkuranTidakDiketahui = status.ringkasan.adaUkuranTidakDiketahui,
                    pesanGalat = null
                )
            )
        }
    }

    private fun ubahFolderDalam(folderBaru: String) {
        _state.update { st ->
            val br = st.browser
            val tampil = olahEntriTampil(br.semuaEntriLapisan, folderBaru, br.kataKunciSaring)
            st.copy(
                browser = br.copy(
                    folderDalamLapisan = folderBaru,
                    entriDitampilkan = tampil,
                    jalurDipilih = emptySet()
                )
            )
        }
    }

    private fun perbaruiSaringan(kueri: String) {
        _state.update { st ->
            val br = st.browser
            val tampil = olahEntriTampil(br.semuaEntriLapisan, br.folderDalamLapisan, kueri)
            st.copy(
                browser = br.copy(
                    kataKunciSaring = kueri,
                    entriDitampilkan = tampil
                )
            )
        }
    }

    private fun togglePilihEntri(jalur: String) {
        _state.update { st ->
            val br = st.browser
            val setBaru = if (jalur in br.jalurDipilih) {
                br.jalurDipilih - jalur
            } else {
                br.jalurDipilih + jalur
            }
            st.copy(browser = br.copy(jalurDipilih = setBaru))
        }
    }

    private fun pilihSemuaEntriTampil() {
        _state.update { st ->
            val br = st.browser
            val semua = br.entriDitampilkan.map { it.jalurInternal }.toSet()
            st.copy(browser = br.copy(jalurDipilih = semua))
        }
    }

    private fun bukaPratinjauBerkas(entri: EntriArsipUi) {
        if (entri.adalahFolder) return
        if (entri.adalahTautanSimbolik) {
            _state.update {
                it.copy(browser = it.browser.copy(pesanInfo = "Tautan simbolik dalam arsip tidak dapat dibuka demi keamanan"))
            }
            return
        }
        if (entri.terenkripsi) {
            _state.update {
                it.copy(browser = it.browser.copy(pesanInfo = "Entri terenkripsi belum didukung untuk dibuka"))
            }
            return
        }

        val batas = _state.value.browser.batasKebijakan
        viewModelScope.launch {
            _state.update { it.copy(browser = it.browser.copy(sedangMemuat = true)) }
            when (
                val hasil = ekstrakArsipUseCase.siapkanPratinjauBerkas(
                    idSesi = ID_SESI_UTAMA,
                    jalurInternal = entri.jalurInternal,
                    batas = batas
                )
            ) {
                is HasilOperasi.Sukses -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(sedangMemuat = false),
                            acaraBukaEditor = AcaraBukaEditorArsip(
                                id = ++urutanAcara,
                                entitas = hasil.nilai
                            )
                        )
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update { st ->
                        st.copy(
                            browser = st.browser.copy(
                                sedangMemuat = false,
                                pesanInfo = hasil.pesan
                            )
                        )
                    }
                }
            }
        }
    }

    private fun mintaEkstrak(jalurTerpilih: Set<String>?) {
        val st = _state.value
        val br = st.browser
        val kap = br.kapabilitasAktif
        if (kap != null && !kap.dapatEkstrak) {
            _state.update {
                it.copy(
                    browser = br.copy(
                        pesanInfo = kap.catatan ?: "Format ini tidak mendukung ekstraksi"
                    )
                )
            }
            return
        }

        val tujuan = st.folderTujuanBawaan ?: st.lokasiPintasan.firstOrNull() ?: run {
            _state.update {
                it.copy(
                    browser = br.copy(
                        pesanInfo = "Buka folder tujuan di tab Berkas sebelum mengekstrak arsip"
                    )
                )
            }
            return
        }

        val daftarCocok = if (jalurTerpilih == null) {
            br.semuaEntriLapisan.filterNot { it.adalahFolder }
        } else {
            br.semuaEntriLapisan.filter { entri ->
                !entri.adalahFolder && jalurTerpilih.any { target ->
                    val t = target.trim('/')
                    entri.jalurInternal == t || entri.jalurInternal.startsWith("$t/")
                }
            }
        }
        val jumlahItem = if (jalurTerpilih == null) {
            daftarCocok.size.coerceAtLeast(br.semuaEntriLapisan.size)
        } else {
            jalurTerpilih.size
        }
        val estimasiUkuran = daftarCocok.sumOf { it.ukuranTidakTerkompresiByte ?: 0L }
        val label = when {
            jalurTerpilih == null -> "Semua isi '${br.jejakLapisan.lastOrNull()?.namaArsip ?: "arsip"}'"
            jalurTerpilih.size == 1 -> jalurTerpilih.first().substringAfterLast('/')
            else -> "${jalurTerpilih.size} item terpilih"
        }

        _state.update {
            it.copy(
                browser = br.copy(
                    dialogKonfirmasiEkstrak = PermintaanKonfirmasiEkstrak(
                        jalurTerpilih = jalurTerpilih,
                        labelTarget = label,
                        jumlahItem = jumlahItem,
                        estimasiUkuranByte = estimasiUkuran,
                        folderTujuan = tujuan
                    )
                )
            )
        }
    }

    private fun jalankanEkstraksi() {
        val st = _state.value
        val br = st.browser
        val konfirmasi = br.dialogKonfirmasiEkstrak ?: return

        pekerjaanEkstrak?.cancel()
        _state.update {
            it.copy(
                browser = br.copy(
                    dialogKonfirmasiEkstrak = null,
                    sedangMengekstrak = true,
                    progresEkstrakDiproses = 0,
                    progresEkstrakTotal = konfirmasi.jumlahItem.coerceAtLeast(1),
                    progresEkstrakNama = "",
                    hasilEkstrakTerakhir = null
                )
            )
        }

        val job = viewModelScope.launch {
            try {
                val hasil = ekstrakArsipUseCase.ekstrak(
                    idSesi = ID_SESI_UTAMA,
                    folderTujuan = konfirmasi.folderTujuan,
                    opsi = OpsiEkstrakArsip(
                        jalurTerpilih = konfirmasi.jalurTerpilih,
                        kebijakanKonflik = konfirmasi.kebijakanKonflik,
                        konfirmasiPengguna = true,
                        kataSandi = konfirmasi.kataSandi,
                        batas = br.batasKebijakan
                    ),
                    onProgres = { selesai, total, jalur ->
                        _state.update { s ->
                            s.copy(
                                browser = s.browser.copy(
                                    progresEkstrakDiproses = selesai,
                                    progresEkstrakTotal = if (total > 0) total else s.browser.progresEkstrakTotal,
                                    progresEkstrakNama = jalur
                                )
                            )
                        }
                    }
                )
                when (hasil) {
                    is HasilOperasi.Sukses -> {
                        val h = hasil.nilai
                        val tutupOtomatis = _state.value.browser.tutupBrowserSetelahEkstrak
                        if (!h.adaGagal && !h.adaKonflik) {
                            if (tutupOtomatis) {
                                ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA)
                                _state.update { s ->
                                    s.copy(
                                        browser = StateBrowserArsip(),
                                        acaraSuksesEkstrak = AcaraSuksesEkstrak(
                                            id = ++urutanAcara,
                                            hasil = h,
                                            folderTujuan = konfirmasi.folderTujuan
                                        )
                                    )
                                }
                            } else {
                                _state.update { s ->
                                    s.copy(
                                        browser = s.browser.copy(
                                            sedangMengekstrak = false,
                                            jalurDipilih = emptySet(),
                                            pesanInfo = "Berhasil mengekstrak ${h.jumlahDiekstrak} berkas"
                                        ),
                                        acaraSuksesEkstrak = AcaraSuksesEkstrak(
                                            id = ++urutanAcara,
                                            hasil = h,
                                            folderTujuan = konfirmasi.folderTujuan
                                        )
                                    )
                                }
                            }
                        } else {
                            _state.update { s ->
                                s.copy(
                                    browser = s.browser.copy(
                                        sedangMengekstrak = false,
                                        jalurDipilih = emptySet(),
                                        hasilEkstrakTerakhir = h
                                    ),
                                    acaraSuksesEkstrak = AcaraSuksesEkstrak(
                                        id = ++urutanAcara,
                                        hasil = h,
                                        folderTujuan = konfirmasi.folderTujuan
                                    )
                                )
                            }
                        }
                    }
                    is HasilOperasi.Gagal -> {
                        val tutupOtomatis = _state.value.browser.tutupBrowserSetelahEkstrak
                        if (tutupOtomatis) {
                            ekstrakArsipUseCase.tutupSesi(ID_SESI_UTAMA)
                            _state.update { s ->
                                s.copy(
                                    browser = StateBrowserArsip(pesanInfo = hasil.pesan)
                                )
                            }
                        } else {
                            _state.update { s ->
                                s.copy(
                                    browser = s.browser.copy(
                                        sedangMengekstrak = false,
                                        pesanInfo = hasil.pesan
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _state.update { s ->
                    s.copy(
                        browser = s.browser.copy(
                            sedangMengekstrak = false,
                            pesanInfo = "Ekstraksi arsip dibatalkan"
                        )
                    )
                }
                throw e
            }
        }
        pekerjaanEkstrak = job
        job.invokeOnCompletion {
            if (pekerjaanEkstrak === job) pekerjaanEkstrak = null
        }
    }

    // ── T47 Kompres / Buat Arsip ──

    private fun bukaDialogKompres(
        daftarSumber: List<ItemSumberKompresi>,
        tujuanInduk: LokasiBerkas
    ) {
        if (daftarSumber.isEmpty()) return
        val formatAwal = FormatArsip.ZIP
        val namaDasar = if (daftarSumber.size == 1) {
            val tunggal = daftarSumber.first()
            if (tunggal.adalahFolder) {
                tunggal.namaDalamArsip
            } else {
                tunggal.namaDalamArsip.substringBeforeLast('.', tunggal.namaDalamArsip).ifBlank { tunggal.namaDalamArsip }
            }
        } else {
            "arsip"
        }
        _state.update {
            it.copy(
                kompres = StateKompresArsip(
                    terbuka = true,
                    tujuanInduk = tujuanInduk,
                    daftarSumber = daftarSumber,
                    formatTerpilih = formatAwal,
                    namaArsipInput = "$namaDasar.${formatAwal.ekstensiUtama}",
                    namaKustomOlehPengguna = false
                )
            )
        }
    }

    private fun ubahFormatKompres(formatBaru: FormatArsip) {
        _state.update { st ->
            val km = st.kompres
            if (!km.dapatPilihGzip && formatBaru == FormatArsip.GZIP) {
                return@update st.copy(
                    kompres = km.copy(
                        pesanGalat = MatriksArsip.untuk(FormatArsip.GZIP).catatan
                    )
                )
            }
            val namaBaru = if (km.namaKustomOlehPengguna) {
                gantiEkstensiJikaBawaan(km.namaArsipInput, formatBaru, km.daftarSumber)
            } else {
                buatNamaDefaultUntukFormat(km.daftarSumber, formatBaru)
            }
            st.copy(
                kompres = km.copy(
                    formatTerpilih = formatBaru,
                    namaArsipInput = namaBaru,
                    konfirmasiTimpa = null,
                    pesanGalat = null
                )
            )
        }
    }

    private fun jalankanKompres(konfirmasiTimpa: Boolean) {
        val km = _state.value.kompres
        val tujuan = km.tujuanInduk ?: return
        val namaBersih = km.namaArsipInput.trim()
        if (!ValidasiNama.sah(namaBersih)) {
            _state.update {
                it.copy(kompres = km.copy(pesanGalat = "Nama arsip tidak sah: $namaBersih"))
            }
            return
        }

        pekerjaanKompres?.cancel()
        _state.update {
            it.copy(
                kompres = km.copy(
                    sedangMengompres = true,
                    progresDiproses = 0,
                    progresTotal = km.daftarSumber.size.coerceAtLeast(1),
                    namaItemSaatIni = "",
                    konfirmasiTimpa = null,
                    pesanGalat = null
                )
            )
        }

        val job = viewModelScope.launch {
            try {
                val hasil = kompresArsipUseCase(
                    daftarSumber = km.daftarSumber,
                    tujuanInduk = tujuan,
                    namaArsip = namaBersih,
                    format = km.formatTerpilih,
                    konfirmasiTimpa = konfirmasiTimpa,
                    onProgres = { selesai, total, nama ->
                        _state.update { s ->
                            s.copy(
                                kompres = s.kompres.copy(
                                    progresDiproses = selesai,
                                    progresTotal = if (total > 0) total else s.kompres.progresTotal,
                                    namaItemSaatIni = nama
                                )
                            )
                        }
                    }
                )
                when (hasil) {
                    is HasilOperasi.Sukses -> {
                        _state.update { s ->
                            s.copy(
                                kompres = StateKompresArsip(),
                                acaraSuksesKompres = AcaraSuksesKompres(
                                    id = ++urutanAcara,
                                    entitasHasil = hasil.nilai
                                )
                            )
                        }
                    }
                    is HasilOperasi.Gagal -> {
                        if (hasil.penyebab == PenyebabGalat.KONFLIK && !konfirmasiTimpa) {
                            _state.update { s ->
                                s.copy(
                                    kompres = s.kompres.copy(
                                        sedangMengompres = false,
                                        konfirmasiTimpa = PermintaanKonfirmasiTimpaKompres(
                                            namaArsip = namaBersih,
                                            pesan = hasil.pesan
                                        )
                                    )
                                )
                            }
                        } else {
                            _state.update { s ->
                                s.copy(
                                    kompres = s.kompres.copy(
                                        sedangMengompres = false,
                                        pesanGalat = hasil.pesan
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _state.update { s ->
                    s.copy(
                        kompres = s.kompres.copy(
                            sedangMengompres = false,
                            pesanGalat = "Pembuatan arsip dibatalkan"
                        )
                    )
                }
                throw e
            }
        }
        pekerjaanKompres = job
        job.invokeOnCompletion {
            if (pekerjaanKompres === job) pekerjaanKompres = null
        }
    }

    private fun tutupDialogKompres() {
        if (_state.value.kompres.sedangMengompres) return
        _state.update { it.copy(kompres = StateKompresArsip()) }
    }

    private fun olahEntriTampil(
        semuaEntri: List<EntriArsip>,
        folderDalam: String,
        kataKunci: String
    ): List<EntriArsipUi> {
        val kueri = kataKunci.trim().lowercase()
        val dasar = if (kueri.isEmpty()) {
            EkstrakArsipUseCase.saringEntriUntukFolder(semuaEntri, folderDalam)
        } else {
            semuaEntri.filter {
                it.nama.lowercase().contains(kueri) || it.jalurInternal.lowercase().contains(kueri)
            }.sortedWith(
                compareByDescending<EntriArsip> { it.adalahFolder }
                    .thenBy { it.jalurInternal.lowercase() }
            )
        }
        return dasar.map { e ->
            EntriArsipUi(
                jalurInternal = e.jalurInternal,
                nama = e.nama,
                adalahFolder = e.adalahFolder,
                ukuranAsliFormat = PemetaanDomainUi.formatUkuran(
                    e.ukuranTidakTerkompresiByte,
                    e.adalahFolder
                ),
                ukuranAsliByte = e.ukuranTidakTerkompresiByte,
                ukuranKompresFormat = PemetaanDomainUi.formatUkuran(
                    e.ukuranTerkompresiByte,
                    e.adalahFolder
                ),
                adalahArsipBersarang = e.adalahArsipBersarang,
                terenkripsi = e.terenkripsi,
                adalahTautanSimbolik = e.adalahTautanSimbolik,
                metodeKompresi = e.metodeKompresi
            )
        }
    }

    private fun buatNamaDefaultUntukFormat(
        daftarSumber: List<ItemSumberKompresi>,
        format: FormatArsip
    ): String {
        val tunggal = daftarSumber.singleOrNull()
        if (format == FormatArsip.GZIP && tunggal != null && !tunggal.adalahFolder) {
            return "${tunggal.namaDalamArsip}.gz"
        }
        val namaDasar = if (tunggal != null) {
            if (tunggal.adalahFolder) {
                tunggal.namaDalamArsip
            } else {
                tunggal.namaDalamArsip.substringBeforeLast('.', tunggal.namaDalamArsip).ifBlank { tunggal.namaDalamArsip }
            }
        } else {
            "arsip"
        }
        val ekstensi = if (format == FormatArsip.TGZ) "tar.gz" else format.ekstensiUtama
        return "$namaDasar.$ekstensi"
    }

    private fun gantiEkstensiJikaBawaan(
        namaLama: String,
        formatBaru: FormatArsip,
        daftarSumber: List<ItemSumberKompresi>
    ): String {
        val bersih = namaLama.trim()
        if (bersih.isEmpty()) return buatNamaDefaultUntukFormat(daftarSumber, formatBaru)
        val lower = bersih.lowercase()
        val akar = when {
            lower.endsWith(".tar.gz") -> bersih.dropLast(7)
            else -> {
                val ext = bersih.substringAfterLast('.', "")
                val semuaExtArsip = FormatArsip.entries.flatMap { it.semuaEkstensi }.toSet()
                if (ext.lowercase() in semuaExtArsip && bersih.length > ext.length + 1) {
                    bersih.dropLast(ext.length + 1)
                } else {
                    bersih
                }
            }
        }
        val ekstensiBaru = if (formatBaru == FormatArsip.TGZ) "tar.gz" else formatBaru.ekstensiUtama
        return "$akar.$ekstensiBaru"
    }

    override fun onCleared() {
        pekerjaanMuat?.cancel()
        pekerjaanEkstrak?.cancel()
        pekerjaanKompres?.cancel()
    }
}