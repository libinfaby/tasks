package dev.libinfaby.tasks.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The sidebar/drawer task views (same set as the web app). */
sealed interface TaskView {
    val title: String
    data object All : TaskView { override val title = "All Tasks" }
    data object Today : TaskView { override val title = "Today" }
    data object Upcoming : TaskView { override val title = "Upcoming" }
    data object Priority : TaskView { override val title = "Priority" }
    data object Completed : TaskView { override val title = "Completed" }
    data class Group(val id: Long, val name: String) : TaskView { override val title get() = name }
}

enum class SearchType(val label: String) { TASK("Task name"), TAG("Any tag"), DATE("Date") }

data class Search(val text: String = "", val type: SearchType = SearchType.TASK, val tagTypeId: Long? = null)

/** Filter chips under the title: Completed toggle, priority, and a tag picked from a chip. */
data class Filters(val completed: Boolean = false, val priority: Int? = null, val tagId: Long? = null, val tagName: String? = null)

data class Section(val title: String, val tasks: List<TaskDto>, val color: String? = null, val group: GroupDto? = null)

data class TaskListState(
    val sections: List<Section> = emptyList(),
    val count: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

private data class Query(val view: TaskView, val filters: Filters, val search: Search)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(private val repo: TasksRepository) : ViewModel() {
    private val view = MutableStateFlow<TaskView>(TaskView.All)
    private val filters = MutableStateFlow(Filters())
    private val search = MutableStateFlow(Search())
    private val refreshTick = MutableStateFlow(0)

    val currentView: StateFlow<TaskView> = view
    val currentFilters: StateFlow<Filters> = filters
    val currentSearch: StateFlow<Search> = search

    val tagTypes: StateFlow<List<TagTypeDto>> = repo.tagTypes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<TaskListState> = combine(view, filters, search.debounce(300), refreshTick) { v, f, s, _ -> Query(v, f, s) }
        .flatMapLatest { q ->
            if (q.needsServer()) {
                // Completed tasks and searches are queried live, like the web app; re-run when the cache changes
                repo.openTasks.flatMapLatest {
                    flow {
                        emit(TaskListState(loading = true))
                        emit(
                            runCatching { repo.query(q.serverFilters()) }
                                .map { tasks -> buildState(q, if (q.view == TaskView.Priority) tasks.filter { it.priority > 0 } else tasks) }
                                .getOrElse { TaskListState(error = it.userMessage()) }
                        )
                    }
                }
            } else {
                repo.openTasks.flatMapLatest { open -> flow { emit(buildState(q, q.localFilter(open))) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState(loading = true))

    fun setView(v: TaskView) {
        view.value = v
        filters.value = Filters(completed = v == TaskView.Completed)
    }

    fun setSearch(s: Search) { search.value = s }
    fun toggleCompleted() = filters.update { it.copy(completed = !it.completed) }
    fun togglePriority(level: Int) = filters.update { it.copy(priority = if (it.priority == level) null else level) }
    fun setTagFilter(id: Long?, name: String? = null) = filters.update { it.copy(tagId = id, tagName = name) }
    fun clearToAll() = setView(TaskView.All)
    fun retry() = refreshTick.update { it + 1 }

    // ==================== Query building ====================

    private fun Query.needsServer() = filters.completed || search.text.isNotBlank()

    /** Same filters the web app sends (frontend/js/components/taskList.js). */
    private fun Query.serverFilters(): Map<String, String> = buildMap {
        val today = Dates.today()
        when (view) {
            TaskView.Today -> { put("date_from", today.toString()); put("date_to", today.toString()) }
            TaskView.Upcoming -> { put("date_from", today.toString()); put("date_to", today.plusDays(7).toString()) }
            is TaskView.Group -> put("group_id", view.id.toString())
            else -> {}
        }
        put("completed", filters.completed.toString())
        filters.priority?.let { put("priority", it.toString()) }
        filters.tagId?.let { put("tag_id", it.toString()) }
        if (search.text.isNotBlank()) {
            put("search", search.text.trim())
            put("search_type", if (search.type == SearchType.TASK) "task" else search.type.name.lowercase())
            search.tagTypeId?.let { put("search_tag_type", it.toString()) }
        }
    }

    /** The cache holds every open task, so the default views filter locally (and work offline). */
    private fun Query.localFilter(open: List<TaskDto>): List<TaskDto> {
        val today = Dates.today()
        return open.filter { t ->
            val date = Dates.parseDate(t.date)
            val inView = when (view) {
                TaskView.All, TaskView.Completed -> true
                TaskView.Today -> date == today
                TaskView.Upcoming -> date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(7))
                TaskView.Priority -> t.priority > 0
                is TaskView.Group -> t.groupId == view.id
            }
            inView &&
                (filters.priority == null || t.priority == filters.priority) &&
                (filters.tagId == null || t.tags.any { it.id == filters.tagId })
        }
    }

    /** "All" splits into Priority / Tasks / one section per group, like the web list; other views are flat. */
    private fun buildState(q: Query, tasks: List<TaskDto>): TaskListState {
        val sections = if (q.view == TaskView.All) {
            val priority = tasks.filter { it.priority > 0 && !it.completed }
            val ungrouped = tasks.filter { it.group == null && (it.priority == 0 || it.completed) }
            val groups = tasks.filter { it.group != null }.groupBy { it.group!!.id }.map { (_, ts) ->
                Section(ts.first().group!!.name, ts, ts.first().group!!.color, ts.first().group)
            }
            buildList {
                if (priority.isNotEmpty()) add(Section("Priority", priority, PRIORITY_COLOR))
                if (ungrouped.isNotEmpty()) add(Section("Tasks", ungrouped, ACCENT_COLOR))
                addAll(groups)
            }
        } else if (tasks.isEmpty()) emptyList() else listOf(Section("", tasks))
        return TaskListState(sections = sections, count = tasks.size)
    }

    // ==================== Actions ====================

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages
    fun consumeMessage() { _messages.value = null }

    fun toggle(task: TaskDto, onCompleted: (TaskDto) -> Unit) = viewModelScope.launch {
        runCatching { repo.toggleTask(task.id) }
            .onSuccess { if (!task.completed) onCompleted(task); retry() }
            .onFailure { _messages.value = it.userMessage() }
    }

    fun toggleSubtask(id: Long) = viewModelScope.launch {
        runCatching { repo.toggleSubtask(id) }.onSuccess { retry() }.onFailure { _messages.value = it.userMessage() }
    }

    fun addToDaily(text: String) = viewModelScope.launch {
        runCatching { repo.addDailyLog(Dates.today(), text) }
            .onSuccess { _messages.value = "Added to Daily Tasks" }
            .onFailure { _messages.value = it.userMessage() }
    }

    fun refresh(onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repo.sync() }.onFailure { _messages.value = it.userMessage() }
        retry()
        onDone()
    }

    private companion object {
        const val PRIORITY_COLOR = "priority"
        const val ACCENT_COLOR = "accent"
    }
}
