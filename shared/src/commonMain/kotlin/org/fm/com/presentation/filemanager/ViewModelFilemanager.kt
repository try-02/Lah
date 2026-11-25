package org.fm.com.presentation.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.repository.RepositoriOperasiBerkas
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.BuatBerkasUseCase
import org.fm.com.domain.usecase.DaftarFolderUseCase
import org.fm.com.domain.usecase.FilterBerkasUseCase
import org.fm.com.domain.usecase.GantiNamaBerkasUseCase
import org.fm.com.domain.usecase.HapusBerkasUseCase
import org.fm.com.domain.usecase.KemampuanDipakaiUseCase
import org.fm.com.domain.usecase.KriteriaUrutan
import org.fm.com.domain.usecase.RiwayatLokasiUseCase
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.domain.usecase.TelusuriLokasiUseCase
import org.fm.com.domain.usecase.UrutkanBerkasUseCase
import org.fm.com.presentation.filemanager.PemetaanDomainUi.keUi
import kotlinx.datetime.TimeZone

private const val AMBANG_PEMROSESAN_LATAR = 100

class ViewModelFilemanager(
    private val repositoriBerkas: RepositoriBerkas,
    repositoriOperasi: RepositoriOperasiBerkas? = repositoriBerkas as? RepositoriOperasiBerkas,
    private val riwayatLokasiUseCase: RiwayatLokasiUseCase? = null,
    private val daftarFolderUseCase: DaftarFolderUseCase = DaftarFolderUseCase(repositoriBerkas),
    private val urutkanBerkasUseCase: UrutkanBerkasUseCase = UrutkanBerkasUseCase(),
    private val filterBerkasUseCase: FilterBerkasUseCase = FilterBerkasUseCase(),
    private val telusuriLokasiUseCase: TelusuriLokasiUseCase = TelusuriLokasiUseCase(),
    private val kemampuanDipakaiUseCase: KemampuanDipakaiUseCase = KemampuanDipakaiUseCase(),
    private val buatBerkasUseCase: BuatBerkasUseCase? = repositoriOperasi?.let { BuatBerkasUseCase(it) },
    private val gantiNamaBerkasUseCase: GantiNamaBerkasUseCase? = repositoriOperasi?.let { GantiNamaBerkasUseCase(it) },
    private val hapusBerkasUseCase: HapusBerkasUseCase? = repositoriOperasi?.let { HapusBerkasUseCase(it) },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val _state = MutableStateFlow(StateFilemanager())
    val state: StateFlow<StateFilemanager> = _state.asStateFlow()

    private var pekerjaanMuatFolder: Job? = null
    private var pekerjaanSaring: Job? = null
    private var daftarEntitasMentah: List<EntitasBerkas> = emptyList()
    private val riwayatNavigasi = mutableListOf<LokasiBerkas>()
    private val riwayatHierarki = mutableListOf<LokasiBerkas>()

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
            is IntentFilemanager.SetBukaDialogBuat -> _state.update { it.copy(dialogBuatTerbuka = intent.buka) }
            is IntentFilemanager.BuatFolderBaru -> buatFolder(intent.nama, konfirmasi = false)
            is IntentFilemanager.BuatBerkasBaru -> buatBerkas(intent.nama, konfirmasi = false)
            is IntentFilemanager.BukaDialogProperti -> _state.update { it.copy(dialogPropertiItem = intent.item) }
            is IntentFilemanager.BukaDialogRename -> _state.update { it.copy(dialogRenameItem = intent.item) }
            is IntentFilemanager.EksekusiRename -> eksekusiRename(intent.sumber, intent.namaBaru, konfirmasi = false)
            is IntentFilemanager.KonfirmasiTimpaAksi -> jalankanAksiKonfirmasiTimpa(intent.permintaan)
            is IntentFilemanager.TutupKonfirmasiTimpa -> _state.update { it.copy(konfirmasiTimpa = null) }
            is IntentFilemanager.BukaDialogHapus -> _state.update { it.copy(dialogHapusItems = intent.items) }
            is IntentFilemanager.EksekusiHapusTerpilih -> eksekusiHapus()
            is IntentFilemanager.BukaDialogBukaDengan -> _state.update { it.copy(dialogBukaDenganItem = intent.item) }
            is IntentFilemanager.BukaDialogBagikan -> _state.update { it.copy(dialogBagikanItem = intent.item) }
            is IntentFilemanager.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
            is IntentFilemanager.HapusPesanGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun navigasiMasuk(lokasi: LokasiBerkas) {
        if (riwayatNavigasi.isEmpty() || riwayatNavigasi.last() != lokasi) {
            riwayatNavigasi.add(lokasi)
        }
        val adalahAnakLangsung = daftarEntitasMentah.any { it.lokasi == lokasi }
        if (lokasi.jenis == JenisLokasi.POSIX) {
            riwayatHierarki.clear()
        } else if (adalahAnakLangsung && riwayatHierarki.isNotEmpty() && riwayatHierarki.last().jenis == lokasi.jenis) {
            if (riwayatHierarki.last() != lokasi) {
                riwayatHierarki.add(lokasi)
            }
        } else {
            val idxAda = riwayatHierarki.indexOfFirst { it == lokasi }
            if (idxAda >= 0) {
                while (riwayatHierarki.size > idxAda + 1) {
                    riwayatHierarki.removeAt(riwayatHierarki.lastIndex)
                }
            } else {
                riwayatHierarki.clear()
                riwayatHierarki.add(lokasi)
            }
        }
        muatFolderInternal(lokasi)
    }

    private fun navigasiKeSegmen(segmen: SegmenLokasi) {
        if (segmen.lokasi == _state.value.lokasiSaatIni) return
        val idxHierarki = riwayatHierarki.indexOfFirst { it == segmen.lokasi }
        if (idxHierarki >= 0) {
            while (riwayatHierarki.size > idxHierarki + 1) {
                riwayatHierarki.removeAt(riwayatHierarki.lastIndex)
            }
        }
        if (riwayatNavigasi.isEmpty() || riwayatNavigasi.last() != segmen.lokasi) {
            riwayatNavigasi.add(segmen.lokasi)
        }
        muatFolderInternal(segmen.lokasi)
    }

    private fun navigasiKembali() {
        if (riwayatNavigasi.size > 1) {
            riwayatNavigasi.removeAt(riwayatNavigasi.lastIndex)
            val tujuan = riwayatNavigasi.last()
            val idxHierarki = riwayatHierarki.indexOfFirst { it == tujuan }
            if (idxHierarki >= 0) {
                while (riwayatHierarki.size > idxHierarki + 1) {
                    riwayatHierarki.removeAt(riwayatHierarki.lastIndex)
                }
            } else if (tujuan.jenis != JenisLokasi.POSIX) {
                riwayatHierarki.clear()
                riwayatHierarki.add(tujuan)
            }
            muatFolderInternal(tujuan)
        }
    }

    private fun muatFolderInternal(lokasi: LokasiBerkas) {
        pekerjaanMuatFolder?.cancel()
        pekerjaanSaring?.cancel()
        val kemampuan = repositoriBerkas.kapabilitas(lokasi)
        val kemampuanUi = ModelKemampuanUi.dari(kemampuan, kemampuanDipakaiUseCase)
        val breadcrumb = telusuriLokasiUseCase.buatSegmenBreadcrumb(lokasi, riwayatHierarki)
        _state.update {
            it.copy(
                lokasiSaatIni = lokasi,
                segmenBreadcrumb = breadcrumb,
                sedangMemuat = true,
                pesanGalat = null,
                itemTerpilih = emptySet(),
                kemampuanLokasi = kemampuan,
                kemampuanUi = kemampuanUi,
                dapatNavigasiKembali = riwayatNavigasi.size > 1
            )
        }
        pekerjaanMuatFolder = viewModelScope.launch {
            var sudahCatatRiwayat = false
            daftarFolderUseCase(lokasi, tampilkanTersembunyi = true)
                .catch { e ->
                    daftarEntitasMentah = emptyList()
                    _state.update {
                        it.copy(
                            daftarItem = emptyList(),
                            sedangMemuat = false,
                            pesanGalat = e.message ?: "Gagal memuat daftar berkas"
                        )
                    }
                }
                .collect { hasil ->
                    when (hasil) {
                        is HasilOperasi.Sukses -> {
                            if (!sudahCatatRiwayat) {
                                sudahCatatRiwayat = true
                                riwayatLokasiUseCase?.catatKunjungan(lokasi, adalahFolder = true)
                            }
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

    private fun buatFolder(nama: String, konfirmasi: Boolean) {
        val induk = _state.value.lokasiSaatIni ?: return
        val uc = buatBerkasUseCase ?: return laporkanOperasiTakDidukung()
        viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update { it.copy(dialogBuatTerbuka = false, konfirmasiTimpa = null, sedangMemuat = true) }
                when (val hasil = uc.buatFolder(induk, nama, konfirmasi = konfirmasi)) {
                    is HasilOperasi.Sukses -> {
                        _state.update { it.copy(pesanInfo = "Folder '${hasil.nilai.nama}' berhasil dibuat") }
                        lanjutSegarkan = true
                        segarkan()
                    }
                    is HasilOperasi.Gagal -> {
                        if (hasil.penyebab == PenyebabGalat.KONFLIK && !konfirmasi) {
                            _state.update {
                                it.copy(
                                    sedangMemuat = false,
                                    konfirmasiTimpa = PermintaanKonfirmasiTimpa(JenisAksiTimpa.BUAT_FOLDER, nama)
                                )
                            }
                        } else {
                            _state.update { it.copy(sedangMemuat = false, pesanInfo = hasil.pesan) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pesanInfo = e.message ?: "Gagal membuat folder") }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false) }
                }
            }
        }
    }

    private fun buatBerkas(nama: String, konfirmasi: Boolean) {
        val induk = _state.value.lokasiSaatIni ?: return
        val uc = buatBerkasUseCase ?: return laporkanOperasiTakDidukung()
        viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update { it.copy(dialogBuatTerbuka = false, konfirmasiTimpa = null, sedangMemuat = true) }
                when (val hasil = uc.buatBerkasKosong(induk, nama, konfirmasi = konfirmasi)) {
                    is HasilOperasi.Sukses -> {
                        _state.update { it.copy(pesanInfo = "Berkas '${hasil.nilai.nama}' berhasil dibuat") }
                        lanjutSegarkan = true
                        segarkan()
                    }
                    is HasilOperasi.Gagal -> {
                        if (hasil.penyebab == PenyebabGalat.KONFLIK && !konfirmasi) {
                            _state.update {
                                it.copy(
                                    sedangMemuat = false,
                                    konfirmasiTimpa = PermintaanKonfirmasiTimpa(JenisAksiTimpa.BUAT_BERKAS, nama)
                                )
                            }
                        } else {
                            _state.update { it.copy(sedangMemuat = false, pesanInfo = hasil.pesan) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pesanInfo = e.message ?: "Gagal membuat berkas") }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false) }
                }
            }
        }
    }

    private fun eksekusiRename(sumber: LokasiBerkas, namaBaru: String, konfirmasi: Boolean) {
        val uc = gantiNamaBerkasUseCase ?: return laporkanOperasiTakDidukung()
        val indukSaatIni = _state.value.lokasiSaatIni
        viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update { it.copy(dialogRenameItem = null, konfirmasiTimpa = null, sedangMemuat = true) }
                when (val hasil = uc(sumber, namaBaru, konfirmasi = konfirmasi, induk = indukSaatIni)) {
                    is HasilOperasi.Sukses -> {
                        _state.update { it.copy(pesanInfo = "Nama berhasil diubah menjadi '${hasil.nilai.nama}'") }
                        lanjutSegarkan = true
                        segarkan()
                    }
                    is HasilOperasi.Gagal -> {
                        if (hasil.penyebab == PenyebabGalat.KONFLIK && !konfirmasi) {
                            _state.update {
                                it.copy(
                                    sedangMemuat = false,
                                    konfirmasiTimpa = PermintaanKonfirmasiTimpa(JenisAksiTimpa.GANTI_NAMA, namaBaru, sumber)
                                )
                            }
                        } else {
                            _state.update { it.copy(sedangMemuat = false, pesanInfo = hasil.pesan) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pesanInfo = e.message ?: "Gagal mengganti nama") }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false) }
                }
            }
        }
    }

    private fun jalankanAksiKonfirmasiTimpa(permintaan: PermintaanKonfirmasiTimpa) {
        when (permintaan.jenis) {
            JenisAksiTimpa.BUAT_FOLDER -> buatFolder(permintaan.nama, konfirmasi = true)
            JenisAksiTimpa.BUAT_BERKAS -> buatBerkas(permintaan.nama, konfirmasi = true)
            JenisAksiTimpa.GANTI_NAMA -> {
                val sumber = permintaan.sumber ?: return
                eksekusiRename(sumber, permintaan.nama, konfirmasi = true)
            }
        }
    }

    private fun eksekusiHapus() {
        val targets = _state.value.dialogHapusItems ?: return
        val uc = hapusBerkasUseCase ?: return laporkanOperasiTakDidukung()
        viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update { it.copy(dialogHapusItems = null, sedangMemuat = true) }
                var jumlahSukses = 0
                var pesanGagal: String? = null
                for (lokasi in targets) {
                    when (val hasil = uc(lokasi, konfirmasi = true)) {
                        is HasilOperasi.Sukses -> jumlahSukses++
                        is HasilOperasi.Gagal -> if (pesanGagal == null) pesanGagal = hasil.pesan
                    }
                }
                val pesanRingkas = when {
                    pesanGagal == null -> "Berhasil menghapus $jumlahSukses item"
                    jumlahSukses > 0 -> "Terhapus $jumlahSukses dari ${targets.size} item. Gagal: $pesanGagal"
                    else -> "Gagal menghapus: $pesanGagal"
                }
                _state.update { it.copy(itemTerpilih = emptySet(), pesanInfo = pesanRingkas) }
                if (jumlahSukses > 0) {
                    lanjutSegarkan = true
                    segarkan()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(pesanInfo = e.message ?: "Gagal menghapus item") }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false) }
                }
            }
        }
    }

    private fun laporkanOperasiTakDidukung() {
        _state.update {
            it.copy(pesanInfo = "Operasi pengubahan berkas tidak didukung pada repositori ini")
        }
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
        pekerjaanSaring?.cancel()
        val entitas = daftarEntitasMentah
        val st = _state.value
        if (entitas.size <= AMBANG_PEMROSESAN_LATAR) {
            val uiList = olahDaftarUi(entitas, st)
            _state.update { it.copy(daftarItem = uiList, sedangMemuat = false) }
        } else {
            pekerjaanSaring = viewModelScope.launch {
                val uiList = withContext(computeDispatcher) {
                    olahDaftarUi(entitas, st)
                }
                _state.update { it.copy(daftarItem = uiList, sedangMemuat = false) }
            }
        }
    }

    private fun olahDaftarUi(entitas: List<EntitasBerkas>, st: StateFilemanager): List<ItemBerkasUi> {
        val tersaring = filterBerkasUseCase(
            daftar = entitas,
            tampilkanTersembunyi = st.tampilkanTersembunyi,
            kataKunci = st.kataKunciSaring
        )
        val terurut = urutkanBerkasUseCase(
            daftar = tersaring,
            kriteria = st.kriteriaUrutan,
            arah = st.arahUrutan,
            folderDiAtas = st.folderDiAtas
        )
        val zonaWaktu = TimeZone.currentSystemDefault()
        return terurut.map { it.keUi(zonaWaktu) }
    }
}