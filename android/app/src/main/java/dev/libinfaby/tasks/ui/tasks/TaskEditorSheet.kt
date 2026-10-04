package dev.libinfaby.tasks.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.PRIORITY_URGENT
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.ui.components.ChipColors
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.ConnectedColumn
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.ConnectedToggleGroup
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.FieldLabel
import dev.libinfaby.tasks.ui.components.ListRow
import dev.libinfaby.tasks.ui.components.TimePickerDialogFor
import dev.libinfaby.tasks.ui.components.ToggleColors
import dev.libinfaby.tasks.ui.components.chipColors
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tonalColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(vm: TaskEditorViewModel, onDismiss: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val tagTypes by vm.tagTypes.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickDate by remember { mutableStateOf(false) }
    var pickReminderDate by remember { mutableStateOf(false) }
    var pickReminderTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingTags by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.imePadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (draft.isEdit) "Edit task" else "New task", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    GroupPicker(groups, groups.firstOrNull { it.id == draft.groupId }) { g -> vm.update { it.copy(groupId = g?.id) } }
                }

                TitleAndDetails(
                    title = draft.title,
                    details = draft.details,
                    autofocus = !draft.isEdit,
                    onTitle = { v -> vm.update { it.copy(title = v) } },
                    onDetails = { v -> vm.update { it.copy(details = v) } },
                )

                Column {
                    FieldLabel("When")
                    WhenChips(draft.date, onPick = { d -> vm.update { it.copy(date = d) } }, onCustom = { pickDate = true })
                }

                ReminderCard(
                    reminder = draft.reminder,
                    repeat = draft.repeat,
                    onEnable = {
                        val day = draft.date ?: Dates.today()
                        val now = LocalDateTime.now()
                        // An hour from now (on the hour) for today, otherwise 9 AM
                        val time = if (day == now.toLocalDate()) now.plusHours(1).truncatedTo(ChronoUnit.HOURS).toLocalTime() else LocalTime.of(9, 0)
                        pickReminderTime = LocalDateTime.of(day, time)
                    },
                    onDisable = { vm.update { it.copy(reminder = null, repeat = null) } },
                    onEdit = { pickReminderDate = true },
                    onRepeat = { r -> vm.update { it.copy(repeat = r) } },
                )

                Column {
                    FieldLabel("Priority")
                    ConnectedToggleGroup(
                        options = listOf(0, PRIORITY_URGENT),
                        selected = draft.priority,
                        onSelect = { p -> vm.update { it.copy(priority = p) } },
                        label = { if (it == 0) "Normal" else "Urgent" },
                        icon = { p, sel ->
                            when {
                                p == 0 -> TasksIcons.Block
                                sel -> TasksIcons.FireFilled
                                else -> TasksIcons.Fire
                            }
                        },
                        selectedColors = { p ->
                            if (p == 0) ToggleColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            else ToggleColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                        },
                        height = 52.dp,
                    )
                }

                Column {
                    FieldLabel("Tags")
                    SelectedTags(tagTypes, draft.tagIds, onRemove = vm::toggleTag, onAdd = { choosingTags = true })
                }

                Column {
                    FieldLabel("Subtasks")
                    SubtaskEditor(draft.subtasks, tagTypes, vm)
                }

                draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }

            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (draft.isEdit) {
                    FilledTonalIconButton(
                        onClick = { confirmDelete = true },
                        enabled = !draft.saving,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.size(48.dp),
                    ) { Icon(TasksIcons.Delete, "Delete task") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.save(onDismiss) },
                    enabled = !draft.saving,
                    contentPadding = PaddingValues(start = 22.dp, end = 28.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(TasksIcons.Check, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (draft.saving) "Saving…" else if (draft.isEdit) "Save" else "Add task", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (choosingTags) TagPickerSheet(tagTypes, draft.tagIds, vm::toggleTag, vm::createTag) { choosingTags = false }
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

@Composable
private fun TitleAndDetails(title: String, details: String, autofocus: Boolean, onTitle: (String) -> Unit, onDetails: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autofocus) runCatching { focus.requestFocus() } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            if (title.isEmpty()) Text("What needs doing?", style = MaterialTheme.typography.headlineMedium, color = muted.copy(alpha = 0.6f))
            BasicTextField(
                value = title,
                onValueChange = onTitle,
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Title" },
            )
        }
        // A roomy box for notes: five lines tall to start, growing as they write
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(TasksIcons.Notes, null, tint = muted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (details.isEmpty()) Text("Add details", style = MaterialTheme.typography.bodyLarge, color = muted)
                    BasicTextField(
                        value = details,
                        onValueChange = onDetails,
                        minLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Details" },
                    )
                }
            }
        }
    }
}

