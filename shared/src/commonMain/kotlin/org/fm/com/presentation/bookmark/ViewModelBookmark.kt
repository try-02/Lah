package org.fm.com.presentation.bookmark

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.BookmarkDenganStatus
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.StatusKetersediaanBookmark
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.BookmarkUseCase
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.domain.usecase.TelusuriLokasiUseCase
import org.fm.com.presentation.komponen.PemformatLokasiRamah

@Immutable
data class ItemBookmarkUi(
    val bookmark: EntitasBookmark,
    val nama: String,
    val ringkasanLokasi: String,
    val labelJenis: String,
    val status: StatusKetersediaanBookmark
) {
    val tersedia: Boolean get() = status is StatusKetersediaanBookmark.Tersedia
    val bermasalah: Boolean get() = status.apakahBermasalah
    val teksStatusMasalah: String?
        get() = when (status) {
            is StatusKetersediaanBookmark.IzinDicabut -> "Izin akses dicabut"
            is StatusKetersediaanBookmark.LokasiHilang -> "Lokasi tidak ditemukan"
            is StatusKetersediaanBookmark.TidakTersedia -> "Tidak tersedia: ${status.pesan}"
            else -> null
        }
}

@Immutable
data class StatePemilihUlangBookmark(
    val terbuka: Boolean = false,
    val bookmarkLama: EntitasBookmark? = null,
    val lokasiSaatIni: LokasiBerkas? = null,
    val segmenBreadcrumb: List<SegmenLokasi> = emptyList(),
    val daftarEntitas: List<EntitasBerkas> = emptyList(),
    val sedangMemuat: Boolean = false,
    val pesanGalat: String? = null
)

@Immutable
data class StateBookmark(
    val daftarBookmark: List<ItemBookmarkUi> = emptyList(),
    val sedangMemuat: Boolean = true,
    val sedangMemeriksaStatus: Boolean = false,
    val dialogHapusSemuaTerbuka: Boolean = false,
    val pemilihUlang: StatePemilihUlangBookmark = StatePemilihUlangBookmark(),
    val pesanInfo: String? = null,
    val pesanGalat: String? = null
) {
    val jumlahItem: Int get() = daftarBookmark.size
}

sealed interface IntentBookmark {
    data object PeriksaUlangSemua : IntentBookmark
    data class HapusItem(val lokasi: LokasiBerkas) : IntentBookmark
    data class SetDialogHapusSemua(val buka: Boolean) : IntentBookmark
    data object KonfirmasiHapusSemua : IntentBookmark
    data class MulaiPilihUlang(val bookmark: EntitasBookmark) : IntentBookmark
    data class BukaFolderDiPemilih(val lokasi: LokasiBerkas) : IntentBookmark
    data class KlikSegmenPemilih(val segmen: SegmenLokasi) : IntentBookmark
    data class PilihEntitasBaruBookmark(val entitas: EntitasBerkas) : IntentBookmark
    data class TerimaLokasiSistemUntukBookmark(val lokasi: LokasiBerkas) : IntentBookmark
    data object GunakanFolderSaatIniUntukBookmark : IntentBookmark
    data object TutupPemilihUlang : IntentBookmark
    data class SetPesanInfo(val pesan: String?) : IntentBookmark
    data object HapusPesanGalat : IntentBookmark
}

