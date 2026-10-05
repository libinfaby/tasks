package dev.libinfaby.tasks.ui.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import dev.libinfaby.tasks.ui.components.BackTopBar
import dev.libinfaby.tasks.ui.components.Chip
import dev.libinfaby.tasks.ui.components.ColorField
import dev.libinfaby.tasks.ui.components.ConfirmDialog
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.EmptyState
import dev.libinfaby.tasks.ui.components.ListRow
import dev.libinfaby.tasks.ui.components.tagColors
import dev.libinfaby.tasks.ui.components.parseHex
import dev.libinfaby.tasks.ui.components.tonalColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
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

@Composable
private fun ManageScaffold(
    title: String,
    onBack: () -> Unit,
    message: String?,
    onConsume: () -> Unit,
    onAdd: () -> Unit,
    addLabel: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); onConsume() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { BackTopBar(title, onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(TasksIcons.Add, null) },
                text = { Text(addLabel, style = MaterialTheme.typography.titleMedium) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

// ==================== Tags ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(vm: ManageViewModel, onBack: () -> Unit) {
    val types by vm.tagTypes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editType by remember { mutableStateOf<TagTypeDto?>(null) }
    var newType by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<Pair<TagTypeDto, TagDto?>?>(null) }
    var deleteType by remember { mutableStateOf<TagTypeDto?>(null) }
    var deleteTag by remember { mutableStateOf<TagDto?>(null) }

    ManageScaffold("Tags", onBack, message, vm::consume, onAdd = { newType = true }, addLabel = "New tag type") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (types.isEmpty()) item { EmptyState(TasksIcons.TagFilled, "No tag types yet", "Create tag types like \"Project\" or \"Client\" to organise tasks.") }
            items(types, key = { it.id }) { type ->
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.animateItem()) {
                    Column(Modifier.padding(bottom = 16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tint = tonalColors(parseHex(type.color))
                            Surface(shape = RoundedCornerShape(12.dp), color = tint.container, contentColor = tint.content, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!type.icon.isNullOrBlank()) Text(type.icon) else Icon(TasksIcons.TagFilled, null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(type.name, style = MaterialTheme.typography.titleMedium)
                                Text("${type.tags.size} tag${if (type.tags.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editType = type }) { Icon(TasksIcons.Edit, "Edit ${type.name}", modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { deleteType = type }) { Icon(TasksIcons.Delete, "Delete ${type.name}", modifier = Modifier.size(20.dp)) }
                        }
                        if (type.tags.isNotEmpty()) {
                            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                type.tags.forEach { tag ->
                                    Chip(tag.name, tagColors(tag.color, type.color), onClick = { editTag = type to tag })
                                }
                            }
                        }
                        // Always on its own line under the chips; its 48dp touch target already spaces it from them
                        Box(Modifier.padding(start = 10.dp)) {
                            Surface(
                                onClick = { editTag = type to null },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Row(Modifier.height(24.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(TasksIcons.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add tag", style = MaterialTheme.typography.labelMedium)
                                }
                            }
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
            initial = ChipStyle(t?.name.orEmpty(), t?.color ?: "#6f6aa8", t?.fgColor ?: "#ffffff", (t?.hasBg ?: 1) != 0, t?.icon.orEmpty()),
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
fun GroupsScreen(vm: ManageViewModel, onBack: () -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GroupDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GroupDto?>(null) }

    ManageScaffold("Groups", onBack, message, vm::consume, onAdd = { creating = true }, addLabel = "New group") { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (groups.isEmpty()) item { EmptyState(TasksIcons.GroupFilled, "No groups yet", "Create groups to keep related tasks together.") }
            itemsIndexed(groups, key = { _, g -> g.id }) { i, g ->
                val tint = tonalColors(parseHex(g.color))
                ConnectedItem(i, groups.size, modifier = Modifier.animateItem(), onClick = { editing = g }) {
                    ListRow(
                        title = g.name,
                        supporting = "${g.taskCount ?: 0} tasks · ${g.activeTaskCount ?: 0} active",
                        icon = TasksIcons.GroupFilled,
                        iconContainer = tint.container,
                        iconContent = tint.content,
                        iconShape = RoundedCornerShape(15.dp, 15.dp, 15.dp, 5.dp),
                    ) {
                        IconButton(onClick = { deleting = g }) { Icon(TasksIcons.Delete, "Delete ${g.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val g = editing
        StyleDialog(
            title = if (g == null) "New group" else "Edit group",
            initial = ChipStyle(g?.name.orEmpty(), g?.color ?: "#8c6d9e", g?.fgColor ?: "#ffffff", (g?.hasBg ?: 1) != 0),
            onDismiss = { creating = false; editing = null },
        ) { s, _ -> vm.saveGroup(g?.id, GroupWrite(s.name, s.color, s.fg, s.filled)) }
    }
    deleting?.let { g -> ConfirmDialog("Delete \"${g.name}\"?", "Tasks in it will be ungrouped.", "Delete", onDismiss = { deleting = null }) { vm.deleteGroup(g.id) } }
}

/** Name + colour (+ icon for tag types) with a live chip preview. */
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
    var s by remember { mutableStateOf(initial) }
    var applyAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip(s.name.ifBlank { "Preview" }, tagColors(s.color))
                OutlinedTextField(s.name, { s = s.copy(name = it) }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                // Text colour and fill are kept as they are: only the website draws them
                ColorField("Colour", s.color) { s = s.copy(color = it) }
                if (withIcon) {
                    OutlinedTextField(s.icon, { s = s.copy(icon = it.take(4)) }, label = { Text("Icon (emoji)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                }
                if (applyAllCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(applyAll, { applyAll = it })
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
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
