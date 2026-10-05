package dev.libinfaby.tasks.ui.daily

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.DailyLogDto
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.ConnectedToggleGroup
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.EmptyState
import dev.libinfaby.tasks.ui.components.Scallop
import dev.libinfaby.tasks.ui.components.SectionHeader
import dev.libinfaby.tasks.ui.components.ToggleColors
import dev.libinfaby.tasks.ui.tasks.NameDialog
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject


data class DailyState(
    val date: LocalDate = LocalDate.now(),
    val from: LocalDate = LocalDate.now().minusDays(6),
    val to: LocalDate = LocalDate.now(),
    val entries: List<DailyLogDto> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class DailyViewModel @Inject constructor(private val repo: TasksRepository) : ViewModel() {
    private val _state = MutableStateFlow(DailyState())
    val state: StateFlow<DailyState> = _state
    private var report = false

    fun load(report: Boolean) {
        this.report = report
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val s = _state.value
            runCatching { if (report) repo.dailyLogs(s.from, s.to) else repo.dailyLogs(s.date) }
                .onSuccess { logs -> _state.update { it.copy(entries = logs, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, message = e.userMessage()) } }
        }
    }

    fun setDate(d: LocalDate) { _state.update { it.copy(date = d) }; load(false) }
    fun setRange(from: LocalDate, to: LocalDate) { _state.update { it.copy(from = from, to = to) }; load(true) }

    fun add(text: String) = mutate { repo.addDailyLog(_state.value.date, text) }
    fun edit(id: Long, text: String) = mutate { repo.updateDailyLog(id, text) }
    fun delete(id: Long) = mutate { repo.deleteDailyLog(id) }
    fun say(message: String) = _state.update { it.copy(message = message) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun mutate(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onSuccess { load(report) }.onFailure { e -> _state.update { it.copy(message = e.userMessage()) } }
    }
}

private fun bullets(entries: List<DailyLogDto>) = entries.joinToString("\n") { "• ${it.text}" }


@Composable
fun DailyScreen(vm: DailyViewModel, report: Boolean, onReport: (Boolean) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DailyLogDto?>(null) }
    var deleting by remember { mutableStateOf<DailyLogDto?>(null) }
    var pick by remember { mutableStateOf<String?>(null) } // "date" | "from" | "to"

    LaunchedEffect(report) { vm.load(report) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val copy: (List<DailyLogDto>) -> Unit = { entries ->
        if (entries.isEmpty()) vm.say("Nothing to copy")
        else scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Daily tasks", bullets(entries))))
            vm.say("Copied ${entries.size} task${if (entries.size == 1) "" else "s"}")
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
        ) {
            item(key = "title") {
                Row(Modifier.padding(start = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily log", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                    if (!report) IconButton(onClick = { copy(state.entries) }) { Icon(TasksIcons.Copy, "Copy the day's entries") }
                }
            }
            item(key = "mode") {
                ConnectedToggleGroup(
                    options = listOf(false, true),
                    selected = report,
                    onSelect = onReport,
                    label = { if (it) "Report" else "Day" },
                    icon = { r, _ -> if (r) TasksIcons.Report else TasksIcons.Today },
                )
            }
            if (report) {
                item(key = "range") { RangeBar(state, onPick = { pick = it }, onRange = vm::setRange) }
            } else {
                item(key = "day") { DayBar(state.date, onShift = { vm.setDate(state.date.plusDays(it)) }, onPick = { pick = "date" }) }
                item(key = "add") { AddRow(onAdd = vm::add) }
            }

            when {
                state.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.entries.isEmpty() -> item(key = "empty") {
                    if (report) EmptyState(TasksIcons.Report, "Nothing in this range", "Entries you log will add up here.")
                    else EmptyState(TasksIcons.DailyLogFilled, "Nothing logged yet", "Finish a task and tap Log it, or add one above.")
                }
                report -> {
                    // The API returns newest day first
                    state.entries.groupBy { it.logDate }.forEach { (day, entries) ->
                        item(key = "d-$day") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader(
                                    Dates.parseDate(day)?.let(Dates::longLabel) ?: day,
                                    Modifier.weight(1f),
                                    icon = TasksIcons.Calendar,
                                    tile = ToggleColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                                    tileShape = RoundedCornerShape(10.dp),
                                    trailing = "${entries.size}",
                                )
                                IconButton(onClick = { copy(entries) }, modifier = Modifier.padding(top = 10.dp)) { Icon(TasksIcons.Copy, "Copy this day", modifier = Modifier.size(20.dp)) }
                            }
                        }
                        entries(entries, "d-$day", onEdit = { editing = it }, onDelete = { deleting = it })
                    }
                }
                else -> {
                    item(key = "gap") { Spacer(Modifier.height(20.dp)) }
                    entries(state.entries, "day", onEdit = { editing = it }, onDelete = { deleting = it })
                }
            }
        }
    }

    editing?.let { e -> NameDialog("Edit entry", "Entry", e.text, onDismiss = { editing = null }) { vm.edit(e.id, it) } }
    deleting?.let { e -> ConfirmDialog("Delete entry?", e.text, "Delete", onDismiss = { deleting = null }) { vm.delete(e.id) } }
    when (pick) {
        "date" -> DatePickerDialogFor(state.date, onDismiss = { pick = null }) { vm.setDate(it) }
        "from" -> DatePickerDialogFor(state.from, onDismiss = { pick = null }) { vm.setRange(it, maxOf(it, state.to)) }
        "to" -> DatePickerDialogFor(state.to, onDismiss = { pick = null }) { vm.setRange(minOf(state.from, it), it) }
    }
}

