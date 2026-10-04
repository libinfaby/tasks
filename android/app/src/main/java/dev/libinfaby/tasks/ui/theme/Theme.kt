package dev.libinfaby.tasks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.data.settings.ThemeMode

/** Mirrors frontend/css/styles.css `:root` tokens — see frontend/css/tokens.md. */
object Tokens {
    @Immutable
    data class Palette(
        val bg: Color,
        val bgSubtle: Color,
        val muted: Color,
        val card: Color,
        val cardHover: Color,
        val border: Color,
        val borderStrong: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val accent: Color,
        val accentText: Color,
        val accentSoft: Color,
        val urgent: Color,
        val urgentSoft: Color,
        val high: Color,
        val highSoft: Color,
        val success: Color,
        val danger: Color,
        val dangerSoft: Color,
        val chipEdge: Color,
    )

    val Light = Palette(
        bg = Color(0xFFFFFFFF),
        bgSubtle = Color(0xFFFAFAFA),
        muted = Color(0xFFF4F4F5),
        card = Color(0xFFFFFFFF),
        cardHover = Color(0xFFFAFAFA),
        border = Color(0xFFE4E4E7),
        borderStrong = Color(0xFFD4D4D8),
        textPrimary = Color(0xFF18181B),
        textSecondary = Color(0xFF52525B),
        textTertiary = Color(0xFF71717A),
        accent = Color(0xFF5B5BD6),
        accentText = Color(0xFF4C4BBD),
        accentSoft = Color(0x1A5B5BD6),
        urgent = Color(0xFFE5484D),
        urgentSoft = Color(0x1AE5484D),
        high = Color(0xFFEF6C1A),
        highSoft = Color(0x1AEF6C1A),
        success = Color(0xFF30A46C),
        danger = Color(0xFFE5484D),
        dangerSoft = Color(0x1AE5484D),
        chipEdge = Color(0x1A09090B),
    )

    val Dark = Palette(
        bg = Color(0xFF111113),
        bgSubtle = Color(0xFF0C0C0E),
        muted = Color(0xFF222226),
        card = Color(0xFF18181B),
        cardHover = Color(0xFF1D1D21),
        border = Color(0xFF27272A),
        borderStrong = Color(0xFF3F3F46),
        textPrimary = Color(0xFFEDEDEF),
        textSecondary = Color(0xFFA1A1AA),
        textTertiary = Color(0xFF7C7C85),
        accent = Color(0xFF5B5BD6),
        accentText = Color(0xFFA8A4FF),
        accentSoft = Color(0x296E6ADE),
        urgent = Color(0xFFFF6369),
        urgentSoft = Color(0x24FF6369),
        high = Color(0xFFFF8B3E),
        highSoft = Color(0x24FF8B3E),
        success = Color(0xFF3DD68C),
        danger = Color(0xFFFF6369),
        dangerSoft = Color(0x24FF6369),
        chipEdge = Color(0x1AFFFFFF),
    )
}

val LocalPalette = staticCompositionLocalOf { Tokens.Light }

/** Current theme palette, for colours Material's scheme has no slot for (priority, chip edge…). */
val palette: Tokens.Palette
    @Composable get() = LocalPalette.current

private fun Tokens.Palette.toScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accentSoft, onPrimaryContainer = accentText,
        secondary = textSecondary, onSecondary = bg, secondaryContainer = muted, onSecondaryContainer = textPrimary,
        background = bg, onBackground = textPrimary, surface = bg, onSurface = textPrimary,
        surfaceVariant = muted, onSurfaceVariant = textSecondary, surfaceContainerLowest = bgSubtle,
        surfaceContainerLow = card, surfaceContainer = card, surfaceContainerHigh = card, surfaceContainerHighest = muted,
        outline = borderStrong, outlineVariant = border, error = danger, onError = Color.White, errorContainer = dangerSoft,
        inverseSurface = textPrimary, inverseOnSurface = bg, scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = accentSoft, onPrimaryContainer = accentText,
        secondary = textSecondary, onSecondary = bg, secondaryContainer = muted, onSecondaryContainer = textPrimary,
        background = bg, onBackground = textPrimary, surface = bg, onSurface = textPrimary,
        surfaceVariant = muted, onSurfaceVariant = textSecondary, surfaceContainerLowest = card,
        surfaceContainerLow = bgSubtle, surfaceContainer = bgSubtle, surfaceContainerHigh = card, surfaceContainerHighest = muted,
        outline = borderStrong, outlineVariant = border, error = danger, onError = Color.White, errorContainer = dangerSoft,
        inverseSurface = textPrimary, inverseOnSurface = bg, scrim = Color.Black,
    )
}

// Inter on the web; the platform sans (Roboto) here is metrically close. Sizes follow the 12/13/14/16/20/24 scale.
private val sans = FontFamily.Default
private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(fontFamily = sans, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = tracking.sp)

private val TasksTypography = Typography(
    headlineSmall = style(24, FontWeight.SemiBold, 30, -0.2),
    titleLarge = style(20, FontWeight.SemiBold, 26, -0.1),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(15, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(13, FontWeight.Medium, 18),
    labelSmall = style(12, FontWeight.Medium, 16),
)

private val TasksShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun TasksTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val p = if (dark) Tokens.Dark else Tokens.Light
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = p.toScheme(dark), typography = TasksTypography, shapes = TasksShapes, content = content)
    }
}
