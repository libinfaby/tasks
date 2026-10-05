package dev.libinfaby.tasks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

// Twelve hues around the wheel at a Material tone-40 strength, plus a neutral. Tags and groups draw
// these as tonal pairs (tagColors), so each hue stays distinct once softened.
private val SWATCHES = listOf(
    "#b3261e" to "Red", "#a04100" to "Orange", "#7d5700" to "Amber", "#5b6300" to "Olive",
    "#2e6b30" to "Green", "#006a60" to "Teal", "#006782" to "Cyan", "#275ea8" to "Blue",
    "#4a5ba8" to "Indigo", "#6750a4" to "Purple", "#8a3f8a" to "Magenta", "#a23a5f" to "Pink",
    "#6f6f78" to "Neutral",
)

private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

/**
 * Colour choice as Material swatches: each circle shows the tone a tag or group takes, with a dot of
 * its text tone; the picked one gets a check and a ring. The last swatch opens a hex field for anything else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorField(label: String, hex: String, onChange: (String) -> Unit) {
    val preset = SWATCHES.any { it.first.equals(hex, ignoreCase = true) }
    var custom by remember { mutableStateOf(!preset) }
    Column {
        FieldLabel(label)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SWATCHES.forEach { (value, name) ->
                Swatch(tagColors(value), selected = !custom && value.equals(hex, ignoreCase = true), name = name) {
                    custom = false
                    onChange(value)
                }
            }
            val customColors = if (custom) tagColors(hex)
            else ToggleColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
            Swatch(customColors, selected = custom, name = "Custom colour", icon = TasksIcons.Palette) { custom = true }
        }
        AnimatedVisibility(custom) {
            val valid = HEX.matches(hex)
            OutlinedTextField(
                value = hex,
                onValueChange = { onChange(it.trim()) },
                label = { Text("Hex") },
                isError = !valid,
                supportingText = if (valid) null else ({ Text("Use a hex colour like #4a5ba8") }),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Swatch(colors: ToggleColors, selected: Boolean, name: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(46.dp).border(2.dp, colors.content, CircleShape))
        Box(Modifier.size(38.dp).background(colors.container, CircleShape), contentAlignment = Alignment.Center) {
            when {
                selected -> Icon(TasksIcons.Check, null, tint = colors.content, modifier = Modifier.size(20.dp))
                icon != null -> Icon(icon, null, tint = colors.content, modifier = Modifier.size(20.dp))
                else -> Box(Modifier.size(12.dp).background(colors.content, CircleShape))
            }
        }
    }
}
