package org.fm.com.presentation.filemanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.JenisBerkas

@Composable
fun KomponenIkonBerkas(
    jenis: JenisBerkas,
    modifier: Modifier = Modifier,
    ukuran: Dp = 36.dp
) {
    val warnaFolder = Color(0xFFFFB300)
    val warnaTeks = MaterialTheme.colorScheme.primary
    val warnaGambar = Color(0xFF43A047)
    val warnaAudio = Color(0xFFE91E63)
    val warnaVideo = Color(0xFF8E24AA)
    val warnaArsip = Color(0xFFFF7043)
    val warnaLain = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.size(ukuran)) {
        val w = size.width
        val h = size.height

        when (jenis) {
            JenisBerkas.FOLDER -> {
                val path = Path().apply {
                    moveTo(0f, h * 0.25f)
                    lineTo(w * 0.4f, h * 0.25f)
                    lineTo(w * 0.5f, h * 0.35f)
                    lineTo(w, h * 0.35f)
                    lineTo(w, h * 0.85f)
                    lineTo(0f, h * 0.85f)
                    close()
                }
                drawPath(path, color = warnaFolder)
            }
            JenisBerkas.BERKAS_GAMBAR -> {
                drawRoundRect(
                    color = warnaGambar,
                    topLeft = Offset(w * 0.15f, h * 0.15f),
                    size = Size(w * 0.7f, h * 0.7f),
                    cornerRadius = CornerRadius(w * 0.1f)
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.1f,
                    center = Offset(w * 0.35f, h * 0.35f)
                )
            }
            JenisBerkas.BERKAS_AUDIO -> {
                drawCircle(color = warnaAudio, radius = w * 0.35f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(color = Color.White, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.5f))
            }
            JenisBerkas.BERKAS_VIDEO -> {
                drawRoundRect(
                    color = warnaVideo,
                    topLeft = Offset(w * 0.15f, h * 0.2f),
                    size = Size(w * 0.7f, h * 0.6f),
                    cornerRadius = CornerRadius(w * 0.08f)
                )
                val segitiga = Path().apply {
                    moveTo(w * 0.42f, h * 0.35f)
                    lineTo(w * 0.65f, h * 0.5f)
                    lineTo(w * 0.42f, h * 0.65f)
                    close()
                }
                drawPath(segitiga, color = Color.White)
            }
            JenisBerkas.ARSIP -> {
                drawRoundRect(
                    color = warnaArsip,
                    topLeft = Offset(w * 0.2f, h * 0.15f),
                    size = Size(w * 0.6f, h * 0.7f),
                    cornerRadius = CornerRadius(w * 0.06f)
                )
                drawLine(Color.White, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.7f), strokeWidth = w * 0.08f)
            }
            else -> {
                val warna = if (jenis == JenisBerkas.BERKAS_TEKS) warnaTeks else warnaLain
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.1f)
                    lineTo(w * 0.6f, h * 0.1f)
                    lineTo(w * 0.8f, h * 0.3f)
                    lineTo(w * 0.8f, h * 0.9f)
                    lineTo(w * 0.2f, h * 0.9f)
                    close()
                }
                drawPath(path, color = warna)
            }
        }
    }
}