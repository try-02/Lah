package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.diff.JenisBarisDiff
import org.fm.com.domain.diff.RentangKarakterBeda
import org.fm.com.presentation.tema.LocalFontMono

@Composable
fun GutterNomorBarisTunggal(
    nomorBaris: Int?,
    jenis: JenisBarisDiff,
    lebarNomor: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    val warnaSimbol = warnaTeksPenanda(jenis)
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} baris ${nomorBaris ?: "-"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = nomorBaris?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = fontMono,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(lebarNomor)
        )
        Text(
            text = jenis.simbol,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = fontMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = warnaSimbol,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(14.dp)
        )
    }
}

@Composable
fun GutterNomorBarisUnified(
    nomorBarisA: Int?,
    nomorBarisB: Int?,
    jenis: JenisBarisDiff,
    modifier: Modifier = Modifier
) {
    val fontMono = LocalFontMono.current
    val warnaSimbol = warnaTeksPenanda(jenis)
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} A:${nomorBarisA ?: "-"} B:${nomorBarisB ?: "-"}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nomorBarisA?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
        Text(
            text = nomorBarisB?.toString() ?: "",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = fontMono, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp).padding(start = 4.dp)
        )
        Text(
            text = jenis.simbol,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = fontMono,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = warnaSimbol,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp)
        )
    }
}

@Composable
fun warnaLatarBarisDiff(jenis: JenisBarisDiff, disorotAktif: Boolean): Color {
    val dasar = when (jenis) {
        JenisBarisDiff.TAMBAH -> MaterialTheme.colorScheme.primaryContainer
        JenisBarisDiff.HAPUS -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        JenisBarisDiff.UBAH -> MaterialTheme.colorScheme.surfaceContainerHigh
        JenisBarisDiff.KOSONG -> MaterialTheme.colorScheme.surfaceContainer
        JenisBarisDiff.SAMA -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    return if (disorotAktif && jenis != JenisBarisDiff.SAMA) {
        when (jenis) {
            JenisBarisDiff.TAMBAH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            JenisBarisDiff.HAPUS -> MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        }
    } else {
        dasar
    }
}

@Composable
fun warnaTeksPenanda(jenis: JenisBarisDiff): Color = when (jenis) {
    JenisBarisDiff.TAMBAH -> MaterialTheme.colorScheme.primary
    JenisBarisDiff.HAPUS -> MaterialTheme.colorScheme.error
    JenisBarisDiff.UBAH -> MaterialTheme.colorScheme.primary
    JenisBarisDiff.KOSONG, JenisBarisDiff.SAMA -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun warnaSorotKarakterDiff(jenis: JenisBarisDiff): Color = when (jenis) {
    JenisBarisDiff.TAMBAH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    JenisBarisDiff.HAPUS -> MaterialTheme.colorScheme.error.copy(alpha = 0.30f)
    JenisBarisDiff.UBAH -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    else -> Color.Transparent
}

fun bangunTeksDenganSorotKarakter(
    teks: String,
    rentang: List<RentangKarakterBeda>,
    warnaSorot: Color
): AnnotatedString {
    if (rentang.isEmpty() || teks.isEmpty() || warnaSorot == Color.Transparent) {
        return AnnotatedString(teks)
    }
    return buildAnnotatedString {
        append(teks)
        for (r in rentang) {
            val awal = r.awal.coerceIn(0, teks.length)
            val akhir = r.akhirEksklusif.coerceIn(awal, teks.length)
            if (akhir > awal) {
                addStyle(
                    style = SpanStyle(
                        background = warnaSorot,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = awal,
                    end = akhir
                )
            }
        }
    }
}