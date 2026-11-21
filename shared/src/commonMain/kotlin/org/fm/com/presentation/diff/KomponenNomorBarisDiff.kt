package org.fm.com.presentation.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.domain.diff.JenisBarisDiff
import org.fm.com.domain.diff.RentangKarakterBeda
import org.fm.com.presentation.tema.LocalFontMono

@Immutable
data class PaletPanelDiff(
    val latarEditor: Color,
    val latarHeader: Color,
    val latarGutter: Color,
    val latarTambah: Color,
    val latarHapus: Color,
    val latarUbah: Color,
    val latarKosong: Color,
    val latarSorotTambah: Color,
    val latarSorotHapus: Color,
    val latarSorotUbah: Color,
    val sorotKarakterTambah: Color,
    val sorotKarakterHapus: Color,
    val sorotKarakterUbah: Color,
    val teksUtama: Color,
    val teksSekunder: Color,
    val teksAksen: Color,
    val teksError: Color,
    val divider: Color,
    val gayaKode: TextStyle,
    val gayaNomor: TextStyle,
    val gayaSimbolNormal: TextStyle,
    val gayaSimbolTebal: TextStyle
) {
    fun latarBaris(jenis: JenisBarisDiff, aktif: Boolean): Color {
        if (aktif && jenis != JenisBarisDiff.SAMA) {
            return when (jenis) {
                JenisBarisDiff.TAMBAH -> latarSorotTambah
                JenisBarisDiff.HAPUS -> latarSorotHapus
                JenisBarisDiff.UBAH -> latarSorotUbah
                else -> latarKosong
            }
        }
        return when (jenis) {
            JenisBarisDiff.TAMBAH -> latarTambah
            JenisBarisDiff.HAPUS -> latarHapus
            JenisBarisDiff.UBAH -> latarUbah
            JenisBarisDiff.KOSONG -> latarKosong
            JenisBarisDiff.SAMA -> latarEditor
        }
    }

    fun sorotKarakter(jenis: JenisBarisDiff): Color = when (jenis) {
        JenisBarisDiff.TAMBAH -> sorotKarakterTambah
        JenisBarisDiff.HAPUS -> sorotKarakterHapus
        JenisBarisDiff.UBAH -> sorotKarakterUbah
        else -> Color.Transparent
    }

    fun warnaSimbol(jenis: JenisBarisDiff): Color = when (jenis) {
        JenisBarisDiff.TAMBAH, JenisBarisDiff.UBAH -> teksAksen
        JenisBarisDiff.HAPUS -> teksError
        JenisBarisDiff.KOSONG, JenisBarisDiff.SAMA -> teksSekunder
    }
}

@Composable
fun ingatPaletPanelDiff(ukuranKodeSp: Float = 12f): PaletPanelDiff {
    val cs = MaterialTheme.colorScheme
    val fontMono = LocalFontMono.current
    return remember(cs, fontMono, ukuranKodeSp) {
        val gayaKode = TextStyle(
            fontFamily = fontMono,
            fontSize = ukuranKodeSp.sp,
            lineHeight = (ukuranKodeSp + 5f).sp,
            color = cs.onSurface
        )
        val gayaNomor = TextStyle(
            fontFamily = fontMono,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant
        )
        val gayaSimbolNormal = TextStyle(
            fontFamily = fontMono,
            fontSize = 11.sp
        )
        val gayaSimbolTebal = TextStyle(
            fontFamily = fontMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        PaletPanelDiff(
            latarEditor = cs.surfaceContainerLowest,
            latarHeader = cs.surfaceContainer,
            latarGutter = cs.surfaceContainerLow,
            latarTambah = cs.primaryContainer,
            latarHapus = cs.errorContainer.copy(alpha = 0.65f),
            latarUbah = cs.surfaceContainerHigh,
            latarKosong = cs.surfaceContainer,
            latarSorotTambah = cs.primary.copy(alpha = 0.22f),
            latarSorotHapus = cs.error.copy(alpha = 0.24f),
            latarSorotUbah = cs.primary.copy(alpha = 0.14f),
            sorotKarakterTambah = cs.primary.copy(alpha = 0.28f),
            sorotKarakterHapus = cs.error.copy(alpha = 0.30f),
            sorotKarakterUbah = cs.primary.copy(alpha = 0.24f),
            teksUtama = cs.onSurface,
            teksSekunder = cs.onSurfaceVariant,
            teksAksen = cs.primary,
            teksError = cs.error,
            divider = cs.outlineVariant,
            gayaKode = gayaKode,
            gayaNomor = gayaNomor,
            gayaSimbolNormal = gayaSimbolNormal,
            gayaSimbolTebal = gayaSimbolTebal
        )
    }
}

@Composable
fun GutterNomorBarisTunggal(
    nomorBaris: Int?,
    jenis: JenisBarisDiff,
    palet: PaletPanelDiff,
    lebarNomor: Dp,
    modifier: Modifier = Modifier
) {
    val adalahSimbolTebal = jenis == JenisBarisDiff.TAMBAH ||
        jenis == JenisBarisDiff.HAPUS ||
        jenis == JenisBarisDiff.UBAH

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(palet.latarGutter)
            .padding(horizontal = 4.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} baris ${nomorBaris ?: "-"}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = nomorBaris?.toString() ?: "",
            style = palet.gayaNomor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(lebarNomor)
        )
        Text(
            text = jenis.simbol,
            style = if (adalahSimbolTebal) palet.gayaSimbolTebal else palet.gayaSimbolNormal,
            color = palet.warnaSimbol(jenis),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(13.dp)
        )
    }
}

@Composable
fun GutterNomorBarisUnified(
    nomorBarisA: Int?,
    nomorBarisB: Int?,
    jenis: JenisBarisDiff,
    palet: PaletPanelDiff,
    lebarKolomNomor: Dp,
    modifier: Modifier = Modifier
) {
    val adalahSimbolTebal = jenis == JenisBarisDiff.TAMBAH ||
        jenis == JenisBarisDiff.HAPUS ||
        jenis == JenisBarisDiff.UBAH

    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(palet.latarGutter)
            .padding(horizontal = 4.dp)
            .semantics {
                contentDescription = "${jenis.labelAksesibilitas} A:${nomorBarisA ?: "-"} B:${nomorBarisB ?: "-"}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = nomorBarisA?.toString() ?: "",
            style = palet.gayaNomor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(lebarKolomNomor)
        )
        Text(
            text = nomorBarisB?.toString() ?: "",
            style = palet.gayaNomor,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(lebarKolomNomor).padding(start = 4.dp)
        )
        Text(
            text = jenis.simbol,
            style = if (adalahSimbolTebal) palet.gayaSimbolTebal else palet.gayaSimbolNormal,
            color = palet.warnaSimbol(jenis),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(15.dp)
        )
    }
}

fun hitungLebarMaksTeksPx(
    kandidat: List<String>,
    gayaKode: TextStyle,
    textMeasurer: TextMeasurer
): Float {
    var maksLebar = 0f
    for (teks in kandidat) {
        if (teks.isEmpty()) continue
        val lebar = textMeasurer.measure(
            text = teks,
            style = gayaKode,
            maxLines = 1,
            softWrap = false
        ).size.width.toFloat()
        if (lebar > maksLebar) maksLebar = lebar
    }
    return maksLebar
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