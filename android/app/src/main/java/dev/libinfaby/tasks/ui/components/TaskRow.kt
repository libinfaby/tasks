package dev.libinfaby.tasks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.data.api.SubtaskDto
import dev.libinfaby.tasks.data.api.TagDto
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette

// Client first, then the kind chip (Issue/Requirement/Modification), then Project, Via, then the rest (as on web).
private val KIND_TAG_NAMES = setOf("issue", "requirement", "modification")
private val NAME_ONLY_TYPES = setOf("client", "project", "via")
private val TAG_ORDER = listOf("client", "kind", "project", "via")

private fun TagDto.isKind() = name.trim().lowercase() in KIND_TAG_NAMES
private fun TagDto.rank(): Int {
    val i = TAG_ORDER.indexOf(if (isKind()) "kind" else typeName.orEmpty().lowercase())
    return if (i == -1) TAG_ORDER.size else i
}

/** Round checkbox whose ring takes the priority colour, like the web list. */
@Composable
fun TaskCheckbox(checked: Boolean, priority: Int, onToggle: () -> Unit, size: Dp = 20.dp, square: Boolean = false, label: String = "") {
    val p = palette
    val ring = when {
        checked -> p.accent
        priority >= 2 -> p.urgent
        priority == 1 -> p.high
        else -> p.borderStrong
    }
    val shape = if (square) RoundedCornerShape(4.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(size + 16.dp) // touch target
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (checked) "Mark $label not done" else "Complete $label" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (checked) p.accent else Color.Transparent)
                .border(1.5.dp, ring, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(TasksIcons.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.65f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: TaskDto,
    showGroup: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onToggleSubtask: (SubtaskDto) -> Unit,
    onTagClick: (TagDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = palette
    val done = task.completed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheckbox(done, task.priority, onToggle, label = task.title)
        Column(modifier = Modifier.weight(1f).padding(top = 10.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
                color = if (done) p.textTertiary else p.textPrimary,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            if (!task.details.isNullOrBlank()) {
                Text(
                    task.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TaskMeta(task, showGroup, onTagClick)
            if (task.subtasks.isNotEmpty()) SubtaskPreview(task.subtasks, onToggleSubtask)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMeta(task: TaskDto, showGroup: Boolean, onTagClick: (TagDto) -> Unit) {
    val p = palette
    val group = task.group?.takeIf { showGroup }
    val repeat = RepeatRule.fromWire(task.reminderRepeat)
    val hasMeta = task.priority > 0 || group != null || task.date != null || task.reminder != null || task.tags.isNotEmpty()
    if (!hasMeta) return
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PriorityBadge(task.priority)
        if (group != null) Chip(group.name, chipColors(p, group.color, group.fgColor, group.hasBg), pill = true)
        if (task.date != null) {
            val color = when {
                Dates.isOverdue(task.date) && !task.completed -> p.danger
                Dates.isToday(task.date) -> p.accentText
                else -> p.textTertiary
            }
            MetaItem(TasksIcons.Calendar, Dates.formatDate(task.date), color)
        }
        if (task.reminder != null) {
            MetaItem(TasksIcons.Bell, Dates.formatReminder(task.reminder), p.textTertiary) {
                if (repeat != null) {
                    Spacer(Modifier.width(2.dp))
                    Icon(TasksIcons.Repeat, null, tint = p.textTertiary, modifier = Modifier.size(13.dp))
                    Text(repeat.label, color = p.textTertiary, fontSize = 12.sp)
                }
            }
        }
        task.tags.sortedBy { it.rank() }.forEach { tag ->
            val kind = tag.isKind()
            val colors = chipColors(p, tag.color, tag.fgColor, if (kind) 1 else tag.hasBg, tag.typeColor)
            val nameOnly = kind || tag.typeName.orEmpty().lowercase() in NAME_ONLY_TYPES
            Chip(tag.name, colors, label = if (nameOnly) null else tag.typeName ?: "Tag", pill = kind, onClick = { onTagClick(tag) })
        }
    }
}

@Composable
private fun SubtaskPreview(subtasks: List<SubtaskDto>, onToggle: (SubtaskDto) -> Unit) {
    val p = palette
    val done = subtasks.count { it.isCompleted != 0 }
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(p.muted.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { done.toFloat() / subtasks.size },
                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)),
                color = p.accent,
                trackColor = p.border,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text("$done/${subtasks.size}", fontSize = 12.sp, color = p.textTertiary)
        }
        subtasks.forEach { s ->
            val checked = s.isCompleted != 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 0.dp)) {
                // The checkbox's touch target overhangs to the left, keeping the box aligned with the progress bar
                Box(modifier = Modifier.offset(x = (-8).dp)) {
                    TaskCheckbox(checked, 0, { onToggle(s) }, size = 15.dp, square = true, label = s.title)
                }
                Text(
                    s.title,
                    modifier = Modifier.offset(x = (-6).dp),
                    fontSize = 13.sp,
                    color = if (checked) p.textTertiary else p.textSecondary,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}
