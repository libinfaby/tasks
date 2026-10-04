package dev.libinfaby.tasks.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.api.tasksJson
import dev.libinfaby.tasks.data.db.ReminderDao
import dev.libinfaby.tasks.data.db.ScheduledReminderEntity
import dev.libinfaby.tasks.data.db.TaskDao
import dev.libinfaby.tasks.data.db.TaskEntity
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.domain.RepeatRule
import dev.libinfaby.tasks.domain.nextOccurrence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one exact alarm per open task that has a reminder. [reconcile] compares the cached tasks with
 * what's scheduled: new or changed reminders get (re)armed, removed ones are cancelled.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** A one-off reminder this late is still shown (e.g. it came due just before a sync); older ones are dropped. */
    private val lateGrace = Duration.ofMinutes(10)

    suspend fun reconcile(now: Instant = Instant.now()) {
        val tasks = taskDao.all().filter { it.reminder != null }
        val scheduled = reminderDao.all().associateBy { it.taskId }

        (scheduled.keys - tasks.map { it.id }.toSet()).forEach { cancel(it) }

        for (task in tasks) {
            val previous = scheduled[task.id]
            val reminder = task.reminder!!
            val due = Dates.parseInstant(reminder) ?: continue
            val rule = RepeatRule.fromWire(task.reminderRepeat)
            val trigger: String? = when {
                due.isAfter(now) -> reminder
                rule != null -> nextOccurrence(reminder, rule, ZoneId.systemDefault(), now)
                // Already armed for this exact reminder: the alarm has fired (or will imminently).
                previous?.reminder == reminder -> null
                Duration.between(due, now) <= lateGrace -> reminder
                else -> null
            }
            if (trigger == null) {
                if (previous != null && previous.reminder != reminder) cancel(task.id)
                continue
            }
            arm(task, trigger)
        }
    }

    /** After a repeating reminder fires, arm the next one straight away (works offline). */
    suspend fun armNext(taskId: Long, firedReminder: String, rule: RepeatRule) {
        val task = taskDao.get(taskId) ?: return
        val next = nextOccurrence(firedReminder, rule, ZoneId.systemDefault())
        // Show the new time right away; the server advances its copy too, and the next sync agrees.
        val dto = runCatching { tasksJson.decodeFromString(TaskDto.serializer(), task.json) }.getOrNull()
        val json = dto?.let { tasksJson.encodeToString(TaskDto.serializer(), it.copy(reminder = next)) } ?: task.json
        val updated = task.copy(reminder = next, json = json)
        taskDao.upsert(updated)
        arm(updated, next)
    }

    private suspend fun arm(task: TaskEntity, triggerIso: String) {
        val at = Dates.parseInstant(triggerIso) ?: return
        val pi = pendingIntent(task.id)
        val triggerMs = maxOf(at.toEpochMilli(), System.currentTimeMillis() + 1_000)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
        reminderDao.upsert(
            ScheduledReminderEntity(
                taskId = task.id,
                reminder = triggerIso,
                repeat = task.reminderRepeat,
                title = task.title,
                body = null,
            )
        )
    }

    suspend fun cancel(taskId: Long) {
        alarms.cancel(pendingIntent(taskId))
        reminderDao.delete(taskId)
    }

    suspend fun cancelAll() {
        reminderDao.all().forEach { alarms.cancel(pendingIntent(it.taskId)) }
        reminderDao.clear()
    }

    private fun pendingIntent(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
