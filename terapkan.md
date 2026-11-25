### Bagian 1: Pastikan Tombol Izin Muncul di Tab Pengaturan (`Aplikasi.kt` & `LayarPengaturan.kt`)

#### 1. `shared/src/commonMain/kotlin/org/fm/com/presentation/Aplikasi.kt`
Pastikan pemanggilan `LayarPengaturan` pada cabang `TabUtama.PENGATURAN` meneruskan `izinMemoriInternalAktif` dan `onMintaIzinMemoriInternal`:

```kotlin
                    TabUtama.PENGATURAN -> LayarPengaturan(
                        viewModelPengaturan = vmPengaturan,
                        viewModelBeranda = vmBeranda,
                        viewModelEditor = vmEditor,
                        lokasiAktifFileManager = lokasiAktifFm,
                        lokasiPintasan = lokasiPintasan,
                        onPilihFolderSistemUntukBeranda = if (onPilihFolder != null) {
                            {
                                tujuanPemilihSistem = TujuanPemilihFolderSistem.BERANDA
                                onPilihFolder()
                            }
                        } else null,
                        izinMemoriInternalAktif = izinMemoriInternalAktif,
                        onMintaIzinMemoriInternal = onMintaIzinMemoriInternal
                    )
```

#### 2. `shared/src/commonMain/kotlin/org/fm/com/presentation/pengaturan/LayarPengaturan.kt`
Pastikan signature `LayarPengaturan` memiliki dua parameter opsional berikut dan blok `"Izin Penyimpanan Sistem"` berada di dalam `Column` (tepat di bawah kartu **Homepage**):

```kotlin
@Composable
fun LayarPengaturan(
    viewModelPengaturan: ViewModelPengaturan,
    viewModelBeranda: ViewModelBeranda,
    viewModelEditor: ViewModelEditor,
    modifier: Modifier = Modifier,
    lokasiAktifFileManager: LokasiBerkas? = null,
    lokasiPintasan: List<LokasiBerkas> = emptyList(),
    onPilihFolderSistemUntukBeranda: (() -> Unit)? = null,
    izinMemoriInternalAktif: Boolean? = null,
    onMintaIzinMemoriInternal: (() -> Unit)? = null
) {
    // ... di dalam Column, tepat setelah kartu Homepage (Folder Awal Tab File):

            if (izinMemoriInternalAktif != null && onMintaIzinMemoriInternal != null) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                JudulBagianPengaturan("Izin Penyimpanan Sistem")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (izinMemoriInternalAktif) {
                            "Akses Semua Berkas: Aktif"
                        } else {
                            "Akses Semua Berkas: Belum Aktif (Mode SAF)"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (izinMemoriInternalAktif) {
                            "Memori internal (/storage/emulated/0) dapat diakses langsung. Folder privat aplikasi (Android/data & Termux) tetap mengikuti batasan OS."
                        } else {
                            "Aktifkan untuk membuka /storage/emulated/0 langsung tanpa memilih subfolder SAF satu per satu."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (izinMemoriInternalAktif) {
                        OutlinedButton(onClick = onMintaIzinMemoriInternal) {
                            Text("Kelola di Pengaturan Sistem")
                        }
                    } else {
                        Button(onClick = onMintaIzinMemoriInternal) {
                            Text("Izinkan Memori Internal")
                        }
                    }
                }
            }
```

---

### Bagian 2: Pelapor Progres Coroutine, Anti-Stuck `ViewModelFilemanager`, & Dialog Progres UI

#### 3. `shared/src/commonMain/kotlin/org/fm/com/domain/repository/RepositoriOperasiBerkas.kt`
Tambahkan elemen `CoroutineContext` murni Kotlin berikut di bagian bawah `RepositoriOperasiBerkas.kt` (tanpa mengubah kontrak interface yang ada dan tanpa melanggar `KonsistArsitekturTest`):

```diff
--- a/shared/src/commonMain/kotlin/org/fm/com/domain/repository/RepositoriOperasiBerkas.kt
+++ b/shared/src/commonMain/kotlin/org/fm/com/domain/repository/RepositoriOperasiBerkas.kt
@@ -1,5 +1,7 @@
 package org.fm.com.domain.repository
 
+import kotlin.coroutines.AbstractCoroutineContextElement
+import kotlin.coroutines.CoroutineContext
 import org.fm.com.domain.model.EntitasBerkas
 import org.fm.com.domain.model.HasilOperasi
 import org.fm.com.domain.model.LokasiBerkas
@@ -64,3 +66,10 @@
     val pesan: String,
     val penyebab: PenyebabGalat = PenyebabGalat.TIDAK_DIKETAHUI
 )
+
+/** Opsional di CoroutineContext agar operasi tunggal SAF/berat bisa melaporkan progres tanpa mengubah interface. */
+class PelaporProgresOperasi(
+    val onProgres: (diproses: Int, total: Int, namaItem: String) -> Unit
+) : AbstractCoroutineContextElement(Key) {
+    companion object Key : CoroutineContext.Key<PelaporProgresOperasi>
+}
```

#### 4. `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/StateFilemanager.kt`
Tambahkan state progres dan intent pembatalan operasi tunggal:

