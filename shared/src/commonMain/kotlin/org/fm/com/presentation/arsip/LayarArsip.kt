@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package org.fm.com.presentation.arsip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.arsip.KapabilitasArsip
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.HasilEkstrakArsip
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.KebijakanKonflikEkstrak
import org.fm.com.domain.model.LokasiBerkas
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.fm.com.domain.model.ValidasiNama
import org.fm.com.domain.usecase.KonteksEditArsip
import org.fm.com.domain.usecase.LapisanJejakArsip
import org.fm.com.domain.usecase.SegmenLokasi
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
import org.fm.com.presentation.filemanager.PemetaanDomainUi
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.komponen.PenanganKembaliSistem
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun LayarArsip(
    viewModel: ViewModelArsip,
    onKembali: () -> Unit,
    onBukaBerkasDiEditor: (EntitasBerkas, KonteksEditArsip) -> Unit,
    modifier: Modifier = Modifier,
    onPilihFolderSistem: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val br = state.browser
    val snackbarHostState = remember { SnackbarHostState() }
    val fontMono = LocalFontMono.current
    var entriAksiTerpilih by remember { mutableStateOf<EntriArsipUi?>(null) }

    LaunchedEffect(br.pesanInfo) {
        br.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentArsip.SetPesanInfoBrowser(null))
        }
    }

    LaunchedEffect(state.acaraBukaEditor?.id) {
        val acara = state.acaraBukaEditor ?: return@LaunchedEffect
        onBukaBerkasDiEditor(acara.entitas, acara.konteks)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEditor)
    }

    PenanganKembaliSistem(aktif = true) {
        when {
            br.sedangMengekstrak -> viewModel.prosesIntent(IntentArsip.BatalkanEkstraksi)
            br.sedangMultiSelect -> viewModel.prosesIntent(IntentArsip.BersihkanPilihanEntri)
            br.kataKunciSaring.isNotEmpty() -> viewModel.prosesIntent(IntentArsip.UbahKataKunciSaring(""))
            br.folderDalamLapisan.isNotEmpty() || br.jejakLapisan.size > 1 ->
                viewModel.prosesIntent(IntentArsip.NavigasiNaik)
            else -> onKembali()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (br.sedangMultiSelect) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${br.jalurDipilih.size} dipilih",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.prosesIntent(IntentArsip.BersihkanPilihanEntri) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = IkonAplikasi.Tutup,
                                contentDescription = "Batalkan pilihan entri",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.prosesIntent(IntentArsip.PilihSemuaEntriTampil) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = IkonAplikasi.PilihSemua,
                                contentDescription = "Pilih semua entri",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        TextButton(
                            enabled = br.kapabilitasAktif?.dapatEkstrak != false,
                            onClick = {
                                viewModel.prosesIntent(IntentArsip.MintaEkstrak(br.jalurDipilih))
                            }
                        ) {
                            Text("Ekstrak")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                val aktif = br.jejakLapisan.lastOrNull()
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = aktif?.namaArsip ?: "Arsip",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontMono),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (aktif != null) {
                                "${aktif.format.namaTampilan} · Tingkat ${aktif.kedalaman}/${br.batasKebijakan.batasKedalamanArsipMaks} · ${br.entriDitampilkan.size} item"
                            } else {
                                "Penjelajah arsip"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (br.folderDalamLapisan.isNotEmpty() || br.jejakLapisan.size > 1) {
                                viewModel.prosesIntent(IntentArsip.NavigasiNaik)
                            } else {
                                onKembali()
                            }
                        }) {
                            Icon(
                                imageVector = IkonAplikasi.Kembali,
                                contentDescription = "Kembali",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        if (aktif != null) {
                            IconButton(
                                onClick = { viewModel.prosesIntent(IntentArsip.MintaEkstrak(null)) },
                                enabled = aktif.kapabilitas.dapatEkstrak && !br.sedangMemuat
                            ) {
                                Icon(
                                    imageVector = IkonAplikasi.Ekstrak,
                                    contentDescription = "Ekstrak semua isi arsip",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.prosesIntent(IntentArsip.SetDialogInfoKapabilitas(true)) }
                            ) {
                                Icon(
                                    imageVector = IkonAplikasi.Info,
                                    contentDescription = "Kapabilitas format arsip",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (br.folderDalamLapisan.isNotEmpty() || br.jejakLapisan.size > 1) {
                            IconButton(onClick = onKembali) {
                                Icon(
                                    imageVector = IkonAplikasi.Tutup,
                                    contentDescription = "Tutup penjelajah arsip",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            if (br.jejakLapisan.isNotEmpty()) {
                BreadcrumbArsipBertingkat(
                    jejakLapisan = br.jejakLapisan,
                    segmenFolderDalam = br.segmenFolderDalam,
                    onKlikLapisan = { idx -> viewModel.prosesIntent(IntentArsip.KembaliKeLapisan(idx)) },
                    onKlikFolderDalam = { jalur -> viewModel.prosesIntent(IntentArsip.NavigasiKeFolderDalam(jalur)) }
                )
            }

            KotakSaringArsip(
                kueri = br.kataKunciSaring,
                onUbah = { viewModel.prosesIntent(IntentArsip.UbahKataKunciSaring(it)) }
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    br.sedangMemuat -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "membaca isi arsip...",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    br.pesanGalat != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = br.pesanGalat,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(onClick = onKembali) {
                                Text("Tutup")
                            }
                        }
                    }
                    br.entriDitampilkan.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "// 0 entri di lokasi ini",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = br.entriDitampilkan,
                                key = { it.jalurInternal }
                            ) { entri ->
                                BarisEntriArsip(
                                    entri = entri,
                                    terpilih = entri.jalurInternal in br.jalurDipilih,
                                    modeSeleksi = br.sedangMultiSelect,
                                    onKlik = {
                                        if (br.sedangMultiSelect) {
                                            viewModel.prosesIntent(IntentArsip.TogglePilihEntri(entri.jalurInternal))
                                        } else if (entri.adalahFolder) {
                                            viewModel.prosesIntent(IntentArsip.MasukFolderDalamArsip(entri.jalurInternal))
                                        } else if (entri.adalahArsipBersarang) {
                                            viewModel.prosesIntent(IntentArsip.MasukArsipBersarang(entri.jalurInternal))
                                        } else {
                                            entriAksiTerpilih = entri
                                        }
                                    },
                                    onTekanLama = {
                                        viewModel.prosesIntent(IntentArsip.TogglePilihEntri(entri.jalurInternal))
                                    },
                                    onKlikEkstrak = {
                                        viewModel.prosesIntent(IntentArsip.MintaEkstrak(setOf(entri.jalurInternal)))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    entriAksiTerpilih?.let { entri ->
        AlertDialog(
            onDismissRequest = { entriAksiTerpilih = null },
            title = {
                Text(
                    text = entri.nama,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = fontMono),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Jalur: ${entri.jalurInternal}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ukuran asli: ${entri.ukuranAsliFormat}" +
                            if (entri.metodeKompresi != null) " · ${entri.metodeKompresi}" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        entriAksiTerpilih = null
                        viewModel.prosesIntent(IntentArsip.BukaEntriUntukPratinjau(entri))
                    },
                    enabled = !entri.terenkripsi && !entri.adalahTautanSimbolik
                ) {
                    Text(
                        if (br.kapabilitasAktif?.dapatBuat == true) {
                            "Buka / Edit di Editor"
                        } else {
                            "Buka di Editor (Baca)"
                        }
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { entriAksiTerpilih = null }) {
                        Text("Batal")
                    }
                    TextButton(
                        onClick = {
                            entriAksiTerpilih = null
                            viewModel.prosesIntent(IntentArsip.MintaEkstrak(setOf(entri.jalurInternal)))
                        },
                        enabled = br.kapabilitasAktif?.dapatEkstrak != false
                    ) {
                        Text("Ekstrak")
                    }
                }
            }
        )
    }

    if (br.dialogInfoKapabilitasTerbuka && br.kapabilitasAktif != null) {
        DialogInfoKapabilitasArsip(
            kapabilitas = br.kapabilitasAktif,
            totalUkuranByte = br.totalUkuranLapisanByte,
            adaUkuranTidakDiketahui = br.adaUkuranTidakDiketahui,
            jumlahEntri = br.semuaEntriLapisan.size,
            onTutup = { viewModel.prosesIntent(IntentArsip.SetDialogInfoKapabilitas(false)) }
        )
    }

    OverlayDialogEkstrakArsip(
        viewModel = viewModel,
        onPilihFolderSistem = onPilihFolderSistem
    )
}

@Composable
fun OverlayDialogEkstrakArsip(
    viewModel: ViewModelArsip,
    modifier: Modifier = Modifier,
    onPilihFolderSistem: (() -> Unit)? = null,
    onEkstrakSelesai: ((HasilEkstrakArsip, LokasiBerkas) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val br = state.browser

    LaunchedEffect(state.acaraSuksesEkstrak?.id) {
        val acara = state.acaraSuksesEkstrak ?: return@LaunchedEffect
        onEkstrakSelesai?.invoke(acara.hasil, acara.folderTujuan)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraEkstrak)
    }

    br.dialogKonfirmasiEkstrak?.let { req ->
        DialogKonfirmasiEkstrak(
            permintaan = req,
            lokasiPintasan = state.lokasiPintasan,
            onPilihTujuan = { viewModel.prosesIntent(IntentArsip.UbahFolderTujuanEkstrak(it)) },
            onBukaPemilihFolder = { viewModel.prosesIntent(IntentArsip.BukaPemilihFolderTujuan) },
            onPilihFolderSistem = onPilihFolderSistem,
            onPilihKebijakan = { viewModel.prosesIntent(IntentArsip.UbahKebijakanKonflikEkstrak(it)) },
            onKonfirmasi = { viewModel.prosesIntent(IntentArsip.KonfirmasiDanEkstrak) },
            onBatal = { viewModel.prosesIntent(IntentArsip.BatalDialogEkstrak) },
            modifier = modifier
        )
    }

    if (state.pemilihFolder.terbuka) {
        DialogPemilihFolderEkstrak(
            statePemilih = state.pemilihFolder,
            lokasiPintasan = state.lokasiPintasan,
            onBukaLokasi = { viewModel.prosesIntent(IntentArsip.BukaLokasiDiPemilihFolder(it)) },
            onKlikSegmen = { viewModel.prosesIntent(IntentArsip.NavigasiSegmenPemilihFolder(it)) },
            onBukaDialogBuatFolder = { viewModel.prosesIntent(IntentArsip.SetDialogBuatFolderTujuan(true)) },
            onTutupDialogBuatFolder = { viewModel.prosesIntent(IntentArsip.SetDialogBuatFolderTujuan(false)) },
            onBuatFolder = { viewModel.prosesIntent(IntentArsip.BuatFolderDiPemilihTujuan(it)) },
            onPilihFolderSistem = onPilihFolderSistem,
            onPilihFolderIni = { viewModel.prosesIntent(IntentArsip.KonfirmasiPemilihFolderTujuan) },
            onBatal = { viewModel.prosesIntent(IntentArsip.TutupPemilihFolderTujuan) }
        )
    }

    if (br.sedangMengekstrak) {
        val progres = if (br.progresEkstrakTotal > 0) {
            (br.progresEkstrakDiproses.toFloat() / br.progresEkstrakTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mengekstrak Arsip...") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progres },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${br.progresEkstrakDiproses} dari ${br.progresEkstrakTotal}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (br.progresEkstrakNama.isNotBlank()) {
                        Text(
                            text = br.progresEkstrakNama,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentArsip.BatalkanEkstraksi) }) {
                    Text("Batalkan")
                }
            }
        )
    }

    br.hasilEkstrakTerakhir?.let { hasil ->
        DialogLaporanEkstrakArsip(
            hasil = hasil,
            onTutup = { viewModel.prosesIntent(IntentArsip.TutupLaporanEkstrak) }
        )
    }
}

@Composable
private fun BreadcrumbArsipBertingkat(
    jejakLapisan: List<LapisanJejakArsip>,
    segmenFolderDalam: List<String>,
    onKlikLapisan: (Int) -> Unit,
    onKlikFolderDalam: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val fontMono = LocalFontMono.current
    LaunchedEffect(jejakLapisan.size, segmenFolderDalam.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        jejakLapisan.forEachIndexed { idx, lapisan ->
            val adalahLapisanAkhirDanDiRoot = idx == jejakLapisan.lastIndex && segmenFolderDalam.isEmpty()
            Text(
                text = lapisan.namaArsip,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = fontMono,
                    fontWeight = if (adalahLapisanAkhirDanDiRoot) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (adalahLapisanAkhirDanDiRoot) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = !adalahLapisanAkhirDanDiRoot) { onKlikLapisan(idx) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (idx < jejakLapisan.lastIndex || segmenFolderDalam.isNotEmpty()) {
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        var akumulasi = ""
        segmenFolderDalam.forEachIndexed { idx, seg ->
            akumulasi = if (akumulasi.isEmpty()) seg else "$akumulasi/$seg"
            val targetJalur = akumulasi
            val akhir = idx == segmenFolderDalam.lastIndex
            Text(
                text = seg,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = fontMono,
                    fontWeight = if (akhir) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (akhir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = !akhir) { onKlikFolderDalam(targetJalur) }
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
            if (!akhir) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun KotakSaringArsip(
    kueri: String,
    onUbah: (String) -> Unit
) {
    val fontMono = LocalFontMono.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = IkonAplikasi.Cari,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (kueri.isEmpty()) {
                Text(
                    text = "cari di dalam arsip...",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = kueri,
                onValueChange = onUbah,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = fontMono,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (kueri.isNotEmpty()) {
            Icon(
                imageVector = IkonAplikasi.Tutup,
                contentDescription = "Bersihkan pencarian arsip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onUbah("") }
            )
        }
    }
}

@Composable
private fun BarisEntriArsip(
    entri: EntriArsipUi,
    terpilih: Boolean,
    modeSeleksi: Boolean,
    onKlik: () -> Unit,
    onTekanLama: () -> Unit,
    onKlikEkstrak: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val haptic = LocalHapticFeedback.current
    val jenisIkon = when {
        entri.adalahFolder -> JenisBerkas.FOLDER
        entri.adalahTautanSimbolik -> JenisBerkas.TAUTAN
        entri.adalahArsipBersarang -> JenisBerkas.ARSIP
        else -> JenisBerkas.LAIN
    }
    val latar = if (terpilih) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxWidth().background(latar)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onKlik,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTekanLama()
                    }
                )
                .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (modeSeleksi) {
                Checkbox(checked = terpilih, onCheckedChange = { onKlik() })
                Spacer(modifier = Modifier.width(6.dp))
            }
            KomponenIkonBerkas(jenis = jenisIkon, ukuran = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entri.nama,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = fontMono,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entri.adalahArsipBersarang) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LencanaTeksArsip("ARSIP")
                    }
                    if (entri.terenkripsi) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LencanaTeksArsip("TERENKRIPSI")
                    }
                    if (entri.adalahTautanSimbolik) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LencanaTeksArsip("TAUTAN")
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val info = buildString {
                    append(if (entri.adalahFolder) "dir" else entri.ukuranAsliFormat)
                    if (!entri.adalahFolder && entri.ukuranKompresFormat != "-") {
                        append(" (kompres ${entri.ukuranKompresFormat})")
                    }
                    if (!entri.adalahFolder && entri.metodeKompresi != null) {
                        append(" · ${entri.metodeKompresi}")
                    }
                }
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!modeSeleksi && !entri.adalahTautanSimbolik) {
                IconButton(onClick = onKlikEkstrak, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = IkonAplikasi.Ekstrak,
                        contentDescription = "Ekstrak ${entri.nama}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 48.dp)
        )
    }
}

@Composable
private fun LencanaTeksArsip(teks: String) {
    val fontMono = LocalFontMono.current
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = teks,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 9.5.sp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DialogKonfirmasiEkstrak(
    permintaan: PermintaanKonfirmasiEkstrak,
    lokasiPintasan: List<LokasiBerkas>,
    onPilihTujuan: (LokasiBerkas) -> Unit,
    onBukaPemilihFolder: () -> Unit,
    onPilihFolderSistem: (() -> Unit)?,
    onPilihKebijakan: (KebijakanKonflikEkstrak) -> Unit,
    onKonfirmasi: () -> Unit,
    onBatal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    var menuTujuanTerbuka by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onBatal,
        title = { Text("Konfirmasi Ekstraksi") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Target: ${permintaan.labelTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${permintaan.jumlahItem} item · Perkiraan ${PemformatLokasiRamah.formatRingkasUkuran(permintaan.estimasiUkuranByte)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Folder Tujuan:", style = MaterialTheme.typography.labelLarge)
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                            .clickable(enabled = lokasiPintasan.isNotEmpty()) { menuTujuanTerbuka = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = permintaan.folderTujuan.label ?: "Folder Tujuan",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(permintaan.folderTujuan),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (lokasiPintasan.isNotEmpty()) {
                            Icon(
                                imageVector = IkonAplikasi.TukarLokasi,
                                contentDescription = "Pintasan folder tujuan",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuTujuanTerbuka,
                        onDismissRequest = { menuTujuanTerbuka = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        lokasiPintasan.forEach { lok ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lok.label ?: PemformatLokasiRamah.formatRingkas(lok))
                                        Text(
                                            text = PemformatLokasiRamah.formatRingkas(lok),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    menuTujuanTerbuka = false
                                    onPilihTujuan(lok)
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBukaPemilihFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Jelajahi Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (onPilihFolderSistem != null) {
                        OutlinedButton(
                            onClick = onPilihFolderSistem,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Folder Sistem", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Text("Jika Nama Sudah Ada:", style = MaterialTheme.typography.labelLarge)
                val daftarKebijakan = listOf(
                    KebijakanKonflikEkstrak.TOLAK_KONFLIK to "Tolak bila konflik",
                    KebijakanKonflikEkstrak.LEWATI to "Lewati berkas sama",
                    KebijakanKonflikEkstrak.TIMPA to "Timpa berkas lama"
                )
                daftarKebijakan.forEach { (kebijakan, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPilihKebijakan(kebijakan) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = permintaan.kebijakanKonflik == kebijakan,
                            onClick = { onPilihKebijakan(kebijakan) }
                        )
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onKonfirmasi) {
                Text("Ekstrak Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onBatal) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun DialogLaporanEkstrakArsip(
    hasil: HasilEkstrakArsip,
    onTutup: () -> Unit
) {
    val fontMono = LocalFontMono.current
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Laporan Ekstraksi Arsip") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Diekstrak: ${hasil.jumlahDiekstrak} · Dilewati: ${hasil.jumlahDilewati} · Gagal: ${hasil.daftarGagal.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (hasil.daftarKonflik.isNotEmpty()) {
                    Text(
                        text = "Konflik nama (${hasil.daftarKonflik.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    hasil.daftarKonflik.take(10).forEach { jalur ->
                        Text(
                            text = "• $jalur",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono)
                        )
                    }
                }
                if (hasil.daftarGagal.isNotEmpty()) {
                    Text(
                        text = "Gagal:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    hasil.daftarGagal.take(10).forEach { gagal ->
                        Text(
                            text = "• ${gagal.jalurInternal}: ${gagal.pesan}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTutup) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun DialogInfoKapabilitasArsip(
    kapabilitas: KapabilitasArsip,
    totalUkuranByte: Long,
    adaUkuranTidakDiketahui: Boolean,
    jumlahEntri: Int,
    onTutup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Info & Kapabilitas ${kapabilitas.format.namaTampilan}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Jumlah entri: $jumlahEntri", style = MaterialTheme.typography.bodySmall)
                val ukuranTeks = PemetaanDomainUi.formatUkuran(totalUkuranByte, adalahFolder = false)
                Text(
                    text = "Total ukuran tidak terkompresi: $ukuranTeks" +
                        if (adaUkuranTidakDiketahui) " (sebagian tidak diketahui)" else "",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                BarisStatusKap("Baca daftar & isi", kapabilitas.dapatBaca)
                BarisStatusKap("Ekstrak ke penyimpanan", kapabilitas.dapatEkstrak)
                BarisStatusKap("Buat arsip baru", kapabilitas.dapatBuat)
                BarisStatusKap("Ubah isi langsung (P1)", kapabilitas.dapatUbahIsi)
                BarisStatusKap("Enkripsi kata sandi", kapabilitas.dapatEnkripsi)
                kapabilitas.catatan?.let { catatan ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = catatan,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTutup) {
                Text("Tutup")
            }
        }
    )
}

@Composable
private fun BarisStatusKap(label: String, didukung: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = if (didukung) "Didukung" else "Tidak didukung",
            style = MaterialTheme.typography.labelSmall,
            color = if (didukung) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DialogPemilihFolderEkstrak(
    statePemilih: StatePemilihFolderTujuan,
    lokasiPintasan: List<LokasiBerkas>,
    onBukaLokasi: (LokasiBerkas) -> Unit,
    onKlikSegmen: (SegmenLokasi) -> Unit,
    onBukaDialogBuatFolder: () -> Unit,
    onTutupDialogBuatFolder: () -> Unit,
    onBuatFolder: (String) -> Unit,
    onPilihFolderSistem: (() -> Unit)?,
    onPilihFolderIni: () -> Unit,
    onBatal: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val lokasiAktif = statePemilih.lokasiSaatIni

    Dialog(
        onDismissRequest = onBatal,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pilih Folder Tujuan", style = MaterialTheme.typography.titleMedium)
                        lokasiAktif?.let {
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(it),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (statePemilih.dapatMembuatFolder) {
                        IconButton(onClick = onBukaDialogBuatFolder) {
                            Icon(
                                imageVector = IkonAplikasi.Tambah,
                                contentDescription = "Buat folder baru",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onPilihFolderSistem != null) {
                        IconButton(onClick = onPilihFolderSistem) {
                            Icon(
                                imageVector = IkonAplikasi.PilihFolder,
                                contentDescription = "Pilih dari penyimpanan sistem",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (lokasiPintasan.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lokasiPintasan.forEach { lok ->
                            val terpilih = lokasiAktif != null &&
                                lokasiAktif.pengenal == lok.pengenal &&
                                lokasiAktif.jenis == lok.jenis
                            androidx.compose.material3.FilterChip(
                                selected = terpilih,
                                onClick = { onBukaLokasi(lok) },
                                label = {
                                    Text(
                                        text = lok.label ?: PemformatLokasiRamah.formatRingkas(lok),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                if (statePemilih.segmenBreadcrumb.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statePemilih.segmenBreadcrumb.forEachIndexed { idx, seg ->
                            val akhir = idx == statePemilih.segmenBreadcrumb.lastIndex
                            Text(
                                text = seg.namaTampilan,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = fontMono,
                                    fontWeight = if (akhir) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (akhir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable(enabled = !akhir) { onKlikSegmen(seg) }
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            )
                            if (!akhir) {
                                Text(
                                    text = "/",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                ) {
                    when {
                        statePemilih.sedangMemuat -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        }
                        statePemilih.pesanGalat != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = statePemilih.pesanGalat,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        statePemilih.daftarSubfolder.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "// tidak ada subfolder",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(
                                    items = statePemilih.daftarSubfolder,
                                    key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                                ) { folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBukaLokasi(folder.lokasi) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        KomponenIkonBerkas(jenis = JenisBerkas.FOLDER, ukuran = 20.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = folder.nama,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 48.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onBatal) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onPilihFolderIni,
                        enabled = lokasiAktif != null
                    ) {
                        Text("Pilih Folder Ini")
                    }
                }
            }
        }
    }

    if (statePemilih.dialogBuatFolderTerbuka) {
        var namaFolder by remember { mutableStateOf("") }
        val namaSah = ValidasiNama.sah(namaFolder.trim())
        AlertDialog(
            onDismissRequest = onTutupDialogBuatFolder,
            title = { Text("Buat Folder Tujuan") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = namaFolder,
                    onValueChange = { namaFolder = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    isError = namaFolder.isNotEmpty() && !namaSah,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { if (namaSah) onBuatFolder(namaFolder.trim()) },
                    enabled = namaSah
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(onClick = onTutupDialogBuatFolder) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun PemformatLokasiRamah.formatRingkasUkuran(byte: Long): String =
    PemetaanDomainUi.formatUkuran(byte, adalahFolder = false)
