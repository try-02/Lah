package org.fm.com.presentation.komponen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DialogIzinMemoriSistem(
    terbuka: Boolean,
    onBukaPengaturanIzin: () -> Unit,
    onGunakanSafSaja: () -> Unit,
    onNanti: () -> Unit
) {
    if (!terbuka) return

    AlertDialog(
        onDismissRequest = onNanti,
        title = { Text("Akses Memori Internal") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Aktifkan izin \"Akses Semua Berkas\" agar aplikasi dapat membuka /storage/emulated/0 secara langsung tanpa harus memilih folder satu per satu lewat pemilih sistem (SAF).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Catatan sistem Android: folder privat aplikasi lain (Android/data, Android/obb, dan ruang Termux) tetap mengikuti batasan sistem dan diakses melalui SAF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onBukaPengaturanIzin) {
                Text("Buka Pengaturan")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onGunakanSafSaja) {
                    Text("Pakai SAF Saja")
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onNanti) {
                    Text("Nanti")
                }
            }
        }
    )
}