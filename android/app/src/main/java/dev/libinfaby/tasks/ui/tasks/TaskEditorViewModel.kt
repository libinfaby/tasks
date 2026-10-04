package dev.libinfaby.tasks.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.PRIORITY_URGENT
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.data.api.TagWrite
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.TaskWrite
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.SubtaskDraft
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.domain.toWireIso
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** A subtask row in the editor; [key] keeps list identity stable while editing. */
data class SubtaskRow(val key: Long, val id: Long?, val title: String, val tagIds: Set<Long>)

data class TaskDraft(
    val original: TaskDto? = null,
    val title: String = "",
    val details: String = "",
    val date: LocalDate? = null,
    val reminder: LocalDateTime? = null,
    val repeat: RepeatRule? = null,
    val priority: Int = 0,
    val groupId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val subtasks: List<SubtaskRow> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val isEdit get() = original != null
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(private val repo: TasksRepository) : ViewModel() {
    private val _draft = MutableStateFlow(TaskDraft())
    val draft: StateFlow<TaskDraft> = _draft

    val tagTypes: StateFlow<List<TagTypeDto>> = repo.tagTypes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups: StateFlow<List<GroupDto>> = repo.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var nextKey = 0L

    fun open(task: TaskDto?) {
        _draft.value = if (task == null) {
            // New tasks default to today, like the web form
            TaskDraft(date = Dates.today())
        } else {
            TaskDraft(
                original = task,
                title = task.title,
                details = task.details.orEmpty(),
                date = Dates.parseDate(task.date),
                reminder = Dates.parseInstant(task.reminder)?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
                repeat = RepeatRule.fromWire(task.reminderRepeat),
                priority = if (task.priority > 0) PRIORITY_URGENT else 0,
                groupId = task.groupId,
                tagIds = task.tags.map { it.id }.toSet(),
                subtasks = task.subtasks.map { SubtaskRow(nextKey++, it.id, it.title, it.tags.map { t -> t.id }.toSet()) },
            )
        }
    }

    fun update(transform: (TaskDraft) -> TaskDraft) = _draft.update { transform(it).copy(error = null) }

    fun toggleTag(id: Long) = update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }

    fun addSubtask() = update { it.copy(subtasks = it.subtasks + SubtaskRow(nextKey++, null, "", emptySet())) }
    fun editSubtask(key: Long, transform: (SubtaskRow) -> SubtaskRow) =
        update { d -> d.copy(subtasks = d.subtasks.map { if (it.key == key) transform(it) else it }) }
    fun removeSubtask(key: Long) = update { d -> d.copy(subtasks = d.subtasks.filterNot { it.key == key }) }

    /** Creates a tag in [type] (inheriting its colours, as the web form does) and selects it. */
    fun createTag(type: TagTypeDto, name: String) = viewModelScope.launch {
        runCatching {
            repo.saveTag(null, TagWrite(name.trim(), type.id, type.color, type.fgColor ?: "#ffffff", (type.hasBg ?: 1) != 0))
        }.onSuccess {
            // saveTag refreshed the cached tag types, so the new tag is there now
            repo.tagTypes.first().firstOrNull { it.id == type.id }
                ?.tags?.firstOrNull { it.name.equals(name.trim(), true) }
                ?.let { toggleTag(it.id) }
        }.onFailure { e -> update { it.copy(error = e.userMessage()) } }
    }

    fun save(onDone: () -> Unit) {
        val d = _draft.value
        if (d.title.isBlank()) {
            update { it.copy(error = "Title is required") }
            return
        }
        val reminderIso = d.reminder?.atZone(ZoneId.systemDefault())?.toInstant()?.toWireIso()
        val write = TaskWrite(
            title = d.title.trim(),
            details = d.details.trim().ifEmpty { null },
            priority = d.priority,
            date = d.date?.toString(),
            reminder = reminderIso,
            // A repeat rule only means something alongside a reminder
            reminderRepeat = if (reminderIso != null) d.repeat?.wire else null,
            groupId = d.groupId,
            tagIds = d.tagIds.toList(),
        )
        val subtasks = d.subtasks.filter { it.title.isNotBlank() }.map { SubtaskDraft(it.id, it.title.trim(), it.tagIds.toList()) }
        _draft.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                if (d.original == null) repo.createTask(write, subtasks) else repo.updateTask(d.original, write, subtasks)
            }.onSuccess { onDone() }
                .onFailure { e -> _draft.update { it.copy(saving = false, error = e.userMessage()) } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val task = _draft.value.original ?: return
        _draft.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.deleteTask(task.id) }
                .onSuccess { onDone() }
                .onFailure { e -> _draft.update { it.copy(saving = false, error = e.userMessage()) } }
        }
    }
}
