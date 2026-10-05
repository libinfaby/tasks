package dev.libinfaby.tasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.R
import dev.libinfaby.tasks.data.settings.ThemeMode

/**
 * "Monochrome": the scheme used when wallpaper colours are off (and by the widget). Captured from Android's
 * monochrome wallpaper preset; the web app's tokens (frontend/css/styles.css) mirror it. Error roles keep Compose's
 * defaults, which is also what the dynamic schemes use.
 */
object TasksColors {
    val Light = lightColorScheme(
        primary = Color.Black, onPrimary = Color(0xFFE2E2E2), primaryContainer = Color(0xFF3B3B3B), onPrimaryContainer = Color.White,
        inversePrimary = Color.White,
        secondary = Color(0xFF5E5E5E), onSecondary = Color.White, secondaryContainer = Color(0xFFD4D4D4), onSecondaryContainer = Color(0xFF1B1B1B),
        tertiary = Color(0xFF3B3B3B), onTertiary = Color(0xFFE2E2E2), tertiaryContainer = Color(0xFF747474), onTertiaryContainer = Color.White,
        background = Color(0xFFF9F9F9), onBackground = Color(0xFF1B1B1B), surface = Color(0xFFF9F9F9), onSurface = Color(0xFF1B1B1B),
        surfaceVariant = Color(0xFFE2E2E2), onSurfaceVariant = Color(0xFF474747), surfaceTint = Color.Black,
        surfaceBright = Color(0xFFF9F9F9), surfaceDim = Color(0xFFDADADA),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3F3), surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE8E8E8), surfaceContainerHighest = Color(0xFFE2E2E2),
        inverseSurface = Color(0xFF131313), inverseOnSurface = Color(0xFFE2E2E2),
        outline = Color(0xFF777777), outlineVariant = Color(0xFFC6C6C6), scrim = Color.Black,
    )

    val Dark = darkColorScheme(
        primary = Color.White, onPrimary = Color(0xFF1B1B1B), primaryContainer = Color(0xFFD4D4D4), onPrimaryContainer = Color.Black,
        inversePrimary = Color.Black,
        secondary = Color(0xFFC6C6C6), onSecondary = Color(0xFF1B1B1B), secondaryContainer = Color(0xFF474747), onSecondaryContainer = Color(0xFFE2E2E2),
        tertiary = Color(0xFFE2E2E2), onTertiary = Color(0xFF1B1B1B), tertiaryContainer = Color(0xFF919191), onTertiaryContainer = Color.Black,
        background = Color(0xFF131313), onBackground = Color(0xFFE2E2E2), surface = Color(0xFF131313), onSurface = Color(0xFFE2E2E2),
        surfaceVariant = Color(0xFF474747), onSurfaceVariant = Color(0xFFC6C6C6), surfaceTint = Color.White,
        surfaceBright = Color(0xFF393939), surfaceDim = Color(0xFF131313),
        surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF1B1B1B), surfaceContainer = Color(0xFF1F1F1F),
        surfaceContainerHigh = Color(0xFF2A2A2A), surfaceContainerHighest = Color(0xFF353535),
        inverseSurface = Color(0xFFF9F9F9), inverseOnSurface = Color(0xFF1B1B1B),
        outline = Color(0xFF919191), outlineVariant = Color(0xFF474747), scrim = Color.Black,
    )
}

private val LocalDarkTheme = staticCompositionLocalOf { false }

/** Whether the app is drawn dark (theme setting, not just the system's). */
val MaterialTheme.isDark: Boolean
    @Composable get() = LocalDarkTheme.current

// Bricolage Grotesque for display and headlines (optical size pinned to its display cut), Figtree for everything else
private fun bricolage(weight: FontWeight) =
    Font(R.font.bricolage_grotesque, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal, FontVariation.Setting("opsz", 36f)))

private fun figtree(weight: FontWeight) =
    Font(R.font.figtree, weight, variationSettings = FontVariation.Settings(weight, FontStyle.Normal))

val DisplayFont = FontFamily(listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map(::bricolage))
val BodyFont = FontFamily(listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map(::figtree))

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val TasksTypography = Typography(
    displayLarge = style(DisplayFont, 57, 64, FontWeight.Bold, -1.0),
    displayMedium = style(DisplayFont, 45, 52, FontWeight.Bold, -0.8),
    displaySmall = style(DisplayFont, 36, 44, FontWeight.Bold, -0.6),
    headlineLarge = style(DisplayFont, 32, 40, FontWeight.Bold, -0.5),
    headlineMedium = style(DisplayFont, 28, 36, FontWeight.Bold, -0.4),
    headlineSmall = style(DisplayFont, 24, 32, FontWeight.Bold, -0.3),
    titleLarge = style(DisplayFont, 22, 28, FontWeight.SemiBold, -0.2),
    titleMedium = style(BodyFont, 16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(BodyFont, 14, 20, FontWeight.Bold, 0.1),
    bodyLarge = style(BodyFont, 16, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(BodyFont, 14, 20, FontWeight.Normal, 0.2),
    bodySmall = style(BodyFont, 13, 18, FontWeight.Normal, 0.2),
    labelLarge = style(BodyFont, 14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(BodyFont, 13, 18, FontWeight.SemiBold, 0.2),
    labelSmall = style(BodyFont, 12, 16, FontWeight.SemiBold, 0.3),
)

// The expressive scale leans rounder than the baseline one
private val TasksShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TasksTheme(mode: ThemeMode = ThemeMode.SYSTEM, wallpaperColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        wallpaperColors && dark -> dynamicDarkColorScheme(context)
        wallpaperColors -> dynamicLightColorScheme(context)
        dark -> TasksColors.Dark
        else -> TasksColors.Light
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, shapes = TasksShapes, typography = TasksTypography, content = content)
    }
}
