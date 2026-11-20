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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.usecase.EncodingTeks
import org.fm.com.domain.usecase.JenisAkhirBaris
import org.fm.com.presentation.tema.LocalFontMono

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
    val fontMono = LocalFontMono.current
    val gayaMono11 = MaterialTheme.typography.labelSmall.copy(
        fontFamily = fontMono,
        fontSize = 11.sp
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "L$baris:C$kolom/$jumlahBaris",
                style = gayaMono11.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = siap, onClick = onKlikPosisiBaris)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )

            Text(
                text = if (adaPerubahanBelumTersimpan) "● diubah" else "tersimpan",
                style = gayaMono11,
                color = if (adaPerubahanBelumTersimpan) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Text(
                text = when {
                    hanyaBacaPermanen -> "RO"
                    modeHanyaBaca -> "BACA"
                    else -> "EDIT"
                },
                style = gayaMono11.copy(fontWeight = FontWeight.Medium),
                color = if (modeHanyaBaca) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (modeHanyaBaca) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable(onClick = onToggleHanyaBaca)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )

            Text(
                text = if (wordWrapAktif) "WRAP" else "NOWRAP",
                style = gayaMono11,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onToggleWordWrap)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )

            Text(
                text = "${encoding.name.replace('_', '-')} · ${akhirBaris.name}",
                style = gayaMono11,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}