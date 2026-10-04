package dev.libinfaby.tasks

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.libinfaby.tasks.data.settings.ThemeMode
import dev.libinfaby.tasks.reminders.Notifications
import dev.libinfaby.tasks.ui.AppRoot
import dev.libinfaby.tasks.ui.AppViewModel
import dev.libinfaby.tasks.ui.theme.TasksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            TasksTheme(settings?.theme ?: ThemeMode.SYSTEM) {
                // Reminders are the point of the app, so ask once the user is signed in
                LaunchedEffect(settings?.signedIn) {
                    if (settings?.signedIn == true && !Notifications.canPost(this@MainActivity)) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Notification taps open the task; the widget's + opens a new one. */
    private fun handle(intent: Intent?) {
        when {
            intent == null -> Unit
            intent.action == ACTION_NEW_TASK -> vm.openNewTask()
            intent.hasExtra(EXTRA_OPEN_TASK) -> vm.openTask(intent.getLongExtra(EXTRA_OPEN_TASK, -1))
        }
    }

    companion object {
        const val ACTION_NEW_TASK = "dev.libinfaby.tasks.NEW_TASK"
        const val ACTION_OPEN_TASK = "dev.libinfaby.tasks.OPEN_TASK/"
        const val EXTRA_OPEN_TASK = "open_task_id"
    }
}
