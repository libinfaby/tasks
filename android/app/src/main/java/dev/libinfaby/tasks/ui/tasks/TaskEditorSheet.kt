package dev.libinfaby.tasks.ui.tasks

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.FieldLabel
import dev.libinfaby.tasks.ui.components.PickerField
import dev.libinfaby.tasks.ui.components.SelectField
import dev.libinfaby.tasks.ui.components.TimePickerDialogFor
import dev.libinfaby.tasks.ui.components.chipColors
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tasksFieldColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.Tokens
import dev.libinfaby.tasks.ui.theme.palette
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(vm: TaskEditorViewModel, onDismiss: () -> Unit) {
    val p = palette
    val draft by vm.draft.collectAsStateWithLifecycle()
    val tagTypes by vm.tagTypes.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickDate by remember { mutableStateOf(false) }
    var pickReminderDate by remember { mutableStateOf(false) }
    var pickReminderTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = p.card,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.imePadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (draft.isEdit) "Edit task" else "New task", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(TasksIcons.X, "Close", tint = p.textTertiary) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { v -> vm.update { it.copy(title = v) } },
                    placeholder = { Text("What needs to be done?") },
                    label = { Text("Title") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = tasksFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.details,
                    onValueChange = { v -> vm.update { it.copy(details = v) } },
                    placeholder = { Text("Add more details…") },
                    label = { Text("Details") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = tasksFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column {
                    FieldLabel("Date")
                    PickerField(
                        value = draft.date?.let { Dates.longLabel(it) }.orEmpty(),
                        placeholder = "No date",
                        icon = TasksIcons.Calendar,
                        onClick = { pickDate = true },
                        onClear = { vm.update { it.copy(date = null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column {
                    FieldLabel("Reminder")
                    PickerField(
                        value = draft.reminder?.let { r ->
                            Dates.formatReminder(r.atZone(java.time.ZoneId.systemDefault()).toInstant().toString())
                        }.orEmpty(),
                        placeholder = "No reminder",
                        icon = TasksIcons.Bell,
                        onClick = { pickReminderDate = true },
                        onClear = { vm.update { it.copy(reminder = null, repeat = null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (draft.reminder != null) {
                        Spacer(Modifier.height(10.dp))
                        SelectField(
                            value = draft.repeat,
                            options = listOf<RepeatRule?>(null) + RepeatRule.entries,
                            label = { it?.label ?: "Does not repeat" },
                            onSelect = { r -> vm.update { it.copy(repeat = r) } },
                            leading = { Icon(TasksIcons.Repeat, null, tint = p.textTertiary, modifier = Modifier.padding(end = 8.dp).size(16.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Column {
                    FieldLabel("Priority")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Normal", "High", "Urgent").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = draft.priority == i,
                                onClick = { vm.update { it.copy(priority = i) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                icon = {},
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = when (i) { 2 -> p.urgentSoft; 1 -> p.highSoft; else -> p.accentSoft },
                                    activeContentColor = when (i) { 2 -> p.urgent; 1 -> p.high; else -> p.accentText },
                                    inactiveContainerColor = p.card,
                                    activeBorderColor = p.borderStrong,
                                    inactiveBorderColor = p.borderStrong,
                                ),
                            ) { Text(label, fontSize = 13.sp) }
                        }
                    }
                }

                Column {
                    FieldLabel("Group")
                    SelectField(
                        value = groups.firstOrNull { it.id == draft.groupId },
                        options = listOf<GroupDto?>(null) + groups,
                        label = { it?.name ?: "No group" },
                        onSelect = { g -> vm.update { it.copy(groupId = g?.id) } },
                        leading = { g ->
                            if (g != null) Box(Modifier.padding(end = 8.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(parseHex(g.color)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column {
                    FieldLabel("Tags")
                    TagSelector(tagTypes, draft.tagIds, vm::toggleTag, onCreate = vm::createTag)
                }

                Column {
                    FieldLabel("Subtasks")
                    SubtaskEditor(draft.subtasks, tagTypes, vm)
                }

                draft.error?.let { Text(it, color = p.danger, style = MaterialTheme.typography.bodySmall) }
            }

            // Footer
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Row(
                Modifier.fillMaxWidth().background(p.bgSubtle).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (draft.isEdit) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !draft.saving) {
                        Icon(TasksIcons.Trash, null, tint = p.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete", color = p.danger)
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss, border = BorderStroke(1.dp, p.borderStrong), shape = RoundedCornerShape(6.dp)) {
                    Text("Cancel", color = p.textPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.save(onDismiss) },
                    enabled = !draft.saving,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
                ) { Text(if (draft.saving) "Saving…" else if (draft.isEdit) "Save changes" else "Create task") }
            }
        }
    }

    if (pickDate) DatePickerDialogFor(draft.date, onDismiss = { pickDate = false }) { d -> vm.update { it.copy(date = d) } }
    if (pickReminderDate) {
        DatePickerDialogFor(draft.reminder?.toLocalDate() ?: draft.date, onDismiss = { pickReminderDate = false }) { d ->
            val time = draft.reminder?.toLocalTime() ?: LocalTime.of(9, 0)
            pickReminderTime = LocalDateTime.of(d, time)
        }
    }
    pickReminderTime?.let { pending ->
        TimePickerDialogFor(pending.toLocalTime(), onDismiss = { pickReminderTime = null }) { t ->
            vm.update { it.copy(reminder = LocalDateTime.of(pending.toLocalDate(), t)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete task?", "\"${draft.title}\" will be deleted.", "Delete", onDismiss = { confirmDelete = false }) {
            vm.delete(onDismiss)
        }
    }
}

/** Tag options grouped by type: outlined when off, filled when on (as on the web form). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(tagTypes: List<TagTypeDto>, selected: Set<Long>, onToggle: (Long) -> Unit, onCreate: ((TagTypeDto, String) -> Unit)? = null) {
    val p = palette
    var creatingIn by remember { mutableStateOf<TagTypeDto?>(null) }
    if (tagTypes.isEmpty()) {
        Text("No tag types yet — create them under Manage › Tags.", color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tagTypes.forEach { type ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.textTertiary, modifier = Modifier.weight(1f))
                    if (onCreate != null) {
                        Text(
                            "+ New",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = p.accentText,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { creatingIn = type }.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (type.tags.isEmpty()) {
                    Text("No tags yet", fontSize = 12.sp, color = p.textTertiary)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        type.tags.forEach { tag ->
                            TagOption(tag.name, chipColors(p, tag.color, tag.fgColor, tag.hasBg, type.color, type.fgColor, type.hasBg), tag.id in selected, p) {
                                onToggle(tag.id)
                            }
                        }
                    }
                }
            }
        }
    }
    creatingIn?.let { type ->
        NameDialog("New ${type.name} tag", "Tag name", onDismiss = { creatingIn = null }) { name -> onCreate?.invoke(type, name) }
    }
}

@Composable
private fun TagOption(name: String, colors: dev.libinfaby.tasks.ui.components.ChipColors, selected: Boolean, p: Tokens.Palette, onClick: () -> Unit) {
    val primary = if (colors.filled) colors.background.let { bg ->
        // An outlined chip in a near-white colour would vanish on light surfaces
        if (kotlin.math.abs(bg.luminanceCompat() - p.card.luminanceCompat()) < 0.15f) p.textSecondary else bg
    } else colors.content
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) colors.background.takeIf { colors.filled } ?: p.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else primary.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) (if (colors.filled) colors.content else p.accentText) else primary.copy(alpha = 0.8f),
        )
    }
}

private fun Color.luminanceCompat() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun SubtaskEditor(rows: List<SubtaskRow>, tagTypes: List<TagTypeDto>, vm: TaskEditorViewModel) {
    val p = palette
    var tagging by remember { mutableStateOf<Long?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, p.borderStrong, RoundedCornerShape(6.dp))
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = row.title,
                        onValueChange = { v -> vm.editSubtask(row.key) { it.copy(title = v) } },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.textPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                                if (row.title.isEmpty()) Text("Subtask title…", color = p.textTertiary, style = MaterialTheme.typography.bodyMedium)
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (tagTypes.isNotEmpty()) {
                        IconButton(onClick = { tagging = if (tagging == row.key) null else row.key }) {
                            Icon(TasksIcons.Tag, "Subtask tags", tint = if (row.tagIds.isNotEmpty()) p.accentText else p.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = { vm.removeSubtask(row.key) }) {
                        Icon(TasksIcons.X, "Remove subtask", tint = p.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
                if (tagging == row.key) {
                    Box(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 6.dp)) {
                        TagSelector(tagTypes, row.tagIds, onToggle = { id ->
                            vm.editSubtask(row.key) { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }
                        })
                    }
                }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.addSubtask() }.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TasksIcons.Plus, null, tint = p.textSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add subtask", color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Small dialog asking for a single name. */
@Composable
fun NameDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                colors = tasksFieldColors(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
