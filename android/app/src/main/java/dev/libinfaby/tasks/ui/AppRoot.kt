package dev.libinfaby.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.ui.components.ConnectedColumn
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.ListRow
import dev.libinfaby.tasks.ui.components.Scallop
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tonalColors
import dev.libinfaby.tasks.ui.daily.DailyScreen
import dev.libinfaby.tasks.ui.daily.DailyViewModel
import dev.libinfaby.tasks.ui.manage.GroupsScreen
import dev.libinfaby.tasks.ui.manage.ManageViewModel
import dev.libinfaby.tasks.ui.manage.TagsScreen
import dev.libinfaby.tasks.ui.tasks.TaskEditorSheet
import dev.libinfaby.tasks.ui.tasks.TaskEditorViewModel
import dev.libinfaby.tasks.ui.tasks.TaskListScreen
import dev.libinfaby.tasks.ui.tasks.TaskListViewModel
import dev.libinfaby.tasks.ui.tasks.TaskView
import dev.libinfaby.tasks.ui.theme.TasksIcons

private data class Tab(val destination: Destination, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    Tab(Destination.Tasks(TaskView.All), "All tasks", TasksIcons.Inbox, TasksIcons.InboxFilled),
    Tab(Destination.Tasks(TaskView.Today), "Today", TasksIcons.Today, TasksIcons.TodayFilled),
    Tab(Destination.Tasks(TaskView.Upcoming), "Upcoming", TasksIcons.Upcoming, TasksIcons.UpcomingFilled),
    Tab(Destination.Daily(report = false), "Daily log", TasksIcons.DailyLog, TasksIcons.DailyLogFilled),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return // first DataStore read is in flight
    if (!s.signedIn) {
        val error by vm.signInError.collectAsStateWithLifecycle()
        val busy by vm.signingIn.collectAsStateWithLifecycle()
        LoginScreen(s.apiUrl, error, busy, vm::signIn)
        return
    }

    val destination by vm.destination.collectAsStateWithLifecycle()
    val editorRequest by vm.editor.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val counts by vm.openCounts.collectAsStateWithLifecycle()
    val taskListVm: TaskListViewModel = hiltViewModel()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.syncOnOpen() }
    LaunchedEffect(destination) { (destination as? Destination.Tasks)?.let { taskListVm.setView(it.view) } }
    BackHandler(enabled = destination != Destination.Start) { vm.back() }

    Scaffold(
        // Each screen handles its own top inset; the bottom bar pads for the navigation bar itself
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (destination.isRoot) {
                ShortNavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    TABS.forEach { tab ->
                        val selected = when (val d = destination) {
                            is Destination.Daily -> tab.destination is Destination.Daily
                            else -> d == tab.destination
                        }
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) vm.go(tab.destination) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (val d = destination) {
                is Destination.Tasks -> TaskListScreen(
                    vm = taskListVm,
                    onOpenTask = { vm.editor.value = EditorRequest(it) },
                    onNewTask = vm::openNewTask,
                    onOpenMenu = { menuOpen = true },
                    onOpenGroup = { vm.go(Destination.Tasks(it)) },
                    onBack = if (d.isRoot) null else ({ vm.back() }),
                )
                is Destination.Daily -> DailyScreen(
                    hiltViewModel<DailyViewModel>(key = if (d.report) "report" else "entry"),
                    report = d.report,
                    onReport = { vm.go(Destination.Daily(it)) },
                )
                Destination.Tags -> TagsScreen(hiltViewModel<ManageViewModel>(), onBack = { vm.back() })
                Destination.Groups -> GroupsScreen(hiltViewModel<ManageViewModel>(), onBack = { vm.back() })
                Destination.Settings -> SettingsScreen(
                    s,
                    onTheme = vm::setTheme,
                    onWallpaperColors = vm::setWallpaperColors,
                    onSignOut = { vm.signOut() },
                    onBack = { vm.back() },
                )
            }
        }
    }

    if (menuOpen) {
        MenuSheet(groups, counts, onDismiss = { menuOpen = false }) { d ->
            menuOpen = false
            vm.go(d)
        }
    }

    editorRequest?.let { request ->
        val editorVm: TaskEditorViewModel = hiltViewModel(key = "editor-${request.task?.id ?: "new"}")
        LaunchedEffect(request) { editorVm.open(request.task) }
        TaskEditorSheet(editorVm, onDismiss = { vm.editor.value = null })
    }
}

/** Everything that used to live in the drawer: groups to jump into, then Tags, Groups and Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(groups: List<GroupDto>, counts: Map<Long, Int>, onDismiss: () -> Unit, onGo: (Destination) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(Modifier.padding(start = 4.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(44)
                Spacer(Modifier.width(14.dp))
                Text("Tasks", style = MaterialTheme.typography.headlineMedium)
            }
            if (groups.isNotEmpty()) {
                MenuLabel("Your groups")
                ConnectedColumn {
                    groups.forEachIndexed { i, g ->
                        val tint = tonalColors(parseHex(g.color))
                        ConnectedItem(i, groups.size, onClick = { onGo(Destination.Tasks(TaskView.Group(g.id, g.name))) }) {
                            ListRow(
                                title = g.name,
                                icon = TasksIcons.GroupFilled,
                                iconContainer = tint.container,
                                iconContent = tint.content,
                                iconShape = RoundedCornerShape(15.dp, 15.dp, 15.dp, 5.dp),
                            ) {
                                Text("${counts[g.id] ?: 0}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            MenuLabel("Manage")
            ConnectedColumn {
                ConnectedItem(0, 3, onClick = { onGo(Destination.Tags) }) { ListRow("Tags", supporting = "Tag types and their colours", icon = TasksIcons.TagFilled) }
                ConnectedItem(1, 3, onClick = { onGo(Destination.Groups) }) { ListRow("Groups", supporting = "Create, rename and recolour", icon = TasksIcons.GroupFilled) }
                ConnectedItem(2, 3, onClick = { onGo(Destination.Settings) }) { ListRow("Settings", supporting = "Theme, reminders and sync", icon = TasksIcons.Settings) }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
}

/** The app mark: a checklist in a primary scallop. */
@Composable
fun BrandMark(size: Int = 40) {
    Surface(shape = Scallop, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(TasksIcons.Checklist, null, modifier = Modifier.size((size * 0.5f).dp)) }
    }
}