/** Header chip that picks the task's group, tinted with the group's colour. */
@Composable
private fun GroupPicker(groups: List<GroupDto>, current: GroupDto?, onPick: (GroupDto?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val tint = current?.let { tonalColors(parseHex(it.color)) }
        ?: ToggleColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = tint.container,
            contentColor = tint.content,
            modifier = Modifier.height(40.dp).semantics { contentDescription = "Group: ${current?.name ?: "none"}. Change group" },
        ) {
            Row(Modifier.padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) GroupDot(current.color) else Icon(TasksIcons.Group, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(current?.name ?: "No group", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(TasksIcons.DropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            DropdownMenuItem(text = { Text("No group") }, leadingIcon = { Icon(TasksIcons.Block, null) }, onClick = { onPick(null); open = false })
            groups.forEach { g ->
                DropdownMenuItem(text = { Text(g.name) }, leadingIcon = { GroupDot(g.color) }, onClick = { onPick(g); open = false })
            }
        }
    }
}

@Composable
private fun GroupDot(color: String?) {
    Box(Modifier.size(12.dp).background(parseHex(color), RoundedCornerShape(6.dp, 6.dp, 6.dp, 2.dp)))
}

private enum class WhenOption(val label: String, val icon: ImageVector?) {
    NONE("No date", TasksIcons.EventBusy),
    TODAY("Today", null),
    TOMORROW("Tomorrow", null),
    CUSTOM("Pick date", TasksIcons.EditCalendar),
}

/** One-tap dates. The selected chip rounds off into a pill; a custom date shows on the last chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenChips(date: LocalDate?, onPick: (LocalDate?) -> Unit, onCustom: () -> Unit) {
    val today = Dates.today()
    val presets = mapOf(
        WhenOption.TODAY to today,
        WhenOption.TOMORROW to today.plusDays(1),
    )
    val selected = when (date) {
        null -> WhenOption.NONE
        else -> presets.entries.firstOrNull { it.value == date }?.key ?: WhenOption.CUSTOM
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WhenOption.entries.forEach { option ->
            val isSelected = option == selected
            val corner by animateDpAsState(if (isSelected) 18.dp else 10.dp, label = "when")
            val label = if (option == WhenOption.CUSTOM && isSelected && date != null) Dates.label(date) else option.label
            FilterChip(
                selected = isSelected,
                onClick = {
                    when (option) {
                        WhenOption.NONE -> onPick(null)
                        WhenOption.CUSTOM -> onCustom()
                        else -> onPick(presets.getValue(option))
                    }
                },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = when {
                    isSelected -> ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(18.dp)) })
                    option.icon != null -> ({ Icon(option.icon, null, modifier = Modifier.size(18.dp)) })
                    else -> null
                },
                shape = RoundedCornerShape(corner),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

/** Reminder on/off with its time, and (when on) the repeat rule, as one connected group. */
@Composable
private fun ReminderCard(
    reminder: LocalDateTime?,
    repeat: RepeatRule?,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onEdit: () -> Unit,
    onRepeat: (RepeatRule?) -> Unit,
) {
    val on = reminder != null
    val label = reminder?.let { "${Dates.label(it.toLocalDate())} · ${Dates.formatTime(it.atZone(ZoneId.systemDefault()).toInstant())}" }
    var repeatMenu by remember { mutableStateOf(false) }
    ConnectedColumn {
        ConnectedItem(0, if (on) 2 else 1, onClick = if (on) onEdit else onEnable) {
            ListRow(
                title = "Reminder",
                supporting = label ?: "Off",
                icon = TasksIcons.Alarm,
                iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Switch(
                    checked = on,
                    onCheckedChange = { if (it) onEnable() else onDisable() },
                    thumbContent = if (on) ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(16.dp)) }) else null,
                )
            }
        }
        AnimatedVisibility(on) {
            Box {
                ConnectedItem(1, 2, onClick = { repeatMenu = true }) {
                    ListRow(title = "Repeat", supporting = repeat?.label ?: "Does not repeat", icon = TasksIcons.Repeat, iconShape = CircleShape) {
                        Icon(TasksIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }, shape = RoundedCornerShape(16.dp)) {
                    (listOf<RepeatRule?>(null) + RepeatRule.entries).forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r?.label ?: "Does not repeat") },
                            trailingIcon = if (r == repeat) ({ Icon(TasksIcons.Check, null) }) else null,
                            onClick = { onRepeat(r); repeatMenu = false },
                        )
                    }
                }
            }
        }
    }
}

