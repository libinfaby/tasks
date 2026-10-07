package dev.libinfaby.tasks.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.data.api.SubtaskDto
import dev.libinfaby.tasks.data.api.TagDto
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.ui.theme.TasksIcons

// Client first, then the kind chip (Issue/Requirement/Modification), then Project, Via, then the rest (as on web).
private val KIND_TAG_NAMES = setOf("issue", "requirement", "modification")
private val TAG_ORDER = listOf("client", "kind", "project", "via")

private fun TagDto.isKind() = name.trim().lowercase() in KIND_TAG_NAMES
private fun TagDto.rank(): Int {
    val i = TAG_ORDER.indexOf(if (isKind()) "kind" else typeName.orEmpty().lowercase())
    return if (i == -1) TAG_ORDER.size else i
}

/** Ring colour: urgent takes the error role. Any non-zero priority is urgent (High was retired). */
@Composable
fun priorityColor(priority: Int): Color = if (priority > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

/**
 * Round checkbox whose ring takes the priority colour. Checking it fills into a solid circle with a
 * bouncy pop, a burst of confetti and a confirm haptic.
 */
@Composable
fun TaskCheckbox(checked: Boolean, priority: Int, onToggle: () -> Unit, label: String) {
    val haptics = LocalHapticFeedback.current
    var bursts by remember { mutableIntStateOf(0) }
    val pop by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "pop",
    )
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        // Outside the clipped touch target so the confetti can fly past it
        CompletionBurst(trigger = bursts.takeIf { it > 0 }, modifier = Modifier.requiredSize(72.dp))
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(role = Role.Checkbox) {
                    if (!checked) {
                        bursts++
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    onToggle()
                }
                .semantics { contentDescription = if (checked) "Mark $label not done" else "Complete $label" },
            contentAlignment = Alignment.Center,
        ) { CheckboxFace(pop, priority) }
    }
}

@Composable
private fun CheckboxFace(pop: Float, priority: Int) {
    Box(contentAlignment = Alignment.Center) {
        if (pop < 0.5f) {
            Box(Modifier.size(24.dp).alpha(1f - pop * 2).border(2.5.dp, priorityColor(priority), CircleShape))
        }
        if (pop > 0f) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp).graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                    rotationZ = (1f - pop) * -60f
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(TasksIcons.Check, null, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

/** Rounded-square checkbox for subtasks, so they read differently from tasks. */
@Composable
private fun SubtaskCheckbox(checked: Boolean, onToggle: () -> Unit, label: String) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (checked) "Mark $label not done" else "Complete $label" },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, shape), contentAlignment = Alignment.Center) {
                Icon(TasksIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(20.dp).border(2.dp, MaterialTheme.colorScheme.outline, shape))
        }
    }
}

/** A task as one item of a connected list ([index] of [count]). */
@Composable
fun TaskRow(
    task: TaskDto,
    index: Int,
    count: Int,
    showGroup: Boolean,
    showDate: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onToggleSubtask: (SubtaskDto) -> Unit,
    onTagClick: (TagDto) -> Unit,
    onLogDaily: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = task.completed
    val fade by animateFloatAsState(if (done) 0.62f else 1f, label = "fade")
    ConnectedItem(index, count, modifier = modifier, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCheckbox(done, task.priority, onToggle, label = task.title)
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp).alpha(fade)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                if (!task.details.isNullOrBlank()) {
                    Text(
                        task.details.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TaskMeta(task, showGroup, showDate, onTagClick)
                if (task.subtasks.isNotEmpty()) SubtaskPreview(task.subtasks, onToggleSubtask)
            }
            // A done task can be logged any time, not only from the snackbar
            if (done) {
                IconButton(onClick = onLogDaily, modifier = Modifier.padding(start = 4.dp, top = 4.dp).offset(x = 8.dp).size(40.dp)) {
                    Icon(
                        TasksIcons.DailyLog,
                        contentDescription = "Add ${task.title} to today's daily log",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMeta(task: TaskDto, showGroup: Boolean, showDate: Boolean, onTagClick: (TagDto) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val group = task.group?.takeIf { showGroup }
    val date = task.date?.takeIf { showDate }
    val repeat = RepeatRule.fromWire(task.reminderRepeat)
    val hasMeta = task.priority > 0 || group != null || date != null || task.reminder != null || task.tags.isNotEmpty()
    if (!hasMeta) return
    FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Urgency is spelled out too, so it never rests on the ring colour alone
        if (task.priority > 0) MetaItem(TasksIcons.FireFilled, "Urgent", MaterialTheme.colorScheme.error)
        if (date != null) {
            val color = when {
                Dates.isOverdue(date) && !task.completed -> MaterialTheme.colorScheme.error
                Dates.isToday(date) -> MaterialTheme.colorScheme.primary
                else -> muted
            }
            MetaItem(TasksIcons.Calendar, Dates.formatDate(date), color)
        }
        if (task.reminder != null) MetaItem(TasksIcons.Alarm, Dates.formatReminder(task.reminder), muted)
        if (repeat != null && task.reminder != null) MetaItem(TasksIcons.Repeat, repeat.label, muted)
        if (group != null) {
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(parseHex(group.color), RoundedCornerShape(5.dp, 5.dp, 5.dp, 1.dp)))
                Spacer(Modifier.width(5.dp))
                Text(group.name, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1)
            }
        }
        task.tags.sortedBy { it.rank() }.forEach { tag ->
            Chip(tag.name, tagColors(tag.color, tag.typeColor), onClick = { onTagClick(tag) })
        }
    }
}

@Composable
private fun SubtaskPreview(subtasks: List<SubtaskDto>, onToggle: (SubtaskDto) -> Unit) {
    val done = subtasks.count { it.isCompleted != 0 }
    val progress by animateFloatAsState(done.toFloat() / subtasks.size, label = "subtasks")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    gapSize = 3.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("$done/${subtasks.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            subtasks.forEach { s ->
                val checked = s.isCompleted != 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The checkbox's touch target overhangs to the left, keeping the box aligned with the bar
                    Box(Modifier.offset(x = (-10).dp)) { SubtaskCheckbox(checked, { onToggle(s) }, s.title) }
                    Text(
                        s.title,
                        modifier = Modifier.offset(x = (-8).dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}
