package dev.libinfaby.tasks.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.libinfaby.tasks.ui.theme.isDark

fun parseHex(hex: String?, fallback: Color = Color(0xFF6366F1)): Color = runCatching {
    var h = hex!!.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    Color(android.graphics.Color.parseColor("#$h"))
}.getOrDefault(fallback)

fun Color.toHex(): String = String.format("#%06x", (android.graphics.Color.argb(alpha, red, green, blue)) and 0xFFFFFF)

/** No real hue (greys, white, black): such tags read as neutral chips. */
private fun Color.isNeutral() = maxOf(red, green, blue) - minOf(red, green, blue) < 0.12f

/**
 * Material 3 tag chip colours: a soft container in the tag's hue with darker (light theme) or lighter
 * (dark theme) text of the same hue, laid over the card. Neutral colours use the theme's own neutral
 * chip, so only tags with a real colour draw the eye. The tag's stored fill and text colour are for the web.
 */
@Composable
fun tagColors(color: String?, typeColor: String? = null): ToggleColors {
    val hue = parseHex(color ?: typeColor)
    if (hue.isNeutral()) {
        return ToggleColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val card = MaterialTheme.colorScheme.surfaceContainer
    return if (MaterialTheme.isDark) {
        ToggleColors(hue.copy(alpha = 0.3f).compositeOver(card), lerp(hue, Color.White, 0.65f))
    } else {
        ToggleColors(hue.copy(alpha = 0.16f).compositeOver(card), lerp(hue, Color.Black, 0.5f))
    }
}

/**
 * A soft container + readable content pair from a user-picked colour (group tiles, section icons),
 * so any hex sits in the theme the way Material's own tonal roles do.
 */
@Composable
fun tonalColors(color: Color): ToggleColors {
    // White, black and greys have no hue to tint with; use the theme's neutral pair so icons stay readable
    if (color.isNeutral()) {
        return ToggleColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val surface = MaterialTheme.colorScheme.surface
    return if (MaterialTheme.isDark) {
        ToggleColors(color.copy(alpha = 0.34f).compositeOver(surface), lerp(color, Color.White, 0.72f))
    } else {
        ToggleColors(color.copy(alpha = 0.2f).compositeOver(surface), lerp(color, Color.Black, 0.5f))
    }
}

/** Tag chip: 24dp, 8dp corners, tonal, no outline. Just the tag's name, never its type. */
@Composable
fun Chip(
    text: String,
    colors: ToggleColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        // A plain clickable, not Surface(onClick): chips sit in dense rows and must stay 24dp tall
        modifier = modifier.clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.height(24.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