/** The task's tags as removable chips, plus an Add tag chip that opens the picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedTags(tagTypes: List<TagTypeDto>, selected: Set<Long>, onRemove: (Long) -> Unit, onAdd: () -> Unit) {
    val chosen = tagTypes.flatMap { type -> type.tags.filter { it.id in selected }.map { type to it } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chosen.forEach { (type, tag) ->
            val colors = chipColors(tag.color, tag.fgColor, tag.hasBg, type.color, type.fgColor, type.hasBg)
            val container = if (colors.filled) colors.background else MaterialTheme.colorScheme.secondaryContainer
            val content = if (colors.filled) colors.content else MaterialTheme.colorScheme.onSecondaryContainer
            Surface(shape = RoundedCornerShape(10.dp), color = container, contentColor = content, modifier = Modifier.height(36.dp)) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.name, style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { onRemove(tag.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(TasksIcons.Close, "Remove tag ${tag.name}", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Surface(
            onClick = onAdd,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.height(36.dp),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TasksIcons.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (chosen.isEmpty()) "Add tags" else "Add tag", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    tagTypes: List<TagTypeDto>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: (TagTypeDto, String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tags", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(16.dp))
            TagSelector(tagTypes, selected, onToggle, onCreate)
        }
    }
}

/** Tag options grouped by type: outlined when off, filled when on (as on the web form). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(tagTypes: List<TagTypeDto>, selected: Set<Long>, onToggle: (Long) -> Unit, onCreate: ((TagTypeDto, String) -> Unit)? = null) {
    var creatingIn by remember { mutableStateOf<TagTypeDto?>(null) }
    if (tagTypes.isEmpty()) {
        Text("No tag types yet. Create them from the menu, under Tags.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tagTypes.forEach { type ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (onCreate != null) {
                        TextButton(onClick = { creatingIn = type }) {
                            Icon(TasksIcons.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (type.tags.isEmpty()) {
                    Text("No tags yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        type.tags.forEach { tag ->
                            TagOption(tag.name, chipColors(tag.color, tag.fgColor, tag.hasBg, type.color, type.fgColor, type.hasBg), tag.id in selected) {
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
private fun TagOption(name: String, colors: ChipColors, selected: Boolean, onClick: () -> Unit) {
    val corner by animateDpAsState(if (selected) 18.dp else 10.dp, label = "tag")
    val selectedContainer = if (colors.filled) colors.background else MaterialTheme.colorScheme.secondaryContainer
    val selectedContent = if (colors.filled) colors.content else MaterialTheme.colorScheme.onSecondaryContainer
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(name, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(18.dp)) }) else null,
        shape = RoundedCornerShape(corner),
        colors = FilterChipDefaults.filterChipColors(
            labelColor = if (colors.filled) MaterialTheme.colorScheme.onSurfaceVariant else colors.content,
            selectedContainerColor = selectedContainer,
            selectedLabelColor = selectedContent,
            selectedLeadingIconColor = selectedContent,
        ),
        modifier = Modifier.height(36.dp),
    )
}

@Composable
private fun SubtaskEditor(rows: List<SubtaskRow>, tagTypes: List<TagTypeDto>, vm: TaskEditorViewModel) {
    var tagging by remember { mutableStateOf<Long?>(null) }
    val count = rows.size + 1
    ConnectedColumn {
        rows.forEachIndexed { i, row ->
            ConnectedItem(i, count) {
                Column {
                    Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                            if (row.title.isEmpty()) Text("Subtask", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            BasicTextField(
                                value = row.title,
                                onValueChange = { v -> vm.editSubtask(row.key) { it.copy(title = v) } },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (tagTypes.isNotEmpty()) {
                            IconButton(onClick = { tagging = if (tagging == row.key) null else row.key }) {
                                Icon(
                                    if (row.tagIds.isNotEmpty()) TasksIcons.TagFilled else TasksIcons.Tag,
                                    "Subtask tags",
                                    tint = if (row.tagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { vm.removeSubtask(row.key) }) {
                            Icon(TasksIcons.Close, "Remove subtask", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (tagging == row.key) {
                        Box(Modifier.padding(start = 18.dp, end = 12.dp, bottom = 14.dp)) {
                            TagSelector(tagTypes, row.tagIds, onToggle = { id ->
                                vm.editSubtask(row.key) { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }
                            })
                        }
                    }
                }
            }
        }
        ConnectedItem(rows.size, count, onClick = { vm.addSubtask() }) {
            Row(Modifier.height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TasksIcons.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Add subtask", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
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
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
