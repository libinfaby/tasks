package dev.libinfaby.tasks.ui

import android.content.Context
import android.widget.Toast
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

/** Screens. Root ones sit on the bottom bar; the rest open from the menu and stack on top of it. */
sealed interface Destination {
    val isRoot: Boolean get() = false

    data class Tasks(val view: TaskView) : Destination {
        override val isRoot get() = view !is TaskView.Group
    }
    data class Daily(val report: Boolean) : Destination {
        override val isRoot get() = true
    }
    data object Menu : Destination
    data object Tags : Destination
    data object Groups : Destination
    data object Settings : Destination

    companion object {
        val Start: Destination = Tasks(TaskView.All)
    }
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

    /** The way back: a root tab first, then any screens opened on top of it. */
    private val _stack = MutableStateFlow(listOf(Destination.Start))
    val stack: StateFlow<List<Destination>> = _stack
    val destination: Destination get() = _stack.value.last()
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
        go(Destination.Start)
    }

    /** Sync when the app comes to the foreground. */
    fun syncOnOpen() {
        viewModelScope.launch {
            if (settings.value?.signedIn ?: settingsRepo.current().signedIn) runCatching { repo.sync() }
        }
    }

    /** A tab replaces the whole stack; any other screen opens on top of the current one. */
    fun go(d: Destination) {
        _stack.value = if (d.isRoot) listOf(d) else _stack.value + d
    }

    /** Back: close the top screen, or return from another tab to All tasks. False when already there. */
    fun back(): Boolean = when {
        _stack.value.size > 1 -> { _stack.value = _stack.value.dropLast(1); true }
        destination != Destination.Start -> { go(Destination.Start); true }
        else -> false
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }

    fun setWallpaperColors(on: Boolean) = viewModelScope.launch { settingsRepo.setWallpaperColors(on) }

    fun setDefaultGroup(id: Long?) = viewModelScope.launch {
        runCatching { repo.setDefaultGroup(id) }
            .onFailure { Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show() }
    }

    fun setGroupHidden(id: Long, hidden: Boolean) = viewModelScope.launch {
        runCatching { repo.setGroupHidden(id, hidden) }
            .onFailure { Toast.makeText(context, it.userMessage(), Toast.LENGTH_SHORT).show() }
    }

    fun openNewTask() { editor.value = EditorRequest(null) }

    /** Opens a task by id (from a notification or the widget), from the cache. */
    fun openTask(id: Long) = viewModelScope.launch {
        repo.cachedTask(id)?.let { editor.value = EditorRequest(it) }
    }
}
