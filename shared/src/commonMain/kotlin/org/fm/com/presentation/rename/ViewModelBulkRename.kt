package org.fm.com.presentation.rename

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.KemampuanPenyimpanan
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.rename.HasilPratinjauRename
import org.fm.com.domain.rename.KonfigurasiNomorUrut
import org.fm.com.domain.rename.OpsiParserPola
import org.fm.com.domain.rename.PratinjauRename
import org.fm.com.domain.repository.RepositoriBerkas
import org.fm.com.domain.usecase.HasilEksekusiRename
import org.fm.com.domain.usecase.HasilJalankanRenameMassal
import org.fm.com.domain.usecase.JalankanRenameMassalUseCase
import org.fm.com.domain.usecase.RingkasanKonfirmasiRename

private const val POLA_BAWAAN = "{P}_{zN}{S}"

enum class UrutanItemRename(val label: String) {
    NAMA_NAIK("Nama A-Z"),
    NAMA_TURUN("Nama Z-A"),
    TANGGAL_NAIK("Terlama"),
    TANGGAL_TURUN("Terbaru"),
    UKURAN_NAIK("Terkecil"),
    UKURAN_TURUN("Terbesar"),
    BALIK("Balik Urutan")
}

data class AcaraSuksesBulkRename(
    val id: Long,
    val jumlahBerhasil: Int
)

@Immutable
data class StateBulkRename(
    val terbuka: Boolean = false,
    val folderInduk: LokasiBerkas? = null,
    val daftarEntitasUrut: List<EntitasBerkas> = emptyList(),
    val pola: String = POLA_BAWAAN,
    val angkaAwalInput: String = "1",
    val langkahInput: String = "1",
    val izinkanAngkaAwalInline: Boolean = true,
    val sedangMemuatIsiFolder: Boolean = false,
    val pratinjau: HasilPratinjauRename = HasilPratinjauRename(pola = POLA_BAWAAN),
    val galatAngkaNomor: String? = null,
    val ringkasanKonfirmasi: RingkasanKonfirmasiRename? = null,
    val sedangMengeksekusi: Boolean = false,
    val progresDiproses: Int = 0,
    val progresTotal: Int = 0,
    val namaDiprosesSaatIni: String = "",
    val hasilEksekusi: HasilEksekusiRename? = null,
    val pesanGalatEksekusi: String? = null,
    val acaraSuksesPenuh: AcaraSuksesBulkRename? = null,
    val perluSegarkanSaatTutup: Boolean = false
) {
    val bolehLanjutKonfirmasi: Boolean
        get() = terbuka &&
            !sedangMengeksekusi &&
            galatAngkaNomor == null &&
            pratinjau.dapatDieksekusi
}

sealed interface IntentBulkRename {
    data class Buka(
        val folderInduk: LokasiBerkas,
        val daftarItem: List<EntitasBerkas>,
        val kemampuanLokasi: KemampuanPenyimpanan? = null
    ) : IntentBulkRename

    data object Tutup : IntentBulkRename
    data class UbahPola(val pola: String) : IntentBulkRename
    data class SisipToken(val token: String) : IntentBulkRename
    data class UbahAngkaAwal(val nilai: String) : IntentBulkRename
    data class UbahLangkah(val nilai: String) : IntentBulkRename
    data object ToggleAngkaAwalInline : IntentBulkRename
    data object ResetPolaBawaan : IntentBulkRename
    data class GeserItemNaik(val indeks: Int) : IntentBulkRename
    data class GeserItemTurun(val indeks: Int) : IntentBulkRename
    data class TerapkanUrutan(val urutan: UrutanItemRename) : IntentBulkRename
    data object MintaKonfirmasiEksekusi : IntentBulkRename
    data object BatalKonfirmasiEksekusi : IntentBulkRename
    data object KonfirmasiDanJalankan : IntentBulkRename
    data object BatalkanEksekusi : IntentBulkRename
    data object TutupLaporanHasil : IntentBulkRename
    data object KonsumsiAcaraSukses : IntentBulkRename
    data object HapusGalatEksekusi : IntentBulkRename
}

