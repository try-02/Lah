@file:OptIn(ExperimentalMaterial3Api::class)

package org.fm.com.presentation.pengaturan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.beranda.IntentBeranda
import org.fm.com.presentation.beranda.ViewModelBeranda
import org.fm.com.presentation.editor.IntentEditor
import org.fm.com.presentation.editor.ViewModelEditor
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

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
    val statePengaturan by viewModelPengaturan.state.collectAsStateWithLifecycle()
    val stateBeranda by viewModelBeranda.state.collectAsStateWithLifecycle()
    val stateEditor by viewModelEditor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current
    var dialogPilihPintasanBeranda by remember { mutableStateOf(false) }

    LaunchedEffect(statePengaturan.pesanInfo) {
        statePengaturan.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModelPengaturan.prosesIntent(IntentPengaturan.SetPesanInfo(null))
        }
    }
    LaunchedEffect(stateBeranda.pesanInfo) {
        stateBeranda.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModelBeranda.prosesIntent(IntentBeranda.SetPesanInfo(null))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") }
            )
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bagian 1: Homepage / Folder Awal
            JudulBagianPengaturan("Homepage (Folder Awal Tab File)")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = IkonAplikasi.Beranda,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (stateBeranda.memakaiLokasiKhusus) {
                                stateBeranda.lokasiKhusus?.label ?: "Folder Khusus"
                            } else {
                                "Bawaan (${stateBeranda.lokasiBawaan.label ?: "Ruang Kerja"})"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stateBeranda.ringkasanLokasiAktif,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                stateBeranda.peringatanIzinAtauHilang?.let { peringatan ->
                    Text(
                        text = peringatan,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lokasiAktifFileManager != null) {
                        OutlinedButton(
                            onClick = {
                                viewModelBeranda.prosesIntent(IntentBeranda.TetapkanBeranda(lokasiAktifFileManager))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pakai Folder Aktif", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(
                        onClick = {
                            if (onPilihFolderSistemUntukBeranda != null && lokasiPintasan.size <= 1) {
                                onPilihFolderSistemUntukBeranda()
                            } else {
                                dialogPilihPintasanBeranda = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pilih Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (stateBeranda.memakaiLokasiKhusus) {
                    TextButton(
                        onClick = { viewModelBeranda.prosesIntent(IntentBeranda.ResetKeBawaan) }
                    ) {
                        Text("Reset ke Bawaan")
                    }
                }
            }

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

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Bagian 2: Editor & Word Wrap
            JudulBagianPengaturan("Editor Teks & Kode")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModelPengaturan.prosesIntent(
                                IntentPengaturan.SetWordWrapUmum(!statePengaturan.wordWrapUmum)
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Word Wrap Bawaan (Umum)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Membungkus baris panjang secara default pada tab editor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = statePengaturan.wordWrapUmum,
                        onCheckedChange = {
                            viewModelPengaturan.prosesIntent(IntentPengaturan.SetWordWrapUmum(it))
                        }
                    )
                }

                if (stateEditor.daftarTab.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Pengaturan Per Berkas Terbuka",
                        style = MaterialTheme.typography.labelLarge
                    )
                    stateEditor.daftarTab.forEach { tab ->
                        val efektif = tab.wordWrapEfektif(statePengaturan.wordWrapUmum)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.namaBerkas,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (tab.wordWrapOverride == null) {
                                        "Mengikuti pengaturan umum"
                                    } else {
                                        "Override khusus berkas"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (tab.wordWrapOverride != null) {
                                TextButton(onClick = {
                                    viewModelEditor.prosesIntent(IntentEditor.SetWordWrapTab(tab.idTab, null))
                                }) {
                                    Text("Ikut Umum")
                                }
                            }
                            Switch(
                                checked = efektif,
                                onCheckedChange = { aktif ->
                                    viewModelEditor.prosesIntent(IntentEditor.SetWordWrapTab(tab.idTab, aktif))
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Bagian 3: Privasi & Riwayat Lokal
            JudulBagianPengaturan("Privasi & Riwayat Lokal")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Riwayat Pencarian", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${statePengaturan.jumlahRiwayatPencarian} kueri tersimpan lokal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        enabled = statePengaturan.jumlahRiwayatPencarian > 0,
                        onClick = {
                            viewModelPengaturan.prosesIntent(
                                IntentPengaturan.SetDialogHapusRiwayatPencarian(true)
                            )
                        }
                    ) {
                        Text("Hapus")
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Riwayat Lokasi Terbaru", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${statePengaturan.jumlahRiwayatLokasi} lokasi tersimpan lokal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        enabled = statePengaturan.jumlahRiwayatLokasi > 0,
                        onClick = {
                            viewModelPengaturan.prosesIntent(
                                IntentPengaturan.SetDialogHapusRiwayatLokasi(true)
                            )
                        }
                    ) {
                        Text("Hapus")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (dialogPilihPintasanBeranda) {
        AlertDialog(
            onDismissRequest = { dialogPilihPintasanBeranda = false },
            title = { Text("Pilih Folder Beranda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lokasiPintasan.forEach { lok ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    dialogPilihPintasanBeranda = false
                                    viewModelBeranda.prosesIntent(IntentBeranda.TetapkanBeranda(lok))
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = IkonAplikasi.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(lok.label ?: PemformatLokasiRamah.formatRingkas(lok))
                                Text(
                                    text = PemformatLokasiRamah.formatRingkas(lok),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (onPilihFolderSistemUntukBeranda != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(
                            onClick = {
                                dialogPilihPintasanBeranda = false
                                onPilihFolderSistemUntukBeranda()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pilih Folder Lain dari Sistem...")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogPilihPintasanBeranda = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (statePengaturan.dialogKonfirmasiHapusRiwayatPencarian) {
        AlertDialog(
            onDismissRequest = {
                viewModelPengaturan.prosesIntent(IntentPengaturan.SetDialogHapusRiwayatPencarian(false))
            },
            title = { Text("Hapus Riwayat Pencarian") },
            text = { Text("Hapus seluruh riwayat pencarian lokal di perangkat ini?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModelPengaturan.prosesIntent(IntentPengaturan.KonfirmasiHapusRiwayatPencarian)
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModelPengaturan.prosesIntent(IntentPengaturan.SetDialogHapusRiwayatPencarian(false))
                }) {
                    Text("Batal")
                }
            }
        )
    }

    if (statePengaturan.dialogKonfirmasiHapusRiwayatLokasi) {
        AlertDialog(
            onDismissRequest = {
                viewModelPengaturan.prosesIntent(IntentPengaturan.SetDialogHapusRiwayatLokasi(false))
            },
            title = { Text("Hapus Riwayat Lokasi") },
            text = { Text("Hapus seluruh riwayat lokasi terbaru yang tersimpan lokal?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModelPengaturan.prosesIntent(IntentPengaturan.KonfirmasiHapusRiwayatLokasi)
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModelPengaturan.prosesIntent(IntentPengaturan.SetDialogHapusRiwayatLokasi(false))
                }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun JudulBagianPengaturan(judul: String) {
    Text(
        text = judul,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}