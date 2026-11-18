package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.fm.com.domain.model.ValidasiNama

@Composable
fun DialogRenameBerkas(
    item: ItemBerkasUi?,
    onGantiNama: (namaBaru: String) -> Unit,
    onTutup: () -> Unit
) {
    if (item == null) return

    var namaBaru by remember(item) { mutableStateOf(item.nama) }
    val namaSah = ValidasiNama.sah(namaBaru.trim())
    val apakahBerubah = namaBaru.trim() != item.nama

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Ganti Nama") },
        text = {
            Column {
                OutlinedTextField(
                    value = namaBaru,
                    onValueChange = { namaBaru = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    isError = namaBaru.isNotEmpty() && !namaSah,
                    supportingText = {
                        if (namaBaru.isNotEmpty() && !namaSah) {
                            Text("Nama tidak boleh mengandung /, \\, .., atau blank", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (namaSah && apakahBerubah) {
                        onGantiNama(namaBaru.trim())
                    }
                },
                enabled = namaSah && apakahBerubah
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}