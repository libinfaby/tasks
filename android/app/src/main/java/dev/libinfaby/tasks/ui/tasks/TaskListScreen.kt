package dev.libinfaby.tasks.ui.tasks

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.ui.components.BackTopBar
import dev.libinfaby.tasks.ui.components.DatePickerDialogFor
import dev.libinfaby.tasks.ui.components.EmptyState
import dev.libinfaby.tasks.ui.components.SectionHeader
import dev.libinfaby.tasks.ui.components.TaskRow
import dev.libinfaby.tasks.ui.components.ToggleColors
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tonalColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
import kotlinx.coroutines.launch

/**
 * One task view. Root tabs (Today, Upcoming, All tasks) open with a search bar and a header; a group
 * view ([onBack] set) gets a back arrow instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    vm: TaskListViewModel,
    onOpenTask: (TaskDto) -> Unit,
    onNewTask: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenGroup: (TaskView.Group) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val view by vm.currentView.collectAsStateWithLifecycle()
    val filters by vm.currentFilters.collectAsStateWithLifecycle()
    val search by vm.currentSearch.collectAsStateWithLifecycle()
    val tagTypes by vm.tagTypes.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    // Finishing a task offers to log it, without stopping the flow with a dialog
    val onCompleted: (TaskDto) -> Unit = { done ->
        val clients = done.tags.filter { it.typeName.equals("client", true) }.map { it.name }
        val entry = if (clients.isEmpty()) done.title else "${clients.joinToString(", ")} - ${done.title}"
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Done! Add it to your daily log?", actionLabel = "Log it", withDismissAction = true, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.addToDaily(entry)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { if (onBack != null) BackTopBar(view.title, onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTask,
                expanded = fabExpanded,
                icon = { Icon(TasksIcons.Add, null, modifier = Modifier.size(26.dp)) },
                text = { Text("New task", style = MaterialTheme.typography.titleMedium) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.height(64.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; vm.refresh { refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp)) {
                if (onBack == null) {
                    item(key = "search") {
                        SearchPill(searching, search, tagTypes.map { it.id to it.name }, vm::setSearch, onOpen = { searching = true }, onClose = {
                            searching = false
                            vm.setSearch(search.copy(text = ""))
                        }, onOpenMenu = onOpenMenu)
                    }
                    item(key = "header") {
                        TitleHeader(view, state.count, state.loading)
                    }
                }
                item(key = "filters") { FilterBar(filters, vm) }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.error != null -> item(key = "error") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            EmptyState(TasksIcons.Refresh, "Couldn't load tasks", state.error!!)
                            FilledTonalButton(onClick = vm::retry) { Text("Try again") }
                        }
                    }
                    state.sections.isEmpty() -> item(key = "empty") { EmptyFor(view, search.text.isNotBlank() || filters.tagId != null || filters.urgent, filters.completed) }
                    else -> sections(state.sections, view, onOpenTask, onOpenGroup, vm, onCompleted)
                }
            }
        }
    }
}

private fun LazyListScope.sections(
    sections: List<Section>,
    view: TaskView,
    onOpenTask: (TaskDto) -> Unit,
    onOpenGroup: (TaskView.Group) -> Unit,
    vm: TaskListViewModel,
    onCompleted: (TaskDto) -> Unit,
) {
    sections.forEach { section ->
        val sectionKey = "${section.kind}-${section.group?.id}-${section.title}"
        if (section.title.isNotEmpty()) {
            item(key = "h-$sectionKey") {
                SectionTitle(section, onOpenGroup, Modifier.animateItem())
            }
        } else {
            item(key = "h-$sectionKey") { Spacer(Modifier.height(16.dp)) }
        }
        val showDate = when {
            section.kind == SectionKind.DAY -> false
            view == TaskView.Today -> section.kind == SectionKind.OVERDUE
            else -> true
        }
        val n = section.tasks.size
        section.tasks.forEachIndexed { i, task ->
            item(key = "$sectionKey-${task.id}") {
                TaskRow(
                    task = task,
                    index = i,
                    count = n,
                    showGroup = section.kind != SectionKind.GROUP && view !is TaskView.Group,
                    showDate = showDate,
                    onOpen = { onOpenTask(task) },
                    onToggle = { vm.toggle(task, onCompleted) },
                    onToggleSubtask = { vm.toggleSubtask(it.id) },
                    onTagClick = { vm.setTagFilter(it.id, it.name) },
                    modifier = Modifier.padding(bottom = 3.dp).animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(section: Section, onOpenGroup: (TaskView.Group) -> Unit, modifier: Modifier = Modifier) {
    val open = section.tasks.count { !it.completed }
    val trailing = if (open == 0) "All done" else "$open left"
    val (icon, tile, shape) = when (section.kind) {
        SectionKind.OVERDUE -> Triple(TasksIcons.Alarm, ToggleColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer), RoundedCornerShape(10.dp))
        SectionKind.URGENT -> Triple(TasksIcons.FireFilled, ToggleColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer), RoundedCornerShape(10.dp))
        SectionKind.GROUP -> Triple(TasksIcons.GroupFilled, tonalColors(parseHex(section.group?.color)), RoundedCornerShape(15.dp, 15.dp, 15.dp, 5.dp))
        SectionKind.DAY -> Triple(TasksIcons.Calendar, ToggleColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer), RoundedCornerShape(10.dp))
        else -> Triple(TasksIcons.TaskAlt, ToggleColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer), RoundedCornerShape(15.dp))
    }
    val group = section.group
    if (section.kind == SectionKind.GROUP && group != null) {
        // A group's header opens that group on its own
        Surface(
            onClick = { onOpenGroup(TaskView.Group(group.id, group.name)) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.semantics { contentDescription = "Open group ${group.name}"; role = Role.Button },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(section.title, Modifier.weight(1f), icon, tile, shape, trailing)
                Icon(TasksIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp).size(20.dp))
            }
        }
    } else {
        SectionHeader(section.title, modifier, icon, tile, shape, trailing)
    }
}

@Composable
private fun TitleHeader(view: TaskView, count: Int, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 24.dp, bottom = 4.dp)) {
        Text(view.title, style = MaterialTheme.typography.displaySmall)
        if (!loading) {
            val sub = when (view) {
                TaskView.Today -> "${Dates.fullLabel(Dates.today())} · $count open"
                TaskView.Upcoming -> "Next 7 days · $count open"
                else -> "$count open"
            }
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterBar(filters: Filters, vm: TaskListViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill("All", !filters.any, onClick = vm::clearFilters)
        FilterPill("Urgent", filters.urgent, TasksIcons.FireFilled, MaterialTheme.colorScheme.error, onClick = vm::toggleUrgent)
        FilterPill("Done", filters.completed, TasksIcons.TaskAlt, MaterialTheme.colorScheme.primary, onClick = vm::toggleCompleted)
        if (filters.tagId != null) {
            FilterPill(filters.tagName ?: "Tag", true, clearable = true) { vm.setTagFilter(null) }
        }
    }
}

/** Filter chip whose corners round off into a pill when selected. */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    clearable: Boolean = false,
    onClick: () -> Unit,
) {
    val corner by animateDpAsState(if (selected) 16.dp else 8.dp, label = "chip")
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = when {
            selected -> ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(18.dp)) })
            icon != null -> ({ Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp)) })
            else -> null
        },
        trailingIcon = if (clearable) ({ Icon(TasksIcons.Close, "Clear tag filter", modifier = Modifier.size(18.dp)) }) else null,
        shape = RoundedCornerShape(corner),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.height(36.dp),
    )
}

