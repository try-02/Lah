package org.fm.com.presentation.filemanager

import androidx.compose.material3.ExperimentalMaterial3Api
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
    kemampuan: ModelKemampuanUi = ModelKemampuanUi.DEFAULT,
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
            TextButton(onClick = onAksiSalin, enabled = kemampuan.dapatSalin) {
                Text("Salin")
            }
            TextButton(onClick = onAksiPindah, enabled = kemampuan.dapatPindah) {
                Text("Pindah")
            }
            TextButton(onClick = onAksiHapus, enabled = kemampuan.dapatHapus) {
                Text(
                    "Hapus",
                    color = if (kemampuan.dapatHapus) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}