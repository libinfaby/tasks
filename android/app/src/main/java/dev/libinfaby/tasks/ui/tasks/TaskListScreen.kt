package dev.libinfaby.tasks.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.EmptyState
import dev.libinfaby.tasks.ui.components.ListCard
import dev.libinfaby.tasks.ui.components.RowDivider
import dev.libinfaby.tasks.ui.components.SectionHeader
import dev.libinfaby.tasks.ui.components.TaskRow
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: TaskListViewModel,
    onOpenDrawer: () -> Unit,
    onOpenTask: (TaskDto) -> Unit,
    onNewTask: () -> Unit,
) {
    val p = palette
    val state by vm.state.collectAsStateWithLifecycle()
    val view by vm.currentView.collectAsStateWithLifecycle()
    val filters by vm.currentFilters.collectAsStateWithLifecycle()
    val search by vm.currentSearch.collectAsStateWithLifecycle()
    val tagTypes by vm.tagTypes.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var askDaily by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) { Icon(TasksIcons.Menu, "Menu", tint = p.textSecondary) }
                    },
                    title = {
                        if (searching) {
                            SearchField(search, tagTypes.map { it.id to it.name }, vm::setSearch) {
                                searching = false
                                vm.setSearch(search.copy(text = ""))
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(view.title, style = MaterialTheme.typography.titleMedium, color = p.textPrimary)
                                if (!state.loading) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        state.count.toString(),
                                        fontSize = 12.sp,
                                        color = p.textTertiary,
                                        modifier = Modifier
                                            .padding(top = 1.dp),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (!searching) {
                            IconButton(onClick = { searching = true }) { Icon(TasksIcons.Search, "Search", tint = p.textSecondary) }
                        }
                    },
                )
                RowDivider()
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTask,
                containerColor = p.accent,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) { Icon(TasksIcons.Plus, "New task") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.refresh { refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)) {
                item {
                    FilterBar(view, filters, vm)
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    state.loading -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = p.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                    state.error != null -> item {
                        EmptyState(TasksIcons.Refresh, "Couldn't load tasks", state.error!!)
                        TextButton(onClick = vm::retry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                    }
                    state.sections.isEmpty() -> item { EmptyFor(view, search.text.isNotBlank() || filters.tagId != null) }
                    else -> items(state.sections, key = { "${it.title}-${it.group?.id}" }) { section ->
                        if (section.title.isNotEmpty()) {
                            val dot = when (section.color) {
                                "priority" -> p.urgent
                                "accent" -> p.accent
                                null -> null
                                else -> parseHex(section.color)
                            }
                            SectionHeader(section.title, section.tasks.size, dot)
                        }
                        ListCard {
                            section.tasks.forEachIndexed { i, task ->
                                if (i > 0) RowDivider()
                                TaskRow(
                                    task = task,
                                    showGroup = view !is TaskView.Group,
                                    onOpen = { onOpenTask(task) },
                                    onToggle = {
                                        vm.toggle(task) { done ->
                                            val clients = done.tags.filter { it.typeName.equals("client", true) }.map { it.name }
                                            askDaily = if (clients.isEmpty()) done.title else "${clients.joinToString(", ")} - ${done.title}"
                                        }
                                    },
                                    onToggleSubtask = { vm.toggleSubtask(it.id) },
                                    onTagClick = { vm.setTagFilter(it.id, it.name) },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    askDaily?.let { text ->
        AlertDialog(
            onDismissRequest = { askDaily = null },
            title = { Text("Add to Daily Tasks?") },
            text = { Text("Log \"$text\" as a daily task entry for today?") },
            confirmButton = { TextButton(onClick = { vm.addToDaily(text); askDaily = null }) { Text("Add entry") } },
            dismissButton = { TextButton(onClick = { askDaily = null }) { Text("No") } },
        )
    }
}

@Composable
private fun FilterBar(view: TaskView, filters: Filters, vm: TaskListViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterPill("All", view == TaskView.All && !filters.completed && filters.priority == null && filters.tagId == null) { vm.clearToAll() }
        FilterPill("Completed", filters.completed) { vm.toggleCompleted() }
        FilterPill("Urgent", filters.priority == 2) { vm.togglePriority(2) }
        FilterPill("High", filters.priority == 1) { vm.togglePriority(1) }
        if (filters.tagId != null) {
            FilterPill(filters.tagName ?: "Tag", true, trailing = true) { vm.setTagFilter(null) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, trailing: Boolean = false, onClick: () -> Unit) {
    val p = palette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        trailingIcon = if (trailing) ({ Icon(TasksIcons.X, "Clear", modifier = Modifier.size(14.dp)) }) else null,
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = p.card,
            labelColor = p.textSecondary,
            selectedContainerColor = p.accentSoft,
            selectedLabelColor = p.accentText,
            selectedTrailingIconColor = p.accentText,
        ),
        border = if (selected) null else BorderStroke(1.dp, p.border),
    )
}

@Composable
private fun EmptyFor(view: TaskView, searching: Boolean) {
    val (icon, title, text) = when {
        searching -> Triple(TasksIcons.Search, "No matching tasks", "Try a different search or clear the filter.")
        view == TaskView.Today -> Triple(TasksIcons.CalendarCheck, "Nothing due today", "Tasks dated today will show up here.")
        view == TaskView.Upcoming -> Triple(TasksIcons.Clock, "Nothing upcoming", "Tasks dated in the next 7 days will show up here.")
        view == TaskView.Priority -> Triple(TasksIcons.Flag, "No priority tasks", "High and urgent tasks will show up here.")
        view == TaskView.Completed -> Triple(TasksIcons.CircleCheck, "No completed tasks", "Tasks you finish will show up here.")
        else -> Triple(TasksIcons.Inbox, "No tasks", "Tap + to create a task.")
    }
    EmptyState(icon, title, text)
}

/** Search box with a type menu (task name / any tag / date / a specific tag type), like the web header. */
@Composable
private fun SearchField(search: Search, tagTypes: List<Pair<Long, String>>, onChange: (Search) -> Unit, onClose: () -> Unit) {
    val p = palette
    var menu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val typeLabel = search.tagTypeId?.let { id -> tagTypes.firstOrNull { it.first == id }?.second } ?: search.type.label
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }) { Text(typeLabel, fontSize = 13.sp, color = p.textSecondary, maxLines = 1) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SearchType.entries.forEach { t ->
                    DropdownMenuItem(text = { Text(t.label) }, onClick = { onChange(Search(if (t == SearchType.DATE) "" else search.text, t)); menu = false })
                }
                tagTypes.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onChange(Search(search.text, SearchType.TAG, id)); menu = false })
                }
            }
        }
        if (search.type == SearchType.DATE) {
            TextButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
                Text(Dates.parseDate(search.text)?.let { Dates.longLabel(it) } ?: "Pick a date", color = p.textPrimary)
            }
        } else {
            TextField(
                value = search.text,
                onValueChange = { onChange(search.copy(text = it)) },
                placeholder = { Text("Search…", color = p.textTertiary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        IconButton(onClick = onClose) { Icon(TasksIcons.X, "Close search", tint = p.textSecondary) }
    }
    if (pickDate) {
        DatePickerDialogFor(Dates.parseDate(search.text), onDismiss = { pickDate = false }) { onChange(search.copy(text = it.toString())) }
    }
}
