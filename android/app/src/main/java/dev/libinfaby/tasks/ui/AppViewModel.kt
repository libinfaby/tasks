package dev.libinfaby.tasks.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.libinfaby.tasks.data.api.GroupDto
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.userMessage
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.data.settings.Settings
import dev.libinfaby.tasks.data.settings.SettingsRepository
import dev.libinfaby.tasks.data.settings.ThemeMode
import dev.libinfaby.tasks.sync.SyncWorker
import dev.libinfaby.tasks.ui.tasks.TaskView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Top-level screens reachable from the drawer. */
sealed interface Destination {
    data class Tasks(val view: TaskView) : Destination
    data object DailyEntry : Destination
    data object DailyReport : Destination
    data object Tags : Destination
    data object Groups : Destination
    data object Settings : Destination
}

/** A task to open in the editor; [task] null means a new task. */
data class EditorRequest(val task: TaskDto?)

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: TasksRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    /** null until the first settings read, so the UI doesn't flash the sign-in screen. */
    val settings: StateFlow<Settings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groups: StateFlow<List<GroupDto>> = repo.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val openCounts: StateFlow<Map<Long, Int>> = repo.openTasks
        .map { tasks -> tasks.filter { it.groupId != null }.groupingBy { it.groupId!! }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val destination = MutableStateFlow<Destination>(Destination.Tasks(TaskView.All))
    val editor = MutableStateFlow<EditorRequest?>(null)

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError
    val signingIn = MutableStateFlow(false)

    fun signIn(apiUrl: String, password: String) {
        signingIn.value = true
        _signInError.value = null
        viewModelScope.launch {
            runCatching { repo.signIn(apiUrl, password) }
                .onSuccess { SyncWorker.schedulePeriodic(context) }
                .onFailure { _signInError.value = it.userMessage() }
            signingIn.value = false
        }
    }

    fun signOut() = viewModelScope.launch {
        SyncWorker.cancelAll(context)
        repo.signOut()
        destination.value = Destination.Tasks(TaskView.All)
    }

    /** Sync when the app comes to the foreground. */
    fun syncOnOpen() {
        viewModelScope.launch {
            if (settings.value?.signedIn ?: settingsRepo.current().signedIn) runCatching { repo.sync() }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }

    fun openNewTask() { editor.value = EditorRequest(null) }

    /** Opens a task by id (from a notification or the widget), from the cache. */
    fun openTask(id: Long) = viewModelScope.launch {
        repo.cachedTask(id)?.let { editor.value = EditorRequest(it) }
    }
}
