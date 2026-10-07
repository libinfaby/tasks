package dev.libinfaby.tasks.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.PRIORITY_URGENT
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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

/** The task views: three bottom-bar tabs plus one per group. */
sealed interface TaskView {
    val title: String
    data object All : TaskView { override val title = "All tasks" }
    data object Today : TaskView { override val title = "Today" }
    data object Upcoming : TaskView { override val title = "Upcoming" }
    data class Group(val id: Long, val name: String) : TaskView { override val title get() = name }
}

enum class SearchType(val label: String) { TASK("Task name"), TAG("Any tag"), DATE("Date") }

data class Search(val text: String = "", val type: SearchType = SearchType.TASK, val tagTypeId: Long? = null)

/** Filter chips under the header: Done toggle, urgent only, and a tag picked from a chip. */
data class Filters(val completed: Boolean = false, val urgent: Boolean = false, val tagId: Long? = null, val tagName: String? = null) {
    val any get() = completed || urgent || tagId != null
}

enum class SectionKind { OVERDUE, URGENT, TASKS, GROUP, DAY, PLAIN }

data class Section(val title: String, val tasks: List<TaskDto>, val kind: SectionKind, val group: GroupDto? = null)

data class TaskListState(
    val sections: List<Section> = emptyList(),
    val count: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
)

private data class Query(val view: TaskView, val filters: Filters, val search: Search)

