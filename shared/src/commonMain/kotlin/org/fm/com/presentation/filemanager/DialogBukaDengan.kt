package org.fm.com.presentation.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DialogBukaDengan(
    item: ItemBerkasUi?,
    onBukaEditorInternal: (ItemBerkasUi) -> Unit,
    onBukaAplikasiEksternal: (ItemBerkasUi) -> Unit,
    onTutup: () -> Unit
) {
    if (item == null) return

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Buka ${item.nama}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBukaEditorInternal(item); onTutup() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KomponenIkonBerkas(jenis = item.jenis, ukuran = 32.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Editor Teks & Kode Internal", style = MaterialTheme.typography.bodyLarge)
                        Text("Buka dan edit langsung di dalam aplikasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBukaAplikasiEksternal(item); onTutup() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KomponenIkonBerkas(jenis = org.fm.com.domain.model.JenisBerkas.LAIN, ukuran = 32.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Aplikasi Sistem Lainnya", style = MaterialTheme.typography.bodyLarge)
                        Text("Gunakan penampil bawaan perangkat (Open with)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}