/** Previous day / the date (opens a picker) / next day, as one pill. */
@Composable
private fun DayBar(date: LocalDate, onShift: (Long) -> Unit, onPick: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(TasksIcons.ChevronLeft, "Previous day") }
            TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text(if (date == Dates.today()) "Today" else Dates.longLabel(date), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { onShift(1) }) { Icon(TasksIcons.ChevronRight, "Next day") }
        }
    }
}

@Composable
private fun RangeBar(state: DailyState, onPick: (String) -> Unit, onRange: (LocalDate, LocalDate) -> Unit) {
    val today = LocalDate.now()
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onPick("from") }, modifier = Modifier.weight(1f)) { Text(Dates.label(state.from), style = MaterialTheme.typography.titleMedium) }
                Icon(TasksIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onPick("to") }, modifier = Modifier.weight(1f)) { Text(Dates.label(state.to), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7L to "Last 7 days", 30L to "Last 30 days").forEach { (days, label) ->
                val selected = state.to == today && state.from == today.minusDays(days - 1)
                FilterChip(
                    selected = selected,
                    onClick = { onRange(today.minusDays(days - 1), today) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(18.dp)) }) else null,
                    shape = RoundedCornerShape(if (selected) 16.dp else 8.dp),
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
}

/** The input stays put and clears right away, so quick consecutive entries aren't lost (as on web). */
@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("What did you get done?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New entry" },
                )
            }
            FilledIconButton(onClick = submit, modifier = Modifier.size(48.dp)) { Icon(TasksIcons.Add, "Add entry") }
        }
    }
}

private fun LazyListScope.entries(entries: List<DailyLogDto>, keyPrefix: String, onEdit: (DailyLogDto) -> Unit, onDelete: (DailyLogDto) -> Unit) {
    entries.forEachIndexed { i, e ->
        item(key = "$keyPrefix-${e.id}") {
            ConnectedItem(i, entries.size, modifier = Modifier.padding(bottom = 3.dp).animateItem(), onClick = { onEdit(e) }) {
                Row(Modifier.padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, Scallop))
                    Spacer(Modifier.width(14.dp))
                    Text(e.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    IconButton(onClick = { onDelete(e) }) { Icon(TasksIcons.Delete, "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}
