@file:OptIn(ExperimentalLayoutApi::class)

package org.fm.com.presentation.rename

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.rename.ItemPratinjauRename
import org.fm.com.domain.rename.StatusItemPratinjauRename
import org.fm.com.domain.usecase.HasilEksekusiRename
import org.fm.com.domain.usecase.RingkasanKonfirmasiRename
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

private data class ChipTokenCepat(
    val token: String,
    val keterangan: String
)

private val DAFTAR_TOKEN_CEPAT = listOf(
    ChipTokenCepat("{P}", "Nama"),
    ChipTokenCepat("{S}", "Eks"),
    ChipTokenCepat("{N}", "No"),
    ChipTokenCepat("{zN}", "01"),
    ChipTokenCepat("{zN:3}", "001")
)

@Composable
fun DialogBulkRename(
    viewModel: ViewModelBulkRename,
    onSelesaiSukses: (jumlahBerhasil: Int) -> Unit,
    onTutupDenganPerubahan: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current
    var menuUrutanTerbuka by remember { mutableStateOf(false) }

    LaunchedEffect(state.acaraSuksesPenuh?.id) {
        val acara = state.acaraSuksesPenuh ?: return@LaunchedEffect
        onSelesaiSukses(acara.jumlahBerhasil)
        viewModel.prosesIntent(IntentBulkRename.KonsumsiAcaraSukses)
    }

    if (state.terbuka) {
        Dialog(
            onDismissRequest = {
                if (!state.sedangMengeksekusi) {
                    val perluSegarkan = state.perluSegarkanSaatTutup
                    viewModel.prosesIntent(IntentBulkRename.Tutup)
                    if (perluSegarkan) onTutupDenganPerubahan()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 640.dp)
                    .heightIn(max = 720.dp)
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ganti Nama Massal",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${state.daftarEntitasUrut.size} item dipilih" +
                                    if (state.sedangMemuatIsiFolder) " · memeriksa folder..." else "",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.prosesIntent(IntentBulkRename.ResetPolaBawaan) },
                            enabled = !state.sedangMengeksekusi
                        ) {
                            Text("Reset")
                        }
                        IconButton(
                            onClick = {
                                val perluSegarkan = state.perluSegarkanSaatTutup
                                viewModel.prosesIntent(IntentBulkRename.Tutup)
                                if (perluSegarkan) onTutupDenganPerubahan()
                            },
                            enabled = !state.sedangMengeksekusi
                        ) {
                            Icon(
                                imageVector = IkonAplikasi.Tutup,
                                contentDescription = "Tutup dialog ganti nama massal",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.pola,
                            onValueChange = { viewModel.prosesIntent(IntentBulkRename.UbahPola(it)) },
                            label = { Text("Pola Nama") },
                            singleLine = true,
                            enabled = !state.sedangMengeksekusi,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                            isError = state.pratinjau.galatPola != null,
                            supportingText = {
                                val galat = state.pratinjau.galatPola
                                if (galat != null) {
                                    Text(galat, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(
                                        "Token: {P} nama, {S} ekstensi, {N} nomor, {zN}/{zN:W} nol depan",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DAFTAR_TOKEN_CEPAT.forEach { itemToken ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.prosesIntent(IntentBulkRename.SisipToken(itemToken.token)) },
                                    enabled = !state.sedangMengeksekusi,
                                    label = {
                                        Text(
                                            text = "${itemToken.token} ${itemToken.keterangan}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono)
                                        )
                                    }
                                )
                            }
                            FilterChip(
                                selected = state.izinkanAngkaAwalInline,
                                onClick = { viewModel.prosesIntent(IntentBulkRename.ToggleAngkaAwalInline) },
                                enabled = !state.sedangMengeksekusi,
                                label = {
                                    Text(
                                        text = "Inline {z8}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono)
                                    )
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.angkaAwalInput,
                                onValueChange = { viewModel.prosesIntent(IntentBulkRename.UbahAngkaAwal(it)) },
                                label = { Text("Angka Awal") },
                                singleLine = true,
                                enabled = !state.sedangMengeksekusi,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                                isError = state.galatAngkaNomor != null,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.langkahInput,
                                onValueChange = { viewModel.prosesIntent(IntentBulkRename.UbahLangkah(it)) },
                                label = { Text("Langkah") },
                                singleLine = true,
                                enabled = !state.sedangMengeksekusi,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                                isError = state.galatAngkaNomor != null,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        state.galatAngkaNomor?.let { galatAngka ->
                            Text(
                                text = galatAngka,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        state.pesanGalatEksekusi?.let { galatEksekusi ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = galatEksekusi,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val p = state.pratinjau
                            val jumlahTabrakan = p.jumlahNamaTidakSah + p.jumlahTabrakanAntarItem + p.jumlahTabrakanDenganDisk
                            Text(
                                text = buildString {
                                    append("Siap: ${p.jumlahSiapUbah}")
                                    append(" · Tetap: ${p.jumlahTidakBerubah}")
                                    if (p.jumlahTidakDapatDiubah > 0) append(" · Tak bisa: ${p.jumlahTidakDapatDiubah}")
                                    if (jumlahTabrakan > 0) append(" · Konflik: $jumlahTabrakan")
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                                color = if (jumlahTabrakan > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Box {
                                TextButton(
                                    onClick = { menuUrutanTerbuka = true },
                                    enabled = !state.sedangMengeksekusi && state.daftarEntitasUrut.size > 1
                                ) {
                                    Icon(
                                        imageVector = IkonAplikasi.Urutkan,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Urutkan")
                                }
                                DropdownMenu(
                                    expanded = menuUrutanTerbuka,
                                    onDismissRequest = { menuUrutanTerbuka = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    UrutanItemRename.entries.forEach { opsiUrutan ->
                                        DropdownMenuItem(
                                            text = { Text(opsiUrutan.label) },
                                            onClick = {
                                                menuUrutanTerbuka = false
                                                viewModel.prosesIntent(IntentBulkRename.TerapkanUrutan(opsiUrutan))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 300.dp)
                    ) {
                        itemsIndexed(
                            items = state.pratinjau.daftarItem,
                            key = { _, item -> "${item.entitas.lokasi.jenis.name}:${item.entitas.lokasi.pengenal}" }
                        ) { indeks, item ->
                            BarisPratinjauRename(
                                item = item,
                                dapatNaik = indeks > 0 && !state.sedangMengeksekusi,
                                dapatTurun = indeks < state.pratinjau.daftarItem.lastIndex && !state.sedangMengeksekusi,
                                onNaik = { viewModel.prosesIntent(IntentBulkRename.GeserItemNaik(indeks)) },
                                onTurun = { viewModel.prosesIntent(IntentBulkRename.GeserItemTurun(indeks)) }
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val perluSegarkan = state.perluSegarkanSaatTutup
                                viewModel.prosesIntent(IntentBulkRename.Tutup)
                                if (perluSegarkan) onTutupDenganPerubahan()
                            },
                            enabled = !state.sedangMengeksekusi
                        ) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.prosesIntent(IntentBulkRename.MintaKonfirmasiEksekusi) },
                            enabled = state.bolehLanjutKonfirmasi
                        ) {
                            Text("Terapkan (${state.pratinjau.jumlahSiapUbah})")
                        }
                    }
                }
            }
        }
    }

    state.ringkasanKonfirmasi?.let { ringkasan ->
        DialogKonfirmasiRingkasanRename(
            ringkasan = ringkasan,
            onKonfirmasi = { viewModel.prosesIntent(IntentBulkRename.KonfirmasiDanJalankan) },
            onBatal = { viewModel.prosesIntent(IntentBulkRename.BatalKonfirmasiEksekusi) }
        )
    }

    if (state.sedangMengeksekusi) {
        DialogProgresRenameMassal(
            diproses = state.progresDiproses,
            total = state.progresTotal,
            namaSaatIni = state.namaDiprosesSaatIni,
            onBatal = { viewModel.prosesIntent(IntentBulkRename.BatalkanEksekusi) }
        )
    }

    state.hasilEksekusi?.let { hasil ->
        DialogLaporanRenameMassal(
            hasil = hasil,
            onTutup = {
                val perluSegarkan = state.perluSegarkanSaatTutup || hasil.berhasil.isNotEmpty()
                viewModel.prosesIntent(IntentBulkRename.TutupLaporanHasil)
                if (perluSegarkan) onTutupDenganPerubahan()
            }
        )
    }
}

@Composable
private fun BarisPratinjauRename(
    item: ItemPratinjauRename,
    dapatNaik: Boolean,
    dapatTurun: Boolean,
    onNaik: () -> Unit,
    onTurun: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val (simbolStatus, teksStatus, warnaStatus) = when (item.status) {
        StatusItemPratinjauRename.SIAP ->
            Triple("✓", "Siap", MaterialTheme.colorScheme.primary)
        StatusItemPratinjauRename.TIDAK_BERUBAH ->
            Triple("•", "Tidak berubah", MaterialTheme.colorScheme.onSurfaceVariant)
        StatusItemPratinjauRename.TIDAK_DAPAT_DIUBAH ->
            Triple("⊘", item.alasan ?: "Tidak dapat diubah", MaterialTheme.colorScheme.onSurfaceVariant)
        StatusItemPratinjauRename.NAMA_TIDAK_SAH ->
            Triple("✕", item.alasan ?: "Nama tidak sah", MaterialTheme.colorScheme.error)
        StatusItemPratinjauRename.TABRAKAN_ANTAR_ITEM,
        StatusItemPratinjauRename.TABRAKAN_DENGAN_DISK ->
            Triple("⚠", item.alasan ?: "Tabrakan nama", MaterialTheme.colorScheme.error)
    }

    val latarBaris = if (item.adalahGalatPenghalang) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(latarBaris)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.indeks + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.namaLama,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = fontMono,
                        fontSize = 12.sp,
                        textDecoration = if (item.dapatDiubah) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "→ ${item.namaBaru}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = fontMono,
                        fontSize = 13.sp,
                        fontWeight = if (item.dapatDiubah) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = when {
                        item.adalahGalatPenghalang -> MaterialTheme.colorScheme.error
                        item.dapatDiubah -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$simbolStatus $teksStatus",
                    style = MaterialTheme.typography.labelSmall,
                    color = warnaStatus,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNaik,
                    enabled = dapatNaik,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = IkonAplikasi.Naik,
                        contentDescription = "Geser ${item.namaLama} naik",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onTurun,
                    enabled = dapatTurun,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = IkonAplikasi.Turun,
                        contentDescription = "Geser ${item.namaLama} turun",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun DialogKonfirmasiRingkasanRename(
    ringkasan: RingkasanKonfirmasiRename,
    onKonfirmasi: () -> Unit,
    onBatal: () -> Unit
) {
    val fontMono = LocalFontMono.current
    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Konfirmasi Ganti Nama Massal") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${ringkasan.jumlahAkanDiubah} item akan diubah namanya.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (ringkasan.jumlahTidakBerubah > 0) {
                    Text(
                        text = "• ${ringkasan.jumlahTidakBerubah} item tidak berubah (dilewati).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ringkasan.jumlahTidakDapatDiubah > 0) {
                    Text(
                        text = "• ${ringkasan.jumlahTidakDapatDiubah} item tidak dapat diubah (dilewati).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    ringkasan.daftarContohPerubahan.forEach { (lama, baru) ->
                        Text(
                            text = "$lama → $baru",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val sisa = ringkasan.jumlahAkanDiubah - ringkasan.daftarContohPerubahan.size
                    if (sisa > 0) {
                        Text(
                            text = "...dan $sisa item lainnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Berkas lain tidak akan ditimpa. Pertukaran nama siklus memakai nama sementara aman.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onKonfirmasi) {
                Text("Konfirmasi")
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
private fun DialogProgresRenameMassal(
    diproses: Int,
    total: Int,
    namaSaatIni: String,
    onBatal: () -> Unit
) {
    val progres = if (total > 0) (diproses.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Mengganti Nama Berkas...") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { progres },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "$diproses dari $total",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (namaSaatIni.isNotBlank()) {
                    Text(
                        text = namaSaatIni,
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
            TextButton(onClick = onBatal) {
                Text("Batalkan")
            }
        }
    )
}

@Composable
private fun DialogLaporanRenameMassal(
    hasil: HasilEksekusiRename,
    onTutup: () -> Unit
) {
    val fontMono = LocalFontMono.current
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Laporan Ganti Nama Massal") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Berhasil: ${hasil.berhasil.size} | Gagal: ${hasil.gagal.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (hasil.dilewatiTidakBerubah > 0 || hasil.dilewatiTidakDapatDiubah > 0) {
                    Text(
                        text = "Dilewati: ${hasil.dilewatiTidakBerubah} tetap, ${hasil.dilewatiTidakDapatDiubah} tidak dapat diubah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                hasil.gagal.forEach { item ->
                    Text(
                        text = "• ${item.sumber.nama} → ${item.namaTujuan}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = fontMono,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = item.pesan,
                        style = MaterialTheme.typography.bodySmall,
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