/** The daily log line for a task: "Client: Title(subtask 1, subtask 2)", without the parts it doesn't have. */
fun TaskDto.dailyLogEntry(): String {
    val clients = tags.filter { it.typeName.equals("client", true) }.map { it.name }
    val subs = subtasks.map { it.title.trim() }.filter { it.isNotEmpty() }
    val text = if (subs.isEmpty()) title else "$title(${subs.joinToString(", ")})"
    return if (clients.isEmpty()) text else "${clients.joinToString(", ")}: $text"
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(private val repo: TasksRepository) : ViewModel() {
    private val view = MutableStateFlow<TaskView>(TaskView.All)
    private val filters = MutableStateFlow(Filters())
    private val search = MutableStateFlow(Search())
    private val refreshTick = MutableStateFlow(0)

    /** Tasks just ticked off: shown as done for a moment (so the animation plays) before they leave the list. */
    private val pending = MutableStateFlow<Map<Long, TaskDto>>(emptyMap())

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
                                .map { tasks -> buildState(q, tasks, emptySet()) }
                                .getOrElse { TaskListState(error = it.userMessage()) }
                        )
                    }
                }
            } else {
                combine(repo.openTasks, pending) { open, held ->
                    // DAO order: priority, then newest first
                    val merged = (open.filterNot { it.id in held } + held.values)
                        .sortedWith(compareByDescending<TaskDto> { it.priority }.thenByDescending { it.id })
                    buildState(q, q.localFilter(merged), held.keys)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState(loading = true))

    fun setView(v: TaskView) {
        view.value = v
        filters.value = Filters()
    }

    fun setSearch(s: Search) { search.value = s }
    fun toggleCompleted() = filters.update { it.copy(completed = !it.completed) }
    fun toggleUrgent() = filters.update { it.copy(urgent = !it.urgent) }
    fun setTagFilter(id: Long?, name: String? = null) = filters.update { it.copy(tagId = id, tagName = name) }
    fun clearFilters() { filters.value = Filters() }
    fun retry() = refreshTick.update { it + 1 }

    // ==================== Query building ====================

    private fun Query.needsServer() = filters.completed || search.text.isNotBlank()

    /** Same filters the web app sends (frontend/js/components/taskList.js). */
    private fun Query.serverFilters(): Map<String, String> = buildMap {
        val today = Dates.today()
        when (view) {
            TaskView.Today -> { put("date_from", today.toString()); put("date_to", today.toString()) }
            TaskView.Upcoming -> { put("date_from", today.plusDays(1).toString()); put("date_to", today.plusDays(7).toString()) }
            is TaskView.Group -> put("group_id", view.id.toString())
            TaskView.All -> {}
        }
        put("completed", filters.completed.toString())
        if (filters.urgent) put("priority", PRIORITY_URGENT.toString())
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
                TaskView.All -> true
                // Today also carries anything overdue, so nothing slips through the cracks
                TaskView.Today -> date != null && !date.isAfter(today)
                TaskView.Upcoming -> date != null && date.isAfter(today) && !date.isAfter(today.plusDays(7))
                is TaskView.Group -> t.groupId == view.id
            }
            inView &&
                (!filters.urgent || t.priority > 0) &&
                (filters.tagId == null || t.tags.any { it.id == filters.tagId })
        }
    }

    /**
     * Today: Overdue, then Urgent / Tasks / one section per group (as "All" splits, like the web list).
     * Upcoming: one section per day. Groups and server results are flat.
     */
    private fun buildState(q: Query, tasks: List<TaskDto>, held: Set<Long>): TaskListState {
        val today = Dates.today()
        // A task ticked off a moment ago keeps its place until it leaves
        fun TaskDto.settledDone() = completed && id !in held
        fun split(ts: List<TaskDto>): List<Section> {
            val urgent = ts.filter { it.priority > 0 && !it.settledDone() }
            val ungrouped = ts.filter { it.group == null && (it.priority == 0 || it.settledDone()) }
            // As on web, a grouped urgent task shows under Urgent and under its group
            val groups = ts.filter { it.group != null }
                .groupBy { it.group!!.id }
                .map { (_, g) -> Section(g.first().group!!.name, g, SectionKind.GROUP, g.first().group) }
            return buildList {
                if (urgent.isNotEmpty()) add(Section("Urgent", urgent, SectionKind.URGENT))
                if (ungrouped.isNotEmpty()) add(Section("Tasks", ungrouped, SectionKind.TASKS))
                addAll(groups)
            }
        }
        val sections = when {
            q.needsServer() || q.view is TaskView.Group -> if (tasks.isEmpty()) emptyList() else listOf(Section("", tasks, SectionKind.PLAIN))
            q.view == TaskView.Today -> {
                val (overdue, rest) = tasks.partition { Dates.parseDate(it.date)?.isBefore(today) == true }
                buildList {
                    if (overdue.isNotEmpty()) add(Section("Overdue", overdue, SectionKind.OVERDUE))
                    addAll(split(rest))
                }
            }
            q.view == TaskView.Upcoming -> tasks
                .groupBy { Dates.parseDate(it.date)!! }
                .toSortedMap()
                .map { (day, ts) -> Section(Dates.dayLabel(day), ts, SectionKind.DAY) }
            else -> split(tasks)
        }
        return TaskListState(sections = sections, count = tasks.count { !it.settledDone() })
    }

    // ==================== Actions ====================

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages
    fun consumeMessage() { _messages.value = null }

    fun toggle(task: TaskDto, onCompleted: (TaskDto) -> Unit) {
        val completing = !task.completed
        if (completing) pending.update { it + (task.id to task.copy(isCompleted = 1)) }
        viewModelScope.launch {
            runCatching { repo.toggleTask(task.id) }
                .onSuccess {
                    if (completing) {
                        onCompleted(task)
                        delay(LINGER_MS)
                    }
                    pending.update { it - task.id }
                    retry()
                }
                .onFailure {
                    pending.update { it - task.id }
                    _messages.value = it.userMessage()
                }
        }
    }

    fun toggleSubtask(id: Long) = viewModelScope.launch {
        runCatching { repo.toggleSubtask(id) }.onSuccess { retry() }.onFailure { _messages.value = it.userMessage() }
    }

    fun addToDaily(task: TaskDto) = viewModelScope.launch {
        runCatching { repo.addDailyLog(Dates.today(), task.dailyLogEntry()) }
            .onSuccess { _messages.value = "Added to today's daily log" }
            .onFailure { _messages.value = it.userMessage() }
    }

    fun refresh(onDone: () -> Unit) = viewModelScope.launch {
        runCatching { repo.sync() }.onFailure { _messages.value = it.userMessage() }
        retry()
        onDone()
    }

    private companion object {
        const val LINGER_MS = 1_200L
    }
}
