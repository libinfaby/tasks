package dev.libinfaby.tasks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.ui.theme.LocalPalette
import dev.libinfaby.tasks.ui.theme.Tokens
import dev.libinfaby.tasks.ui.theme.palette

fun parseHex(hex: String?, fallback: Color = Color(0xFF6366F1)): Color = runCatching {
    var h = hex!!.trim().removePrefix("#")
    if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
    Color(android.graphics.Color.parseColor("#$h"))
}.getOrDefault(fallback)

fun Color.toHex(): String = String.format("#%06x", (android.graphics.Color.argb(alpha, red, green, blue)) and 0xFFFFFF)

data class ChipColors(val background: Color, val content: Color, val filled: Boolean)

/**
 * Same rules as getChipStyle() in frontend/js/utils.js: filled chips use the chosen colours; unfilled
 * chips use the colour as text, falling back to a neutral text colour when it would be unreadable on
 * the current surface (e.g. white text in light mode).
 */
fun chipColors(
    p: Tokens.Palette,
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
    return if (filled) ChipColors(bg, fg, true) else ChipColors(Color.Transparent, readableOn(bg, p.card, p.textSecondary), false)
}

private fun readableOn(color: Color, surface: Color, fallback: Color): Color {
    val a = color.luminance()
    val b = surface.luminance()
    val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    return if (contrast >= 2.2f) color else fallback
}

@Composable
fun Chip(
    text: String,
    colors: ChipColors,
    modifier: Modifier = Modifier,
    label: String? = null,
    pill: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (pill) 50 else 5)
    Row(
        modifier = modifier
            .height(22.dp)
            .background(colors.background, shape)
            .border(1.dp, LocalPalette.current.chipEdge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (pill) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text("$label: ", color = colors.content.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Text(text, color = colors.content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun PriorityBadge(priority: Int, modifier: Modifier = Modifier) {
    if (priority <= 0) return
    val p = palette
    val (fg, bg, label) = if (priority >= 2) Triple(p.urgent, p.urgentSoft, "Urgent") else Triple(p.high, p.highSoft, "High")
    IconChip(dev.libinfaby.tasks.ui.theme.TasksIcons.Flag, label, fg, bg, modifier)
}

@Composable
fun IconChip(icon: ImageVector, text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(22.dp).background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Meta text with a small leading icon (date, reminder…). */
@Composable
fun MetaItem(icon: ImageVector, text: String, color: Color, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1)
        trailing?.invoke()
    }
}
