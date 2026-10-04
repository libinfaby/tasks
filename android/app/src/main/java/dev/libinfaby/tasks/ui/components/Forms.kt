package dev.libinfaby.tasks.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.libinfaby.tasks.ui.theme.TasksIcons
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(bottom = 10.dp))
}

/** Top bar for screens opened from the account menu: back arrow and a display-font title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TasksIcons.Back, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogFor(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The Material picker works in UTC-midnight millis
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogFor(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, destructive: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val SWATCHES = listOf(
    "#e5484d", "#ef6c1a", "#f5b400", "#30a46c", "#12a594", "#0090ff", "#1a5fb4", "#5b5bd6",
    "#8e4ec6", "#d6409f", "#613583", "#a51d2d", "#1d5609", "#71717a", "#18181b", "#ffffff",
)

/** Colour choice: preset swatches plus a hex field (same values the web colour input stores). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorField(label: String, hex: String, onChange: (String) -> Unit) {
    Column {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { s ->
                val selected = s.equals(hex, ignoreCase = true)
                val color = parseHex(s)
                // The picked swatch turns into a scallop with a check, like a completed task
                Surface(
                    onClick = { onChange(s) },
                    shape = if (selected) Scallop else CircleShape,
                    color = color,
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, if (selected) Scallop else CircleShape)
                        .semantics { contentDescription = s; this.selected = selected },
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(TasksIcons.Check, null, tint = if (color.isLight()) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { onChange(it.trim()) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            prefix = {
                Box(
                    Modifier.padding(end = 8.dp).size(16.dp).clip(RoundedCornerShape(5.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
                ) { Surface(color = parseHex(hex, Color.Transparent), modifier = Modifier.size(16.dp)) {} }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Color.isLight() = 0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.6f
