package dev.libinfaby.tasks.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A closed circle whose radius swings by [amplitude] around [radius], [lobes] times per turn. */
private fun wobblePath(center: Offset, radius: Float, amplitude: Float, lobes: Int, steps: Int = lobes * 24) = Path().apply {
    for (i in 0..steps) {
        val a = -PI / 2 + i * 2 * PI / steps
        val r = radius + amplitude * cos(lobes * (a + PI / 2))
        val x = center.x + (r * cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** M3 Expressive's soft-burst "cookie": a circle with [lobes] gentle bumps. */
class ScallopShape(private val lobes: Int = 8, private val depth: Float = 0.12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val outer = min(size.width, size.height) / 2
        val radius = outer / (1 + depth)
        return Outline.Generic(wobblePath(Offset(size.width / 2, size.height / 2), radius, radius * depth, lobes))
    }
}

val Scallop = ScallopShape()

/** Shape of item [index] of [count] in a connected list: big outer corners, small inner ones. */
fun connectedShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

/** Vertical stack for connected items, with the expressive 3dp seam between them. */
@Composable
fun ConnectedColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
}

/** One item of a connected list. Clickable when [onClick] is given. */
@Composable
fun ConnectedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val shape = connectedShape(index, count)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth(), content = content)
    } else {
        Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth(), content = content)
    }
}

/** The usual settings-style row inside a [ConnectedItem]: icon tile, two lines, trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconShape: Shape = RoundedCornerShape(14.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Surface(shape = iconShape, color = iconContainer, contentColor = iconContent, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = supportingColor)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Confetti that bursts outward once each time [trigger] changes to a new non-null value. */
@Composable
fun CompletionBurst(trigger: Any?, modifier: Modifier = Modifier) {
    val t = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            t.snapTo(0f)
            t.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.inversePrimary)
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val distance = 8.dp.toPx() + 20.dp.toPx() * p
        val alpha = 1f - p
        repeat(10) { i ->
            rotate(i * 36f + p * 40f) {
                val c = colors[i % colors.size].copy(alpha = alpha)
                val pos = Offset(center.x, center.y - distance)
                val s = (1f - 0.6f * p)
                if (i % 2 == 0) {
                    drawCircle(c, 3.dp.toPx() * s, pos)
                } else {
                    val w = 3.dp.toPx() * s
                    val h = 7.dp.toPx() * s
                    drawRoundRect(c, Offset(pos.x - w / 2, pos.y - h / 2), Size(w, h), CornerRadius(w / 2))
                }
            }
        }
    }
}

/** Colours for one option of a [ConnectedToggleGroup]. */
data class ToggleColors(val container: Color, val content: Color)

/**
 * M3 Expressive connected button group with single selection. The selected button morphs into a
 * full pill; the others keep big outer and small inner corners.
 */
@Composable
fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T, Boolean) -> ImageVector)? = null,
    selectedColors: @Composable (T) -> ToggleColors = {
        ToggleColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    },
    height: Dp = 48.dp,
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, option ->
            val isSelected = option == selected
            val full = height / 2
            val bouncy = spring<Dp>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
            val start by animateDpAsState(if (isSelected || i == 0) full else 8.dp, bouncy, label = "start")
            val end by animateDpAsState(if (isSelected || i == options.lastIndex) full else 8.dp, bouncy, label = "end")
            val colors = if (isSelected) selectedColors(option)
            else ToggleColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = colors.container,
                contentColor = colors.content,
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(option) },
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    if (icon != null) {
                        Icon(icon(option, isSelected), null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Section title with a small shaped icon tile and an optional trailing note. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tile: ToggleColors = ToggleColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
    tileShape: Shape = RoundedCornerShape(15.dp),
    trailing: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Surface(shape = tileShape, color = tile.container, contentColor = tile.content, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Friendly empty state: a tilted icon tile, a headline and a line of help. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(88.dp).rotate(-8f),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(44.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
