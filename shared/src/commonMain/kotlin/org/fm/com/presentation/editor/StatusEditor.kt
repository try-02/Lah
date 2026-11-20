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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fm.com.domain.usecase.EncodingTeks
import org.fm.com.domain.usecase.JenisAkhirBaris

@Composable
fun StatusEditor(
    baris: Int,
    kolom: Int,
    jumlahBaris: Int,
    adaPerubahanBelumTersimpan: Boolean,
    hanyaBacaPermanen: Boolean,
    modeHanyaBaca: Boolean,
    wordWrapAktif: Boolean,
    encoding: EncodingTeks,
    akhirBaris: JenisAkhirBaris,
    siap: Boolean,
    onKlikPosisiBaris: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onToggleHanyaBaca: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brs $baris/$jumlahBaris · Kol $kolom",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = siap, onClick = onKlikPosisiBaris)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Text(
                text = if (adaPerubahanBelumTersimpan) "● Diubah" else "Tersimpan",
                style = MaterialTheme.typography.labelSmall,
                color = if (adaPerubahanBelumTersimpan) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )

            Text(
                text = when {
                    hanyaBacaPermanen -> "RO"
                    modeHanyaBaca -> "Baca"
                    else -> "Edit"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (modeHanyaBaca) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (modeHanyaBaca) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable(onClick = onToggleHanyaBaca)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Text(
                text = if (wordWrapAktif) "Wrap" else "NoWrap",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onToggleWordWrap)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Text(
                text = "${encoding.name.replace('_', '-')} · ${akhirBaris.name}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}