```diff
--- a/shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/StateFilemanager.kt
+++ b/shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/StateFilemanager.kt
@@ -43,6 +43,19 @@
     val waktuUbahEpochMs: Long? = null
 )
 
+@Immutable
+data class StateProgresOperasiTunggal(
+    val judul: String,
+    val diproses: Int = 0,
+    val total: Int = 0,
+    val namaItemSaatIni: String = ""
+) {
+    val progresFloat: Float
+        get() = if (total > 0) (diproses.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
+}
+
 @Immutable
 data class StateFilemanager(
     val lokasiSaatIni: LokasiBerkas? = null,
@@ -66,7 +79,8 @@
     val dialogBukaDenganItem: ItemBerkasUi? = null,
     val dialogBagikanItem: ItemBerkasUi? = null,
     val konfirmasiTimpa: PermintaanKonfirmasiTimpa? = null,
-    val dapatNavigasiKembali: Boolean = false
+    val dapatNavigasiKembali: Boolean = false,
+    val progresOperasi: StateProgresOperasiTunggal? = null
 ) {
     val jumlahItem: Int get() = daftarItem.size
     val sedangMultiSelect: Boolean get() = itemTerpilih.isNotEmpty()
@@ -94,6 +108,7 @@
     data object TutupKonfirmasiTimpa : IntentFilemanager
     data class BukaDialogHapus(val items: List<LokasiBerkas>?) : IntentFilemanager
     data object EksekusiHapusTerpilih : IntentFilemanager
+    data object BatalkanOperasiTunggal : IntentFilemanager
     data class BukaDialogBukaDengan(val item: ItemBerkasUi?) : IntentFilemanager
     data class BukaDialogBagikan(val item: ItemBerkasUi?) : IntentFilemanager
     data class SetPesanInfo(val pesan: String?) : IntentFilemanager
```

#### 5. `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/ViewModelFilemanager.kt` (ganti seluruh isi file)
Memastikan:
- Semua mutasi tunggal (`buatFolder`, `buatBerkas`, `eksekusiRename`, `eksekusiHapus`) berjalan di `pekerjaanOperasi`, memiliki `try / catch / finally` sehingga `sedangMemuat` & `progresOperasi` **dijamin kembali normal**, dan dapat dibatalkan lewat `BatalkanOperasiTunggal`.
- `muatFolderInternal` memiliki `.catch` pada Flow.
- `onCleared()` membatalkan seluruh job agar bebas leak.

