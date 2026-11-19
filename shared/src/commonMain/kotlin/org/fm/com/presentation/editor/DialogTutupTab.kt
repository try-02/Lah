package org.fm.com.presentation.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.ValidasiNama

@Composable
fun DialogTutupTab(
    tab: ModelTabEditor?,
    sedangMenyimpan: Boolean,
    onSimpanDanTutup: () -> Unit,
    onBuangDanTutup: () -> Unit,
    onBatal: () -> Unit
) {
    if (tab == null) return

    val dapatSimpan = !tab.hanyaBacaPermanen && !sedangMenyimpan

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Perubahan Belum Tersimpan") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Berkas '${tab.namaBerkas}' memiliki perubahan yang belum disimpan. Simpan perubahan sebelum menutup tab?"
                )
                if (tab.hanyaBacaPermanen) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lokasi asli tidak mendukung tulis; pilih Buang untuk menutup atau Batal untuk menyimpan salinan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSimpanDanTutup,
                enabled = dapatSimpan
            ) {
                Text(if (sedangMenyimpan) "Menyimpan..." else "Simpan")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onBatal,
                    enabled = !sedangMenyimpan
                ) {
                    Text("Batal")
                }
                TextButton(
                    onClick = onBuangDanTutup,
                    enabled = !sedangMenyimpan
                ) {
                    Text("Buang", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun DialogKonflikSimpanEksternal(
    permintaan: PermintaanKonfirmasiKonflikSimpan?,
    sedangMenyimpan: Boolean,
    onTimpa: () -> Unit,
    onBatal: () -> Unit
) {
    if (permintaan == null) return

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Konflik Simpan Berkas") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(permintaan.pesan)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Berkas: ${permintaan.namaBerkas}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTimpa, enabled = !sedangMenyimpan) {
                Text(
                    if (permintaan.berkasHilangDiDisk) "Buat Ulang" else "Timpa",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBatal, enabled = !sedangMenyimpan) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DialogSimpanSebagai(
    permintaan: PermintaanSimpanSebagai?,
    sedangMenyimpan: Boolean,
    onSimpan: (namaBerkas: String, paksaTimpa: Boolean) -> Unit,
    onTutup: () -> Unit
) {
    if (permintaan == null) return

    var namaInput by remember(permintaan.idTab, permintaan.namaAwal) {
        mutableStateOf(permintaan.namaAwal)
    }
    val namaBersih = namaInput.trim()
    val namaSah = ValidasiNama.sah(namaBersih)
    val pesanKonflik = permintaan.pesanKonflikTujuan
    val namaKonflikSama = pesanKonflik != null && namaBersih == permintaan.namaKonflik

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Simpan Berkas Sebagai") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Folder tujuan: ${permintaan.folderTujuan.label ?: permintaan.folderTujuan.pengenal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = namaInput,
                    onValueChange = { namaInput = it },
                    label = { Text("Nama Berkas") },
                    singleLine = true,
                    isError = (namaInput.isNotEmpty() && !namaSah) || namaKonflikSama,
                    supportingText = {
                        when {
                            namaInput.isNotEmpty() && !namaSah -> {
                                Text(
                                    "Nama tidak boleh mengandung /, \\, .., atau kosong",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            namaKonflikSama && pesanKonflik != null -> {
                                Text(
                                    pesanKonflik,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(namaBersih, namaKonflikSama) },
                enabled = namaSah && !sedangMenyimpan
            ) {
                Text(
                    text = when {
                        sedangMenyimpan -> "Menyimpan..."
                        namaKonflikSama -> "Timpa"
                        else -> "Simpan"
                    },
                    color = if (namaKonflikSama) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup, enabled = !sedangMenyimpan) {
                Text("Batal")
            }
        }
    )
}