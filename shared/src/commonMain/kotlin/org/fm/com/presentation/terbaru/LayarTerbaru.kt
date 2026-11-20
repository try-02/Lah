package org.fm.com.presentation.terbaru

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fm.com.domain.model.LokasiBerkas
import org.fm.com.presentation.filemanager.KomponenIkonBerkas
import org.fm.com.presentation.filemanager.LayarStatusFilemanager
import org.fm.com.presentation.komponen.IkonAplikasi
import org.fm.com.presentation.tema.LocalFontMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayarTerbaru(
    viewModel: ViewModelTerbaru,
    onBukaLokasi: (LokasiBerkas) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontMono = LocalFontMono.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lokasi Terbaru") },
                actions = {
                    if (state.jumlahItem > 0) {
                        TextButton(
                            onClick = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(true)) }
                        ) {
                            Text("Bersihkan")
                        }
                    }
                }
            )
        }
    ) { bantalan ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bantalan)
        ) {
            when {
                state.sedangMemuat || state.pesanGalat != null -> {
                    LayarStatusFilemanager(
                        sedangMemuat = state.sedangMemuat,
                        pesanGalat = state.pesanGalat,
                        jumlahItem = state.jumlahItem,
                        onMuatUlang = { viewModel.prosesIntent(IntentTerbaru.Segarkan) }
                    )
                }
                state.daftarItem.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "riwayat: kosong  // geser kiri untuk hapus item nanti",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = fontMono, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.daftarItem,
                            key = { "${it.lokasi.jenis.name}:${it.lokasi.pengenal}" }
                        ) { item ->
                            ItemRiwayatSwipeable(
                                item = item,
                                onKlik = { onBukaLokasi(item.lokasi) },
                                onHapus = { viewModel.prosesIntent(IntentTerbaru.HapusItem(item.lokasi)) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.dialogHapusSemuaTerbuka) {
        AlertDialog(
            onDismissRequest = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(false)) },
            title = { Text("Bersihkan Riwayat") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh daftar riwayat lokasi terbaru?") },
            confirmButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentTerbaru.KonfirmasiHapusSemua) }) {
                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.prosesIntent(IntentTerbaru.SetBukaDialogHapusSemua(false)) }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun ItemRiwayatSwipeable(
    item: ItemRiwayatUi,
    onKlik: () -> Unit,
    onHapus: () -> Unit
) {
    val fontMono = LocalFontMono.current
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { arah ->
            if (arah == SwipeToDismissBoxValue.EndToStart) {
                onHapus()
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = IkonAplikasi.Hapus,
                    contentDescription = "Hapus riwayat",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(onClick = onKlik)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KomponenIkonBerkas(jenis = item.jenis, ukuran = 20.dp)
                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.nama,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = fontMono,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.waktuFormat,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = fontMono,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.pathFormat,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = fontMono,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 50.dp)
            )
        }
    }
}