package dev.libinfaby.tasks.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.GroupWrite
import dev.libinfaby.tasks.data.api.TagDto
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.data.api.TagTypeWrite
import dev.libinfaby.tasks.data.api.TagWrite
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.ui.components.Chip
import dev.libinfaby.tasks.ui.components.ChipColors
import dev.libinfaby.tasks.ui.components.ColorField
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.EmptyState
import dev.libinfaby.tasks.ui.components.ListCard
import dev.libinfaby.tasks.ui.components.RowDivider
import dev.libinfaby.tasks.ui.components.chipColors
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tasksFieldColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageViewModel @Inject constructor(private val repo: TasksRepository) : ViewModel() {
    val tagTypes: StateFlow<List<TagTypeDto>> = repo.tagTypes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups: StateFlow<List<GroupDto>> = repo.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { run("") { repo.refreshMeta() } }

    fun saveTagType(id: Long?, write: TagTypeWrite, applyTo: List<Long>) = run(if (id == null) "Tag type created" else "Updated") { repo.saveTagType(id, write, applyTo) }
    fun deleteTagType(id: Long) = run("Deleted") { repo.deleteTagType(id) }
    fun saveTag(id: Long?, write: TagWrite) = run(if (id == null) "Tag created" else "Tag updated") { repo.saveTag(id, write) }
    fun deleteTag(id: Long) = run("Tag deleted") { repo.deleteTag(id) }
    fun saveGroup(id: Long?, write: GroupWrite) = run(if (id == null) "Group created" else "Updated") { repo.saveGroup(id, write) }
    fun deleteGroup(id: Long) = run("Group deleted") { repo.deleteGroup(id) }
    fun consume() { _message.value = null }

    private fun run(done: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { if (done.isNotEmpty()) _message.value = done }
            .onFailure { _message.value = it.userMessage() }
    }
}

/** Colour settings shared by tag types, tags and groups (colour, text colour, filled). */
data class ChipStyle(val name: String, val color: String, val fg: String, val filled: Boolean, val icon: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageScaffold(title: String, onOpenDrawer: () -> Unit, message: String?, onConsume: () -> Unit, onAdd: () -> Unit, addLabel: String, content: @Composable (PaddingValues) -> Unit) {
    val p = palette
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onConsume() } }
    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
                    navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(TasksIcons.Menu, "Menu", tint = p.textSecondary) } },
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                )
                RowDivider()
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = p.accent, contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                Icon(TasksIcons.Plus, addLabel)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