```kotlin
package org.fm.com.presentation.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
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
import kotlinx.datetime.TimeZone
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilOperasi
import org.fm.com.domain.model.JenisLokasi
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.PenyebabGalat
import org.fm.com.domain.repository.PelaporProgresOperasi
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
    private var pekerjaanOperasi: Job? = null
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
            is IntentFilemanager.BatalkanOperasiTunggal -> batalkanOperasiTunggal()
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
        pekerjaanOperasi?.cancel()
        val job = viewModelScope.launch {
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
        pekerjaanOperasi = job
        job.invokeOnCompletion { if (pekerjaanOperasi === job) pekerjaanOperasi = null }
    }

    private fun buatBerkas(nama: String, konfirmasi: Boolean) {
        val induk = _state.value.lokasiSaatIni ?: return
        val uc = buatBerkasUseCase ?: return laporkanOperasiTakDidukung()
        pekerjaanOperasi?.cancel()
        val job = viewModelScope.launch {
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
        pekerjaanOperasi = job
        job.invokeOnCompletion { if (pekerjaanOperasi === job) pekerjaanOperasi = null }
    }

    private fun eksekusiRename(sumber: LokasiBerkas, namaBaru: String, konfirmasi: Boolean) {
        val uc = gantiNamaBerkasUseCase ?: return laporkanOperasiTakDidukung()
        val indukSaatIni = _state.value.lokasiSaatIni
        pekerjaanOperasi?.cancel()
        val pelapor = PelaporProgresOperasi { diproses, total, namaItem ->
            _state.update {
                it.copy(
                    sedangMemuat = false,
                    progresOperasi = StateProgresOperasiTunggal(
                        judul = "Mengganti Nama ($namaBaru)...",
                        diproses = diproses,
                        total = total,
                        namaItemSaatIni = namaItem
                    )
                )
            }
        }
        val job = viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update {
                    it.copy(
                        dialogRenameItem = null,
                        konfirmasiTimpa = null,
                        sedangMemuat = true,
                        progresOperasi = null
                    )
                }
                val hasil = withContext(pelapor) {
                    uc(sumber, namaBaru, konfirmasi = konfirmasi, induk = indukSaatIni)
                }
                when (hasil) {
                    is HasilOperasi.Sukses -> {
                        _state.update {
                            it.copy(
                                progresOperasi = null,
                                pesanInfo = "Nama berhasil diubah menjadi '${hasil.nilai.nama}'"
                            )
                        }
                        lanjutSegarkan = true
                        segarkan()
                    }
                    is HasilOperasi.Gagal -> {
                        if (hasil.penyebab == PenyebabGalat.KONFLIK && !konfirmasi) {
                            _state.update {
                                it.copy(
                                    sedangMemuat = false,
                                    progresOperasi = null,
                                    konfirmasiTimpa = PermintaanKonfirmasiTimpa(JenisAksiTimpa.GANTI_NAMA, namaBaru, sumber)
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    sedangMemuat = false,
                                    progresOperasi = null,
                                    pesanInfo = hasil.pesan
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        sedangMemuat = false,
                        progresOperasi = null,
                        pesanInfo = "Ganti nama dibatalkan"
                    )
                }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        progresOperasi = null,
                        pesanInfo = e.message ?: "Gagal mengganti nama"
                    )
                }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false, progresOperasi = null) }
                }
            }
        }
        pekerjaanOperasi = job
        job.invokeOnCompletion { if (pekerjaanOperasi === job) pekerjaanOperasi = null }
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
        pekerjaanOperasi?.cancel()
        val pelapor = PelaporProgresOperasi { diproses, total, namaItem ->
            _state.update {
                it.copy(
                    sedangMemuat = false,
                    progresOperasi = StateProgresOperasiTunggal(
                        judul = "Menghapus...",
                        diproses = diproses,
                        total = total,
                        namaItemSaatIni = namaItem
                    )
                )
            }
        }
        val job = viewModelScope.launch {
            var lanjutSegarkan = false
            try {
                _state.update { it.copy(dialogHapusItems = null, sedangMemuat = true, progresOperasi = null) }
                var jumlahSukses = 0
                var pesanGagal: String? = null
                withContext(pelapor) {
                    for (lokasi in targets) {
                        when (val hasil = uc(lokasi, konfirmasi = true)) {
                            is HasilOperasi.Sukses -> jumlahSukses++
                            is HasilOperasi.Gagal -> if (pesanGagal == null) pesanGagal = hasil.pesan
                        }
                    }
                }
                val pesanRingkas = when {
                    pesanGagal == null -> "Berhasil menghapus $jumlahSukses item"
                    jumlahSukses > 0 -> "Terhapus $jumlahSukses dari ${targets.size} item. Gagal: $pesanGagal"
                    else -> "Gagal menghapus: $pesanGagal"
                }
                _state.update {
                    it.copy(
                        itemTerpilih = emptySet(),
                        progresOperasi = null,
                        pesanInfo = pesanRingkas
                    )
                }
                if (jumlahSukses > 0) {
                    lanjutSegarkan = true
                    segarkan()
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        sedangMemuat = false,
                        progresOperasi = null,
                        pesanInfo = "Penghapusan dibatalkan"
                    )
                }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        progresOperasi = null,
                        pesanInfo = e.message ?: "Gagal menghapus item"
                    )
                }
            } finally {
                if (!lanjutSegarkan) {
                    _state.update { it.copy(sedangMemuat = false, progresOperasi = null) }
                }
            }
        }
        pekerjaanOperasi = job
        job.invokeOnCompletion { if (pekerjaanOperasi === job) pekerjaanOperasi = null }
    }

    private fun batalkanOperasiTunggal() {
        pekerjaanOperasi?.cancel()
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

    override fun onCleared() {
        pekerjaanMuatFolder?.cancel()
        pekerjaanSaring?.cancel()
        pekerjaanOperasi?.cancel()
    }
}
```

#### 6. `shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt`
Tambahkan `import androidx.compose.material3.LinearProgressIndicator` dan render dialog progres saat `state.progresOperasi != null`:

```diff
--- a/shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt
+++ b/shared/src/commonMain/kotlin/org/fm/com/presentation/filemanager/DaftarBerkasLayar.kt
@@ -26,6 +26,7 @@
 import androidx.compose.material3.FloatingActionButtonDefaults
 import androidx.compose.material3.HorizontalDivider
 import androidx.compose.material3.Icon
 import androidx.compose.material3.IconButton
+import androidx.compose.material3.LinearProgressIndicator
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.ModalBottomSheet
@@ -330,7 +331,7 @@
             HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
 
             Box(modifier = Modifier.weight(1f)) {
-                if (state.sedangMemuat || state.pesanGalat != null || state.daftarItem.isEmpty()) {
+                if (state.progresOperasi == null && (state.sedangMemuat || state.pesanGalat != null || state.daftarItem.isEmpty())) {
                     LayarStatusFilemanager(
                         sedangMemuat = state.sedangMemuat,
                         pesanGalat = state.pesanGalat,
@@ -710,6 +711,39 @@
     if (stateMassal.sedangBerjalan) {
         IndikatorProgresMassal(
             state = stateMassal,
             onBatal = { viewModelOperasiMassal?.batalkanOperasi() }
         )
     }
+
+    state.progresOperasi?.let { prog ->
+        AlertDialog(
+            onDismissRequest = {},
+            title = { Text(prog.judul) },
+            text = {
+                Column(modifier = Modifier.fillMaxWidth()) {
+                    if (prog.total > 0) {
+                        LinearProgressIndicator(
+                            progress = { prog.progresFloat },
+                            modifier = Modifier.fillMaxWidth()
+                        )
+                        Spacer(modifier = Modifier.height(10.dp))
+                        Text(
+                            text = "${prog.diproses} dari ${prog.total}",
+                            style = MaterialTheme.typography.bodyMedium
+                        )
+                    } else {
+                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
+                        Spacer(modifier = Modifier.height(10.dp))
+                    }
+                    if (prog.namaItemSaatIni.isNotBlank()) {
+                        Text(
+                            text = prog.namaItemSaatIni,
+                            style = MaterialTheme.typography.bodySmall,
+                            color = MaterialTheme.colorScheme.outline,
+                            maxLines = 1,
+                            overflow = TextOverflow.Ellipsis
+                        )
+                    }
+                }
+            },
+            confirmButton = {},
+            dismissButton = {
+                TextButton(onClick = { viewModel.prosesIntent(IntentFilemanager.BatalkanOperasiTunggal) }) {
+                    Text("Batalkan")
+                }
+            }
+        )
+    }
```

