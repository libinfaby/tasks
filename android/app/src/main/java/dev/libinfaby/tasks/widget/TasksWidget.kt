package dev.libinfaby.tasks.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.libinfaby.tasks.MainActivity
import dev.libinfaby.tasks.R
import dev.libinfaby.tasks.data.api.TaskDto
import dev.libinfaby.tasks.data.repo.TasksRepository
import dev.libinfaby.tasks.domain.Dates
import dev.libinfaby.tasks.ui.theme.TasksColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: open tasks dated today or earlier (overdue first), each with a tap-to-complete
 * checkbox, and a + that opens the new-task sheet. Reads the Room cache, so it works offline.
 */
class TasksWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): TasksRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(context, Deps::class.java).repository()
        val initial = repo.openTasks.first()
        provideContent {
            // Collected inside the composition so cache changes (sync, widget ticks) redraw the live session
            val open by repo.openTasks.collectAsState(initial)
            val today = Dates.today()
            val due = open
                .filter { t -> Dates.parseDate(t.date)?.let { !it.isAfter(today) } == true }
                .sortedWith(compareBy<TaskDto>({ it.date }, { -it.priority }))
            Content(context, due)
        }
    }

    @Composable
    private fun Content(context: Context, tasks: List<TaskDto>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(WidgetColors.surface).cornerRadius(28.dp).padding(16.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today",
                    style = TextStyle(color = WidgetColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    if (tasks.isEmpty()) "" else tasks.size.toString(),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Box(
                    modifier = GlanceModifier.size(40.dp).cornerRadius(14.dp).background(WidgetColors.accent)
                        .clickable(actionStartActivity(newTaskIntent(context)))
                        .semantics { contentDescription = "New task" },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(WidgetColors.onAccent),
                        modifier = GlanceModifier.size(22.dp),
                    )
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (tasks.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("All clear for today", style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp))
                }
            } else {
                LazyColumn {
                    items(tasks, itemId = { it.id }) { task -> TaskRow(context, task) }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(context: Context, task: TaskDto) {
        val ring = if (task.priority > 0) WidgetColors.danger else WidgetColors.ring
        val overdue = Dates.isOverdue(task.date)
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                .clickable(actionStartActivity(openTaskIntent(context, task.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.widget_check),
                contentDescription = "Complete ${task.title}",
                colorFilter = ColorFilter.tint(ring),
                modifier = GlanceModifier.size(22.dp)
                    .clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(ToggleTaskAction.TaskId to task.id))),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    task.title,
                    maxLines = 1,
                    style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                val reminderToday = task.reminder?.let { Dates.parseInstant(it) }
                    ?.takeIf { it.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == Dates.today() }
                val meta = listOfNotNull(
                    if (overdue) Dates.formatDate(task.date) else null,
                    reminderToday?.let { Dates.formatTime(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        maxLines = 1,
                        style = TextStyle(
                            color = if (overdue) WidgetColors.danger else WidgetColors.muted,
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }

    private fun newTaskIntent(context: Context) = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_NEW_TASK)

    // A distinct action per task keeps each row's PendingIntent separate
    private fun openTaskIntent(context: Context, id: Long) = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_TASK + id)
        .putExtra(MainActivity.EXTRA_OPEN_TASK, id)

    companion object {
        suspend fun refresh(context: Context) = runCatching { TasksWidget().updateAll(context) }
    }
}

/** The app's monochrome scheme as day/night pairs; the widget follows the phone's theme. */
private object WidgetColors {
    private val L = TasksColors.Light
    private val D = TasksColors.Dark
    val surface = ColorProvider(day = L.surfaceContainerLow, night = D.surfaceContainer)
    val text = ColorProvider(day = L.onSurface, night = D.onSurface)
    val muted = ColorProvider(day = L.onSurfaceVariant, night = D.onSurfaceVariant)
    val accent = ColorProvider(day = L.primaryContainer, night = D.primaryContainer)
    val onAccent = ColorProvider(day = L.onPrimaryContainer, night = D.onPrimaryContainer)
    val danger = ColorProvider(day = L.error, night = D.error)
    val ring = ColorProvider(day = L.outline, night = D.outline)
}

/** Completes a task from the widget. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskId] ?: return
        val repo = EntryPointAccessors.fromApplication(context, TasksWidget.Deps::class.java).repository()
        runCatching { repo.toggleTask(id) }
        TasksWidget.refresh(context)
    }

    companion object {
        val TaskId = ActionParameters.Key<Long>("task_id")
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