@Composable
private fun EmptyFor(view: TaskView, filtered: Boolean, done: Boolean) {
    val (icon, title, text) = when {
        filtered -> Triple(TasksIcons.Search404, "No matches", "Try a different search, or clear the filter.")
        done -> Triple(TasksIcons.TaskAlt, "Nothing finished yet", "Tasks you complete will show up here.")
        view == TaskView.Today -> Triple(TasksIcons.Sunny, "Nothing due today", "Enjoy the calm, or tap New task to plan something.")
        view == TaskView.Upcoming -> Triple(TasksIcons.Upcoming, "Nothing coming up", "Tasks dated in the next 7 days land here.")
        view is TaskView.Group -> Triple(TasksIcons.GroupFilled, "This group is empty", "Tap New task to add one.")
        else -> Triple(TasksIcons.Beach, "All clear", "Tap New task to add one.")
    }
    EmptyState(icon, title, text)
}

/**
 * The search bar: a pill that opens into a field with a type menu (task name / any tag / date / a
 * specific tag type), like the web header. The face on the right opens the account menu.
 */
@Composable
private fun SearchPill(
    searching: Boolean,
    search: Search,
    tagTypes: List<Pair<Long, String>>,
    onChange: (Search) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (searching) {
            SearchField(search, tagTypes, onChange, onClose)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onOpen, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.weight(1f).height(56.dp)) {
                    Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(TasksIcons.Search, null)
                        Spacer(Modifier.width(14.dp))
                        Text("Search tasks and tags", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Surface(
                    onClick = onOpenMenu,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 8.dp).size(40.dp).semantics { contentDescription = "Menu: groups, tags and settings" },
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(TasksIcons.Menu, null) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(search: Search, tagTypes: List<Pair<Long, String>>, onChange: (Search) -> Unit, onClose: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val typeLabel = search.tagTypeId?.let { id -> tagTypes.firstOrNull { it.first == id }?.second } ?: search.type.label
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text(typeLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(TasksIcons.DropDown, "Search by", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp)) {
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
                Text(Dates.parseDate(search.text)?.let { Dates.longLabel(it) } ?: "Pick a date", color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (search.text.isEmpty()) Text("Search…", style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = search.text,
                    onValueChange = { onChange(search.copy(text = it)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        IconButton(onClick = onClose) { Icon(TasksIcons.Close, "Close search") }
    }
    if (pickDate) {
        DatePickerDialogFor(Dates.parseDate(search.text), onDismiss = { pickDate = false }) { onChange(search.copy(text = it.toString())) }
    }
}
