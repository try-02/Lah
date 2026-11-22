@file:OptIn(ExperimentalMaterial3Api::class)

package org.fm.com.presentation.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.EntitasBookmark
import org.fm.com.domain.model.JenisBerkas
import org.fm.com.domain.model.JenisTargetBookmark
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun LayarBookmark(
    viewModel: ViewModelBookmark,
    onBukaBookmark: (EntitasBookmark) -> Unit,
    modifier: Modifier = Modifier,
    lokasiPintasan: List<LokasiBerkas> = emptyList(),
    onPilihFolderSistem: ((EntitasBookmark) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current
    val snackbarHostState = remember { SnackbarHostState() }
    var itemBermasalahDipilih by remember { mutableStateOf<ItemBookmarkUi?>(null) }

    LaunchedEffect(state.pesanInfo) {
        state.pesanInfo?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.prosesIntent(IntentBookmark.SetPesanInfo(null))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bookmark")
                        Text(
                            text = "${state.jumlahItem} lokasi tersimpan" +
                                if (state.sedangMemeriksaStatus) " · memeriksa..." else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.jumlahItem > 0) {
                        IconButton(onClick = { viewModel.prosesIntent(IntentBookmark.PeriksaUlangSemua) }) {
                            Icon(
                                imageVector = IkonAplikasi.Segarkan,
                                contentDescription = "Periksa ketersediaan semua bookmark",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        TextButton(onClick = { viewModel.prosesIntent(IntentBookmark.SetDialogHapusSemua(true)) }) {
                            Text("Bersihkan")
                        }
                    }
                }
            )
        }
    ) { bantalan ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            when {
                state.sedangMemuat -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
                state.pesanGalat != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.pesanGalat ?: "Gagal memuat bookmark",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.daftarBookmark.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = IkonAplikasi.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "// belum ada bookmark tersimpan",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.daftarBookmark,
                            key = { "${it.bookmark.lokasi.jenis.name}:${it.bookmark.lokasi.pengenal}" }
                        ) { item ->
                            BarisBookmark(
                                item = item,
                                onKlik = {
                                    if (item.bermasalah) {
                                        itemBermasalahDipilih = item
                                    } else {
                                        onBukaBookmark(item.bookmark)
                                    }
                                },
                                onPilihUlang = {
                                    viewModel.prosesIntent(IntentBookmark.MulaiPilihUlang(item.bookmark))
                                },
                                onHapus = {
                                    viewModel.prosesIntent(IntentBookmark.HapusItem(item.bookmark.lokasi))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    itemBermasalahDipilih?.let { item ->
        AlertDialog(
            onDismissRequest = { itemBermasalahDipilih = null },
            title = { Text("Lokasi Tidak Tersedia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.nama,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = fontMono)
                    )
                    Text(
                        text = item.teksStatusMasalah ?: "Izin akses dicabut atau lokasi sudah dipindahkan/dihapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = item.ringkasanLokasi,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    itemBermasalahDipilih = null
                    viewModel.prosesIntent(IntentBookmark.MulaiPilihUlang(item.bookmark))
                }) {
                    Text("Pilih Ulang Lokasi")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { itemBermasalahDipilih = null }) {
                        Text("Batal")
                    }
                    TextButton(onClick = {
                        itemBermasalahDipilih = null
                        viewModel.prosesIntent(IntentBookmark.HapusItem(item.bookmark.lokasi))
                    }) {
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    if (state.dialogHapusSemuaTerbuka) {
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentBookmark.SetDialogHapusSemua(false)) },
            title = { Text("Hapus Semua Bookmark") },
            text = { Text("Hapus seluruh daftar bookmark yang tersimpan?") },
            confirmButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentBookmark.KonfirmasiHapusSemua) }) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentBookmark.SetDialogHapusSemua(false)) }) {
                    Text("Batal")
                }
            }
        )
    }

    if (state.pemilihUlang.terbuka) {
        DialogPilihUlangBookmark(
            statePemilih = state.pemilihUlang,
            lokasiPintasan = lokasiPintasan,
            onBukaFolder = { viewModel.prosesIntent(IntentBookmark.BukaFolderDiPemilih(it)) },
            onKlikSegmen = { viewModel.prosesIntent(IntentBookmark.KlikSegmenPemilih(it)) },
            onPilihEntitas = { viewModel.prosesIntent(IntentBookmark.PilihEntitasBaruBookmark(it)) },
            onGunakanFolderIni = { viewModel.prosesIntent(IntentBookmark.GunakanFolderSaatIniUntukBookmark) },
            onPilihFolderSistem = state.pemilihUlang.bookmarkLama?.let { bm ->
                if (onPilihFolderSistem != null) {
                    { onPilihFolderSistem(bm) }
                } else null
            },
            onBatal = { viewModel.prosesIntent(IntentBookmark.TutupPemilihUlang) }
        )
    }
}

