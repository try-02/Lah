package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fm.com.presentation.komponen.IkonAplikasi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarAksiSeleksi(
    jumlahTerpilih: Int,
    kemampuan: ModelKemampuanUi = ModelKemampuanUi.DEFAULT,
    onPilihSemua: () -> Unit,
    onBersihkanPilihan: () -> Unit,
    onAksiHapus: () -> Unit,
    onAksiSalin: () -> Unit,
    onAksiPindah: () -> Unit,
    onAksiBandingkan: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                text = "$jumlahTerpilih dipilih",
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBersihkanPilihan) {
                Icon(
                    imageVector = IkonAplikasi.Tutup,
                    contentDescription = "Batalkan pilihan",
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            if (jumlahTerpilih == 2 && onAksiBandingkan != null) {
                IconButton(onClick = onAksiBandingkan) {
                    Icon(
                        imageVector = IkonAplikasi.Bandingkan,
                        contentDescription = "Bandingkan 2 berkas terpilih",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onPilihSemua) {
                Icon(
                    imageVector = IkonAplikasi.PilihSemua,
                    contentDescription = "Pilih semua",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onAksiSalin, enabled = kemampuan.dapatSalin) {
                Icon(
                    imageVector = IkonAplikasi.Salin,
                    contentDescription = "Salin",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onAksiPindah, enabled = kemampuan.dapatPindah) {
                Icon(
                    imageVector = IkonAplikasi.Pindah,
                    contentDescription = "Pindah",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onAksiHapus, enabled = kemampuan.dapatHapus) {
                Icon(
                    imageVector = IkonAplikasi.Hapus,
                    contentDescription = "Hapus",
                    tint = if (kemampuan.dapatHapus) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}