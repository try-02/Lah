package org.fm.com.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatusEditor(
    tab: ModelTabEditor,
    wordWrapUmum: Boolean,
    onKlikPosisiBaris: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onToggleHanyaBaca: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wrapAktif = tab.wordWrapEfektif(wordWrapUmum)
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brs ${tab.posisiKursor.baris}/${tab.jumlahBaris}, Kol ${tab.posisiKursor.kolom}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = tab.statusMuat == StatusMuatTab.SIAP, onClick = onKlikPosisiBaris)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Text(
                text = if (tab.adaPerubahanBelumTersimpan) "● Belum Tersimpan" else "Tersimpan",
                style = MaterialTheme.typography.labelMedium,
                color = if (tab.adaPerubahanBelumTersimpan) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = when {
                    tab.hanyaBacaPermanen -> "Hanya Baca (Terkunci)"
                    tab.modeHanyaBaca -> "Hanya Baca"
                    else -> "Edit"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (tab.modeHanyaBaca) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .background(
                        color = if (tab.modeHanyaBaca) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .clickable(onClick = onToggleHanyaBaca)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Text(
                text = if (wrapAktif) "Wrap: Aktif" else "Wrap: Mati",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onToggleWordWrap)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Text(
                text = "${tab.encoding.name} • ${tab.akhirBaris.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}