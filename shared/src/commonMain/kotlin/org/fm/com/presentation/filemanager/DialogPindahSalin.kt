package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.domain.repository.HasilMassal

@Composable
fun DialogPindahSalin(
    terbuka: Boolean,
    apakahPindah: Boolean,
    jumlahItem: Int,
    lokasiTujuan: LokasiBerkas?,
    onKonfirmasi: () -> Unit,
    onTutup: () -> Unit
) {
    if (!terbuka || lokasiTujuan == null) return
    val aksi = if (apakahPindah) "Pindahkan" else "Salin"
    val namaTujuan = lokasiTujuan.label ?: lokasiTujuan.pengenal.substringAfterLast('/')
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("$aksi $jumlahItem Item") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$aksi $jumlahItem item ke folder tujuan berikut?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = namaTujuan,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = lokasiTujuan.pengenal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onKonfirmasi) {
                Text(aksi)
            }
        },
        dismissButton = {
            TextButton(onClick = onTutup) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DialogHasilOperasiMassal(
    terbuka: Boolean,
    hasil: HasilMassal?,
    onTutup: () -> Unit
) {
    if (!terbuka || hasil == null) return
    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Laporan Operasi Massal") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Berhasil: ${hasil.berhasil.size} | Gagal: ${hasil.gagal.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                hasil.gagal.forEach { itemGagal ->
                    val nama = itemGagal.sumber.label ?: itemGagal.sumber.pengenal.substringAfterLast('/')
                    Text(
                        text = "• $nama",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = itemGagal.pesan,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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