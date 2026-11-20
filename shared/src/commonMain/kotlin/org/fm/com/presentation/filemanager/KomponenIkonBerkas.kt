package org.fm.com.presentation.filemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fm.com.domain.model.JenisBerkas

@Composable
fun KomponenIkonBerkas(
    jenis: JenisBerkas,
    modifier: Modifier = Modifier,
    ukuran: Dp = 36.dp
) {
    val warnaFolder = Color(0xFFD97706)
    val warnaFolderAtas = Color(0xFFF59E0B)
    val warnaTeks = MaterialTheme.colorScheme.primary
    val warnaGambar = Color(0xFF059669)
    val warnaAudio = Color(0xFFDB2777)
    val warnaVideo = Color(0xFF7C3AED)
    val warnaArsip = Color(0xFFEA580C)
    val warnaTautan = MaterialTheme.colorScheme.secondary
    val warnaLain = MaterialTheme.colorScheme.outline
    val warnaKertasLipat = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .size(ukuran)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val radius = CornerRadius(w * 0.12f, w * 0.12f)
                val strokeLebar = (w * 0.06f).coerceAtLeast(1.5f)

                val folderPath = Path().apply {
                    moveTo(w * 0.08f, h * 0.24f)
                    lineTo(w * 0.42f, h * 0.24f)
                    lineTo(w * 0.51f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.84f)
                    lineTo(w * 0.08f, h * 0.84f)
                    close()
                }
                val folderTabPath = Path().apply {
                    moveTo(w * 0.08f, h * 0.24f)
                    lineTo(w * 0.42f, h * 0.24f)
                    lineTo(w * 0.51f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.34f)
                    lineTo(w * 0.92f, h * 0.46f)
                    lineTo(w * 0.08f, h * 0.46f)
                    close()
                }
                val docPath = Path().apply {
                    moveTo(w * 0.18f, h * 0.1f)
                    lineTo(w * 0.62f, h * 0.1f)
                    lineTo(w * 0.84f, h * 0.32f)
                    lineTo(w * 0.84f, h * 0.9f)
                    lineTo(w * 0.18f, h * 0.9f)
                    close()
                }
                val lipatPath = Path().apply {
                    moveTo(w * 0.62f, h * 0.1f)
                    lineTo(w * 0.62f, h * 0.32f)
                    lineTo(w * 0.84f, h * 0.32f)
                    close()
                }
                val playPath = Path().apply {
                    moveTo(w * 0.41f, h * 0.36f)
                    lineTo(w * 0.68f, h * 0.5f)
                    lineTo(w * 0.41f, h * 0.64f)
                    close()
                }

                onDrawBehind {
                    when (jenis) {
                        JenisBerkas.FOLDER -> {
                            drawPath(folderPath, color = warnaFolderAtas)
                            drawPath(folderTabPath, color = warnaFolder)
                        }
                        JenisBerkas.BERKAS_GAMBAR -> {
                            drawRoundRect(
                                color = warnaGambar.copy(alpha = 0.16f),
                                topLeft = Offset(w * 0.12f, h * 0.14f),
                                size = Size(w * 0.76f, h * 0.72f),
                                cornerRadius = radius
                            )
                            drawRoundRect(
                                color = warnaGambar,
                                topLeft = Offset(w * 0.12f, h * 0.14f),
                                size = Size(w * 0.76f, h * 0.72f),
                                cornerRadius = radius,
                                style = Stroke(width = strokeLebar)
                            )
                            drawCircle(
                                color = warnaGambar,
                                radius = w * 0.09f,
                                center = Offset(w * 0.35f, h * 0.38f)
                            )
                            drawLine(
                                color = warnaGambar,
                                start = Offset(w * 0.24f, h * 0.72f),
                                end = Offset(w * 0.54f, h * 0.48f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = warnaGambar,
                                start = Offset(w * 0.54f, h * 0.48f),
                                end = Offset(w * 0.76f, h * 0.72f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                        }
                        JenisBerkas.BERKAS_AUDIO -> {
                            drawCircle(
                                color = warnaAudio.copy(alpha = 0.16f),
                                radius = w * 0.38f,
                                center = Offset(w * 0.5f, h * 0.5f)
                            )
                            drawCircle(
                                color = warnaAudio,
                                radius = w * 0.38f,
                                center = Offset(w * 0.5f, h * 0.5f),
                                style = Stroke(width = strokeLebar)
                            )
                            drawCircle(
                                color = warnaAudio,
                                radius = w * 0.11f,
                                center = Offset(w * 0.5f, h * 0.5f)
                            )
                        }
                        JenisBerkas.BERKAS_VIDEO -> {
                            drawRoundRect(
                                color = warnaVideo.copy(alpha = 0.16f),
                                topLeft = Offset(w * 0.12f, h * 0.18f),
                                size = Size(w * 0.76f, h * 0.64f),
                                cornerRadius = radius
                            )
                            drawRoundRect(
                                color = warnaVideo,
                                topLeft = Offset(w * 0.12f, h * 0.18f),
                                size = Size(w * 0.76f, h * 0.64f),
                                cornerRadius = radius,
                                style = Stroke(width = strokeLebar)
                            )
                            drawPath(playPath, color = warnaVideo)
                        }
                        JenisBerkas.ARSIP -> {
                            drawRoundRect(
                                color = warnaArsip.copy(alpha = 0.16f),
                                topLeft = Offset(w * 0.18f, h * 0.12f),
                                size = Size(w * 0.64f, h * 0.76f),
                                cornerRadius = radius
                            )
                            drawRoundRect(
                                color = warnaArsip,
                                topLeft = Offset(w * 0.18f, h * 0.12f),
                                size = Size(w * 0.64f, h * 0.76f),
                                cornerRadius = radius,
                                style = Stroke(width = strokeLebar)
                            )
                            drawLine(
                                color = warnaArsip,
                                start = Offset(w * 0.5f, h * 0.2f),
                                end = Offset(w * 0.5f, h * 0.8f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = warnaArsip,
                                start = Offset(w * 0.42f, h * 0.36f),
                                end = Offset(w * 0.58f, h * 0.36f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = warnaArsip,
                                start = Offset(w * 0.42f, h * 0.54f),
                                end = Offset(w * 0.58f, h * 0.54f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                        }
                        else -> {
                            val warna = when (jenis) {
                                JenisBerkas.BERKAS_TEKS -> warnaTeks
                                JenisBerkas.TAUTAN -> warnaTautan
                                else -> warnaLain
                            }
                            drawPath(docPath, color = warna.copy(alpha = 0.14f))
                            drawPath(docPath, color = warna, style = Stroke(width = strokeLebar))
                            drawPath(lipatPath, color = warnaKertasLipat.copy(alpha = 0.85f))
                            drawLine(
                                color = warna,
                                start = Offset(w * 0.32f, h * 0.52f),
                                end = Offset(w * 0.68f, h * 0.52f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = warna,
                                start = Offset(w * 0.32f, h * 0.68f),
                                end = Offset(w * 0.58f, h * 0.68f),
                                strokeWidth = strokeLebar,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
    )
}