@Composable
private fun BarisBookmark(
    item: ItemBookmarkUi,
    onKlik: () -> Unit,
    onPilihUlang: () -> Unit,
    onHapus: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val jenisIkon = when (item.bookmark.jenisTarget) {
        JenisTargetBookmark.FOLDER -> JenisBerkas.FOLDER
        JenisTargetBookmark.ARSIP -> JenisBerkas.ARSIP
        JenisTargetBookmark.BERKAS -> JenisBerkas.LAIN
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onKlik)
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KomponenIkonBerkas(jenis = jenisIkon, ukuran = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nama,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = fontMono,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.labelJenis,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.bermasalah) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "⚠ HILANG",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 9.5.sp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.teksStatusMasalah?.let { "$it · ${item.ringkasanLokasi}" } ?: item.ringkasanLokasi,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 11.5.sp),
                    color = if (item.bermasalah) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.bermasalah) {
                TextButton(onClick = onPilihUlang) {
                    Text("Pilih Ulang")
                }
            }
            IconButton(onClick = onHapus, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = IkonAplikasi.Hapus,
                    contentDescription = "Hapus bookmark ${item.nama}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
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
private fun DialogPilihUlangBookmark(
    statePemilih: StatePemilihUlangBookmark,
    lokasiPintasan: List<LokasiBerkas>,
    onBukaFolder: (LokasiBerkas) -> Unit,
    onKlikSegmen: (org.fm.com.domain.usecase.SegmenLokasi) -> Unit,
    onPilihEntitas: (org.fm.com.domain.model.EntitasBerkas) -> Unit,
    onGunakanFolderIni: () -> Unit,
    onPilihFolderSistem: (() -> Unit)?,
    onBatal: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val bmLama = statePemilih.bookmarkLama
    val adalahFolder = bmLama?.adalahFolder == true

    Dialog(
        onDismissRequest = onBatal,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pilih Ulang Lokasi Bookmark", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (adalahFolder) "Pilih folder pengganti" else "Pilih berkas/arsip pengganti",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            val terpilih = statePemilih.lokasiSaatIni == lok
                            FilterChip(
                                selected = terpilih,
                                onClick = { onBukaFolder(lok) },
                                label = { Text(lok.label ?: PemformatLokasiRamah.formatRingkas(lok)) }
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
                                Text("/", modifier = Modifier.padding(horizontal = 2.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
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
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        statePemilih.daftarEntitas.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "// folder kosong",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(
                                    items = statePemilih.daftarEntitas,
                                    key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                                ) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (item.jenis == JenisBerkas.FOLDER) {
                                                    onBukaFolder(item.lokasi)
                                                } else {
                                                    onPilihEntitas(item)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        KomponenIkonBerkas(jenis = item.jenis, ukuran = 20.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = item.nama,
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
                    if (adalahFolder) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onGunakanFolderIni,
                            enabled = statePemilih.lokasiSaatIni != null
                        ) {
                            Text("Pilih Folder Ini")
                        }
                    }
                }
            }
        }
    }
}
