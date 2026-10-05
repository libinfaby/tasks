package dev.libinfaby.tasks.data.repo

import android.content.Context
import android.util.Base64
import dev.libinfaby.tasks.data.api.DailyLogDto
import dev.libinfaby.tasks.data.api.DailyLogUpdate
import dev.libinfaby.tasks.data.api.DailyLogWrite
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.GroupWrite
import dev.libinfaby.tasks.data.api.LoginRequest
import dev.libinfaby.tasks.data.api.SettingsDto
import dev.libinfaby.tasks.data.api.SubtaskWrite
import dev.libinfaby.tasks.data.api.TagTypeDto
import dev.libinfaby.tasks.data.api.TagTypeWrite
import dev.libinfaby.tasks.data.api.TagWrite
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.TaskWrite
import dev.libinfaby.tasks.data.api.TasksApi
import dev.libinfaby.tasks.data.api.tasksJson
import dev.libinfaby.tasks.data.db.BlobDao
import dev.libinfaby.tasks.data.db.BlobEntity
import dev.libinfaby.tasks.data.db.TaskDao
import dev.libinfaby.tasks.data.db.TaskEntity
import dev.libinfaby.tasks.data.settings.SettingsRepository
import dev.libinfaby.tasks.reminders.ReminderScheduler
import dev.libinfaby.tasks.widget.TasksWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A subtask as edited in the task sheet; [id] is null for new ones. */
data class SubtaskDraft(val id: Long?, val title: String, val tagIds: List<Long>)

/**
 * Online-first: reads come from the Room cache (open tasks, tag types, groups) and refresh from the
 * server; writes go to the server, then the cache is refreshed. Completed tasks and searches are
 * queried live, like the web app.
 */
