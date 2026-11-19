package org.fm.com.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fm.com.domain.usecase.HasilStatistikTeks
import org.fm.com.presentation.filemanager.PemetaanDomainUi

@Composable
fun DialogStatistik(
    state: StateDialogStatistik,
    namaBerkas: String,
    onTutup: () -> Unit
) {
    if (!state.terbuka) return

    AlertDialog(
        onDismissRequest = onTutup,
        title = { Text("Statistik Teks") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = namaBerkas,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    state.sedangMenghitung -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Menghitung statistik di latar belakang...")
                        }
                    }
                    state.hasil is HasilStatistikTeks.DilewatiBiner -> {
                        Text(
                            text = state.hasil.alasan,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.hasil is HasilStatistikTeks.Gagal -> {
                        Text(
                            text = state.hasil.pesan,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.hasil is HasilStatistikTeks.Sukses -> {
                        val s = state.hasil.statistik
                        val ukuranUtf8Format = PemetaanDomainUi.formatUkuran(s.ukuranByteUtf8, adalahFolder = false)
                        BarisStatistik("Ukuran Konten (UTF-8)", "$ukuranUtf8Format (${s.ukuranByteUtf8} byte)")
                        if (s.ukuranByteAsli != null && s.ukuranByteAsli != s.ukuranByteUtf8) {
                            val asliFormat = PemetaanDomainUi.formatUkuran(s.ukuranByteAsli, adalahFolder = false)
                            BarisStatistik("Ukuran Berkas Asli", "$asliFormat (${s.ukuranByteAsli} byte)")
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        BarisStatistik("Jumlah Baris (Total)", s.jumlahBarisTotal.toString())
                        BarisStatistik("Baris Tidak Kosong", s.jumlahBarisTidakKosong.toString())
                        BarisStatistik("Baris Kosong", s.jumlahBarisKosong.toString())
                        BarisStatistik("Jumlah Kata", s.jumlahKata.toString())
                        BarisStatistik("Jumlah Karakter", s.jumlahKarakter.toString())
                        BarisStatistik("Karakter (Tanpa Akhir Baris)", s.jumlahKarakterTanpaAkhirBaris.toString())
                        BarisStatistik("Panjang Baris Terpanjang", "${s.panjangBarisTerpanjang} karakter")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        BarisStatistik("Encoding", s.encoding.name)
                        BarisStatistik("Akhir Baris", s.akhirBaris.name)
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
private fun BarisStatistik(label: String, nilai: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = nilai,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}