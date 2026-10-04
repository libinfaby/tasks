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

/** Screens. Root ones sit on the bottom bar; the rest open from the menu and go back to the last root. */
sealed interface Destination {
    val isRoot: Boolean get() = false

    data class Tasks(val view: TaskView) : Destination {
        override val isRoot get() = view !is TaskView.Group
    }
    data class Daily(val report: Boolean) : Destination {
        override val isRoot get() = true
    }
    data object Tags : Destination
    data object Groups : Destination
    data object Settings : Destination

    companion object {
        val Start: Destination = Tasks(TaskView.Today)
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

    private val _destination = MutableStateFlow(Destination.Start)
    val destination: StateFlow<Destination> = _destination
    private var lastRoot = Destination.Start
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

    fun go(d: Destination) {
        if (d.isRoot) lastRoot = d
        _destination.value = d
    }

    /** Back: a menu screen returns to the last tab, another tab returns to Today. False when already there. */
    fun back(): Boolean = when {
        !_destination.value.isRoot -> { _destination.value = lastRoot; true }
        _destination.value != Destination.Start -> { go(Destination.Start); true }
        else -> false
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }

    fun setWallpaperColors(on: Boolean) = viewModelScope.launch { settingsRepo.setWallpaperColors(on) }

    fun openNewTask() { editor.value = EditorRequest(null) }

    /** Opens a task by id (from a notification or the widget), from the cache. */
    fun openTask(id: Long) = viewModelScope.launch {
        repo.cachedTask(id)?.let { editor.value = EditorRequest(it) }
    }
}
