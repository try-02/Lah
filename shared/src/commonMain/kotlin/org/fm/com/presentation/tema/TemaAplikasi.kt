package org.fm.com.presentation.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fm.com.resources.Res
import org.fm.com.resources.ibm_plex_mono_medium
import org.fm.com.resources.ibm_plex_mono_regular
import org.fm.com.resources.ibm_plex_mono_semibold
import org.fm.com.resources.ibm_plex_sans_medium
import org.fm.com.resources.ibm_plex_sans_regular
import org.fm.com.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

private val AksenTerang = Color(0xFF1E7A46)
private val AksenGelap = Color(0xFF7EE787)

private val SkemaWarnaTerang = lightColorScheme(
    primary = AksenTerang,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E7A46).copy(alpha = 0.11f),
    onPrimaryContainer = Color(0xFF1E7A46),
    secondary = AksenTerang,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1E7A46).copy(alpha = 0.11f),
    onSecondaryContainer = Color(0xFF1E7A46),
    tertiary = Color(0xFF5D6470),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECEAE4),
    onTertiaryContainer = Color(0xFF1B1E24),
    background = Color(0xFFF7F6F3),
    onBackground = Color(0xFF1B1E24),
    surface = Color(0xFFF7F6F3),
    onSurface = Color(0xFF1B1E24),
    surfaceVariant = Color(0xFFECEAE4),
    onSurfaceVariant = Color(0xFF5D6470),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFEA),
    surfaceContainerHigh = Color(0xFFE9E7E0),
    outline = Color(0xFF5D6470),
    outlineVariant = Color(0xFFE5E2DC),
    error = Color(0xFFC93B3B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF8C1D1D)
)

private val SkemaWarnaGelap = darkColorScheme(
    primary = AksenGelap,
    onPrimary = Color(0xFF0A1F10),
    primaryContainer = Color(0xFF7EE787).copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFF7EE787),
    secondary = AksenGelap,
    onSecondary = Color(0xFF0A1F10),
    secondaryContainer = Color(0xFF7EE787).copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF7EE787),
    tertiary = Color(0xFF98A0AE),
    onTertiary = Color(0xFF111318),
    tertiaryContainer = Color(0xFF232730),
    onTertiaryContainer = Color(0xFFE8EAF0),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF222630),
    onSurfaceVariant = Color(0xFF98A0AE),
    surfaceContainerLowest = Color(0xFF15181E),
    surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF191C22),
    surfaceContainerHigh = Color(0xFF222630),
    outline = Color(0xFF98A0AE),
    outlineVariant = Color(0xFF2A2F3A),
    error = Color(0xFFF47067),
    onError = Color(0xFF2B0B0B),
    errorContainer = Color(0xFF471B1B),
    onErrorContainer = Color(0xFFFFD8D5)
)

private val BentukAplikasi = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

val LocalFontMono = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

@Composable
fun TemaAplikasi(
    temaGelap: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val sansFont = FontFamily(
        Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold)
    )
    val monoFont = FontFamily(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
    )

    val tipografi = remember(sansFont) {
        Typography(
            titleLarge = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 25.sp
            ),
            titleMedium = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp
            ),
            titleSmall = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 18.sp
            ),
            bodySmall = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            labelLarge = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 17.sp
            ),
            labelMedium = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 15.sp
            ),
            labelSmall = TextStyle(
                fontFamily = sansFont,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        )
    }

    CompositionLocalProvider(LocalFontMono provides monoFont) {
        MaterialTheme(
            colorScheme = if (temaGelap) SkemaWarnaGelap else SkemaWarnaTerang,
            shapes = BentukAplikasi,
            typography = tipografi,
            content = content
        )
    }
}