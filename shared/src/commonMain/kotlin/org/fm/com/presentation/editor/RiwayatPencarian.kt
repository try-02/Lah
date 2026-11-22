package org.fm.com.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.EntitasRiwayatPencarian

@Immutable
data class ItemRiwayatPencarianUi(
    val kueri: String,
    val pekaHuruf: Boolean,
    val kataUtuh: Boolean,
    val gunakanRegex: Boolean
) {
    val labelTampilan: String
        get() = if (kueri.isBlank()) "[spasi: ${kueri.length} karakter]" else kueri

    val ringkasanOpsi: String
        get() = buildList {
            if (pekaHuruf) add("Aa")
            if (kataUtuh) add("Kata")
            if (gunakanRegex) add(".*")
        }.joinToString(" · ")
}

fun EntitasRiwayatPencarian.keUi(): ItemRiwayatPencarianUi = ItemRiwayatPencarianUi(
    kueri = kueri,
    pekaHuruf = pekaHuruf,
    kataUtuh = kataUtuh,
    gunakanRegex = gunakanRegex
)

@Composable
fun BagianRiwayatPencarian(
    daftarRiwayat: List<ItemRiwayatPencarianUi>,
    onPilihItem: (ItemRiwayatPencarianUi) -> Unit,
    onHapusItem: (String) -> Unit,
    onHapusSemua: () -> Unit,
    modifier: Modifier = Modifier
) {
    var konfirmasiHapusSemua by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Pencarian",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (daftarRiwayat.isNotEmpty()) {
                TextButton(onClick = { konfirmasiHapusSemua = true }) {
                    Text(
                        text = "Hapus Semua",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (daftarRiwayat.isEmpty()) {
            Text(
                text = "Belum ada riwayat pencarian tersimpan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                daftarRiwayat.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPilihItem(item) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.labelTampilan,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.ringkasanOpsi.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.ringkasanOpsi,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .clickable { onHapusItem(item.kueri) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (konfirmasiHapusSemua) {
        AlertDialog(
            onDismissRequest = { konfirmasiHapusSemua = false },
            title = { Text("Hapus Riwayat Pencarian") },
            text = { Text("Hapus seluruh riwayat pencarian lokal?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        konfirmasiHapusSemua = false
                        onHapusSemua()
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiHapusSemua = false }) {
                    Text("Batal")
                }
            }
        )
    }
}