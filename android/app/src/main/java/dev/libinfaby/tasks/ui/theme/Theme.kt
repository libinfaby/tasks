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

/** "Grape": the scheme used when wallpaper colours are off (and by the widget). */
object TasksColors {
    val Light = lightColorScheme(
        primary = Color(0xFF6750A4), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF4F378A),
        inversePrimary = Color(0xFFD0BCFF),
        secondary = Color(0xFF625B71), onSecondary = Color.White, secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF4A4458),
        tertiary = Color(0xFF8F4C2E), onTertiary = Color.White, tertiaryContainer = Color(0xFFFFDBCB), onTertiaryContainer = Color(0xFF6E3A1F),
        background = Color(0xFFFEF7FF), onBackground = Color(0xFF1D1B20), surface = Color(0xFFFEF7FF), onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454F), surfaceTint = Color(0xFF6750A4),
        surfaceBright = Color(0xFFFEF7FF), surfaceDim = Color(0xFFDED8E1),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F1FA), surfaceContainer = Color(0xFFF2ECF4),
        surfaceContainerHigh = Color(0xFFECE6EE), surfaceContainerHighest = Color(0xFFE6E0E9),
        inverseSurface = Color(0xFF322F35), inverseOnSurface = Color(0xFFF5EFF7),
        error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF7A757F), outlineVariant = Color(0xFFCAC4D0), scrim = Color.Black,
    )

    val Dark = darkColorScheme(
        primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
        inversePrimary = Color(0xFF6750A4),
        secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFFFB596), onTertiary = Color(0xFF55200A), tertiaryContainer = Color(0xFF723520), onTertiaryContainer = Color(0xFFFFDBCB),
        background = Color(0xFF141218), onBackground = Color(0xFFE6E0E9), surface = Color(0xFF141218), onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0), surfaceTint = Color(0xFFD0BCFF),
        surfaceBright = Color(0xFF3B383E), surfaceDim = Color(0xFF141218),
        surfaceContainerLowest = Color(0xFF0F0D13), surfaceContainerLow = Color(0xFF1D1B20), surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930), surfaceContainerHighest = Color(0xFF36343B),
        inverseSurface = Color(0xFFE6E0E9), inverseOnSurface = Color(0xFF322F35),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F), scrim = Color.Black,
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
fun TasksTheme(mode: ThemeMode = ThemeMode.SYSTEM, wallpaperColors: Boolean = true, content: @Composable () -> Unit) {
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