---

### Bagian 3: Optimasi & Skala Besar di `SafOperasiBerkas.kt`

Perbarui `SafOperasiBerkas.kt` untuk pra-pindai 1x (`kumpulkanPohonSaf`), progres via `PelaporProgresOperasi`, reuse buffer 32 KiB & `CRC32`, batas aman `5.000` item rename / `10.000` item hapus, rollback tanpa query ulang, dan penghapusan sumber langsung dari daftar hasil pra-pindai:

1. **Tambahkan import:**
```kotlin
import org.fm.com.domain.repository.PelaporProgresOperasi
```

2. **Perbarui konstanta teratas:**
```kotlin
private const val UKURAN_BUFFER_SALIN_SAF = 32 * 1024
private const val BATAS_BFS_FOLDER_SAF = 250
private const val BATAS_KEDALAMAN_REKURSIF_SAF = 32
private const val BATAS_ITEM_FALLBACK_SAF = 5_000
private const val BATAS_ITEM_HAPUS_SAF = 10_000
```

3. **Ganti fungsi `gantiNamaViaSalinDanHapus`, `salinEntriKeNamaBaru`, `salinFolderRekursifSaf`, `salinDanVerifikasiStreamSaf`, `hapusFolderRekursifBottomUp`:**

```kotlin
    private suspend fun gantiNamaViaSalinDanHapus(
        docUri: Uri,
        entitasLama: EntitasBerkas,
        namaBaru: String,
        konfirmasi: Boolean,
        hintInduk: LokasiBerkas?
    ): HasilOperasi<EntitasBerkas> {
        val parentUri = cariIndukLangsung(docUri, hintInduk)
            ?: return HasilOperasi.Gagal(
                "Folder induk dokumen tidak dapat ditentukan untuk ganti nama",
                PenyebabGalat.TIDAK_DIDUKUNG
            )
        val flagsInduk = bacaFlagsAtauNull(parentUri)
        if (flagsInduk != null && (flagsInduk and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
            return HasilOperasi.Gagal(
                "Folder induk tidak mengizinkan pembuatan berkas untuk ganti nama",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val flagsDoc = bacaFlagsAtauNull(docUri)
        if (flagsDoc != null && (flagsDoc and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) == 0) {
            return HasilOperasi.Gagal(
                "Dokumen tidak mengizinkan penghapusan setelah disalin untuk ganti nama",
                PenyebabGalat.IZIN_DITOLAK
            )
        }

        val pelapor = currentCoroutineContext()[PelaporProgresOperasi]
        val adalahFolder = entitasLama.jenis == JenisBerkas.FOLDER
        val konflikUri = cariAnakBernama(parentUri, namaBaru, ignoreCase = true)
        val caseOnly = konflikUri != null && apakahDokumenSama(konflikUri, docUri)

        if (konflikUri != null && !caseOnly) {
            if (!konfirmasi) {
                return HasilOperasi.Gagal(
                    "Nama target sudah ada di folder: $namaBaru",
                    PenyebabGalat.KONFLIK
                )
            }
            val entitasKonflik = bacaSatuEntitas(konflikUri)
            if (adalahFolder || entitasKonflik?.jenis == JenisBerkas.FOLDER) {
                return HasilOperasi.Gagal(
                    "Penimpaan folder saat ganti nama tidak didukung di penyedia SAF ini",
                    PenyebabGalat.KONFLIK
                )
            }
            return gantiNamaBerkasDenganTimpaKonflik(
                docUri = docUri,
                parentUri = parentUri,
                konflikUri = konflikUri,
                namaBaru = namaBaru
            )
        }

        if (adalahFolder) {
            val pohon = when (val pindai = kumpulkanPohonSaf(docUri, namaBaru, BATAS_ITEM_FALLBACK_SAF, pelapor)) {
                is HasilOperasi.Sukses -> pindai.nilai
                is HasilOperasi.Gagal -> return pindai
            }
            if (caseOnly) {
                val namaTemp = ".fm_rn_${Random.nextInt(0x100000, 0xFFFFFF).toString(16)}_$namaBaru"
                val tempResult = when (val salin = salinPohonSafTerpindai(pohon, parentUri, namaTemp, pelapor)) {
                    is HasilOperasi.Sukses -> salin.nilai
                    is HasilOperasi.Gagal -> return salin
                }
                val hapusAsal = hapusPohonDariDaftar(pohon)
                if (hapusAsal is HasilOperasi.Gagal) {
                    withContext(NonCancellable) { hapusHasilSalinPohon(tempResult) }
                    return hapusAsal
                }
                val renameNativeTemp = runCatching {
                    DocumentsContract.renameDocument(contentResolver, tempResult.rootTujuanUri, namaBaru)
                }.getOrNull()
                val uriFinal = if (renameNativeTemp != null) {
                    renameNativeTemp
                } else {
                    val pohonTemp = PohonSaf(
                        daftarFolder = pohon.daftarFolder.mapIndexed { idx, f ->
                            f.copy(uri = tempResult.folderTujuanUris[idx])
                        },
                        daftarBerkas = pohon.daftarBerkas.mapIndexed { idx, b ->
                            b.copy(uri = tempResult.berkasTujuanUris[idx])
                        }
                    )
                    when (val salinFinal = salinPohonSafTerpindai(pohonTemp, parentUri, namaBaru, pelapor)) {
                        is HasilOperasi.Sukses -> {
                            withContext(NonCancellable) { hapusHasilSalinPohon(tempResult) }
                            salinFinal.nilai.rootTujuanUri
                        }
                        is HasilOperasi.Gagal -> {
                            withContext(NonCancellable) {
                                val pulih = salinPohonSafTerpindai(pohonTemp, parentUri, entitasLama.nama, pelapor = null)
                                if (pulih is HasilOperasi.Sukses) hapusHasilSalinPohon(tempResult)
                            }
                            return salinFinal
                        }
                    }
                }
                val entitasBaru = bacaSatuEntitas(uriFinal)
                    ?: return HasilOperasi.Gagal("Gagal membaca folder hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                return HasilOperasi.Sukses(entitasBaru)
            }

            val hasilSalin = when (val salin = salinPohonSafTerpindai(pohon, parentUri, namaBaru, pelapor)) {
                is HasilOperasi.Sukses -> salin.nilai
                is HasilOperasi.Gagal -> return salin
            }
            val hapusAsal = hapusPohonDariDaftar(pohon)
            if (hapusAsal is HasilOperasi.Gagal) {
                return HasilOperasi.Gagal(
                    "Folder '$namaBaru' sudah disalin, tetapi gagal menghapus folder asal: ${hapusAsal.pesan}",
                    hapusAsal.penyebab
                )
            }
            val entitasBaru = bacaSatuEntitas(hasilSalin.rootTujuanUri)
                ?: return HasilOperasi.Gagal("Gagal membaca folder hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
            return HasilOperasi.Sukses(entitasBaru)
        }

        if (caseOnly) {
            val namaTemp = ".fm_rn_${Random.nextInt(0x100000, 0xFFFFFF).toString(16)}_$namaBaru"
            val tempUri = when (val buatTemp = salinBerkasTunggalKeNama(docUri, parentUri, namaTemp)) {
                is HasilOperasi.Sukses -> buatTemp.nilai
                is HasilOperasi.Gagal -> return buatTemp
            }
            val hapusAsal = runCatching { DocumentsContract.deleteDocument(contentResolver, docUri) }.getOrDefault(false)
            if (!hapusAsal) {
                withContext(NonCancellable) { hapusAman(tempUri) }
                return HasilOperasi.Gagal("Gagal menghapus berkas asal saat ganti nama", PenyebabGalat.IZIN_DITOLAK)
            }
            val hasilFinal = salinBerkasTunggalKeNama(tempUri, parentUri, namaBaru)
            return when (hasilFinal) {
                is HasilOperasi.Sukses -> {
                    withContext(NonCancellable) { hapusAman(tempUri) }
                    val entitasBaru = bacaSatuEntitas(hasilFinal.nilai)
                        ?: return HasilOperasi.Gagal("Gagal membaca berkas hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
                    HasilOperasi.Sukses(entitasBaru)
                }
                is HasilOperasi.Gagal -> {
                    withContext(NonCancellable) {
                        val pulih = salinBerkasTunggalKeNama(tempUri, parentUri, entitasLama.nama)
                        if (pulih is HasilOperasi.Sukses) hapusAman(tempUri)
                    }
                    hasilFinal
                }
            }
        }

        val targetBaruUri = when (val salin = salinBerkasTunggalKeNama(docUri, parentUri, namaBaru)) {
            is HasilOperasi.Sukses -> salin.nilai
            is HasilOperasi.Gagal -> return salin
        }
        val terhapus = runCatching { DocumentsContract.deleteDocument(contentResolver, docUri) }.getOrDefault(false)
        if (!terhapus) {
            return HasilOperasi.Gagal(
                "Salinan '$namaBaru' sudah dibuat, tetapi gagal menghapus berkas asal",
                PenyebabGalat.IZIN_DITOLAK
            )
        }
        val entitasBaru = bacaSatuEntitas(targetBaruUri)
            ?: return HasilOperasi.Gagal("Gagal membaca berkas hasil ganti nama", PenyebabGalat.TIDAK_DITEMUKAN)
        return HasilOperasi.Sukses(entitasBaru)
    }

    private suspend fun salinBerkasTunggalKeNama(
        sumberUri: Uri,
        parentUri: Uri,
        namaBaru: String
    ): HasilOperasi<Uri> {
        val mime = tentukanMimeType(namaBaru)
        val createdUri = try {
            DocumentsContract.createDocument(contentResolver, parentUri, mime, namaBaru)
                ?: return HasilOperasi.Gagal("Gagal membuat berkas '$namaBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin buat berkas ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal membuat berkas '$namaBaru'", PenyebabGalat.TIDAK_DIKETAHUI)
        }

        val buffer = ByteArray(UKURAN_BUFFER_SALIN_SAF)
        return when (val salin = salinDanVerifikasiStreamSaf(sumberUri, createdUri, buffer, CRC32(), CRC32())) {
            is HasilOperasi.Sukses -> HasilOperasi.Sukses(createdUri)
            is HasilOperasi.Gagal -> {
                withContext(NonCancellable) { hapusAman(createdUri) }
                salin
            }
        }
    }

    private data class EntriFolderPohon(
        val uri: Uri,
        val docId: String,
        val nama: String,
        val indeksFolderInduk: Int
    )

    private data class EntriBerkasPohon(
        val uri: Uri,
        val docId: String,
        val nama: String,
        val indeksFolderInduk: Int
    )

    private data class PohonSaf(
        val daftarFolder: List<EntriFolderPohon>,
        val daftarBerkas: List<EntriBerkasPohon>
    ) {
        val totalItemOperasi: Int get() = daftarFolder.size + daftarBerkas.size
    }

    private data class HasilSalinPohon(
        val rootTujuanUri: Uri,
        val folderTujuanUris: List<Uri>,
        val berkasTujuanUris: List<Uri>
    )

    private suspend fun kumpulkanPohonSaf(
        rootFolderUri: Uri,
        namaRootTampilan: String,
        batasTotalItem: Int,
        pelapor: PelaporProgresOperasi?
    ): HasilOperasi<PohonSaf> {
        val rootDocId = runCatching { DocumentsContract.getDocumentId(rootFolderUri) }.getOrNull()
            ?: rootFolderUri.toString()
        val daftarFolder = ArrayList<EntriFolderPohon>(32)
        val daftarBerkas = ArrayList<EntriBerkasPohon>(64)
        val dikunjungi = HashSet<String>(128)
        val antrean = ArrayDeque<Pair<Int, Int>>()

        dikunjungi.add(rootDocId)
        daftarFolder.add(EntriFolderPohon(rootFolderUri, rootDocId, namaRootTampilan, indeksFolderInduk = -1))
        antrean.addLast(0 to 0)

        while (antrean.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (idxInduk, kedalaman) = antrean.removeFirst()
            if (kedalaman >= BATAS_KEDALAMAN_REKURSIF_SAF) {
                return HasilOperasi.Gagal(
                    "Kedalaman folder melebihi batas aman ($BATAS_KEDALAMAN_REKURSIF_SAF tingkat)",
                    PenyebabGalat.VALIDASI
                )
            }
            pelapor?.onProgres(
                0,
                0,
                "Memindai ${daftarFolder.size} folder, ${daftarBerkas.size} berkas..."
            )

            val anakList = when (val res = daftarAnakSingkat(daftarFolder[idxInduk].uri)) {
                is HasilOperasi.Sukses -> res.nilai
                is HasilOperasi.Gagal -> return res
            }

            for (anak in anakList) {
                currentCoroutineContext().ensureActive()
                if (!dikunjungi.add(anak.docId)) continue
                if (apakahJalurBerulang(anak.docId)) {
                    return HasilOperasi.Gagal(
                        "Terdeteksi potensi siklus tautan simbolik pada '${anak.nama}'",
                        PenyebabGalat.VALIDASI
                    )
                }
                val totalSekarang = daftarFolder.size + daftarBerkas.size
                if (totalSekarang >= batasTotalItem) {
                    return HasilOperasi.Gagal(
                        "Folder berisi lebih dari $batasTotalItem item; operasi fallback SAF dibatalkan agar tidak membebani sistem",
                        PenyebabGalat.TIDAK_DIDUKUNG
                    )
                }
                if (anak.adalahFolder) {
                    val idxBaru = daftarFolder.size
                    daftarFolder.add(EntriFolderPohon(anak.uri, anak.docId, anak.nama, indeksFolderInduk = idxInduk))
                    antrean.addLast(idxBaru to (kedalaman + 1))
                } else {
                    daftarBerkas.add(EntriBerkasPohon(anak.uri, anak.docId, anak.nama, indeksFolderInduk = idxInduk))
                }
            }
        }
        return HasilOperasi.Sukses(PohonSaf(daftarFolder, daftarBerkas))
    }

    private suspend fun salinPohonSafTerpindai(
        pohon: PohonSaf,
        parentTujuanUri: Uri,
        namaFolderBaru: String,
        pelapor: PelaporProgresOperasi?
    ): HasilOperasi<HasilSalinPohon> {
        val folderTujuanUris = ArrayList<Uri>(pohon.daftarFolder.size)
        val berkasTujuanUris = ArrayList<Uri>(pohon.daftarBerkas.size)
        val totalOps = pohon.totalItemOperasi
        var diproses = 0
        val buffer = ByteArray(UKURAN_BUFFER_SALIN_SAF)
        val crcSumber = CRC32()
        val crcAktual = CRC32()

        fun bersihkanSebagian() {
            for (b in berkasTujuanUris.asReversed()) hapusAman(b)
            for (f in folderTujuanUris.asReversed()) hapusAman(f)
        }

        try {
            for (idx in pohon.daftarFolder.indices) {
                currentCoroutineContext().ensureActive()
                val f = pohon.daftarFolder[idx]
                val parentTarget = if (f.indeksFolderInduk < 0) {
                    parentTujuanUri
                } else {
                    folderTujuanUris[f.indeksFolderInduk]
                }
                val namaBuat = if (idx == 0) namaFolderBaru else f.nama
                pelapor?.onProgres(++diproses, totalOps, namaBuat)

                val dirBaru = DocumentsContract.createDocument(
                    contentResolver,
                    parentTarget,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    namaBuat
                ) ?: run {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return HasilOperasi.Gagal("Gagal membuat folder '$namaBuat'", PenyebabGalat.TIDAK_DIKETAHUI)
                }
                folderTujuanUris.add(dirBaru)
            }

            for (berkas in pohon.daftarBerkas) {
                currentCoroutineContext().ensureActive()
                val parentTarget = folderTujuanUris[berkas.indeksFolderInduk]
                pelapor?.onProgres(++diproses, totalOps, berkas.nama)

                val berkasBaru = DocumentsContract.createDocument(
                    contentResolver,
                    parentTarget,
                    tentukanMimeType(berkas.nama),
                    berkas.nama
                ) ?: run {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return HasilOperasi.Gagal("Gagal membuat berkas '${berkas.nama}'", PenyebabGalat.TIDAK_DIKETAHUI)
                }
                berkasTujuanUris.add(berkasBaru)

                val salin = salinDanVerifikasiStreamSaf(berkas.uri, berkasBaru, buffer, crcSumber, crcAktual)
                if (salin is HasilOperasi.Gagal) {
                    withContext(NonCancellable) { bersihkanSebagian() }
                    return salin
                }
            }

            return HasilOperasi.Sukses(
                HasilSalinPohon(
                    rootTujuanUri = folderTujuanUris.first(),
                    folderTujuanUris = folderTujuanUris,
                    berkasTujuanUris = berkasTujuanUris
                )
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) { bersihkanSebagian() }
            throw e
        } catch (e: SecurityException) {
            withContext(NonCancellable) { bersihkanSebagian() }
            return HasilOperasi.Gagal(e.message ?: "Izin menyalin folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            withContext(NonCancellable) { bersihkanSebagian() }
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private fun hapusHasilSalinPohon(hasil: HasilSalinPohon) {
        for (b in hasil.berkasTujuanUris.asReversed()) hapusAman(b)
        for (f in hasil.folderTujuanUris.asReversed()) hapusAman(f)
    }

    private suspend fun hapusPohonDariDaftar(pohon: PohonSaf): HasilOperasi<Unit> {
        return try {
            for (b in pohon.daftarBerkas.asReversed()) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, b.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus berkas asal '${b.nama}'", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            for (f in pohon.daftarFolder.asReversed()) {
                currentCoroutineContext().ensureActive()
                if (!DocumentsContract.deleteDocument(contentResolver, f.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus folder asal '${f.nama}'", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin hapus folder asal SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus folder asal SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }

    private suspend fun salinDanVerifikasiStreamSaf(
        sumberUri: Uri,
        targetUri: Uri,
        buffer: ByteArray,
        crcSumber: CRC32,
        crcAktual: CRC32
    ): HasilOperasi<Unit> {
        crcSumber.reset()
        var totalByte = 0L
        try {
            val inStream = contentResolver.openInputStream(sumberUri)
                ?: return HasilOperasi.Gagal("Gagal membuka aliran baca sumber SAF", PenyebabGalat.IZIN_DITOLAK)
            val outStream = bukaOutputStreamTarget(targetUri, preferModeLangsung = true)
                ?: run {
                    runCatching { inStream.close() }
                    return HasilOperasi.Gagal("Gagal membuka aliran tulis target SAF", PenyebabGalat.IZIN_DITOLAK)
                }

            inStream.use { src ->
                outStream.use { dst ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            dst.write(buffer, 0, n)
                            crcSumber.update(buffer, 0, n)
                            totalByte += n
                        }
                    }
                    dst.flush()
                }
            }

            if (totalByte == 0L) {
                return HasilOperasi.Sukses(Unit)
            }

            val verifikasiIn = try {
                contentResolver.openInputStream(targetUri)
            } catch (_: Exception) {
                null
            }
            if (verifikasiIn != null) {
                crcAktual.reset()
                var byteAktual = 0L
                verifikasiIn.use { ver ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = ver.read(buffer)
                        if (n == -1) break
                        if (n > 0) {
                            crcAktual.update(buffer, 0, n)
                            byteAktual += n
                        }
                    }
                }
                if (byteAktual != totalByte || crcAktual.value != crcSumber.value) {
                    return HasilOperasi.Gagal(
                        "Verifikasi salinan gagal: diharapkan $totalByte byte, terbaca $byteAktual byte",
                        PenyebabGalat.TIDAK_DIKETAHUI
                    )
                }
            }
            return HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            return HasilOperasi.Gagal(e.message ?: "Izin salin dokumen SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: FileNotFoundException) {
            return HasilOperasi.Gagal(e.message ?: "Berkas SAF tidak ditemukan", PenyebabGalat.TIDAK_DITEMUKAN)
        } catch (e: Exception) {
            return HasilOperasi.Gagal(e.message ?: "Gagal menyalin dokumen SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
```

