package dev.libinfaby.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.ui.components.BackTopBar
import dev.libinfaby.tasks.ui.components.ConnectedColumn
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.ListRow
import dev.libinfaby.tasks.ui.components.PebbleShape
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

    val stack by vm.stack.collectAsStateWithLifecycle()
    val destination = stack.last()
    val editorRequest by vm.editor.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val counts by vm.openCounts.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.syncOnOpen() }
    BackHandler(enabled = stack.size > 1 || destination != Destination.Start) { vm.back() }

    // The page area never changes size: the bottom bar floats over it and tab screens keep room for it.
    // (Resizing it mid-transition made the outgoing page jump.)
    val density = LocalDensity.current
    // Start from the bar's usual height (64dp over the navigation bar) so tab screens don't shift once it's measured
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barHeight by remember { mutableStateOf(64.dp + navBarBottom) }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack,
            contentKey = { it.last() },
            transitionSpec = { navTransition(initialState.size, targetState.size) },
            modifier = Modifier.fillMaxSize(),
            label = "screen",
        ) { screens ->
            val barSpace = PaddingValues(bottom = if (screens.last().isRoot) barHeight else 0.dp)
            // Draw an opaque page so the one sliding away never shows through
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize().padding(barSpace).consumeWindowInsets(barSpace)) {
                when (val d = screens.last()) {
                    is Destination.Tasks -> {
                        // One list per view, so a page sliding out keeps its own tasks
                        val listVm: TaskListViewModel = hiltViewModel(key = "tasks-${d.view.key}")
                        remember(listVm) { listVm.setView(d.view) }
                        TaskListScreen(
                            vm = listVm,
                            onOpenTask = { vm.editor.value = EditorRequest(it) },
                            onNewTask = vm::openNewTask,
                            onOpenMenu = { vm.go(Destination.Menu) },
                            onOpenGroup = { vm.go(Destination.Tasks(it)) },
                            onBack = if (d.isRoot) null else ({ vm.back() }),
                        )
                    }
                    is Destination.Daily -> DailyScreen(
                        hiltViewModel<DailyViewModel>(key = if (d.report) "report" else "entry"),
                        report = d.report,
                        onReport = { vm.go(Destination.Daily(it)) },
                    )
                    Destination.Menu -> MenuScreen(groups, counts, onBack = { vm.back() }, onGo = vm::go)
                    Destination.Tags -> TagsScreen(hiltViewModel<ManageViewModel>(), onBack = { vm.back() })
                    Destination.Groups -> GroupsScreen(hiltViewModel<ManageViewModel>(), onBack = { vm.back() })
                    Destination.Settings -> SettingsScreen(
                        s,
                        onTheme = vm::setTheme,
                        onWallpaperColors = vm::setWallpaperColors,
                        groups = groups,
                        onDefaultGroup = vm::setDefaultGroup,
                        onSignOut = { vm.signOut() },
                        onBack = { vm.back() },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = destination.isRoot,
            enter = slideInVertically(tween(300, easing = EmphasizedDecelerate)) { it },
            exit = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ShortNavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
            ) {
                TABS.forEach { tab ->
                    val selected = when (destination) {
                        is Destination.Daily -> tab.destination is Destination.Daily
                        else -> destination == tab.destination
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
    }

    editorRequest?.let { request ->
        val editorVm: TaskEditorViewModel = hiltViewModel(key = "editor-${request.task?.id ?: "new"}")
        LaunchedEffect(request) { editorVm.open(request.task, s.defaultGroupId) }
        TaskEditorSheet(editorVm, onDismiss = { vm.editor.value = null })
    }
}

// Material 3 emphasized easing, split into its decelerate (enter) and accelerate (exit) halves
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Material shared-axis motion: opening a page slides it in from the right over the old one, going back
 * slides it away to the right; switching tabs fades through.
 */
private fun AnimatedContentTransitionScope<List<Destination>>.navTransition(from: Int, to: Int): ContentTransform = when {
    to > from -> (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 4 } + fadeIn(tween(250, 50)))
        .togetherWith(slideOutHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 4 } + fadeOut(tween(150)))
    to < from -> (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 4 } + fadeIn(tween(250, 50)))
        .togetherWith(slideOutHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 4 } + fadeOut(tween(150)))
        .apply { targetContentZIndex = -1f }
    else -> (fadeIn(tween(220, 90)) + scaleIn(tween(220, 90), initialScale = 0.96f))
        .togetherWith(fadeOut(tween(90)))
}.using(null) // every page fills the same area, so there is no size to animate

private val TaskView.key: String
    get() = when (this) {
        is TaskView.Group -> "group-$id"
        else -> title
    }

/** Everything that used to live in the drawer, as a full page: groups to jump into, then Tags, Groups and Settings. */
@Composable
private fun MenuScreen(groups: List<GroupDto>, counts: Map<Long, Int>, onBack: () -> Unit, onGo: (Destination) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(52)
                Spacer(Modifier.width(16.dp))
                Text("Tasks", style = MaterialTheme.typography.displaySmall)
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
                Spacer(Modifier.height(28.dp))
            }
            MenuLabel("Manage")
            ConnectedColumn {
                ConnectedItem(0, 3, onClick = { onGo(Destination.Tags) }) { ListRow("Tags", supporting = "Tag types and their colours", icon = TasksIcons.TagFilled) }
                ConnectedItem(1, 3, onClick = { onGo(Destination.Groups) }) { ListRow("Groups", supporting = "Create, rename and recolour", icon = TasksIcons.GroupFilled) }
                ConnectedItem(2, 3, onClick = { onGo(Destination.Settings) }) { ListRow("Settings", supporting = "Theme, default group, reminders and sync", icon = TasksIcons.Settings) }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
}

/** The app mark: a checklist in a primary pebble. */
@Composable
fun BrandMark(size: Int = 40) {
    Surface(shape = PebbleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(TasksIcons.Checklist, null, modifier = Modifier.size((size * 0.5f).dp)) }
    }
}
