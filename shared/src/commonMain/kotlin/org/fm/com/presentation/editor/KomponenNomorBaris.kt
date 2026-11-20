package org.fm.com.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.presentation.tema.LocalFontMono

private class CachePosisiBaris {
    var teksRef: String = ""
    var offsetTerakhir: Int = 0
    var nomorTerakhir: Int = 1

    fun nomorUntukOffset(teks: String, targetOffset: Int): Int {
        val batas = targetOffset.coerceIn(0, teks.length)
        if (teksRef !== teks) {
            teksRef = teks
            offsetTerakhir = 0
            nomorTerakhir = 1
        }
        var nomor = nomorTerakhir
        val awal = offsetTerakhir
        if (batas > awal) {
            for (i in awal until batas) {
                if (teks[i] == '\n') nomor++
            }
        } else if (batas < awal) {
            if (awal - batas <= batas) {
                for (i in batas until awal) {
                    if (teks[i] == '\n') nomor--
                }
            } else {
                nomor = 1
                for (i in 0 until batas) {
                    if (teks[i] == '\n') nomor++
                }
            }
        }
        offsetTerakhir = batas
        nomorTerakhir = nomor
        return nomor
    }
}

@Composable
fun KomponenNomorBaris(
    jumlahBaris: Int,
    barisAktif: Int,
    textLayoutResult: TextLayoutResult?,
    scrollY: Int,
    tinggiViewportPx: Int,
    paddingAtasPx: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val density = LocalDensity.current
    val cacheBaris = remember { CachePosisiBaris() }
    val fontMono = LocalFontMono.current

    val warnaLatar = MaterialTheme.colorScheme.surfaceContainerLow
    val warnaNomorNormal = MaterialTheme.colorScheme.onSurfaceVariant
    val warnaNomorAktif = MaterialTheme.colorScheme.primary
    val warnaSorotBarisAktif = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)

    val gayaNormal = remember(warnaNomorNormal, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 12.sp,
            color = warnaNomorNormal
        )
    }
    val gayaAktif = remember(warnaNomorAktif, fontMono) {
        TextStyle(
            fontFamily = fontMono,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = warnaNomorAktif
        )
    }

    val jumlahDigit = remember(jumlahBaris) { jumlahBaris.coerceAtLeast(1).toString().length.coerceAtLeast(2) }
    val lebarTeksMaksPx = remember(jumlahDigit, gayaNormal, textMeasurer) {
        textMeasurer.measure("8".repeat(jumlahDigit), style = gayaNormal).size.width
    }
    val lebarGutterDp = with(density) { lebarTeksMaksPx.toDp() + 20.dp }
    val paddingKananPx = with(density) { 8.dp.toPx() }

    Canvas(
        modifier = modifier
            .width(lebarGutterDp)
            .fillMaxHeight()
            .background(warnaLatar)
            .padding(horizontal = 2.dp)
    ) {
        val layout = textLayoutResult ?: return@Canvas
        val totalBarisVisual = layout.lineCount
        if (totalBarisVisual <= 0) return@Canvas

        val teksLayout = layout.layoutInput.text.text
        val batasBawah = if (tinggiViewportPx > 0) {
            (scrollY + tinggiViewportPx).toFloat()
        } else {
            size.height
        }
        val awalY = (scrollY.toFloat() - paddingAtasPx).coerceAtLeast(0f)
        val akhirY = (batasBawah - paddingAtasPx).coerceAtLeast(awalY + 1f)

        val visualAwal = layout.getLineForVerticalPosition(awalY).coerceIn(0, totalBarisVisual - 1)
        val visualAkhir = layout.getLineForVerticalPosition(akhirY).coerceIn(visualAwal, totalBarisVisual - 1)

        var nomorBerjalan = cacheBaris.nomorUntukOffset(
            teksLayout,
            layout.getLineStart(visualAwal).coerceIn(0, teksLayout.length)
        )
        var sudahBarisAwalTerlihat = false

        for (barisVisual in visualAwal..visualAkhir) {
            val startOffset = layout.getLineStart(barisVisual).coerceIn(0, teksLayout.length)
            val adalahAwalBarisLogis = barisVisual == 0 ||
                (startOffset > 0 && teksLayout[startOffset - 1] == '\n')
            if (!adalahAwalBarisLogis) continue

            if (!sudahBarisAwalTerlihat) {
                sudahBarisAwalTerlihat = true
            } else {
                nomorBerjalan++
            }

            val adalahAktif = nomorBerjalan == barisAktif
            val topY = layout.getLineTop(barisVisual) + paddingAtasPx
            val bottomY = layout.getLineBottom(barisVisual) + paddingAtasPx
            val tinggiVisual = (bottomY - topY).coerceAtLeast(1f)

            if (adalahAktif) {
                drawRect(
                    color = warnaSorotBarisAktif,
                    topLeft = Offset(0f, topY),
                    size = Size(size.width, tinggiVisual)
                )
            }

            val hasilUkur = textMeasurer.measure(
                text = nomorBerjalan.toString(),
                style = if (adalahAktif) gayaAktif else gayaNormal
            )
            val posisiX = (size.width - hasilUkur.size.width - paddingKananPx).coerceAtLeast(0f)
            val posisiY = topY + ((tinggiVisual - hasilUkur.size.height) / 2f).coerceAtLeast(0f)
            drawText(
                textLayoutResult = hasilUkur,
                topLeft = Offset(posisiX, posisiY)
            )
        }
    }
}

internal fun bangunIndeksAwalBaris(teks: String): IntArray {
    var jumlah = 1
    for (i in 0 until teks.length) {
        if (teks[i] == '\n') jumlah++
    }
    val hasil = IntArray(jumlah)
    var idx = 1
    hasil[0] = 0
    for (i in 0 until teks.length) {
        if (teks[i] == '\n') {
            hasil[idx++] = i + 1
        }
    }
    return hasil
}

internal fun cariNomorBarisLogis(indeksAwalBaris: IntArray, offset: Int): Int {
    var low = 0
    var high = indeksAwalBaris.lastIndex
    var ketemu = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        val valMid = indeksAwalBaris[mid]
        if (valMid <= offset) {
            ketemu = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return ketemu + 1
}