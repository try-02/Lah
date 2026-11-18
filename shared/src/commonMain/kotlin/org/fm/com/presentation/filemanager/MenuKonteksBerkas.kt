package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarAksiSeleksi(
    jumlahTerpilih: Int,
    onPilihSemua: () -> Unit,
    onBersihkanPilihan: () -> Unit,
    onAksiHapus: () -> Unit,
    onAksiSalin: () -> Unit,
    onAksiPindah: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "$jumlahTerpilih dipilih",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            TextButton(onClick = onBersihkanPilihan) {
                Text("Batal")
            }
        },
        actions = {
            TextButton(onClick = onPilihSemua) {
                Text("Semua")
            }
            IconButton(onClick = onAksiSalin) {
                Text("Salin")
            }
            IconButton(onClick = onAksiPindah) {
                Text("Pindah")
            }
            IconButton(onClick = onAksiHapus) {
                Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}