package app.tplay.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.data.source.RepoEntry
import app.tplay.data.source.SourceInfo
import app.tplay.ui.common.HealthDot
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.SectionLabel

@Composable
fun ManageSourcesScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ManageSourcesViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val found by viewModel.found.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_FILE).toString(Charsets.UTF_8) }
            }.getOrNull()
            if (text == null) onMessage("Could not read that file") else viewModel.importJson(text)
        }
    }
    var open by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            onMessage(it)
            viewModel.clearMessage()
        }
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                ScreenTitle("Sources", Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp))
            }
        }
        item { SectionLabel("Installed") }
        items(sources, key = { it.def.id }) { info ->
            SourceRow(
                info = info,
                expanded = open == info.def.id,
                testing = info.def.id in testing,
                onToggleOpen = { open = if (open == info.def.id) null else info.def.id },
                onEnabled = { viewModel.setEnabled(info.def.id, it) },
                onUpdate = { viewModel.update(info.def.id) },
                onTest = { viewModel.runSelfTest(info.def.id) },
                onRemove = { viewModel.remove(info.def.id) },
            )
        }
        item { SectionLabel("Repositories") }
        items(repos, key = { it.url }) { repo ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${repo.sources.size} sources · ${repo.url.removePrefix("https://")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { viewModel.removeRepo(repo.url) }) { Text("Remove") }
                }
            }
        }
        if (repos.isNotEmpty()) item { OutlinedButton(onClick = viewModel::refresh) { Text("Refresh repositories") } }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Repository address (https://…/index.json)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.check(address) }, enabled = address.isNotBlank() && !busy) { Text("Check") }
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                    Text("Choose a file")
                }
                OutlinedButton(onClick = { importing = true }) { Text("Paste") }
            }
        }
        found?.let { repo -> item { FoundRepo(repo, sources, viewModel) } }
        item {
            Text(
                "A source only says where to look on a page. It runs no code and cannot read your tokens. " +
                    "Add repositories you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
    if (importing) ImportDialog(onClose = { importing = false }, onImport = viewModel::importJson)
}

@Composable
private fun FoundRepo(repo: RepoEntry, installed: List<SourceInfo>, viewModel: ManageSourcesViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${repo.name} · ${repo.sources.size} sources", style = MaterialTheme.typography.titleSmall)
            repo.sources.forEach { source ->
                val have = installed.firstOrNull { it.def.id == source.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(source.name)
                        Text(
                            "v${source.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (have != null && have.def.version >= source.version) {
                        Text("added")
                    } else {
                        Button(onClick = { viewModel.install(repo, source) }) {
                            Text(if (have == null) "Add" else "Update")
                        }
                    }
                }
            }
            OutlinedButton(onClick = { viewModel.saveRepo(repo) }) { Text("Save repository only") }
        }
    }
}

@Composable
private fun SourceRow(
    info: SourceInfo,
    expanded: Boolean,
    testing: Boolean,
    onToggleOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.clickable(onClick = onToggleOpen), verticalAlignment = Alignment.CenterVertically) {
                HealthDot(info.health?.level)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(info.def.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "v${info.def.version} · " + when {
                            info.builtIn -> "built in"
                            info.repoUrl == null -> "local file"
                            else -> "from a repository"
                        } + (info.updateTo?.let { " · update to v$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.updateTo != null) TextButton(onClick = onUpdate) { Text("Update") }
                if (!info.builtIn) Switch(info.enabled, onEnabled)
            }
            if (expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Site: ${info.def.site.removePrefix("https://")}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Filters: " + info.def.filters.joinToString { it.name }.ifBlank { "none" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    info.health?.let {
                        Text(
                            it.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onTest, enabled = !testing) { Text("Run a self test") }
                        if (testing) CircularProgressIndicator(Modifier.padding(start = 4.dp))
                        if (!info.builtIn) TextButton(onClick = onRemove) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(onClose: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Paste a source file") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Source JSON") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onImport(text)
                onClose()
            }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

private const val MAX_FILE = 70_000