class ViewModelBulkRename(
    private val repositoriBerkas: RepositoriBerkas,
    private val jalankanRenameMassalUseCase: JalankanRenameMassalUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StateBulkRename())
    val state: StateFlow<StateBulkRename> = _state.asStateFlow()

    private var namaDiFolderDiskCache: List<String> = emptyList()
    private var kemampuanLokasiCache: KemampuanPenyimpanan? = null
    private var pekerjaanMuatFolder: Job? = null
    private var pekerjaanEksekusi: Job? = null
    private var idSesiBuka = 0L
    private var urutanAcara = 0L

    fun prosesIntent(intent: IntentBulkRename) {
        when (intent) {
            is IntentBulkRename.Buka -> bukaDialog(
                folderInduk = intent.folderInduk,
                daftarItem = intent.daftarItem,
                kemampuan = intent.kemampuanLokasi
            )
            is IntentBulkRename.Tutup -> tutupDialog()
            is IntentBulkRename.UbahPola -> perbaruiDanHitung { it.copy(pola = intent.pola, pesanGalatEksekusi = null) }
            is IntentBulkRename.SisipToken -> perbaruiDanHitung {
                it.copy(pola = it.pola + intent.token, pesanGalatEksekusi = null)
            }
            is IntentBulkRename.UbahAngkaAwal -> perbaruiDanHitung {
                it.copy(angkaAwalInput = intent.nilai, pesanGalatEksekusi = null)
            }
            is IntentBulkRename.UbahLangkah -> perbaruiDanHitung {
                it.copy(langkahInput = intent.nilai, pesanGalatEksekusi = null)
            }
            is IntentBulkRename.ToggleAngkaAwalInline -> perbaruiDanHitung {
                it.copy(izinkanAngkaAwalInline = !it.izinkanAngkaAwalInline, pesanGalatEksekusi = null)
            }
            is IntentBulkRename.ResetPolaBawaan -> perbaruiDanHitung {
                it.copy(
                    pola = POLA_BAWAAN,
                    angkaAwalInput = "1",
                    langkahInput = "1",
                    pesanGalatEksekusi = null
                )
            }
            is IntentBulkRename.GeserItemNaik -> geserItem(intent.indeks, arah = -1)
            is IntentBulkRename.GeserItemTurun -> geserItem(intent.indeks, arah = 1)
            is IntentBulkRename.TerapkanUrutan -> terapkanUrutan(intent.urutan)
            is IntentBulkRename.MintaKonfirmasiEksekusi -> mintaKonfirmasiEksekusi()
            is IntentBulkRename.BatalKonfirmasiEksekusi -> _state.update { it.copy(ringkasanKonfirmasi = null) }
            is IntentBulkRename.KonfirmasiDanJalankan -> jalankanEksekusi()
            is IntentBulkRename.BatalkanEksekusi -> pekerjaanEksekusi?.cancel()
            is IntentBulkRename.TutupLaporanHasil -> tutupLaporanHasil()
            is IntentBulkRename.KonsumsiAcaraSukses -> _state.update { it.copy(acaraSuksesPenuh = null) }
            is IntentBulkRename.HapusGalatEksekusi -> _state.update { it.copy(pesanGalatEksekusi = null) }
        }
    }

    private fun bukaDialog(
        folderInduk: LokasiBerkas,
        daftarItem: List<EntitasBerkas>,
        kemampuan: KemampuanPenyimpanan?
    ) {
        val unik = daftarItem.distinctBy { it.lokasi }
        if (unik.isEmpty()) return

        pekerjaanMuatFolder?.cancel()
        pekerjaanEksekusi?.cancel()
        val sesiSaatIni = ++idSesiBuka

        val kemampuanAktif = kemampuan ?: repositoriBerkas.kapabilitas(folderInduk)
        kemampuanLokasiCache = kemampuanAktif
        namaDiFolderDiskCache = unik.map { it.nama }

        val stateAwal = StateBulkRename(
            terbuka = true,
            folderInduk = folderInduk,
            daftarEntitasUrut = unik,
            pola = POLA_BAWAAN,
            angkaAwalInput = "1",
            langkahInput = "1",
            izinkanAngkaAwalInline = true,
            sedangMemuatIsiFolder = true,
            ringkasanKonfirmasi = null,
            sedangMengeksekusi = false,
            hasilEksekusi = null,
            pesanGalatEksekusi = null,
            perluSegarkanSaatTutup = false
        )
        _state.value = hitungStatePratinjau(stateAwal)

        pekerjaanMuatFolder = viewModelScope.launch {
            val hasilDaftar = runCatching {
                repositoriBerkas.daftarIsi(folderInduk).first()
            }.getOrNull()

            if (sesiSaatIni != idSesiBuka || !_state.value.terbuka) return@launch

            if (hasilDaftar is HasilOperasi.Sukses) {
                val daftarDisk = hasilDaftar.nilai
                namaDiFolderDiskCache = daftarDisk.map { it.nama }
                val petaTerbaru = daftarDisk.associateBy { it.lokasi }
                _state.update { st ->
                    val daftarSegar = st.daftarEntitasUrut.map { lama -> petaTerbaru[lama.lokasi] ?: lama }
                    hitungStatePratinjau(
                        st.copy(
                            daftarEntitasUrut = daftarSegar,
                            sedangMemuatIsiFolder = false
                        )
                    )
                }
            } else {
                _state.update { it.copy(sedangMemuatIsiFolder = false) }
            }
        }
    }

    private fun tutupDialog() {
        if (_state.value.sedangMengeksekusi) return
        idSesiBuka++
        pekerjaanMuatFolder?.cancel()
        namaDiFolderDiskCache = emptyList()
        kemampuanLokasiCache = null
        _state.update {
            StateBulkRename(acaraSuksesPenuh = it.acaraSuksesPenuh)
        }
    }

    private fun tutupLaporanHasil() {
        idSesiBuka++
        pekerjaanMuatFolder?.cancel()
        namaDiFolderDiskCache = emptyList()
        kemampuanLokasiCache = null
        _state.update { StateBulkRename() }
    }

    private fun geserItem(indeks: Int, arah: Int) {
        val tujuan = indeks + arah
        val daftar = _state.value.daftarEntitasUrut
        if (indeks !in daftar.indices || tujuan !in daftar.indices) return
        val baru = daftar.toMutableList()
        val sementara = baru[indeks]
        baru[indeks] = baru[tujuan]
        baru[tujuan] = sementara
        perbaruiDanHitung { it.copy(daftarEntitasUrut = baru) }
    }

    private fun terapkanUrutan(urutan: UrutanItemRename) {
        val daftar = _state.value.daftarEntitasUrut
        if (daftar.size <= 1) return
        val terurut = when (urutan) {
            UrutanItemRename.NAMA_NAIK -> daftar.sortedBy { it.nama.lowercase() }
            UrutanItemRename.NAMA_TURUN -> daftar.sortedByDescending { it.nama.lowercase() }
            UrutanItemRename.TANGGAL_NAIK -> daftar.sortedBy { it.waktuUbahEpochMs ?: Long.MAX_VALUE }
            UrutanItemRename.TANGGAL_TURUN -> daftar.sortedByDescending { it.waktuUbahEpochMs ?: Long.MIN_VALUE }
            UrutanItemRename.UKURAN_NAIK -> daftar.sortedBy { it.ukuranByte ?: -1L }
            UrutanItemRename.UKURAN_TURUN -> daftar.sortedByDescending { it.ukuranByte ?: -1L }
            UrutanItemRename.BALIK -> daftar.reversed()
        }
        perbaruiDanHitung { it.copy(daftarEntitasUrut = terurut) }
    }

    private fun perbaruiDanHitung(transformasi: (StateBulkRename) -> StateBulkRename) {
        _state.update { st -> hitungStatePratinjau(transformasi(st)) }
    }

    private fun hitungStatePratinjau(st: StateBulkRename): StateBulkRename {
        val (konfig, galatAngka) = parseKonfigurasiNomor(st.angkaAwalInput, st.langkahInput)
        val pratinjau = PratinjauRename.bangun(
            daftarItem = st.daftarEntitasUrut,
            pola = st.pola,
            konfigurasiNomor = konfig ?: KonfigurasiNomorUrut(),
            namaDiFolderDisk = namaDiFolderDiskCache,
            kemampuanLokasi = kemampuanLokasiCache,
            opsiParser = OpsiParserPola(izinkanAngkaAwalInline = st.izinkanAngkaAwalInline)
        )
        return st.copy(
            pratinjau = pratinjau,
            galatAngkaNomor = galatAngka
        )
    }

    private fun parseKonfigurasiNomor(
        angkaAwalInput: String,
        langkahInput: String
    ): Pair<KonfigurasiNomorUrut?, String?> {
        val awalBersih = angkaAwalInput.trim()
        val langkahBersih = langkahInput.trim()
        val angkaAwal = awalBersih.toIntOrNull()
        if (awalBersih.isEmpty() || angkaAwal == null || angkaAwal < 0) {
            return null to "Angka awal harus berupa bilangan bulat >= 0"
        }
        val langkah = langkahBersih.toIntOrNull()
        if (langkahBersih.isEmpty() || langkah == null || langkah < 1) {
            return null to "Langkah penomoran harus berupa bilangan bulat >= 1"
        }
        return KonfigurasiNomorUrut(angkaAwal = angkaAwal, langkah = langkah) to null
    }

    private fun mintaKonfirmasiEksekusi() {
        val st = _state.value
        val (konfig, galatAngka) = parseKonfigurasiNomor(st.angkaAwalInput, st.langkahInput)
        if (konfig == null || galatAngka != null) {
            _state.update { it.copy(galatAngkaNomor = galatAngka) }
            return
        }
        viewModelScope.launch {
            when (
                val hasil = jalankanRenameMassalUseCase(
                    daftarItem = st.daftarEntitasUrut,
                    pola = st.pola,
                    konfigurasiNomor = konfig,
                    namaDiFolderDisk = namaDiFolderDiskCache,
                    kemampuanLokasi = kemampuanLokasiCache,
                    konfirmasiRingkasan = false,
                    opsiParser = OpsiParserPola(izinkanAngkaAwalInline = st.izinkanAngkaAwalInline)
                )
            ) {
                is HasilJalankanRenameMassal.PerluKonfirmasi -> {
                    _state.update {
                        it.copy(
                            ringkasanKonfirmasi = hasil.ringkasan,
                            pesanGalatEksekusi = null
                        )
                    }
                }
                is HasilJalankanRenameMassal.DitolakValidasi -> {
                    _state.update {
                        it.copy(
                            pratinjau = hasil.pratinjau ?: it.pratinjau,
                            pesanGalatEksekusi = hasil.pesan
                        )
                    }
                }
                is HasilJalankanRenameMassal.Selesai -> Unit
            }
        }
    }

    private fun jalankanEksekusi() {
        val st = _state.value
        val (konfig, galatAngka) = parseKonfigurasiNomor(st.angkaAwalInput, st.langkahInput)
        if (konfig == null || galatAngka != null) {
            _state.update { it.copy(galatAngkaNomor = galatAngka, ringkasanKonfirmasi = null) }
            return
        }

        val totalUbah = st.pratinjau.jumlahSiapUbah.coerceAtLeast(1)
        _state.update {
            it.copy(
                ringkasanKonfirmasi = null,
                sedangMengeksekusi = true,
                progresDiproses = 0,
                progresTotal = totalUbah,
                namaDiprosesSaatIni = "",
                pesanGalatEksekusi = null
            )
        }

        pekerjaanEksekusi?.cancel()
        val job = viewModelScope.launch {
            try {
                when (
                    val hasil = jalankanRenameMassalUseCase(
                        daftarItem = st.daftarEntitasUrut,
                        pola = st.pola,
                        konfigurasiNomor = konfig,
                        namaDiFolderDisk = namaDiFolderDiskCache,
                        kemampuanLokasi = kemampuanLokasiCache,
                        konfirmasiRingkasan = true,
                        opsiParser = OpsiParserPola(izinkanAngkaAwalInline = st.izinkanAngkaAwalInline),
                        onProgres = { diproses, total, nama ->
                            _state.update {
                                it.copy(
                                    progresDiproses = diproses,
                                    progresTotal = total,
                                    namaDiprosesSaatIni = nama
                                )
                            }
                        }
                    )
                ) {
                    is HasilJalankanRenameMassal.Selesai -> {
                        val eksekusi = hasil.hasil
                        if (!eksekusi.adaGagal) {
                            idSesiBuka++
                            pekerjaanMuatFolder?.cancel()
                            namaDiFolderDiskCache = emptyList()
                            kemampuanLokasiCache = null
                            _state.value = StateBulkRename(
                                acaraSuksesPenuh = AcaraSuksesBulkRename(
                                    id = ++urutanAcara,
                                    jumlahBerhasil = eksekusi.berhasil.size
                                )
                            )
                        } else {
                            _state.update {
                                it.copy(
                                    sedangMengeksekusi = false,
                                    hasilEksekusi = eksekusi,
                                    perluSegarkanSaatTutup = eksekusi.berhasil.isNotEmpty()
                                )
                            }
                        }
                    }
                    is HasilJalankanRenameMassal.DitolakValidasi -> {
                        _state.update {
                            it.copy(
                                sedangMengeksekusi = false,
                                pratinjau = hasil.pratinjau ?: it.pratinjau,
                                pesanGalatEksekusi = hasil.pesan
                            )
                        }
                    }
                    is HasilJalankanRenameMassal.PerluKonfirmasi -> {
                        _state.update {
                            it.copy(
                                sedangMengeksekusi = false,
                                ringkasanKonfirmasi = hasil.ringkasan
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        sedangMengeksekusi = false,
                        pesanGalatEksekusi = "Eksekusi ganti nama massal dibatalkan",
                        perluSegarkanSaatTutup = true
                    )
                }
                throw e
            }
        }
        pekerjaanEksekusi = job
        job.invokeOnCompletion {
            if (pekerjaanEksekusi === job) pekerjaanEksekusi = null
        }
    }

    override fun onCleared() {
        pekerjaanMuatFolder?.cancel()
        pekerjaanEksekusi?.cancel()
    }
}