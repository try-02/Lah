@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package org.fm.com.presentation.pencarian

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

private data class ChipFilterAktif(
    val label: String,
    val onHapus: () -> Unit
)

@Composable
fun LayarPencarian(
    viewModel: ViewModelPencarian,
    onBukaFolder: (LokasiBerkas) -> Unit,
    onBukaBerkasDiEditor: (EntitasBerkas, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current
    var tampilDialogGalatBerkas by remember { mutableStateOf(false) }

    val daftarChipAktif = remember(state) {
        bangunChipFilterAktif(state, viewModel::prosesIntent)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pencarian")
                        state.folderAkar?.let { akar ->
                            Text(
                                text = PemformatLokasiRamah.formatRingkas(akar),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = fontMono,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.prosesIntent(IntentPencarian.ToggleFilterTerbuka) }) {
                        Icon(
                            imageVector = IkonAplikasi.Filter,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val labelFilter = if (daftarChipAktif.isNotEmpty()) {
                            "Filter (${daftarChipAktif.size})"
                        } else {
                            "Filter"
                        }
                        Text(labelFilter)
                    }
                }
            )
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val daftarMode = ModePencarian.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    daftarMode.forEachIndexed { indeks, mode ->
                        SegmentedButton(
                            selected = state.modePencarian == mode,
                            onClick = { viewModel.prosesIntent(IntentPencarian.UbahModePencarian(mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index = indeks, count = daftarMode.size),
                            icon = {},
                            label = {
                                Text(
                                    text = if (mode == ModePencarian.NAMA) "Cari Nama" else "Cari Isi",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.kueri.isEmpty()) {
                                Text(
                                    text = if (state.modePencarian == ModePencarian.NAMA) {
                                        "nama atau pola..."
                                    } else {
                                        "teks atau regex..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = fontMono,
                                        fontSize = 13.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = state.kueri,
                                onValueChange = { viewModel.prosesIntent(IntentPencarian.UbahKueri(it)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = fontMono,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { viewModel.prosesIntent(IntentPencarian.MulaiPencarian) }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (state.kueri.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = IkonAplikasi.Tutup,
                                contentDescription = "Hapus kueri",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.prosesIntent(IntentPencarian.UbahKueri("")) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.sedangMencari) {
                        OutlinedButton(onClick = { viewModel.prosesIntent(IntentPencarian.BatalkanPencarian) }) {
                            Text("Batal")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.prosesIntent(IntentPencarian.MulaiPencarian) },
                            enabled = state.folderAkar != null
                        ) {
                            Text("Cari")
                        }
                    }
                }

                if (daftarChipAktif.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        daftarChipAktif.forEach { chip ->
                            InputChip(
                                selected = true,
                                onClick = chip.onHapus,
                                label = {
                                    Text(
                                        text = chip.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = IkonAplikasi.Tutup,
                                        contentDescription = "Hapus filter ${chip.label}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                state.pesanGalat?.let { galat ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = galat,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.sedangMencari,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = buildString {
                                append("${state.jumlahBerkasDipindai} berkas · ${state.jumlahFolderDipindai} folder")
                                if (state.itemDiprosesSaatIni.isNotBlank()) {
                                    append(" · ${state.itemDiprosesSaatIni}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!state.sedangMencari && state.pernahMencari) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val jumlahHasil = if (state.modePencarian == ModePencarian.NAMA) {
                            state.hasilNama.size
                        } else {
                            state.hasilIsi.size
                        }
                        Text(
                            text = buildString {
                                append("$jumlahHasil hasil · ${state.jumlahBerkasDipindai} dipindai")
                                if (state.jumlahBinerDilewati > 0) append(" · ${state.jumlahBinerDilewati} biner")
                                if (state.jumlahTerlaluBesarDilewati > 0) append(" · ${state.jumlahTerlaluBesarDilewati} besar")
                                if (state.terpotongBatasHasil) append(" (maks)")
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.daftarGalatBerkas.isNotEmpty() || state.jumlahPeringatanFolder > 0) {
                            TextButton(onClick = { tampilDialogGalatBerkas = true }) {
                                val totalGalat = state.daftarGalatBerkas.size + state.jumlahPeringatanFolder
                                Text(
                                    text = "$totalGalat gagal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val daftarKosong = if (state.modePencarian == ModePencarian.NAMA) {
                    state.hasilNama.isEmpty()
                } else {
                    state.hasilIsi.isEmpty()
                }

                if (!state.pernahMencari) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "find . -name  // ketik pola atau buka Filter",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!state.sedangMencari && daftarKosong && state.pesanGalat == null) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "// 0 berkas cocok",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (state.modePencarian == ModePencarian.NAMA) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.hasilNama,
                            key = { "${it.entitas.lokasi.jenis.name}:${it.entitas.lokasi.pengenal}" }
                        ) { item ->
                            KartuHasilCariNama(
                                item = item,
                                onKlik = {
                                    if (item.adalahFolder) {
                                        onBukaFolder(item.entitas.lokasi)
                                    } else {
                                        onBukaBerkasDiEditor(item.entitas, 1)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.hasilIsi,
                            key = { "${it.entitas.lokasi.jenis.name}:${it.entitas.lokasi.pengenal}" }
                        ) { item ->
                            KartuHasilCariIsi(
                                item = item,
                                onBukaPadaBaris = onBukaBerkasDiEditor
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.filterTerbuka) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.prosesIntent(IntentPencarian.ToggleFilterTerbuka) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            LembarFilterPencarian(
                state = state,
                onIntent = viewModel::prosesIntent,
                onSelesai = { viewModel.prosesIntent(IntentPencarian.ToggleFilterTerbuka) }
            )
        }
    }

    if (tampilDialogGalatBerkas) {
        AlertDialog(
            onDismissRequest = { tampilDialogGalatBerkas = false },
            title = { Text("Berkas / Folder Tidak Terbaca") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.jumlahPeringatanFolder > 0) {
                        Text(
                            text = "${state.jumlahPeringatanFolder} subfolder gagal dibuka dan dilewati.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    state.daftarGalatBerkas.take(20).forEach { g ->
                        Text(
                            text = "• ${g.lokasiRelatif}: ${g.pesan}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.daftarGalatBerkas.size > 20) {
                        Text(
                            text = "...dan ${state.daftarGalatBerkas.size - 20} berkas lainnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tampilDialogGalatBerkas = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun LembarFilterPencarian(
    state: StatePencarian,
    onIntent: (IntentPencarian) -> Unit,
    onSelesai: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter Pencarian", style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { onIntent(IntentPencarian.ResetFilter) }) {
                    Text("Reset")
                }
                TextButton(onClick = onSelesai) {
                    Text("Terapkan")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onIntent(IntentPencarian.ToggleRekursif) }
            ) {
                Checkbox(
                    checked = state.rekursif,
                    onCheckedChange = { onIntent(IntentPencarian.ToggleRekursif) }
                )
                Text("Subfolder", style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onIntent(IntentPencarian.ToggleTersembunyi) }
            ) {
                Checkbox(
                    checked = state.sertakanTersembunyi,
                    onCheckedChange = { onIntent(IntentPencarian.ToggleTersembunyi) }
                )
                Text("Tersembunyi", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = state.filterEkstensiInput,
            onValueChange = { onIntent(IntentPencarian.UbahFilterEkstensi(it)) },
            label = { Text("Ekstensi (mis: kt, py, js)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.ukuranMinInput,
                onValueChange = { onIntent(IntentPencarian.UbahUkuranMin(it)) },
                label = { Text("Ukuran Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.ukuranMaksInput,
                onValueChange = { onIntent(IntentPencarian.UbahUkuranMaks(it)) },
                label = { Text("Ukuran Maks") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        val satuanList = SatuanUkuranFilter.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            satuanList.forEachIndexed { idx, satuan ->
                SegmentedButton(
                    selected = state.satuanUkuran == satuan,
                    onClick = { onIntent(IntentPencarian.UbahSatuanUkuran(satuan)) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = satuanList.size),
                    icon = {},
                    label = { Text(satuan.label) }
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        if (state.modePencarian == ModePencarian.NAMA) {
            Text("Mode Pencocokan", style = MaterialTheme.typography.labelLarge)
            val modeList = ModeCocokNama.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modeList.forEachIndexed { idx, mode ->
                    val label = when (mode) {
                        ModeCocokNama.SEBAGIAN -> "Sebagian"
                        ModeCocokNama.LENGKAP -> "Penuh"
                        ModeCocokNama.GLOB -> "Glob"
                        ModeCocokNama.REGEX -> "Regex"
                    }
                    SegmentedButton(
                        selected = state.modeCocokNama == mode,
                        onClick = { onIntent(IntentPencarian.UbahModeCocokNama(mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modeList.size),
                        icon = {},
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            Text("Jenis Entitas", style = MaterialTheme.typography.labelLarge)
            val jenisList = FilterJenisEntitas.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                jenisList.forEachIndexed { idx, jenis ->
                    val label = when (jenis) {
                        FilterJenisEntitas.SEMUA -> "Semua"
                        FilterJenisEntitas.BERKAS_SAJA -> "Berkas"
                        FilterJenisEntitas.FOLDER_SAJA -> "Folder"
                    }
                    SegmentedButton(
                        selected = state.filterJenisNama == jenis,
                        onClick = { onIntent(IntentPencarian.UbahFilterJenisNama(jenis)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = jenisList.size),
                        icon = {},
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }

            Text("Tanggal Modifikasi", style = MaterialTheme.typography.labelLarge)
            val tanggalList = RentangTanggalFilter.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                tanggalList.forEachIndexed { idx, rentang ->
                    SegmentedButton(
                        selected = state.rentangTanggal == rentang,
                        onClick = { onIntent(IntentPencarian.UbahRentangTanggal(rentang)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tanggalList.size),
                        icon = {},
                        label = { Text(rentang.label, maxLines = 1) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onIntent(IntentPencarian.TogglePekaHurufNama) }
            ) {
                Checkbox(
                    checked = state.pekaHurufNama,
                    onCheckedChange = { onIntent(IntentPencarian.TogglePekaHurufNama) }
                )
                Text("Peka huruf besar/kecil (Aa)", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("Opsi Pemindaian Isi", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIntent(IntentPencarian.TogglePekaHurufIsi) }
                ) {
                    Checkbox(
                        checked = state.pekaHurufIsi,
                        onCheckedChange = { onIntent(IntentPencarian.TogglePekaHurufIsi) }
                    )
                    Text("Peka Huruf", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIntent(IntentPencarian.ToggleKataUtuhIsi) }
                ) {
                    Checkbox(
                        checked = state.kataUtuhIsi,
                        onCheckedChange = { onIntent(IntentPencarian.ToggleKataUtuhIsi) }
                    )
                    Text("Kata Utuh", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIntent(IntentPencarian.ToggleRegexIsi) }
                ) {
                    Checkbox(
                        checked = state.gunakanRegexIsi,
                        onCheckedChange = { onIntent(IntentPencarian.ToggleRegexIsi) }
                    )
                    Text("Regex", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIntent(IntentPencarian.ToggleHanyaFormatTeksIsi) }
                ) {
                    Checkbox(
                        checked = state.hanyaFormatTeksIsi,
                        onCheckedChange = { onIntent(IntentPencarian.ToggleHanyaFormatTeksIsi) }
                    )
                    Text("Format Teks", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Batas Ukuran Per Berkas", style = MaterialTheme.typography.labelLarge)
            val batasList = OpsiBatasUkuranIsi.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                batasList.forEachIndexed { idx, opsi ->
                    SegmentedButton(
                        selected = state.batasUkuranIsi == opsi,
                        onClick = { onIntent(IntentPencarian.UbahBatasUkuranIsi(opsi)) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = batasList.size),
                        icon = {},
                        label = { Text(opsi.label, maxLines = 1) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun bangunChipFilterAktif(
    state: StatePencarian,
    onIntent: (IntentPencarian) -> Unit
): List<ChipFilterAktif> = buildList {
    if (!state.rekursif) {
        add(ChipFilterAktif("folder ini saja") { onIntent(IntentPencarian.ToggleRekursif) })
    }
    if (state.sertakanTersembunyi) {
        add(ChipFilterAktif("tersembunyi") { onIntent(IntentPencarian.ToggleTersembunyi) })
    }
    if (state.filterEkstensiInput.isNotBlank()) {
        add(ChipFilterAktif("ext:${state.filterEkstensiInput.trim()}") {
            onIntent(IntentPencarian.UbahFilterEkstensi(""))
        })
    }
    if (state.ukuranMinInput.isNotBlank()) {
        add(ChipFilterAktif(">= ${state.ukuranMinInput.trim()} ${state.satuanUkuran.label}") {
            onIntent(IntentPencarian.UbahUkuranMin(""))
        })
    }
    if (state.ukuranMaksInput.isNotBlank()) {
        add(ChipFilterAktif("<= ${state.ukuranMaksInput.trim()} ${state.satuanUkuran.label}") {
            onIntent(IntentPencarian.UbahUkuranMaks(""))
        })
    }
    if (state.modePencarian == ModePencarian.NAMA) {
        if (state.modeCocokNama != ModeCocokNama.SEBAGIAN) {
            add(ChipFilterAktif(state.modeCocokNama.name.lowercase()) {
                onIntent(IntentPencarian.UbahModeCocokNama(ModeCocokNama.SEBAGIAN))
            })
        }
        if (state.pekaHurufNama) {
            add(ChipFilterAktif("Aa") { onIntent(IntentPencarian.TogglePekaHurufNama) })
        }
        if (state.filterJenisNama != FilterJenisEntitas.SEMUA) {
            val label = if (state.filterJenisNama == FilterJenisEntitas.BERKAS_SAJA) "berkas" else "folder"
            add(ChipFilterAktif(label) {
                onIntent(IntentPencarian.UbahFilterJenisNama(FilterJenisEntitas.SEMUA))
            })
        }
        if (state.rentangTanggal != RentangTanggalFilter.SEMUA) {
            add(ChipFilterAktif(state.rentangTanggal.label) {
                onIntent(IntentPencarian.UbahRentangTanggal(RentangTanggalFilter.SEMUA))
            })
        }
    } else {
        if (state.pekaHurufIsi) {
            add(ChipFilterAktif("Aa") { onIntent(IntentPencarian.TogglePekaHurufIsi) })
        }
        if (state.kataUtuhIsi) {
            add(ChipFilterAktif("kata utuh") { onIntent(IntentPencarian.ToggleKataUtuhIsi) })
        }
        if (state.gunakanRegexIsi) {
            add(ChipFilterAktif("regex") { onIntent(IntentPencarian.ToggleRegexIsi) })
        }
        if (!state.hanyaFormatTeksIsi) {
            add(ChipFilterAktif("semua tipe") { onIntent(IntentPencarian.ToggleHanyaFormatTeksIsi) })
        }
        if (state.batasUkuranIsi != OpsiBatasUkuranIsi.DUA_MIB) {
            add(ChipFilterAktif("maks:${state.batasUkuranIsi.label}") {
                onIntent(IntentPencarian.UbahBatasUkuranIsi(OpsiBatasUkuranIsi.DUA_MIB))
            })
        }
    }
}