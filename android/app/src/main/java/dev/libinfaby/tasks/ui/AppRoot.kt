package dev.libinfaby.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.ui.components.parseHex
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
import dev.libinfaby.tasks.ui.theme.palette
import kotlinx.coroutines.launch

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

    val p = palette
    val destination by vm.destination.collectAsStateWithLifecycle()
    val editorRequest by vm.editor.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val counts by vm.openCounts.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val taskListVm: TaskListViewModel = hiltViewModel()
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }

    fun go(d: Destination) {
        if (d is Destination.Tasks) taskListVm.setView(d.view)
        vm.destination.value = d
        scope.launch { drawer.close() }
    }

    LaunchedEffect(Unit) { vm.syncOnOpen() }
    // Back from any other screen returns to All Tasks before leaving the app
    BackHandler(enabled = destination != Destination.Tasks(TaskView.All) && !drawer.isOpen) { go(Destination.Tasks(TaskView.All)) }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = p.bgSubtle, drawerShape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Row(Modifier.padding(start = 8.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrandMark()
                        Spacer(Modifier.width(10.dp))
                        Text("Tasks", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                    DrawerSection("Tasks")
                    listOf(
                        Triple(TaskView.All, TasksIcons.Inbox, "All Tasks"),
                        Triple(TaskView.Today, TasksIcons.CalendarCheck, "Today"),
                        Triple(TaskView.Upcoming, TasksIcons.Clock, "Upcoming"),
                        Triple(TaskView.Priority, TasksIcons.Flag, "Priority"),
                        Triple(TaskView.Completed, TasksIcons.CircleCheck, "Completed"),
                    ).forEach { (view, icon, label) ->
                        DrawerItem(label, icon, destination == Destination.Tasks(view)) { go(Destination.Tasks(view)) }
                    }
                    DrawerSection("Daily Tasks")
                    DrawerItem("Entry", TasksIcons.Clipboard, destination == Destination.DailyEntry) { go(Destination.DailyEntry) }
                    DrawerItem("Report", TasksIcons.FileText, destination == Destination.DailyReport) { go(Destination.DailyReport) }
                    if (groups.isNotEmpty()) {
                        DrawerSection("Groups")
                        groups.forEach { g ->
                            val view = TaskView.Group(g.id, g.name)
                            NavigationDrawerItem(
                                label = { Text(g.name, fontSize = 14.sp) },
                                selected = destination == Destination.Tasks(view),
                                onClick = { go(Destination.Tasks(view)) },
                                icon = { Box(Modifier.padding(horizontal = 4.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(parseHex(g.color))) },
                                badge = { Text((counts[g.id] ?: 0).toString(), fontSize = 12.sp, color = p.textTertiary) },
                                colors = drawerColors(),
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                    DrawerSection("Manage")
                    DrawerItem("Tags", TasksIcons.Tag, destination == Destination.Tags) { go(Destination.Tags) }
                    DrawerItem("Groups", TasksIcons.Layers, destination == Destination.Groups) { go(Destination.Groups) }
                    DrawerItem("Settings", TasksIcons.Settings, destination == Destination.Settings) { go(Destination.Settings) }
                    Spacer(Modifier.padding(bottom = 16.dp))
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val d = destination) {
                is Destination.Tasks -> TaskListScreen(
                    vm = taskListVm,
                    onOpenDrawer = openDrawer,
                    onOpenTask = { vm.editor.value = EditorRequest(it) },
                    onNewTask = vm::openNewTask,
                )
                Destination.DailyEntry -> DailyScreen(hiltViewModel<DailyViewModel>(key = "entry"), report = false, onOpenDrawer = openDrawer)
                Destination.DailyReport -> DailyScreen(hiltViewModel<DailyViewModel>(key = "report"), report = true, onOpenDrawer = openDrawer)
                Destination.Tags -> TagsScreen(hiltViewModel<ManageViewModel>(), openDrawer)
                Destination.Groups -> GroupsScreen(hiltViewModel<ManageViewModel>(), openDrawer)
                Destination.Settings -> SettingsScreen(s, vm::setTheme, onSignOut = { vm.signOut() }, onOpenDrawer = openDrawer)
            }
        }
    }

    editorRequest?.let { request ->
        val editorVm: TaskEditorViewModel = hiltViewModel(key = "editor-${request.task?.id ?: "new"}")
        LaunchedEffect(request) { editorVm.open(request.task) }
        TaskEditorSheet(editorVm, onDismiss = { vm.editor.value = null })
    }
}

@Composable
private fun DrawerSection(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = palette.textTertiary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontSize = 14.sp) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        colors = drawerColors(),
        modifier = Modifier.padding(vertical = 1.dp),
    )
}

@Composable
private fun drawerColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = palette.muted,
    unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    selectedTextColor = palette.textPrimary,
    unselectedTextColor = palette.textSecondary,
    selectedIconColor = palette.accentText,
    unselectedIconColor = palette.textTertiary,
)
