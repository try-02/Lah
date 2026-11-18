package org.fm.com.presentation.terbaru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
import org.fm.com.presentation.filemanager.LayarStatusFilemanager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarTerbaru(
    viewModel: ViewModelTerbaru,
    onBukaLokasi: (LokasiBerkas) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lokasi Terbaru") },
                actions = {
                    if (state.jumlahItem > 0) {
                        IconButton(onClick = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(true)) }) {
                            Text("Bersihkan", style = MaterialTheme.typography.labelMedium)
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
            if (state.sedangMemuat || state.pesanGalat != null || state.daftarItem.isEmpty()) {
                LayarStatusFilemanager(
                    sedangMemuat = state.sedangMemuat,
                    pesanGalat = state.pesanGalat,
                    jumlahItem = state.jumlahItem,
                    onMuatUlang = {}
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = state.daftarItem,
                        key = { "${it.lokasi.jenis}_${it.lokasi.pengenal}" }
                    ) { item ->
                        ItemRiwayatBaris(
                            item = item,
                            onKlik = { onBukaLokasi(item.lokasi) },
                            onHapus = { viewModel.prosesIntent(IntentTerbaru.HapusItem(item.lokasi)) }
                        )
                    }
                }
            }
        }
    }

    if (state.dialogHapusSemuaTerbuka) {
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(false)) },
            title = { Text("Bersihkan Riwayat") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh daftar riwayat lokasi terbaru?") },
            confirmButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentTerbaru.KonfirmasiHapusSemua) }) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(false)) }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun ItemRiwayatBaris(
    item: ItemRiwayatUi,
    onKlik: () -> Unit,
    onHapus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onKlik)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KomponenIkonBerkas(jenis = item.jenis, ukuran = 40.dp)
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.nama,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.pathFormat,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Terakhir dibuka: ${item.waktuFormat}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        IconButton(onClick = onHapus) {
            Text("×", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}