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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.model.OperasiBerkas
import org.fm.com.presentation.komponen.PemformatLokasiRamah
import org.fm.com.presentation.tema.LocalFontMono

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
    val lokasiRamah = PemformatLokasiRamah.formatRingkas(item.lokasi)
    val labelLokasi = if (dukungPosix) "Path" else "Lokasi (${item.lokasi.jenis.name})"
    val tombolLokasi = if (dukungPosix) "Salin Path" else "Salin Pengenal"
    val alasanPosix = kemampuan.alasan(OperasiBerkas.MELIHAT_PATH_POSIX)

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Properti") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BarisProperti("Nama", item.nama, mono = true)
                BarisProperti("Jenis", item.jenis.name, mono = false)
                BarisProperti("Ukuran", item.ukuranFormat, mono = true)
                BarisProperti("Modifikasi", item.tanggalFormat, mono = true)
                BarisProperti(
                    "Akses",
                    buildString {
                        append(if (item.dapatDibaca) "Baca " else "")
                        append(if (item.dapatDitulis) "Tulis" else "Hanya-Baca")
                    },
                    mono = false
                )
                BarisProperti(labelLokasi, lokasiRamah, mono = true)
                if (!dukungPosix && alasanPosix != null) {
                    Text(
                        text = alasanPosix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
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
private fun BarisProperti(label: String, nilai: String, mono: Boolean) {
    val fontMono = LocalFontMono.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = nilai,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            } else {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}