Dan di `hapusFolderRekursifBottomUp(rootFolderUri: Uri)`:
```kotlin
    private suspend fun hapusFolderRekursifBottomUp(rootFolderUri: Uri): HasilOperasi<Unit> {
        val pelapor = currentCoroutineContext()[PelaporProgresOperasi]
        val pohon = when (val res = kumpulkanPohonSaf(rootFolderUri, "", BATAS_ITEM_HAPUS_SAF, pelapor)) {
            is HasilOperasi.Sukses -> res.nilai
            is HasilOperasi.Gagal -> return res
        }
        val totalOps = pohon.totalItemOperasi
        var diproses = 0
        return try {
            for (b in pohon.daftarBerkas.asReversed()) {
                currentCoroutineContext().ensureActive()
                pelapor?.onProgres(++diproses, totalOps, b.nama)
                if (!DocumentsContract.deleteDocument(contentResolver, b.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus salah satu berkas di dalam folder", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            for (f in pohon.daftarFolder.asReversed()) {
                currentCoroutineContext().ensureActive()
                pelapor?.onProgres(++diproses, totalOps, f.nama.ifBlank { "folder" })
                if (!DocumentsContract.deleteDocument(contentResolver, f.uri)) {
                    return HasilOperasi.Gagal("Gagal menghapus folder SAF setelah dikosongkan", PenyebabGalat.IZIN_DITOLAK)
                }
            }
            HasilOperasi.Sukses(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            HasilOperasi.Gagal(e.message ?: "Izin hapus isi folder SAF ditolak", PenyebabGalat.IZIN_DITOLAK)
        } catch (e: Exception) {
            HasilOperasi.Gagal(e.message ?: "Gagal menghapus isi folder SAF", PenyebabGalat.TIDAK_DIKETAHUI)
        }
    }
```
*(Perbarui juga pemanggilan `salinDanVerifikasiStreamSaf` di `gantiNamaBerkasDenganTimpaKonflik` dan `salinEntriKeNamaBaru` menjadi `salinBerkasTunggalKeNama(tempUri, parentUri, namaBaru)` sehingga tidak ada overload lama yang tertinggal.)*

