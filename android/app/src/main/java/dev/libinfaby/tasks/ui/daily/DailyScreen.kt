package dev.libinfaby.tasks.ui.daily

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.DailyLogDto
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.ListCard
import dev.libinfaby.tasks.ui.components.RowDivider
import dev.libinfaby.tasks.ui.components.tasksFieldColors
import dev.libinfaby.tasks.ui.tasks.NameDialog
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScreen(vm: DailyViewModel, report: Boolean, onOpenDrawer: () -> Unit) {
    val p = palette
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

    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
                    navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(TasksIcons.Menu, "Menu", tint = p.textSecondary) } },
                    title = { Text(if (report) "Daily Report" else "Daily Tasks", style = MaterialTheme.typography.titleMedium) },
                    actions = {
                        if (!report) IconButton(onClick = { copy(state.entries) }) { Icon(TasksIcons.Copy, "Copy tasks", tint = p.textSecondary) }
                    },
                )
                RowDivider()
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (report) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pick = "from" }) { Text(Dates.label(state.from)) }
                    Text("→", color = p.textTertiary)
                    TextButton(onClick = { pick = "to" }) { Text(Dates.label(state.to)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.setRange(LocalDate.now().minusDays(6), LocalDate.now()) }) { Text("7d") }
                    TextButton(onClick = { vm.setRange(LocalDate.now().minusDays(29), LocalDate.now()) }) { Text("30d") }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.setDate(state.date.minusDays(1)) }) { Icon(TasksIcons.ChevronLeft, "Previous day", tint = p.textSecondary) }
                    TextButton(onClick = { pick = "date" }, modifier = Modifier.weight(1f)) {
                        Text(Dates.longLabel(state.date), color = p.textPrimary, style = MaterialTheme.typography.titleSmall)
                    }
                    IconButton(onClick = { vm.setDate(state.date.plusDays(1)) }) { Icon(TasksIcons.ChevronRight, "Next day", tint = p.textSecondary) }
                }
                AddRow(onAdd = vm::add)
            }

            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                state.entries.isEmpty() -> Text(
                    if (report) "No tasks logged in this range." else "Nothing logged for this day yet.",
                    color = p.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                report -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    // The API returns newest day first
                    val byDay = state.entries.groupBy { it.logDate }
                    items(byDay.keys.toList()) { day ->
                        val entries = byDay.getValue(day)
                        Column {
                            Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Dates.parseDate(day)?.let(Dates::longLabel) ?: day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text("${entries.size}", fontSize = 12.sp, color = p.textTertiary)
                                IconButton(onClick = { copy(entries) }) { Icon(TasksIcons.Copy, "Copy", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
                            }
                            EntryList(entries, onEdit = { editing = it }, onDelete = { deleting = it })
                        }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    item { EntryList(state.entries, onEdit = { editing = it }, onDelete = { deleting = it }) }
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

/** The input stays put and clears right away, so quick consecutive entries aren't lost (as on web). */
@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    val p = palette
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            placeholder = { Text("What did you get done?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = tasksFieldColors(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = submit,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
            modifier = Modifier.height(52.dp),
        ) { Icon(TasksIcons.Plus, "Add", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun EntryList(entries: List<DailyLogDto>, onEdit: (DailyLogDto) -> Unit, onDelete: (DailyLogDto) -> Unit) {
    val p = palette
    ListCard {
        entries.forEachIndexed { i, e ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(p.accent))
                Spacer(Modifier.width(12.dp))
                Text(e.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { onEdit(e) }) { Icon(TasksIcons.Pencil, "Edit", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { onDelete(e) }) { Icon(TasksIcons.Trash, "Delete", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
