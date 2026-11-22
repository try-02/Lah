@file:OptIn(ExperimentalLayoutApi::class)

package org.fm.com.presentation.arsip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.arsip.MatriksArsip
import org.fm.com.domain.model.EntitasBerkas
import org.fm.com.domain.model.FormatArsip
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun DialogKompres(
    viewModel: ViewModelArsip,
    onSelesaiKompres: (EntitasBerkas) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val km = state.kompres
    val fontMono = LocalFontMono.current

    LaunchedEffect(state.acaraSuksesKompres?.id) {
        val acara = state.acaraSuksesKompres ?: return@LaunchedEffect
        onSelesaiKompres(acara.entitasHasil)
        viewModel.prosesIntent(IntentArsip.KonsumsiAcaraKompres)
    }

    if (km.terbuka && !km.sedangMengompres) {
        val daftarFormat = MatriksArsip.formatYangBisaDibuat()
        AlertDialog(
            modifier = modifier,
            onDismissRequest = { viewModel.prosesIntent(IntentArsip.TutupDialogKompres) },
            title = { Text("Kompres ke Arsip") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${km.daftarSumber.size} item dipilih untuk dikompres",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Format Arsip", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        daftarFormat.forEach { format ->
                            val enabled = format != FormatArsip.GZIP || km.dapatPilihGzip
                            FilterChip(
                                selected = km.formatTerpilih == format,
                                enabled = enabled,
                                onClick = { viewModel.prosesIntent(IntentArsip.UbahFormatKompres(format)) },
                                label = {
                                    Text(
                                        text = format.namaTampilan,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono)
                                    )
                                }
                            )
                        }
                    }

                    if (!km.dapatPilihGzip) {
                        Text(
                            text = "GZIP hanya untuk satu berkas tunggal (bukan folder/banyak item).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = km.namaArsipInput,
                        onValueChange = { viewModel.prosesIntent(IntentArsip.UbahNamaArsipKompres(it)) },
                        label = { Text("Nama Berkas Arsip") },
                        singleLine = true,
                        isError = km.namaArsipInput.isNotEmpty() && !km.namaValid,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontMono),
                        supportingText = {
                            when {
                                km.namaArsipInput.isNotEmpty() && !km.namaValid -> {
                                    Text(
                                        "Nama tidak boleh mengandung /, \\, .., atau kosong",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                km.pesanGalat != null -> {
                                    Text(
                                        km.pesanGalat,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.prosesIntent(IntentArsip.MulaiKompres) },
                    enabled = km.namaValid
                ) {
                    Text("Buat Arsip")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentArsip.TutupDialogKompres) }) {
                    Text("Batal")
                }
            }
        )
    }

    km.konfirmasiTimpa?.let { konflik ->
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentArsip.BatalTimpaKompres) },
            title = { Text("Berkas Arsip Sudah Ada") },
            text = {
                Text("${konflik.pesan}. Timpa berkas '${konflik.namaArsip}' yang sudah ada?")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentArsip.KonfirmasiTimpaKompres) }) {
                    Text("Timpa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentArsip.BatalTimpaKompres) }) {
                    Text("Batal")
                }
            }
        )
    }

    if (km.sedangMengompres) {
        val progres = if (km.progresTotal > 0) {
            (km.progresDiproses.toFloat() / km.progresTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Membuat Arsip ${km.formatTerpilih.namaTampilan}...") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progres },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${km.progresDiproses} dari ${km.progresTotal}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (km.namaItemSaatIni.isNotBlank()) {
                        Text(
                            text = km.namaItemSaatIni,
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
                TextButton(onClick = { viewModel.prosesIntent(IntentArsip.BatalkanKompres) }) {
                    Text("Batalkan")
                }
            }
        )
    }
}