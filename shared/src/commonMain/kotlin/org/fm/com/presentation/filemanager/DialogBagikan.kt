package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DialogBagikan(
    item: ItemBerkasUi?,
    onKonfirmasiBagikan: (ItemBerkasUi) -> Unit,
    onTutup: () -> Unit
) {
    if (item == null) return

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Bagikan Berkas") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Bagikan '${item.nama}' ke aplikasi lain menggunakan fitur berbagi sistem operasi?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ukuran: ${item.ukuranFormat} | Tipe: ${item.jenis.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onKonfirmasiBagikan(item); onTutup() }) {
                Text("Bagikan")
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}