---

### Bagian 4: Test Regresi untuk Opsi 1

#### 7. `shared/src/androidHostTest/kotlin/org/fm/com/data/saf/SafAdapterTest.kt`
```diff
--- a/shared/src/androidHostTest/kotlin/org/fm/com/data/saf/SafAdapterTest.kt
+++ b/shared/src/androidHostTest/kotlin/org/fm/com/data/saf/SafAdapterTest.kt
@@ -292,5 +292,15 @@
                 treeDocId = "/data/data/com.termux/files/home"
             )
         )
+
+        assertFalse(SafOperasiBerkas.apakahJalurBerulang("/data/data/com.termux/files/home/src/src/main.kt"))
+        assertTrue(SafOperasiBerkas.apakahJalurBerulang("/data/data/com.termux/files/home/link/link/link/a.txt"))
+        assertTrue(SafOperasiBerkas.apakahJalurBerulang("/home/a/b/a/b/a/b"))
     }
 }
```

#### 8. `shared/src/commonTest/kotlin/org/fm/com/presentation/filemanager/ViewModelFilemanagerTest.kt`
Tambahkan pengujian agar `sedangMemuat` selalu kembali `false` bila `gantiNama` gagal/melempar exception atau saat progres dilaporkan & dibatalkan:

```kotlin
    @Test
    fun renameGagalTidakMembuatSedangMemuatStuckDanProgresDapatDibatalkan() = runTest {
        val repo = ImplementasiRepositoriBerkas()
        repo.simpan(DataBerkas("/root", "root", JenisLokasi.POSIX, adalahFolder = true))
        repo.simpan(DataBerkas("/root/f.txt", "f.txt", JenisLokasi.POSIX, adalahFolder = false))

        val opLempar = object : org.fm.com.domain.repository.RepositoriOperasiBerkas by ImplementasiRepositoriOperasiBerkas(repo) {
            override suspend fun gantiNama(permintaan: org.fm.com.domain.repository.PermintaanOperasi): org.fm.com.domain.model.HasilOperasi<org.fm.com.domain.model.EntitasBerkas> {
                throw IllegalStateException("Simulasi galat tak terduga")
            }
        }
        val vm = ViewModelFilemanager(repositoriBerkas = repo, repositoriOperasi = opLempar)
        val root = LokasiBerkas("/root", JenisLokasi.POSIX, "root")
        vm.prosesIntent(IntentFilemanager.BukaLokasi(root))
        val item = vm.state.first { !it.sedangMemuat && it.daftarItem.size == 1 }.daftarItem.single()

        vm.prosesIntent(IntentFilemanager.EksekusiRename(item.lokasi, "baru.txt"))
        val stAkhir = vm.state.first { !it.sedangMemuat && it.pesanInfo != null }
        assertFalse(stAkhir.sedangMemuat)
        assertEquals("Simulasi galat tak terduga", stAkhir.pesanInfo)
    }
```

---
