package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DialogPropertiBerkas(
    item: ItemBerkasUi?,
    onSalinNama: (String) -> Unit,
    onSalinPath: (String) -> Unit,
    onTutup: () -> Unit
) {
    if (item == null) return

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Properti Berkas") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BarisProperti("Nama", item.nama)
                BarisProperti("Jenis", item.jenis.name)
                BarisProperti("Ukuran", item.ukuranFormat)
                BarisProperti("Tanggal Modifikasi", item.tanggalFormat)
                BarisProperti("Akses", buildString {
                    append(if (item.dapatDibaca) "Baca " else "")
                    append(if (item.dapatDitulis) "Tulis" else "Hanya-Baca")
                })
                BarisProperti("Pengenal Lokasi", item.lokasi.pengenal)

                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { onSalinNama(item.nama) }) {
                        Text("Salin Nama")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onSalinPath(item.lokasi.pengenal) }) {
                        Text("Salin Lokasi")
                    }
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

@Composable
private fun BarisProperti(label: String, nilai: String) {
    Column(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(nilai, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}