class ViewModelBookmark(
    private val bookmarkUseCase: BookmarkUseCase,
    private val repositoriBerkas: RepositoriBerkas,
    private val telusuriLokasiUseCase: TelusuriLokasiUseCase = TelusuriLokasiUseCase(),
    private val lokasiPintasanAwal: List<LokasiBerkas> = emptyList(),
    private val siapkanAksesLokasi: (suspend (LokasiBerkas) -> LokasiBerkas)? = null
) : ViewModel() {

    private val _state = MutableStateFlow(StateBookmark())
    val state: StateFlow<StateBookmark> = _state.asStateFlow()

    private var pekerjaanAmati: Job? = null
    private var pekerjaanCek: Job? = null
    private var pekerjaanPemilih: Job? = null
    private var daftarMentah: List<EntitasBookmark> = emptyList()
    private val riwayatHierarki = mutableListOf<LokasiBerkas>()

    init {
        amatiBookmark()
    }

    fun prosesIntent(intent: IntentBookmark) {
        when (intent) {
            is IntentBookmark.PeriksaUlangSemua -> periksaKetersediaan(daftarMentah)
            is IntentBookmark.HapusItem -> hapusSatu(intent.lokasi)
            is IntentBookmark.SetDialogHapusSemua -> _state.update { it.copy(dialogHapusSemuaTerbuka = intent.buka) }
            is IntentBookmark.KonfirmasiHapusSemua -> hapusSemua()
            is IntentBookmark.MulaiPilihUlang -> mulaiPilihUlang(intent.bookmark)
            is IntentBookmark.BukaFolderDiPemilih -> muatFolderPemilih(intent.lokasi, dariSegmen = false)
            is IntentBookmark.KlikSegmenPemilih -> klikSegmenPemilih(intent.segmen)
            is IntentBookmark.PilihEntitasBaruBookmark -> terapkanLokasiPengganti(
                lokasiBaru = intent.entitas.lokasi,
                jenisBaru = intent.entitas.jenis,
                adalahFolderBaru = intent.entitas.jenis == JenisBerkas.FOLDER
            )
            is IntentBookmark.TerimaLokasiSistemUntukBookmark -> terapkanLokasiPengganti(
                lokasiBaru = intent.lokasi,
                jenisBaru = JenisBerkas.FOLDER,
                adalahFolderBaru = true
            )
            is IntentBookmark.GunakanFolderSaatIniUntukBookmark -> {
                val lok = _state.value.pemilihUlang.lokasiSaatIni ?: return
                terapkanLokasiPengganti(
                    lokasiBaru = lok,
                    jenisBaru = JenisBerkas.FOLDER,
                    adalahFolderBaru = true
                )
            }
            is IntentBookmark.TutupPemilihUlang -> {
                pekerjaanPemilih?.cancel()
                _state.update { it.copy(pemilihUlang = StatePemilihUlangBookmark()) }
            }
            is IntentBookmark.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
            is IntentBookmark.HapusPesanGalat -> _state.update { it.copy(pesanGalat = null) }
        }
    }

    private fun amatiBookmark() {
        pekerjaanAmati?.cancel()
        pekerjaanAmati = viewModelScope.launch {
            bookmarkUseCase.amatiDaftarBookmark()
                .catch { e ->
                    _state.update {
                        it.copy(
                            sedangMemuat = false,
                            pesanGalat = e.message ?: "Gagal memuat bookmark"
                        )
                    }
                }
                .collect { daftar ->
                    daftarMentah = daftar
                    _state.update { st ->
                        st.copy(
                            daftarBookmark = daftar.map { b ->
                                BookmarkDenganStatus(b, StatusKetersediaanBookmark.BelumDiperiksa).keUi()
                            },
                            sedangMemuat = false,
                            pesanGalat = null
                        )
                    }
                    periksaKetersediaan(daftar)
                }
        }
    }

    private fun periksaKetersediaan(daftar: List<EntitasBookmark>) {
        pekerjaanCek?.cancel()
        if (daftar.isEmpty()) return
        pekerjaanCek = viewModelScope.launch {
            _state.update { it.copy(sedangMemeriksaStatus = true) }
            val hasil = daftar.map { item ->
                val lokasiSiap = runCatching { siapkanAksesLokasi?.invoke(item.lokasi) ?: item.lokasi }
                    .getOrDefault(item.lokasi)
                val status = bookmarkUseCase.periksaKetersediaanSatu(item.copy(lokasi = lokasiSiap))
                BookmarkDenganStatus(item.copy(lokasi = lokasiSiap), status).keUi()
            }
            _state.update {
                it.copy(
                    daftarBookmark = hasil,
                    sedangMemeriksaStatus = false
                )
            }
        }
    }

    private fun hapusSatu(lokasi: LokasiBerkas) {
        viewModelScope.launch {
            when (val res = bookmarkUseCase.hapusBookmark(lokasi)) {
                is HasilOperasi.Sukses -> _state.update { it.copy(pesanInfo = "Bookmark dihapus") }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanInfo = res.pesan) }
            }
        }
    }

    private fun hapusSemua() {
        _state.update { it.copy(dialogHapusSemuaTerbuka = false) }
        viewModelScope.launch {
            when (val res = bookmarkUseCase.hapusSemua()) {
                is HasilOperasi.Sukses -> _state.update { it.copy(pesanInfo = "Semua bookmark dihapus") }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanInfo = res.pesan) }
            }
        }
    }

    private fun mulaiPilihUlang(bookmark: EntitasBookmark) {
        val lokasiAwal = lokasiPintasanAwal.firstOrNull() ?: bookmark.lokasi
        riwayatHierarki.clear()
        if (lokasiAwal.jenis != JenisLokasi.POSIX) {
            riwayatHierarki.add(lokasiAwal)
        }
        _state.update {
            it.copy(
                pemilihUlang = StatePemilihUlangBookmark(
                    terbuka = true,
                    bookmarkLama = bookmark,
                    lokasiSaatIni = lokasiAwal
                )
            )
        }
        muatFolderPemilih(lokasiAwal, dariSegmen = false)
    }

    private fun klikSegmenPemilih(segmen: SegmenLokasi) {
        val idx = riwayatHierarki.indexOfFirst { it == segmen.lokasi }
        if (idx >= 0) {
            while (riwayatHierarki.size > idx + 1) {
                riwayatHierarki.removeAt(riwayatHierarki.lastIndex)
            }
        }
        muatFolderPemilih(segmen.lokasi, dariSegmen = true)
    }

    private fun muatFolderPemilih(lokasi: LokasiBerkas, dariSegmen: Boolean) {
        pekerjaanPemilih?.cancel()
        if (!dariSegmen) {
            if (lokasi.jenis == JenisLokasi.POSIX) {
                riwayatHierarki.clear()
            } else {
                val adaAnak = _state.value.pemilihUlang.daftarEntitas.any { it.lokasi == lokasi }
                if (adaAnak && riwayatHierarki.isNotEmpty() && riwayatHierarki.last().jenis == lokasi.jenis) {
                    if (riwayatHierarki.last() != lokasi) riwayatHierarki.add(lokasi)
                } else {
                    val idx = riwayatHierarki.indexOfFirst { it == lokasi }
                    if (idx >= 0) {
                        while (riwayatHierarki.size > idx + 1) riwayatHierarki.removeAt(riwayatHierarki.lastIndex)
                    } else {
                        riwayatHierarki.clear()
                        riwayatHierarki.add(lokasi)
                    }
                }
            }
        }

        val segmen = telusuriLokasiUseCase.buatSegmenBreadcrumb(lokasi, riwayatHierarki)
        _state.update {
            it.copy(
                pemilihUlang = it.pemilihUlang.copy(
                    terbuka = true,
                    lokasiSaatIni = lokasi,
                    segmenBreadcrumb = segmen,
                    sedangMemuat = true,
                    pesanGalat = null
                )
            )
        }
        pekerjaanPemilih = viewModelScope.launch {
            val lokasiSiap = runCatching { siapkanAksesLokasi?.invoke(lokasi) ?: lokasi }.getOrDefault(lokasi)
            val hasil = runCatching { repositoriBerkas.daftarIsi(lokasiSiap).first() }.getOrNull()
            when (hasil) {
                is HasilOperasi.Sukses -> {
                    val bookmarkLama = _state.value.pemilihUlang.bookmarkLama
                    val entitas = if (bookmarkLama?.adalahFolder == true) {
                        hasil.nilai.filter { it.jenis == JenisBerkas.FOLDER }
                    } else {
                        hasil.nilai
                    }.sortedWith(compareByDescending<EntitasBerkas> { it.jenis == JenisBerkas.FOLDER }.thenBy { it.nama.lowercase() })
                    _state.update {
                        it.copy(
                            pemilihUlang = it.pemilihUlang.copy(
                                daftarEntitas = entitas,
                                sedangMemuat = false,
                                pesanGalat = null
                            )
                        )
                    }
                }
                is HasilOperasi.Gagal -> _state.update {
                    it.copy(
                        pemilihUlang = it.pemilihUlang.copy(
                            daftarEntitas = emptyList(),
                            sedangMemuat = false,
                            pesanGalat = hasil.pesan
                        )
                    )
                }
                null -> _state.update {
                    it.copy(
                        pemilihUlang = it.pemilihUlang.copy(
                            sedangMemuat = false,
                            pesanGalat = "Gagal memuat isi folder"
                        )
                    )
                }
            }
        }
    }

    private fun terapkanLokasiPengganti(
        lokasiBaru: LokasiBerkas,
        jenisBaru: JenisBerkas?,
        adalahFolderBaru: Boolean
    ) {
        val bookmarkLama = _state.value.pemilihUlang.bookmarkLama ?: return
        viewModelScope.launch {
            val lokasiSiap = runCatching { siapkanAksesLokasi?.invoke(lokasiBaru) ?: lokasiBaru }.getOrDefault(lokasiBaru)
            when (
                val hasil = bookmarkUseCase.gantiLokasiBookmark(
                    bookmarkLama = bookmarkLama,
                    lokasiBaru = lokasiSiap,
                    jenisBerkasBaru = jenisBaru,
                    adalahFolderBaru = adalahFolderBaru
                )
            ) {
                is HasilOperasi.Sukses -> {
                    _state.update {
                        it.copy(
                            pemilihUlang = StatePemilihUlangBookmark(),
                            pesanInfo = "Lokasi bookmark diperbarui: ${hasil.nilai.namaTampilan}"
                        )
                    }
                }
                is HasilOperasi.Gagal -> {
                    _state.update {
                        it.copy(
                            pemilihUlang = it.pemilihUlang.copy(pesanGalat = hasil.pesan),
                            pesanInfo = hasil.pesan
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        pekerjaanAmati?.cancel()
        pekerjaanCek?.cancel()
        pekerjaanPemilih?.cancel()
    }
}

private fun BookmarkDenganStatus.keUi(): ItemBookmarkUi = ItemBookmarkUi(
    bookmark = bookmark,
    nama = bookmark.namaTampilan,
    ringkasanLokasi = PemformatLokasiRamah.formatRingkas(bookmark.lokasi),
    labelJenis = when (bookmark.jenisTarget) {
        JenisTargetBookmark.FOLDER -> "Folder"
        JenisTargetBookmark.ARSIP -> "Arsip"
        JenisTargetBookmark.BERKAS -> "Berkas"
    },
    status = status
)