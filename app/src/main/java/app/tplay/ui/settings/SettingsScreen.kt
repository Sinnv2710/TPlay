package app.tplay.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.domain.CleanupPreset
import app.tplay.ui.common.FolderPicker
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.SectionLabel
import app.tplay.ui.common.formatBytes

@Composable
fun SettingsScreen(onMessage: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val used by viewModel.usedBytes.collectAsStateWithLifecycle()
    val cache by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickFolder = FolderPicker.rememberLauncher { viewModel.setFolder(it) }
    var editing by remember { mutableStateOf<ServiceRow?>(null) }
    var addingCustom by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }

    message?.let {
        onMessage(it)
        viewModel.message.value = null
    }

    Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings")

        SectionLabel("Storage")
        Text(folder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Used by downloads: ${formatBytes(used)}")
        Row {
            TextButton(onClick = pickFolder) { Text("Change folder") }
            if (needsAllFilesAccess(folder, context.getExternalFilesDir(null)?.parent)) {
                TextButton(onClick = { context.startActivity(allFilesIntent(context.packageName)) }) {
                    Text("Allow all files access")
                }
            }
        }

        SectionLabel("Auto cleanup")
        Text("Delete downloaded files after", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CleanupPreset.NEVER to "Never",
                CleanupPreset.ONE_WEEK to "1 week",
                CleanupPreset.ONE_MONTH to "1 month",
                CleanupPreset.CUSTOM to "Custom",
            ).forEach { (preset, label) ->
                FilterChip(
                    selected = policy.preset == preset,
                    onClick = { viewModel.setPreset(preset) },
                    label = { Text(label) },
                )
            }
        }
        if (policy.preset == CleanupPreset.CUSTOM) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.setCustomDays(policy.customDays - 1) }) { Text("-") }
                Text("  ${policy.customDays} days  ")
                OutlinedButton(onClick = { viewModel.setCustomDays(policy.customDays + 1) }) { Text("+") }
            }
        }
        SwitchRow("Keep history after files are deleted", policy.keepHistory, viewModel::setKeepHistory)

        SectionLabel("Clean up now")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.cleanOlderThan(7) }) { Text("Older than 1 week") }
            OutlinedButton(onClick = { viewModel.cleanOlderThan(30) }) { Text("Older than 1 month") }
        }
        OutlinedButton(onClick = viewModel::clearCache, modifier = Modifier.padding(top = 8.dp)) {
            Text("Clear cache (${formatBytes(cache)})")
        }
        OutlinedButton(onClick = { confirmAll = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Delete all downloaded files")
        }

        SectionLabel("Playback")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("builtin" to "Built-in", "vlc" to "VLC", "mx" to "MX Player").forEach { (id, label) ->
                FilterChip(selected = player == id, onClick = { viewModel.setPlayer(id) }, label = { Text(label) })
            }
        }
        SwitchRow("Download on Wi-Fi only", wifiOnly, viewModel::setWifiOnly)

        SectionLabel("Browse")
        var startDraft by remember(startPage) { mutableStateOf(startPage) }
        OutlinedTextField(
            value = startDraft,
            onValueChange = { startDraft = it },
            label = { Text("Start page (web address)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { viewModel.setStartPage(startDraft) }) { Text("Save start page") }

        SectionLabel("Services and tokens")
        Text(
            "Stored encrypted on this phone. Real-Debrid is required; the others unlock subtitles and metadata.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        services.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.label)
                    Text(
                        if (row.hasValue) "Saved" else "Not set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { editing = row }) { Text(if (row.hasValue) "Replace" else "Set") }
            }
        }
        OutlinedButton(onClick = { addingCustom = true }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Add another service token")
        }
        Text("", modifier = Modifier.padding(bottom = 24.dp))
    }

    editing?.let { row ->
        TokenDialog(
            title = row.label,
            askName = false,
            canRemove = row.hasValue,
            onSave = { _, value -> viewModel.saveToken(row.id, row.label, value); editing = null },
            onRemove = { viewModel.removeToken(row.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (addingCustom) {
        TokenDialog(
            title = "New service token",
            askName = true,
            canRemove = false,
            onSave = { name, value ->
                viewModel.saveToken("custom:" + name.trim().lowercase(), name.trim(), value)
                addingCustom = false
            },
            onRemove = {},
            onDismiss = { addingCustom = false },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Removes every downloaded file. History is kept if that switch is on.") },
            confirmButton = {
                TextButton(onClick = { viewModel.cleanOlderThan(0); confirmAll = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun TokenDialog(
    title: String,
    askName: Boolean,
    canRemove: Boolean,
    onSave: (name: String, value: String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (askName) {
                    OutlinedTextField(name, { name = it }, label = { Text("Service name") }, singleLine = true)
                }
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text("Token or key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, value) },
                enabled = value.isNotBlank() && (!askName || name.isNotBlank()),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canRemove) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun needsAllFilesAccess(folder: String, appDir: String?): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    if (appDir != null && folder.startsWith(appDir)) return false
    return !Environment.isExternalStorageManager()
}

private fun allFilesIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
