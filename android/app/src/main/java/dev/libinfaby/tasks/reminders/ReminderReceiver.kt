package dev.libinfaby.tasks.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.libinfaby.tasks.MainActivity
import dev.libinfaby.tasks.R
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.tasksJson
import dev.libinfaby.tasks.data.db.ReminderDao
import dev.libinfaby.tasks.data.db.TaskDao
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires a task's reminder notification and, for repeating reminders, arms the next one. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var reminderDao: ReminderDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        val pending = goAsync()
        scope.launch {
            try {
                val armed = reminderDao.get(taskId) ?: return@launch
                val task = taskDao.get(taskId)?.let { runCatching { tasksJson.decodeFromString(TaskDto.serializer(), it.json) }.getOrNull() }
                // The task was completed or deleted since the alarm was set
                if (task == null || task.completed) {
                    scheduler.cancel(taskId)
                    return@launch
                }
                Notifications.showReminder(context, task)
                val rule = RepeatRule.fromWire(armed.repeat)
                if (rule != null) scheduler.armNext(taskId, armed.reminder, rule)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Task reminders at the time you set"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showReminder(context: Context, task: TaskDto) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_TASK, task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val repeat = RepeatRule.fromWire(task.reminderRepeat)
        val subtitle = listOfNotNull(
            task.date?.let { Dates.formatDate(it) },
            repeat?.let { "Repeats ${it.label.lowercase()}" },
        ).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(task.details?.takeIf { it.isNotBlank() } ?: subtitle.ifEmpty { "Task reminder" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.details ?: subtitle))
            .setSubText(subtitle.ifEmpty { null })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }
}
