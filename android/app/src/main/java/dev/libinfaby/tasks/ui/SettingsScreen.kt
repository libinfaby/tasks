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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.libinfaby.tasks.data.settings.Settings
import dev.libinfaby.tasks.data.settings.ThemeMode
import dev.libinfaby.tasks.reminders.Notifications
import dev.libinfaby.tasks.sync.SyncWorker
import dev.libinfaby.tasks.ui.components.ListCard
import dev.libinfaby.tasks.ui.components.RowDivider
import dev.libinfaby.tasks.ui.components.SectionHeader
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette
import java.text.DateFormat
import java.util.Date

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onTheme: (ThemeMode) -> Unit, onSignOut: () -> Unit, onOpenDrawer: () -> Unit) {
    val p = palette
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

    Scaffold(
        containerColor = p.bg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg),
                    navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(TasksIcons.Menu, "Menu", tint = p.textSecondary) } },
                    title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                )
                RowDivider()
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { onTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = p.accentSoft, activeContentColor = p.accentText, inactiveContainerColor = p.card),
                    ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.width(8.dp))
            SectionHeader("Reminders")
            ListCard {
                StatusRow("Notifications", if (canNotify) "Allowed" else "Off — reminders can't be shown", canNotify, "Allow") {
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                RowDivider()
                StatusRow("Exact timing", if (canExact) "Reminders fire on the minute" else "Reminders may be a few minutes late", canExact, "Open") {
                    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
                RowDivider()
                StatusRow("Battery", if (unrestricted) "Unrestricted — background sync is reliable" else "Optimised — sync may be delayed", unrestricted, "Allow") {
                    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
            }
            Text(
                "Tip: if this phone's browser also gets Tasks notifications, turn them off there to avoid duplicates.",
                style = MaterialTheme.typography.bodySmall,
                color = p.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            SectionHeader("Sync")
            ListCard {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Last synced", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (settings.lastSyncAt == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastSyncAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = p.textTertiary,
                        )
                    }
                    TextButton(onClick = { SyncWorker.syncNow(context) }) { Text("Sync now") }
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Server", style = MaterialTheme.typography.bodyMedium)
                    Text(settings.apiUrl, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
                }
            }

            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSignOut, shape = RoundedCornerShape(6.dp)) {
                Icon(TasksIcons.LogOut, null, tint = p.danger)
                Spacer(Modifier.width(8.dp))
                Text("Sign out", color = p.danger)
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, detail: String, ok: Boolean, action: String, onAction: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (ok) p.textTertiary else p.high)
        }
        if (!ok) TextButton(onClick = onAction) { Text(action) }
        else Icon(TasksIcons.Check, "OK", tint = p.success, modifier = Modifier.padding(end = 12.dp))
    }
}
