package dev.libinfaby.tasks.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Shapes mirror the worker's JSON (worker/src/routes). SQLite booleans arrive as 0/1 ints.

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class TokenResponse(val token: String)
@Serializable data class MessageResponse(val message: String? = null)
@Serializable data class CreatedResponse(val id: Long)
@Serializable data class ErrorResponse(val error: String? = null)

@Serializable
data class TagDto(
    val id: Long,
    val name: String,
    @SerialName("tag_type_id") val tagTypeId: Long? = null,
    val color: String? = null,
    @SerialName("fg_color") val fgColor: String? = null,
    @SerialName("has_bg") val hasBg: Int? = null,
    @SerialName("type_name") val typeName: String? = null,
    @SerialName("type_color") val typeColor: String? = null,
)

@Serializable
data class TagTypeDto(
    val id: Long,
    val name: String,
    val color: String = "#6366f1",
    @SerialName("fg_color") val fgColor: String? = null,
    @SerialName("has_bg") val hasBg: Int? = null,
    val icon: String? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
data class GroupDto(
    val id: Long,
    val name: String,
    val color: String = "#8b5cf6",
    @SerialName("fg_color") val fgColor: String? = null,
    @SerialName("has_bg") val hasBg: Int? = null,
    val position: Int? = null,
    @SerialName("task_count") val taskCount: Int? = null,
    @SerialName("active_task_count") val activeTaskCount: Int? = null,
)

@Serializable
data class SubtaskDto(
    val id: Long,
    @SerialName("task_id") val taskId: Long,
    val title: String,
    @SerialName("is_completed") val isCompleted: Int = 0,
    val position: Int = 0,
    val tags: List<TagDto> = emptyList(),
)

/** The only raised priority. High (1) was retired: the server stores any non-zero priority as urgent. */
const val PRIORITY_URGENT = 2

@Serializable
data class TaskDto(
    val id: Long,
    val title: String,
    val details: String? = null,
    @SerialName("is_completed") val isCompleted: Int = 0,
    val priority: Int = 0,
    val date: String? = null,
    val reminder: String? = null,
    @SerialName("reminder_repeat") val reminderRepeat: String? = null,
    @SerialName("group_id") val groupId: Long? = null,
    val position: Int = 0,
    val subtasks: List<SubtaskDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val group: GroupDto? = null,
) {
    val completed get() = isCompleted != 0
}

@Serializable
data class DailyLogDto(
    val id: Long,
    @SerialName("log_date") val logDate: String,
    val text: String,
)

@Serializable data class TasksResponse(val tasks: List<TaskDto> = emptyList())
@Serializable data class TagTypesResponse(@SerialName("tag_types") val tagTypes: List<TagTypeDto> = emptyList())
@Serializable data class GroupsResponse(val groups: List<GroupDto> = emptyList())
@Serializable data class DailyLogsResponse(val logs: List<DailyLogDto> = emptyList())

/**
 * Body for POST and PUT /tasks. PUT nulls any field it isn't sent, so this always carries the whole task
 * (nulls are encoded explicitly). `subtasks` is only read on create.
 */
@Serializable
data class TaskWrite(
    val title: String,
    val details: String?,
    val priority: Int,
    val date: String?,
    val reminder: String?,
    @SerialName("reminder_repeat") val reminderRepeat: String?,
    @SerialName("group_id") val groupId: Long?,
    @SerialName("tag_ids") val tagIds: List<Long>,
    val subtasks: List<SubtaskWrite>? = null,
)

@Serializable
data class SubtaskWrite(
    val title: String,
    @SerialName("tag_ids") val tagIds: List<Long> = emptyList(),
    @SerialName("task_id") val taskId: Long? = null,
)

@Serializable
data class TagTypeWrite(
    val name: String,
    val color: String,
    @SerialName("fg_color") val fgColor: String,
    @SerialName("has_bg") val hasBg: Boolean,
    val icon: String? = null,
)

@Serializable
data class TagWrite(
    val name: String,
    @SerialName("tag_type_id") val tagTypeId: Long,
    val color: String,
    @SerialName("fg_color") val fgColor: String,
    @SerialName("has_bg") val hasBg: Boolean,
)

@Serializable
data class GroupWrite(
    val name: String,
    val color: String,
    @SerialName("fg_color") val fgColor: String,
    @SerialName("has_bg") val hasBg: Boolean,
)

@Serializable data class DailyLogWrite(val date: String, val text: String)

/** Preferences shared with the web app (GET/PUT /settings). */
@Serializable data class SettingsDto(
    @SerialName("default_group_id") val defaultGroupId: Long? = null,
    /** Groups whose tasks only show inside the group, not in All tasks, Today or Upcoming. */
    @SerialName("hidden_group_ids") val hiddenGroupIds: List<Long> = emptyList(),
)
// PUT /settings changes only the keys it's sent, so each setting has its own body
@Serializable data class DefaultGroupWrite(@SerialName("default_group_id") val defaultGroupId: Long?)
@Serializable data class HiddenGroupsWrite(@SerialName("hidden_group_ids") val hiddenGroupIds: List<Long>)
@Serializable data class SettingsResponse(val settings: SettingsDto = SettingsDto())
@Serializable data class DailyLogUpdate(val text: String)
