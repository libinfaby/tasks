package dev.libinfaby.tasks.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libinfaby.tasks.ui.components.tasksFieldColors
import dev.libinfaby.tasks.ui.theme.TasksIcons
import dev.libinfaby.tasks.ui.theme.palette

@Composable
fun LoginScreen(apiUrl: String, error: String?, busy: Boolean, onSignIn: (apiUrl: String, password: String) -> Unit) {
    val p = palette
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(apiUrl) }
    var advanced by remember { mutableStateOf(false) }
    val submit = { if (password.isNotEmpty() && !busy) onSignIn(url, password) }

    Box(Modifier.fillMaxSize().background(p.bgSubtle).imePadding(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = p.card,
            border = BorderStroke(1.dp, p.border),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandMark(40)
                Column {
                    Text("Sign in to Tasks", style = MaterialTheme.typography.titleLarge)
                    Text("Your personal task manager", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                }
                if (error != null) {
                    Text(
                        error,
                        color = p.danger,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.dangerSoft).padding(10.dp),
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    colors = tasksFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = submit,
                    enabled = password.isNotEmpty() && !busy,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { Text(if (busy) "Signing in…" else "Sign in") }
                Text(
                    if (advanced) "▾ Advanced" else "▸ Advanced",
                    fontSize = 12.sp,
                    color = p.textTertiary,
                    modifier = Modifier.clickable { advanced = !advanced },
                )
                if (advanced) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("API URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = tasksFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Accent tile with the checklist glyph — the web app's brand mark. */
@Composable
fun BrandMark(size: Int = 28) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5f).dp)).background(palette.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TasksIcons.ListChecks, null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp))
    }
}
