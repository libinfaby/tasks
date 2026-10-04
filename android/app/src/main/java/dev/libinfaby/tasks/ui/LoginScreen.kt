package dev.libinfaby.tasks.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.libinfaby.tasks.ui.theme.TasksIcons

@Composable
fun LoginScreen(apiUrl: String, error: String?, busy: Boolean, onSignIn: (apiUrl: String, password: String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(apiUrl) }
    var advanced by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (advanced) 90f else 0f, label = "chevron")
    val submit = { if (password.isNotEmpty() && !busy) onSignIn(url, password) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(48.dp))
                BrandMark(80)
                Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    Text("Welcome back", style = MaterialTheme.typography.displaySmall)
                    Text("Sign in to pick up where you left off.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (error != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(14.dp))
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(TasksIcons.Lock, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = submit,
                    enabled = password.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (busy) "Signing in…" else "Sign in", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { advanced = !advanced }) {
                    Icon(TasksIcons.ChevronRight, null, modifier = Modifier.size(20.dp).rotate(chevron))
                    Spacer(Modifier.width(4.dp))
                    Text("Advanced")
                }
                AnimatedVisibility(advanced) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("API URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
