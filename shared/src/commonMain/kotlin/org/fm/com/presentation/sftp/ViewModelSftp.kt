package org.fm.com.presentation.sftp

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.sftp.HasilUjiKoneksiSftp
import org.fm.com.domain.sftp.JenisAutentikasi
import org.fm.com.domain.sftp.LayananKoneksiSftp
import org.fm.com.domain.sftp.PengenalSftp
import org.fm.com.domain.sftp.ProfilSftp
import org.fm.com.domain.sftp.RahasiaSftp

@Immutable
data class PermintaanKonfirmasiHostKey(
    val profil: ProfilSftp,
    val rahasia: RahasiaSftp?,
    val jenisKunci: String,
    val sidikJariBaru: String,
    val sidikJariLama: String?,
    val bukaSetelahSimpan: Boolean
) {
    val adalahPerubahanKunci: Boolean get() = sidikJariLama != null
}

@Immutable
data class FormProfilSftpUi(
    val terbuka: Boolean = false,
    val modeEdit: Boolean = false,
    val id: String = "",
    val nama: String = "Termux (SFTP)",
    val host: String = "127.0.0.1",
    val portInput: String = "8022",
    val namaPengguna: String = "",
    val pathAwal: String = "/data/data/com.termux/files/home",
    val autentikasi: JenisAutentikasi = JenisAutentikasi.SANDI,
    val sandiInput: String = "",
    val kunciPemInput: String = "",
    val frasaSandiInput: String = "",
    val kunciPublikDibuat: String? = null,
    val sudahPunyaRahasiaTersimpan: Boolean = false,
    val jenisKunciHost: String? = null,
    val sidikJariHost: String? = null
)

@Immutable
data class StateSftp(
    val dialogTerbuka: Boolean = false,
    val daftarProfil: List<ProfilSftp> = emptyList(),
    val form: FormProfilSftpUi = FormProfilSftpUi(),
    val sedangMemproses: Boolean = false,
    val konfirmasiHostKey: PermintaanKonfirmasiHostKey? = null,
    val pesanGalat: String? = null,
    val pesanInfo: String? = null,
    val lokasiSiapDibuka: Pair<Long, LokasiBerkas>? = null
)

sealed interface IntentSftp {
    data object BukaDialogDaftar : IntentSftp
    data object TutupDialog : IntentSftp
    data object BukaFormBaruTermux : IntentSftp
    data class BukaFormEdit(val profil: ProfilSftp) : IntentSftp
    data object TutupForm : IntentSftp
    data class UbahNama(val v: String) : IntentSftp
    data class UbahHost(val v: String) : IntentSftp
    data class UbahPort(val v: String) : IntentSftp
    data class UbahPengguna(val v: String) : IntentSftp
    data class UbahPathAwal(val v: String) : IntentSftp
    data class UbahAutentikasi(val v: JenisAutentikasi) : IntentSftp
    data class UbahSandi(val v: String) : IntentSftp
    data class UbahKunciPem(val v: String) : IntentSftp
    data class UbahFrasaSandi(val v: String) : IntentSftp
    data object BuatKunciEcdsaBaru : IntentSftp
    data class UjiDanSimpanForm(val bukaSetelahnya: Boolean) : IntentSftp
    data object KonfirmasiPercayaiHostKey : IntentSftp
    data object BatalKonfirmasiHostKey : IntentSftp
    data class BukaProfil(val profil: ProfilSftp) : IntentSftp
    data class HapusProfil(val id: String) : IntentSftp
    data class SetPesanInfo(val pesan: String?) : IntentSftp
    data object KonsumsiLokasiDibuka : IntentSftp
}

