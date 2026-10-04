package dev.libinfaby.tasks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.libinfaby.tasks.ui.theme.isDark

fun parseHex(hex: String?, fallback: Color = Color(0xFF6366F1)): Color = runCatching {
    var h = hex!!.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    Color(android.graphics.Color.parseColor("#$h"))
}.getOrDefault(fallback)

fun Color.toHex(): String = String.format("#%06x", (android.graphics.Color.argb(alpha, red, green, blue)) and 0xFFFFFF)

data class ChipColors(val background: Color, val content: Color, val filled: Boolean)

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * Same rules as getChipStyle() in frontend/js/utils.js: filled chips use the chosen colours; unfilled
 * chips use the colour as text, falling back to a neutral text colour when it would be unreadable on
 * the current surface (e.g. white text in light mode).
 */
@Composable
fun chipColors(
    color: String?,
    fgColor: String?,
    hasBg: Int?,
    typeColor: String? = null,
    typeFgColor: String? = null,
    typeHasBg: Int? = null,
): ChipColors {
    val bg = parseHex(color ?: typeColor)
    val fg = parseHex(fgColor ?: typeFgColor, Color.White)
    val filled = (hasBg ?: typeHasBg ?: 1) != 0
    if (filled) return ChipColors(bg, fg, true)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val text = if (contrast(bg, surface) >= 2.2f) bg else MaterialTheme.colorScheme.onSurfaceVariant
    return ChipColors(Color.Transparent, text, false)
}

/**
 * A soft container + readable content pair from a user-picked colour (group tiles, section icons),
 * so any hex sits in the theme the way Material's own tonal roles do.
 */
@Composable
fun tonalColors(color: Color): ToggleColors {
    val surface = MaterialTheme.colorScheme.surface
    return if (MaterialTheme.isDark) {
        ToggleColors(color.copy(alpha = 0.34f).compositeOver(surface), lerp(color, Color.White, 0.72f))
    } else {
        ToggleColors(color.copy(alpha = 0.2f).compositeOver(surface), lerp(color, Color.Black, 0.5f))
    }
}

/** Tag/group chip: 24dp, 8dp corners (pill for kind tags). Unfilled chips get a hairline outline. */
@Composable
fun Chip(
    text: String,
    colors: ChipColors,
    modifier: Modifier = Modifier,
    label: String? = null,
    pill: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (pill) 12.dp else 8.dp)
    val border = when {
        !colors.filled -> BorderStroke(1.dp, colors.content.copy(alpha = 0.45f))
        // A fill close to the surface (black in dark mode, white in light) would lose its edge
        contrast(colors.background, MaterialTheme.colorScheme.surfaceContainer) < 1.6f -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    Surface(
        shape = shape,
        color = colors.background,
        contentColor = colors.content,
        border = border,
        // A plain clickable, not Surface(onClick): chips sit in dense rows and must stay 24dp tall
        modifier = modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.height(24.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (label != null) Text("$label ", style = MaterialTheme.typography.labelSmall, color = colors.content.copy(alpha = 0.72f), maxLines = 1)
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Meta text with a small leading icon (date, reminder, subtasks…). */
@Composable
fun MetaItem(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
