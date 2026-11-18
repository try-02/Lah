package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun IndikatorProgresMassal(
    state: StateOperasiMassal,
    onBatal: () -> Unit
) {
    if (!state.sedangBerjalan) return

    val judul = if (state.apakahPindah) "Memindahkan Berkas..." else "Menyalin Berkas..."

    AlertDialog(
        onDismissRequest = {},
        title = { Text(judul) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { state.progresFloat },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${state.itemDiproses} dari ${state.totalItem}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.namaItemSaatIni.isNotBlank()) {
                    Text(
                        text = state.namaItemSaatIni,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onBatal) {
                Text("Batalkan")
            }
        }
    )
}