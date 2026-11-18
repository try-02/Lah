package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.domain.usecase.ArahUrutan
import org.fm.com.domain.usecase.KriteriaUrutan

@Composable
fun DialogUrutkanBerkas(
    terbuka: Boolean,
    kriteriaSaatIni: KriteriaUrutan,
    arahSaatIni: ArahUrutan,
    folderDiAtas: Boolean,
    tampilkanTersembunyi: Boolean,
    onPilihKriteria: (KriteriaUrutan) -> Unit,
    onPilihArah: (ArahUrutan) -> Unit,
    onToggleFolderDiAtas: () -> Unit,
    onToggleTersembunyi: () -> Unit,
    onTutup: () -> Unit
) {
    if (!terbuka) return

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Urutkan & Saring") },
        text = {
            Column {
                Text("Berdasarkan:", style = MaterialTheme.typography.labelLarge)
                KriteriaUrutan.entries.forEach { kriteria ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = kriteriaSaatIni == kriteria,
                            onClick = { onPilihKriteria(kriteria) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(kriteria.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Arah:", style = MaterialTheme.typography.labelLarge)
                ArahUrutan.entries.forEach { arah ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = arahSaatIni == arah,
                            onClick = { onPilihArah(arah) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (arah == ArahUrutan.NAIK) "Naik (A-Z, Kecil-Besar)" else "Turun (Z-A, Besar-Kecil)")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = folderDiAtas, onCheckedChange = { onToggleFolderDiAtas() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Folder selalu di atas")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tampilkanTersembunyi, onCheckedChange = { onToggleTersembunyi() })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tampilkan berkas tersembunyi")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTutup) {
                Text("Selesai")
            }
        }
    )
}