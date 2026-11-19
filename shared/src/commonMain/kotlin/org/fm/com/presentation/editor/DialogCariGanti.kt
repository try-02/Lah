package org.fm.com.presentation.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fm.com.domain.usecase.RentangKecocokan

@Composable
fun DialogCariGanti(
    state: StateCariGanti,
    teksDokumen: String,
    modeHanyaBaca: Boolean,
    riwayatPencarian: List<ItemRiwayatPencarianUi>,
    onUbahKueriCari: (String) -> Unit,
    onUbahTeksPengganti: (String) -> Unit,
    onTogglePekaHuruf: () -> Unit,
    onToggleKataUtuh: () -> Unit,
    onToggleRegex: () -> Unit,
    onCariSebelumnya: () -> Unit,
    onCariBerikutnya: () -> Unit,
    onGantiSatu: () -> Unit,
    onMintaGantiSemua: () -> Unit,
    onKonfirmasiGantiSemua: () -> Unit,
    onBatalGantiSemua: () -> Unit,
    onPilihRiwayat: (ItemRiwayatPencarianUi) -> Unit,
    onHapusItemRiwayat: (String) -> Unit,
    onHapusSemuaRiwayat: () -> Unit,
    onTutup: () -> Unit
) {
    if (!state.terbuka) return

    val cuplikanAktif = remember(teksDokumen, state.kecocokanAktif) {
        state.kecocokanAktif?.let { ambilCuplikanBaris(teksDokumen, it) }
    }
    val polaValid = state.galatPola == null
    val adaHasil = state.jumlahKecocokan > 0
    val dapatGanti = !modeHanyaBaca && state.kueriCari.isNotEmpty() && adaHasil && polaValid

    AlertDialog(
        onDismissRequest = onTutup,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cari & Ganti")
                val labelStatus = when {
                    state.kueriCari.isEmpty() -> ""
                    state.sedangMencari -> "Mencari..."
                    !polaValid -> "Pola salah"
                    state.jumlahKecocokan == 0 -> "0 hasil"
                    state.indeksKecocokanAktif >= 0 -> "${state.indeksKecocokanAktif + 1} / ${state.jumlahKecocokan}"
                    else -> "${state.jumlahKecocokan} hasil"
                }
                if (labelStatus.isNotEmpty()) {
                    Text(
                        text = labelStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!polaValid) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.kueriCari,
                    onValueChange = onUbahKueriCari,
                    label = { Text(if (state.gunakanRegex) "Pola Regex" else "Cari teks") },
                    singleLine = true,
                    isError = !polaValid,
                    trailingIcon = {
                        if (state.kueriCari.isNotEmpty()) {
                            TextButton(onClick = { onUbahKueriCari("") }) {
                                Text("×")
                            }
                        }
                    },
                    supportingText = {
                        when {
                            state.galatPola != null -> {
                                Text(
                                    text = state.galatPola,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            cuplikanAktif != null -> {
                                Text(
                                    text = cuplikanAktif,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onCariBerikutnya() }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.teksPengganti,
                    onValueChange = onUbahTeksPengganti,
                    label = { Text("Ganti dengan") },
                    enabled = !modeHanyaBaca,
                    singleLine = true,
                    supportingText = if (modeHanyaBaca) {
                        {
                            Text(
                                text = "Mode hanya baca aktif — operasi ganti dinonaktifkan",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.pekaHuruf,
                        onClick = onTogglePekaHuruf,
                        label = { Text("Peka Huruf (Aa)") }
                    )
                    FilterChip(
                        selected = state.kataUtuh,
                        onClick = onToggleKataUtuh,
                        label = { Text("Kata Utuh") }
                    )
                    FilterChip(
                        selected = state.gunakanRegex,
                        onClick = onToggleRegex,
                        label = { Text("Regex (.*)") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCariSebelumnya,
                        enabled = adaHasil && polaValid,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sebelumnya", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onCariBerikutnya,
                        enabled = adaHasil && polaValid,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Berikutnya", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onGantiSatu,
                        enabled = dapatGanti,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ganti", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onMintaGantiSemua,
                        enabled = dapatGanti,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ganti Semua", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

                BagianRiwayatPencarian(
                    daftarRiwayat = riwayatPencarian,
                    onPilihItem = onPilihRiwayat,
                    onHapusItem = onHapusItemRiwayat,
                    onHapusSemua = onHapusSemuaRiwayat
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTutup) {
                Text("Tutup")
            }
        }
    )

    state.konfirmasiGantiSemuaJumlah?.let { jumlah ->
        AlertDialog(
            onDismissRequest = onBatalGantiSemua,
            title = { Text("Konfirmasi Ganti Semua") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Apakah Anda yakin ingin mengganti $jumlah kecocokan sekaligus?")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Cari: '${state.kueriCari}' → Ganti: '${state.teksPengganti}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onKonfirmasiGantiSemua) {
                    Text("Ganti ($jumlah)", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = onBatalGantiSemua) {
                    Text("Batal")
                }
            }
        )
    }
}

internal fun ambilCuplikanBaris(teks: String, rentang: RentangKecocokan): String {
    if (teks.isEmpty()) return "Baris 1"
    val awalAman = rentang.awal.coerceIn(0, teks.length)
    var nomorBaris = 1
    for (i in 0 until awalAman) {
        if (teks[i] == '\n') nomorBaris++
    }
    var awalBaris = teks.lastIndexOf('\n', startIndex = (awalAman - 1).coerceAtLeast(0))
    awalBaris = if (awalAman == 0 || awalBaris < 0) 0 else awalBaris + 1
    var akhirBaris = teks.indexOf('\n', startIndex = awalAman)
    if (akhirBaris < 0) akhirBaris = teks.length

    val isiBaris = teks.substring(awalBaris, akhirBaris).trim()
    val ringkas = if (isiBaris.length > 72) "${isiBaris.take(72)}…" else isiBaris
    return "Baris $nomorBaris: $ringkas"
}