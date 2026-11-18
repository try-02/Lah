package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.ValidasiNama

@Composable
fun DialogBuatBerkas(
    terbuka: Boolean,
    onBuatFolder: (String) -> Unit,
    onBuatBerkas: (String) -> Unit,
    onTutup: () -> Unit
) {
    if (!terbuka) return

    var nama by remember { mutableStateOf("") }
    var adalahFolder by remember { mutableStateOf(true) }
    val namaSah = ValidasiNama.sah(nama.trim())

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Buat Baru") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = adalahFolder, onClick = { adalahFolder = true })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = !adalahFolder, onClick = { adalahFolder = false })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Berkas Kosong")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text(if (adalahFolder) "Nama Folder" else "Nama Berkas") },
                    singleLine = true,
                    isError = nama.isNotEmpty() && !namaSah,
                    supportingText = {
                        if (nama.isNotEmpty() && !namaSah) {
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
                    if (namaSah) {
                        if (adalahFolder) onBuatFolder(nama.trim()) else onBuatBerkas(nama.trim())
                    }
                },
                enabled = namaSah
            ) {
                Text("Buat")
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}