class ViewModelSftp(
    private val layanan: LayananKoneksiSftp?
) : ViewModel() {

    private val _state = MutableStateFlow(StateSftp())
    val state: StateFlow<StateSftp> = _state.asStateFlow()

    private var pekerjaanAmati: Job? = null
    private var pekerjaanAksi: Job? = null
    private var urutanAcara = 0L

    init {
        if (layanan != null) {
            pekerjaanAmati = viewModelScope.launch {
                layanan.amatiDaftarProfil()
                    .catch { }
                    .collect { daftar -> _state.update { it.copy(daftarProfil = daftar) } }
            }
        }
    }

    fun prosesIntent(intent: IntentSftp) {
        when (intent) {
            is IntentSftp.BukaDialogDaftar -> _state.update {
                it.copy(dialogTerbuka = true, form = FormProfilSftpUi(), pesanGalat = null)
            }
            is IntentSftp.TutupDialog -> {
                pekerjaanAksi?.cancel()
                _state.update {
                    it.copy(
                        dialogTerbuka = false,
                        form = FormProfilSftpUi(),
                        konfirmasiHostKey = null,
                        sedangMemproses = false,
                        pesanGalat = null
                    )
                }
            }
            is IntentSftp.BukaFormBaruTermux -> _state.update {
                it.copy(
                    dialogTerbuka = true,
                    form = FormProfilSftpUi(
                        terbuka = true,
                        modeEdit = false,
                        id = "termux_${Random.nextInt(0x1000, 0xFFFF).toString(16)}"
                    ),
                    pesanGalat = null
                )
            }
            is IntentSftp.BukaFormEdit -> bukaFormEdit(intent.profil)
            is IntentSftp.TutupForm -> _state.update {
                it.copy(form = FormProfilSftpUi(), pesanGalat = null, konfirmasiHostKey = null)
            }
            is IntentSftp.UbahNama -> ubahForm { it.copy(nama = intent.v) }
            is IntentSftp.UbahHost -> ubahForm {
                if (it.host.trim() != intent.v.trim()) {
                    it.copy(host = intent.v, jenisKunciHost = null, sidikJariHost = null)
                } else {
                    it.copy(host = intent.v)
                }
            }
            is IntentSftp.UbahPort -> ubahForm {
                if (it.portInput.trim() != intent.v.trim()) {
                    it.copy(portInput = intent.v, jenisKunciHost = null, sidikJariHost = null)
                } else {
                    it.copy(portInput = intent.v)
                }
            }
            is IntentSftp.UbahPengguna -> ubahForm { it.copy(namaPengguna = intent.v) }
            is IntentSftp.UbahPathAwal -> ubahForm { it.copy(pathAwal = intent.v) }
            is IntentSftp.UbahAutentikasi -> ubahForm { it.copy(autentikasi = intent.v) }
            is IntentSftp.UbahSandi -> ubahForm { it.copy(sandiInput = intent.v) }
            is IntentSftp.UbahKunciPem -> ubahForm { it.copy(kunciPemInput = intent.v) }
            is IntentSftp.UbahFrasaSandi -> ubahForm { it.copy(frasaSandiInput = intent.v) }
            is IntentSftp.BuatKunciEcdsaBaru -> buatKunciEcdsa()
            is IntentSftp.UjiDanSimpanForm -> ujiDanSimpan(intent.bukaSetelahnya)
            is IntentSftp.KonfirmasiPercayaiHostKey -> percayaiHostKey()
            is IntentSftp.BatalKonfirmasiHostKey -> _state.update { it.copy(konfirmasiHostKey = null) }
            is IntentSftp.BukaProfil -> {
                val srv = layanan ?: return
                _state.update {
                    it.copy(
                        dialogTerbuka = false,
                        form = FormProfilSftpUi(),
                        lokasiSiapDibuka = (++urutanAcara) to srv.keLokasiBerkas(intent.profil)
                    )
                }
            }
            is IntentSftp.HapusProfil -> hapusProfil(intent.id)
            is IntentSftp.SetPesanInfo -> _state.update { it.copy(pesanInfo = intent.pesan) }
            is IntentSftp.KonsumsiLokasiDibuka -> _state.update { it.copy(lokasiSiapDibuka = null) }
        }
    }

    private fun ubahForm(fn: (FormProfilSftpUi) -> FormProfilSftpUi) {
        _state.update { it.copy(form = fn(it.form), pesanGalat = null) }
    }

    private fun bukaFormEdit(p: ProfilSftp) {
        val srv = layanan ?: return
        viewModelScope.launch {
            val adaRahasia = srv.apakahAdaRahasia(p.id)
            _state.update {
                it.copy(
                    dialogTerbuka = true,
                    form = FormProfilSftpUi(
                        terbuka = true,
                        modeEdit = true,
                        id = p.id,
                        nama = p.nama,
                        host = p.host,
                        portInput = p.port.toString(),
                        namaPengguna = p.namaPengguna,
                        pathAwal = p.pathAwal,
                        autentikasi = p.autentikasi,
                        sudahPunyaRahasiaTersimpan = adaRahasia,
                        jenisKunciHost = p.jenisKunciHost,
                        sidikJariHost = p.sidikJariHost
                    ),
                    pesanGalat = null
                )
            }
        }
    }

    private fun buatKunciEcdsa() {
        val srv = layanan ?: return
        viewModelScope.launch {
            when (val res = srv.buatKunciEcdsaP256("fm-app")) {
                is HasilOperasi.Sukses -> {
                    val (pemBytes, pubString) = res.nilai
                    _state.update {
                        it.copy(
                            form = it.form.copy(
                                autentikasi = JenisAutentikasi.KUNCI_PRIBADI,
                                kunciPemInput = pemBytes.decodeToString(),
                                frasaSandiInput = "",
                                kunciPublikDibuat = pubString
                            ),
                            pesanInfo = "Kunci ECDSA P-256 dibuat. Salin kunci publik ke ~/.ssh/authorized_keys di Termux."
                        )
                    }
                }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanGalat = res.pesan) }
            }
        }
    }

    private fun ujiDanSimpan(bukaSetelahnya: Boolean) {
        val srv = layanan ?: return
        val f = _state.value.form
        val nama = f.nama.trim()
        val host = f.host.trim()
        val port = f.portInput.trim().toIntOrNull()
        val user = f.namaPengguna.trim()
        val pathAwal = f.pathAwal.trim().ifEmpty { "/" }

        if (nama.isEmpty() || host.isEmpty() || port == null || port !in 1..65535 || user.isEmpty()) {
            _state.update { it.copy(pesanGalat = "Nama, host, port (1-65535), dan username wajib diisi") }
            return
        }
        val pathNormal = runCatching { PengenalSftp.normalisasi(pathAwal) }.getOrNull()
            ?: run {
                _state.update { it.copy(pesanGalat = "Path awal tidak valid") }
                return
            }

        val rahasiaBaru: RahasiaSftp? = when (f.autentikasi) {
            JenisAutentikasi.SANDI -> {
                if (f.sandiInput.isNotEmpty()) {
                    RahasiaSftp.Sandi(f.sandiInput.encodeToByteArray())
                } else if (f.modeEdit && f.sudahPunyaRahasiaTersimpan) {
                    null
                } else {
                    _state.update { it.copy(pesanGalat = "Kata sandi SSH wajib diisi") }
                    return
                }
            }
            JenisAutentikasi.KUNCI_PRIBADI -> {
                if (f.kunciPemInput.isNotBlank()) {
                    val frasa = f.frasaSandiInput.takeIf { it.isNotEmpty() }?.encodeToByteArray()
                    RahasiaSftp.KunciPribadi(f.kunciPemInput.trim().encodeToByteArray(), frasa)
                } else if (f.modeEdit && f.sudahPunyaRahasiaTersimpan) {
                    null
                } else {
                    _state.update { it.copy(pesanGalat = "Kunci pribadi PEM wajib diisi atau buat kunci baru") }
                    return
                }
            }
        }

        val profil = ProfilSftp(
            id = f.id,
            nama = nama,
            host = host,
            port = port,
            namaPengguna = user,
            pathAwal = pathNormal,
            autentikasi = f.autentikasi,
            jenisKunciHost = f.jenisKunciHost,
            sidikJariHost = f.sidikJariHost
        )

        pekerjaanAksi?.cancel()
        pekerjaanAksi = viewModelScope.launch {
            _state.update { it.copy(sedangMemproses = true, pesanGalat = null) }
            when (val hasilUji = srv.ujiKoneksi(profil, rahasiaBaru)) {
                is HasilUjiKoneksiSftp.Sukses -> simpanFinal(profil, rahasiaBaru, bukaSetelahnya)
                is HasilUjiKoneksiSftp.HostBelumDipercaya -> _state.update {
                    it.copy(
                        sedangMemproses = false,
                        konfirmasiHostKey = PermintaanKonfirmasiHostKey(
                            profil = profil,
                            rahasia = rahasiaBaru,
                            jenisKunci = hasilUji.jenisKunci,
                            sidikJariBaru = hasilUji.sidikJari,
                            sidikJariLama = null,
                            bukaSetelahSimpan = bukaSetelahnya
                        )
                    )
                }
                is HasilUjiKoneksiSftp.HostBerubah -> _state.update {
                    it.copy(
                        sedangMemproses = false,
                        konfirmasiHostKey = PermintaanKonfirmasiHostKey(
                            profil = profil,
                            rahasia = rahasiaBaru,
                            jenisKunci = hasilUji.jenisKunci,
                            sidikJariBaru = hasilUji.sidikJariBaru,
                            sidikJariLama = hasilUji.sidikJariLama,
                            bukaSetelahSimpan = bukaSetelahnya
                        )
                    )
                }
                is HasilUjiKoneksiSftp.Gagal -> _state.update {
                    it.copy(sedangMemproses = false, pesanGalat = hasilUji.pesan)
                }
            }
        }
    }

    private fun percayaiHostKey() {
        val srv = layanan ?: return
        val konf = _state.value.konfirmasiHostKey ?: return
        val profilTerpercaya = konf.profil.copy(
            jenisKunciHost = konf.jenisKunci,
            sidikJariHost = konf.sidikJariBaru
        )
        pekerjaanAksi?.cancel()
        pekerjaanAksi = viewModelScope.launch {
            _state.update { it.copy(konfirmasiHostKey = null, sedangMemproses = true, pesanGalat = null) }
            when (val uji = srv.ujiKoneksi(profilTerpercaya, konf.rahasia)) {
                is HasilUjiKoneksiSftp.Sukses -> simpanFinal(profilTerpercaya, konf.rahasia, konf.bukaSetelahSimpan)
                is HasilUjiKoneksiSftp.Gagal -> _state.update {
                    it.copy(sedangMemproses = false, pesanGalat = uji.pesan)
                }
                is HasilUjiKoneksiSftp.HostBelumDipercaya,
                is HasilUjiKoneksiSftp.HostBerubah -> _state.update {
                    it.copy(sedangMemproses = false, pesanGalat = "Verifikasi kunci host gagal")
                }
            }
        }
    }

    private suspend fun simpanFinal(
        profil: ProfilSftp,
        rahasiaBaru: RahasiaSftp?,
        bukaSetelahnya: Boolean
    ) {
        val srv = layanan ?: return
        when (val simpan = srv.simpanProfilDanRahasia(profil, rahasiaBaru)) {
            is HasilOperasi.Sukses -> {
                _state.update {
                    it.copy(
                        sedangMemproses = false,
                        dialogTerbuka = !bukaSetelahnya,
                        form = FormProfilSftpUi(),
                        pesanInfo = "Koneksi '${profil.nama}' tersimpan",
                        lokasiSiapDibuka = if (bukaSetelahnya) {
                            (++urutanAcara) to srv.keLokasiBerkas(profil)
                        } else {
                            it.lokasiSiapDibuka
                        }
                    )
                }
            }
            is HasilOperasi.Gagal -> _state.update {
                it.copy(sedangMemproses = false, pesanGalat = simpan.pesan)
            }
        }
    }

    private fun hapusProfil(id: String) {
        val srv = layanan ?: return
        viewModelScope.launch {
            when (val res = srv.hapusProfil(id)) {
                is HasilOperasi.Sukses -> _state.update { it.copy(pesanInfo = "Profil SFTP dihapus") }
                is HasilOperasi.Gagal -> _state.update { it.copy(pesanGalat = res.pesan) }
            }
        }
    }

    override fun onCleared() {
        pekerjaanAmati?.cancel()
        pekerjaanAksi?.cancel()
    }
}