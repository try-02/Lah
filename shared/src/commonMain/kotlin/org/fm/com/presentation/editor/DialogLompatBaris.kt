package org.fm.com.presentation.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun DialogLompatBaris(
    terbuka: Boolean,
    jumlahBaris: Int,
    barisSaatIni: Int,
    onLompat: (Int) -> Unit,
    onTutup: () -> Unit
) {
    if (!terbuka) return
    var inputBaris by remember(jumlahBaris, barisSaatIni) {
        mutableStateOf(barisSaatIni.toString())
    }
    val angka = inputBaris.trim().toIntOrNull()
    val valid = angka != null && angka in 1..jumlahBaris.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Lompat ke Nomor Baris") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inputBaris,
                    onValueChange = { inputBaris = it },
                    label = { Text("Nomor baris (1 - ${jumlahBaris.coerceAtLeast(1)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputBaris.isNotEmpty() && !valid,
                    supportingText = {
                        if (inputBaris.isNotEmpty() && !valid) {
                            Text(
                                text = "Masukkan angka antara 1 dan ${jumlahBaris.coerceAtLeast(1)}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (angka != null && valid) onLompat(angka) },
                enabled = valid
            ) {
                Text("Lompat")
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}