// ==================== Tags ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(vm: ManageViewModel, onOpenDrawer: () -> Unit) {
    val p = palette
    val types by vm.tagTypes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editType by remember { mutableStateOf<TagTypeDto?>(null) }
    var newType by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<Pair<TagTypeDto, TagDto?>?>(null) }
    var deleteType by remember { mutableStateOf<TagTypeDto?>(null) }
    var deleteTag by remember { mutableStateOf<TagDto?>(null) }

    ManageScaffold("Tags", onOpenDrawer, message, vm::consume, onAdd = { newType = true }, addLabel = "New tag type") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (types.isEmpty()) item { EmptyState(TasksIcons.Tag, "No tag types yet", "Create tag types like \"Project\" or \"Client\" to organise tasks.") }
            items(types, key = { it.id }) { type ->
                ListCard {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(parseHex(type.color)).border(1.dp, p.chipEdge, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(10.dp))
                        if (!type.icon.isNullOrBlank()) Text(type.icon + " ")
                        Text(type.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text("${type.tags.size}", fontSize = 12.sp, color = p.textTertiary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editType = type }) { Icon(TasksIcons.Pencil, "Edit ${type.name}", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = { deleteType = type }) { Icon(TasksIcons.Trash, "Delete ${type.name}", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
                    }
                    RowDivider()
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        type.tags.forEach { tag ->
                            Chip(tag.name, chipColors(p, tag.color, tag.fgColor, tag.hasBg, type.color, type.fgColor, type.hasBg), onClick = { editTag = type to tag })
                        }
                        Row(
                            Modifier.height(22.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, p.borderStrong, RoundedCornerShape(5.dp))
                                .clickable { editTag = type to null }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(TasksIcons.Plus, null, tint = p.textTertiary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add tag", fontSize = 12.sp, color = p.textTertiary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (newType || editType != null) {
        val t = editType
        StyleDialog(
            title = if (t == null) "New tag type" else "Edit tag type",
            initial = ChipStyle(t?.name.orEmpty(), t?.color ?: "#5b5bd6", t?.fgColor ?: "#ffffff", (t?.hasBg ?: 1) != 0, t?.icon.orEmpty()),
            withIcon = true,
            applyAllCount = t?.tags?.size ?: 0,
            onDismiss = { newType = false; editType = null },
        ) { s, applyAll ->
            vm.saveTagType(t?.id, TagTypeWrite(s.name, s.color, s.fg, s.filled, s.icon.ifBlank { null }), if (applyAll) t?.tags?.map { it.id }.orEmpty() else emptyList())
        }
    }
    editTag?.let { (type, tag) ->
        // New Client tags start white on black (as on web); otherwise they inherit the type's colours
        val client = type.name.equals("client", true)
        StyleDialog(
            title = if (tag == null) "Add ${type.name} tag" else "Edit tag",
            initial = ChipStyle(
                tag?.name.orEmpty(),
                tag?.color ?: if (client) "#ffffff" else type.color,
                tag?.fgColor ?: if (client) "#000000" else (type.fgColor ?: "#ffffff"),
                (tag?.hasBg ?: if (client) 1 else (type.hasBg ?: 1)) != 0,
            ),
            onDismiss = { editTag = null },
            onDelete = tag?.let { { deleteTag = it; editTag = null } },
        ) { s, _ -> vm.saveTag(tag?.id, TagWrite(s.name, type.id, s.color, s.fg, s.filled)) }
    }
    deleteType?.let { t -> ConfirmDialog("Delete \"${t.name}\"?", "The tag type and all its tags will be deleted.", "Delete", onDismiss = { deleteType = null }) { vm.deleteTagType(t.id) } }
    deleteTag?.let { t -> ConfirmDialog("Delete tag \"${t.name}\"?", "It will be removed from every task.", "Delete", onDismiss = { deleteTag = null }) { vm.deleteTag(t.id) } }
}

// ==================== Groups ====================

@Composable
fun GroupsScreen(vm: ManageViewModel, onOpenDrawer: () -> Unit) {
    val p = palette
    val groups by vm.groups.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GroupDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GroupDto?>(null) }

    ManageScaffold("Manage Groups", onOpenDrawer, message, vm::consume, onAdd = { creating = true }, addLabel = "New group") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            if (groups.isEmpty()) {
                item { EmptyState(TasksIcons.Layers, "No groups yet", "Create groups to organise related tasks together.") }
            } else item {
                ListCard {
                    groups.forEachIndexed { i, g ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().clickable { editing = g }.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(parseHex(g.color)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Text("${g.taskCount ?: 0} tasks · ${g.activeTaskCount ?: 0} active", fontSize = 12.sp, color = p.textTertiary)
                            }
                            IconButton(onClick = { deleting = g }) { Icon(TasksIcons.Trash, "Delete ${g.name}", tint = p.textTertiary, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val g = editing
        StyleDialog(
            title = if (g == null) "New group" else "Edit group",
            initial = ChipStyle(g?.name.orEmpty(), g?.color ?: "#8b5cf6", g?.fgColor ?: "#ffffff", (g?.hasBg ?: 1) != 0),
            onDismiss = { creating = false; editing = null },
        ) { s, _ -> vm.saveGroup(g?.id, GroupWrite(s.name, s.color, s.fg, s.filled)) }
    }
    deleting?.let { g -> ConfirmDialog("Delete \"${g.name}\"?", "Tasks in it will be ungrouped.", "Delete", onDismiss = { deleting = null }) { vm.deleteGroup(g.id) } }
}

/** Name + colours (+ icon for tag types) with a live chip preview, like the web modals. */
@Composable
private fun StyleDialog(
    title: String,
    initial: ChipStyle,
    withIcon: Boolean = false,
    applyAllCount: Int = 0,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (ChipStyle, Boolean) -> Unit,
) {
    val p = palette
    var s by remember { mutableStateOf(initial) }
    var applyAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Chip(s.name.ifBlank { "Preview" }, if (s.filled) ChipColors(parseHex(s.color), parseHex(s.fg, Color.White), true) else chipColors(p, s.color, s.fg, 0))
                OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Name") }, singleLine = true, colors = tasksFieldColors(), modifier = Modifier.fillMaxWidth())
                ColorField("Colour", s.color) { s = s.copy(color = it) }
                ColorField("Text colour", s.fg) { s = s.copy(fg = it) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { s = s.copy(filled = !s.filled) }) {
                    Checkbox(s.filled, { s = s.copy(filled = it) }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                    Text("Filled background", style = MaterialTheme.typography.bodyMedium)
                }
                if (withIcon) {
                    OutlinedTextField(s.icon, { s = s.copy(icon = it.take(4)) }, label = { Text("Icon (emoji)") }, singleLine = true, colors = tasksFieldColors(), modifier = Modifier.fillMaxWidth())
                }
                if (applyAllCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { applyAll = !applyAll }) {
                        Checkbox(applyAll, { applyAll = it }, colors = CheckboxDefaults.colors(checkedColor = p.accent))
                        Text("Apply these colours to all $applyAllCount tag${if (applyAllCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = s.name.isNotBlank(), onClick = { onSave(s.copy(name = s.name.trim()), applyAll); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = p.danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
