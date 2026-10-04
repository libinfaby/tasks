package dev.libinfaby.tasks.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.ButtonDefaults
import dev.libinfaby.tasks.data.settings.Settings
import dev.libinfaby.tasks.data.settings.ThemeMode
import dev.libinfaby.tasks.reminders.Notifications
import dev.libinfaby.tasks.sync.SyncWorker
import dev.libinfaby.tasks.ui.components.BackTopBar
import dev.libinfaby.tasks.ui.components.ConnectedColumn
import dev.libinfaby.tasks.ui.components.ConnectedItem
import dev.libinfaby.tasks.ui.components.ConnectedToggleGroup
import dev.libinfaby.tasks.ui.components.FieldLabel
import dev.libinfaby.tasks.ui.components.ListRow
import dev.libinfaby.tasks.ui.theme.TasksIcons
import java.text.DateFormat
import java.util.Date

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(
    settings: Settings,
    onTheme: (ThemeMode) -> Unit,
    onWallpaperColors: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Re-read permission state when returning from system settings
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val canNotify = remember(tick) { Notifications.canPost(context) }
    val canExact = remember(tick) { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val unrestricted = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = { BackTopBar("Settings", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            FieldLabel("Appearance", Modifier.padding(start = 4.dp))
            ConnectedToggleGroup(
                options = ThemeMode.entries,
                selected = settings.theme,
                onSelect = onTheme,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                icon = { mode, _ ->
                    when (mode) {
                        ThemeMode.SYSTEM -> TasksIcons.AutoMode
                        ThemeMode.LIGHT -> TasksIcons.LightMode
                        ThemeMode.DARK -> TasksIcons.DarkMode
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            ConnectedItem(0, 1, onClick = { onWallpaperColors(!settings.wallpaperColors) }) {
                ListRow(
                    "Wallpaper colours",
                    supporting = if (settings.wallpaperColors) "Colours follow your wallpaper" else "Using the Grape palette",
                    icon = TasksIcons.Palette,
                    iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    iconContent = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Switch(settings.wallpaperColors, onWallpaperColors, thumbContent = if (settings.wallpaperColors) ({ Icon(TasksIcons.Check, null, modifier = Modifier.size(16.dp)) }) else null)
                }
            }

            Spacer(Modifier.height(28.dp))
            FieldLabel("Reminders", Modifier.padding(start = 4.dp))
            ConnectedColumn {
                StatusItem(0, TasksIcons.Notifications, "Notifications", if (canNotify) "Allowed" else "Off, so reminders can't be shown", canNotify, "Allow") {
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                StatusItem(1, TasksIcons.Timer, "Exact timing", if (canExact) "Reminders fire on the minute" else "Reminders may be a few minutes late", canExact, "Open") {
                    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                StatusItem(2, TasksIcons.Battery, "Battery", if (unrestricted) "Unrestricted, so background sync is reliable" else "Optimised, so sync may be delayed", unrestricted, "Allow") {
                    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
            }
            Text(
                "Tip: if this phone's browser also gets Tasks notifications, turn them off there to avoid duplicates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )

            Spacer(Modifier.height(18.dp))
            FieldLabel("Sync", Modifier.padding(start = 4.dp))
            ConnectedColumn {
                ConnectedItem(0, 2) {
                    ListRow(
                        "Last synced",
                        supporting = if (settings.lastSyncAt == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastSyncAt)),
                        icon = TasksIcons.Sync,
                        iconShape = CircleShape,
                    ) { TextButton(onClick = { SyncWorker.syncNow(context) }) { Text("Sync now") } }
                }
                ConnectedItem(1, 2) { ListRow("Server", supporting = settings.apiUrl, icon = TasksIcons.Server, iconShape = CircleShape) }
            }

            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onSignOut,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(TasksIcons.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign out", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A permission/health row: a check when fine, an action button when not. */
@Composable
private fun StatusItem(index: Int, icon: ImageVector, title: String, detail: String, ok: Boolean, action: String, onAction: () -> Unit) {
    ConnectedItem(index, 3) {
        ListRow(
            title,
            supporting = detail,
            supportingColor = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            icon = icon,
            iconContainer = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
            iconContent = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ) {
            if (ok) Icon(TasksIcons.Check, "OK", tint = MaterialTheme.colorScheme.primary)
            else FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}
