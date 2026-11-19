package org.fm.com.presentation.filemanager

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.OperasiBerkas

@Composable
fun DialogPropertiBerkas(
    item: ItemBerkasUi?,
    kemampuan: ModelKemampuanUi = ModelKemampuanUi.DEFAULT,
    onSalinNama: (String) -> Unit,
    onSalinPath: (String) -> Unit,
    onTutup: () -> Unit
) {
    if (item == null) return
    val dukungPosix = kemampuan.dapatLihatPathPosix
    val labelLokasi = if (dukungPosix) "Path POSIX" else "Pengenal Lokasi (URI/Sandbox)"
    val tombolLokasi = if (dukungPosix) "Salin Path" else "Salin Pengenal"
    val alasanPosix = kemampuan.alasan(OperasiBerkas.MELIHAT_PATH_POSIX)

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
                BarisProperti(labelLokasi, item.lokasi.pengenal)
                if (!dukungPosix && alasanPosix != null) {
                    Text(
                        text = alasanPosix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { onSalinNama(item.nama) }) {
                        Text("Salin Nama")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onSalinPath(item.lokasi.pengenal) }) {
                        Text(tombolLokasi)
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(nilai, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}