@Singleton
class TasksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TasksApi,
    private val taskDao: TaskDao,
    private val blobDao: BlobDao,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    private val syncLock = Mutex()

    val openTasks: Flow<List<TaskDto>> = taskDao.observeAll().map { rows -> rows.mapNotNull { decodeTask(it.json) } }

    val tagTypes: Flow<List<TagTypeDto>> = blobDao.observe(KEY_TAG_TYPES).map { json ->
        json?.let { runCatching { tasksJson.decodeFromString(ListSerializer(TagTypeDto.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    val groups: Flow<List<GroupDto>> = blobDao.observe(KEY_GROUPS).map { json ->
        json?.let { runCatching { tasksJson.decodeFromString(ListSerializer(GroupDto.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    // ==================== Auth ====================

    suspend fun signIn(apiUrl: String, password: String) {
        settings.setApiUrl(apiUrl)
        val token = api.login(LoginRequest("admin", password)).token
        settings.setToken(token)
        sync()
    }

    suspend fun signOut() {
        settings.signOut()
        scheduler.cancelAll()
        taskDao.clear()
        blobDao.clear()
        TasksWidget.refresh(context)
    }

    /** Swap the 7-day token for a fresh one once it's within two days of expiring. */
    private suspend fun refreshTokenIfNeeded() {
        val token = settings.current().token ?: return
        val exp = jwtExpiry(token) ?: return
        if (Duration.between(Instant.now(), exp) < Duration.ofDays(2)) {
            settings.setToken(api.refresh().token)
        }
    }

    // ==================== Sync ====================

    /** Pull open tasks, tag types and groups, then re-arm reminders and redraw the widget. */
    suspend fun sync() = syncLock.withLock {
        refreshTokenIfNeeded()
        refreshTasksLocked()
        refreshMeta()
        settings.setLastSync(System.currentTimeMillis())
    }

    suspend fun refreshTasks() = syncLock.withLock { refreshTasksLocked() }

    private suspend fun refreshTasksLocked() {
        val open = api.tasks(mapOf("completed" to "false")).tasks
        taskDao.replaceAll(open.map(::toEntity))
        scheduler.reconcile()
        TasksWidget.refresh(context)
    }

    suspend fun refreshMeta() {
        val types = api.tagTypes().tagTypes
        val groups = api.groups().groups
        blobDao.put(BlobEntity(KEY_TAG_TYPES, tasksJson.encodeToString(ListSerializer(TagTypeDto.serializer()), types)))
        blobDao.put(BlobEntity(KEY_GROUPS, tasksJson.encodeToString(ListSerializer(GroupDto.serializer()), groups)))
        // Shared with the web app; kept locally so new tasks get it offline. An older server without
        // /settings leaves the local value alone.
        runCatching { api.settings().settings }.onSuccess { settings.setDefaultGroup(it.defaultGroupId) }
    }

    /** Saves the default group on the server (so the web app uses it too), then locally. */
    suspend fun setDefaultGroup(id: Long?) {
        api.updateSettings(SettingsDto(id))
        settings.setDefaultGroup(id)
    }

    /** Server-side query for views the cache doesn't hold (completed tasks, search). */
    suspend fun query(filters: Map<String, String>): List<TaskDto> = api.tasks(filters).tasks

    suspend fun cachedTask(id: Long): TaskDto? = taskDao.get(id)?.let { decodeTask(it.json) }

    // ==================== Tasks ====================

    suspend fun createTask(write: TaskWrite, subtasks: List<SubtaskDraft>) {
        api.createTask(write.copy(subtasks = subtasks.map { SubtaskWrite(it.title, it.tagIds) }))
        afterTaskChange()
    }

    /** Saves the task, then applies subtask edits the same way the web form does. */
    suspend fun updateTask(task: TaskDto, write: TaskWrite, subtasks: List<SubtaskDraft>) {
        api.updateTask(task.id, write)
        val keptIds = subtasks.mapNotNull { it.id }.toSet()
        task.subtasks.filter { it.id !in keptIds }.forEach { api.deleteSubtask(it.id) }
        subtasks.forEach { s ->
            if (s.id != null) api.updateSubtask(s.id, SubtaskWrite(s.title, s.tagIds))
            else api.createSubtask(SubtaskWrite(s.title, s.tagIds, taskId = task.id))
        }
        afterTaskChange()
    }

    suspend fun deleteTask(id: Long) {
        api.deleteTask(id)
        afterTaskChange()
    }

    suspend fun toggleTask(id: Long) {
        api.toggleTask(id)
        afterTaskChange()
    }

    suspend fun toggleSubtask(id: Long) {
        api.toggleSubtask(id)
        refreshTasks()
    }

    // Group counts depend on tasks, so they refresh with them.
    private suspend fun afterTaskChange() {
        refreshTasks()
        runCatching { refreshMeta() }
    }

    // ==================== Tags & groups ====================

    suspend fun saveTagType(id: Long?, write: TagTypeWrite, applyToTags: List<Long> = emptyList()) {
        if (id == null) api.createTagType(write) else api.updateTagType(id, write)
        applyToTags.forEach { tagId ->
            val tag = tagTypesSnapshot().flatMap { it.tags }.firstOrNull { it.id == tagId } ?: return@forEach
            api.updateTag(tagId, TagWrite(tag.name, tag.tagTypeId ?: id!!, write.color, write.fgColor, write.hasBg))
        }
        refreshMeta()
    }

    suspend fun deleteTagType(id: Long) { api.deleteTagType(id); refreshMeta(); refreshTasks() }

    suspend fun saveTag(id: Long?, write: TagWrite) {
        if (id == null) api.createTag(write) else api.updateTag(id, write)
        refreshMeta()
        if (id != null) refreshTasks()
    }

    suspend fun deleteTag(id: Long) { api.deleteTag(id); refreshMeta(); refreshTasks() }

    suspend fun saveGroup(id: Long?, write: GroupWrite) {
        if (id == null) api.createGroup(write) else api.updateGroup(id, write)
        refreshMeta()
        if (id != null) refreshTasks()
    }

    suspend fun deleteGroup(id: Long) { api.deleteGroup(id); refreshMeta(); refreshTasks() }

    private suspend fun tagTypesSnapshot(): List<TagTypeDto> = blobDao.get(KEY_TAG_TYPES)
        ?.let { tasksJson.decodeFromString(ListSerializer(TagTypeDto.serializer()), it) } ?: emptyList()

    // ==================== Daily log ====================

    suspend fun dailyLogs(date: LocalDate): List<DailyLogDto> = api.dailyLogs(mapOf("date" to date.toString())).logs

    suspend fun dailyLogs(from: LocalDate, to: LocalDate): List<DailyLogDto> =
        api.dailyLogs(mapOf("from" to from.toString(), "to" to to.toString())).logs

    suspend fun addDailyLog(date: LocalDate, text: String) { api.createDailyLog(DailyLogWrite(date.toString(), text)) }
    suspend fun updateDailyLog(id: Long, text: String) { api.updateDailyLog(id, DailyLogUpdate(text)) }
    suspend fun deleteDailyLog(id: Long) { api.deleteDailyLog(id) }

    // ==================== Helpers ====================

    private fun toEntity(t: TaskDto) = TaskEntity(
        id = t.id,
        title = t.title,
        priority = t.priority,
        date = t.date,
        reminder = t.reminder,
        reminderRepeat = t.reminderRepeat,
        groupId = t.groupId,
        json = tasksJson.encodeToString(TaskDto.serializer(), t),
    )

    private fun decodeTask(json: String) = runCatching { tasksJson.decodeFromString(TaskDto.serializer(), json) }.getOrNull()

    private fun jwtExpiry(token: String): Instant? = runCatching {
        val payload = token.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Json.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
    }.getOrNull()

    private companion object {
        const val KEY_TAG_TYPES = "tag_types"
        const val KEY_GROUPS = "groups"
    }
}
