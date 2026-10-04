package dev.libinfaby.tasks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Bordered, rounded container for a list of rows (the web's `.task-list`). */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = palette
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = p.card,
        border = BorderStroke(1.dp, p.border),
    ) {
        Column { content() }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
}

@Composable
fun SectionHeader(title: String, count: Int? = null, dot: Color? = null, modifier: Modifier = Modifier) {
    val p = palette
    Row(modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), color = p.textPrimary)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), fontSize = 12.sp, color = p.textTertiary)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val p = palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(8.dp))
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(p.muted),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = p.textTertiary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = p.textPrimary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
fun tasksFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.borderStrong,
    focusedContainerColor = palette.card,
    unfocusedContainerColor = palette.card,
)

/** A read-only field that opens a picker when tapped. */
@Composable
fun PickerField(
    value: String,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    val p = palette
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, p.borderStrong, RoundedCornerShape(6.dp))
            .background(p.card)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            value.ifEmpty { placeholder },
            color = if (value.isEmpty()) p.textTertiary else p.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null && value.isNotEmpty()) {
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Icon(TasksIcons.X, "Clear", tint = p.textTertiary, modifier = Modifier.size(15.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Dropdown over a fixed set of options, rendered like a select. */
@Composable
fun <T> SelectField(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val p = palette
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, p.borderStrong, RoundedCornerShape(6.dp))
                .background(p.card)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke(value)
            Text(label(value), style = MaterialTheme.typography.bodyMedium, color = p.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(TasksIcons.ChevronRight, null, tint = p.textTertiary, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    leadingIcon = leading?.let { { it(option) } },
                    onClick = { onSelect(option); open = false },
                )
            }
        }
    }
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
                Text(confirm, color = if (destructive) palette.danger else palette.accentText, fontWeight = FontWeight.SemiBold)
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
    val p = palette
    Column {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SWATCHES.forEach { s ->
                val selected = s.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHex(s))
                        .border(if (selected) 2.dp else 1.dp, if (selected) p.accent else p.border, CircleShape)
                        .clickable { onChange(s) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { onChange(it.trim()) },
            singleLine = true,
            prefix = {
                Box(Modifier.padding(end = 8.dp).size(16.dp).clip(RoundedCornerShape(4.dp)).background(parseHex(hex, Color.Transparent)).border(1.dp, p.border, RoundedCornerShape(4.dp)))
            },
            colors = tasksFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
