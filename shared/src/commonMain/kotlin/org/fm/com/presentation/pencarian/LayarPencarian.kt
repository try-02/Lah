package org.fm.com.presentation.pencarian

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FilterJenisEntitas
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.model.ModeCocokNama
import org.fm.com.presentation.komponen.IkonAplikasi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarPencarian(
    viewModel: ViewModelPencarian,
    onBukaFolder: (LokasiBerkas) -> Unit,
    onBukaBerkasDiEditor: (EntitasBerkas, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tampilDialogGalatBerkas by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pencarian Berkas")
                        state.folderAkar?.let { akar ->
                            Text(
                                text = "Target: ${akar.label ?: akar.pengenal}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.prosesIntent(IntentPencarian.ToggleFilterTerbuka) }) {
                        Icon(
                            imageVector = IkonAplikasi.Filter,
                            contentDescription = if (state.filterTerbuka) "Tutup filter" else "Buka filter",
                            tint = if (state.filterTerbuka) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            )
        }
    ) { bantalan ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.modePencarian == ModePencarian.NAMA,
                    onClick = { viewModel.prosesIntent(IntentPencarian.UbahModePencarian(ModePencarian.NAMA)) },
                    label = { Text("Cari Nama") }
                )
                FilterChip(
                    selected = state.modePencarian == ModePencarian.ISI,
                    onClick = { viewModel.prosesIntent(IntentPencarian.UbahModePencarian(ModePencarian.ISI)) },
                    label = { Text("Cari Isi Berkas") }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.kueri,
                    onValueChange = { viewModel.prosesIntent(IntentPencarian.UbahKueri(it)) },
                    label = {
                        Text(
                            if (state.modePencarian == ModePencarian.NAMA) {
                                "Nama berkas/pola (opsional jika pakai filter)"
                            } else {
                                "Teks atau regex di dalam berkas"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { viewModel.prosesIntent(IntentPencarian.MulaiPencarian) }
                    ),
                    modifier = Modifier.weight(1f)
                )
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

            AnimatedVisibility(visible = state.filterTerbuka) {
                PanelFilterPencarian(
                    state = state,
                    onIntent = viewModel::prosesIntent
                )
            }

            state.pesanGalat?.let { galat ->
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = galat,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (state.sedangMencari) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("Memindai ${state.jumlahBerkasDipindai} berkas di ${state.jumlahFolderDipindai} folder")
                        if (state.itemDiprosesSaatIni.isNotBlank()) {
                            append(" • ${state.itemDiprosesSaatIni}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (state.pernahMencari) {
                Spacer(modifier = Modifier.height(6.dp))
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
                            append("$jumlahHasil hasil • ${state.jumlahBerkasDipindai} dipindai")
                            if (state.jumlahBinerDilewati > 0) append(" • ${state.jumlahBinerDilewati} biner dilewati")
                            if (state.jumlahTerlaluBesarDilewati > 0) append(" • ${state.jumlahTerlaluBesarDilewati} terlalu besar")
                            if (state.terpotongBatasHasil) append(" (batas maks)")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.daftarGalatBerkas.isNotEmpty() || state.jumlahPeringatanFolder > 0) {
                        TextButton(onClick = { tampilDialogGalatBerkas = true }) {
                            val totalGalat = state.daftarGalatBerkas.size + state.jumlahPeringatanFolder
                            Text(
                                text = "$totalGalat gagal baca",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val daftarKosong = if (state.modePencarian == ModePencarian.NAMA) {
                    state.hasilNama.isEmpty()
                } else {
                    state.hasilIsi.isEmpty()
                }

                if (!state.pernahMencari) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Masukkan kata kunci atau aktifkan filter lalu tekan Cari",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!state.sedangMencari && daftarKosong && state.pesanGalat == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tidak ada berkas yang cocok dengan kriteria pencarian",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (state.modePencarian == ModePencarian.NAMA) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
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
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                        items(
                            items = state.hasilIsi,
                            key = { "${it.entitas.lokasi.jenis.name}:${it.entitas.lokasi.pengenal}" }
                        ) { item ->
                            KartuHasilCariIsi(
                                item = item,
                                onBukaPadaBaris = onBukaBerkasDiEditor
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.daftarGalatBerkas.size > 20) {
                        Text(
                            text = "...dan ${state.daftarGalatBerkas.size - 20} berkas lainnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
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
private fun PanelFilterPencarian(
    state: StatePencarian,
    onIntent: (IntentPencarian) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pengaturan Filter", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onIntent(IntentPencarian.ResetFilter) }) {
                    Text("Reset")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.rekursif,
                        onCheckedChange = { onIntent(IntentPencarian.ToggleRekursif) }
                    )
                    Text("Subfolder", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.sertakanTersembunyi,
                        onCheckedChange = { onIntent(IntentPencarian.ToggleTersembunyi) }
                    )
                    Text("Tersembunyi", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.filterEkstensiInput,
                onValueChange = { onIntent(IntentPencarian.UbahFilterEkstensi(it)) },
                label = { Text("Ekstensi (pisahkan koma, mis: kt, txt, md)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SatuanUkuranFilter.entries.forEach { satuan ->
                    FilterChip(
                        selected = state.satuanUkuran == satuan,
                        onClick = { onIntent(IntentPencarian.UbahSatuanUkuran(satuan)) },
                        label = { Text(satuan.label) }
                    )
                }
            }

            if (state.modePencarian == ModePencarian.NAMA) {
                Text("Mode Pencocokan Nama", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeCocokNama.entries.forEach { mode ->
                        val label = when (mode) {
                            ModeCocokNama.SEBAGIAN -> "Sebagian"
                            ModeCocokNama.LENGKAP -> "Lengkap"
                            ModeCocokNama.GLOB -> "Glob (*?)"
                            ModeCocokNama.REGEX -> "Regex"
                        }
                        FilterChip(
                            selected = state.modeCocokNama == mode,
                            onClick = { onIntent(IntentPencarian.UbahModeCocokNama(mode)) },
                            label = { Text(label) }
                        )
                    }
                    FilterChip(
                        selected = state.pekaHurufNama,
                        onClick = { onIntent(IntentPencarian.TogglePekaHurufNama) },
                        label = { Text("Peka Huruf (Aa)") }
                    )
                }

                Text("Jenis & Tanggal Modifikasi", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterJenisEntitas.entries.forEach { jenis ->
                        val label = when (jenis) {
                            FilterJenisEntitas.SEMUA -> "Semua"
                            FilterJenisEntitas.BERKAS_SAJA -> "Berkas"
                            FilterJenisEntitas.FOLDER_SAJA -> "Folder"
                        }
                        FilterChip(
                            selected = state.filterJenisNama == jenis,
                            onClick = { onIntent(IntentPencarian.UbahFilterJenisNama(jenis)) },
                            label = { Text(label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RentangTanggalFilter.entries.forEach { rentang ->
                        FilterChip(
                            selected = state.rentangTanggal == rentang,
                            onClick = { onIntent(IntentPencarian.UbahRentangTanggal(rentang)) },
                            label = { Text(rentang.label) }
                        )
                    }
                }
            } else {
                Text("Opsi Pencarian Isi", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.pekaHurufIsi,
                        onClick = { onIntent(IntentPencarian.TogglePekaHurufIsi) },
                        label = { Text("Peka Huruf (Aa)") }
                    )
                    FilterChip(
                        selected = state.kataUtuhIsi,
                        onClick = { onIntent(IntentPencarian.ToggleKataUtuhIsi) },
                        label = { Text("Kata Utuh") }
                    )
                    FilterChip(
                        selected = state.gunakanRegexIsi,
                        onClick = { onIntent(IntentPencarian.ToggleRegexIsi) },
                        label = { Text("Regex") }
                    )
                    FilterChip(
                        selected = state.hanyaFormatTeksIsi,
                        onClick = { onIntent(IntentPencarian.ToggleHanyaFormatTeksIsi) },
                        label = { Text("Format Teks Saja") }
                    )
                }

                Text("Batas Ukuran Per Berkas", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OpsiBatasUkuranIsi.entries.forEach { opsi ->
                        FilterChip(
                            selected = state.batasUkuranIsi == opsi,
                            onClick = { onIntent(IntentPencarian.UbahBatasUkuranIsi(opsi)) },
                            label = { Text(opsi.label) }
                        )
                    }
                }
            }
        }
    }
}