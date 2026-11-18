package org.fm.com.presentation.filemanager

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.fm.com.domain.model.LokasiBerkas

@Composable
fun DialogKonfirmasiHapus(
    daftarHapus: List<LokasiBerkas>?,
    onKonfirmasiHapus: () -> Unit,
    onTutup: () -> Unit
) {
    if (daftarHapus.isNullOrEmpty()) return

    val pesan = if (daftarHapus.size == 1) {
        val nama = daftarHapus.first().label ?: daftarHapus.first().pengenal.substringAfterLast('/')
        "Apakah Anda yakin ingin menghapus '$nama'? Tindakan ini permanen dan tidak dapat dibatalkan."
    } else {
        "Apakah Anda yakin ingin menghapus ${daftarHapus.size} item terpilih? Tindakan ini permanen dan tidak dapat dibatalkan."
    }

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Konfirmasi Hapus") },
        text = { Text(pesan) },
        confirmButton = {
            TextButton(onClick = onKonfirmasiHapus) {
                Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}