package app.tplay.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.ui.common.FolderPicker
import app.tplay.ui.common.ScreenTitle

@Composable
fun AddScreen(
    incoming: String?,
    onIncomingUsed: () -> Unit,
    onStarted: () -> Unit,
    viewModel: AddViewModel = hiltViewModel(),
) {
    val magnet by viewModel.magnet.collectAsStateWithLifecycle()
    val subtitle by viewModel.subtitle.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val folder by viewModel.folder.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val clipText = clipboard.getText()?.text.orEmpty().trim()
    val clipHasMagnet = AddViewModel.isValidMagnet(clipText) && clipText != magnet.trim()
    val pickFolder = FolderPicker.rememberLauncher { viewModel.setParentFolder(it) }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            viewModel.setMagnet(incoming)
            onIncomingUsed()
        }
    }

    Column(
        Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("New movie")
        if (clipHasMagnet) {
            AssistChip(
                onClick = { viewModel.setMagnet(clipText) },
                label = { Text("Magnet link found in clipboard: use it") },
                leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
            )
        }
        OutlinedTextField(
            value = magnet,
            onValueChange = viewModel::setMagnet,
            label = { Text("Magnet link") },
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            trailingIcon = {
                TextButton(onClick = { viewModel.setMagnet(clipText) }) { Text("Paste") }
            },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = subtitle,
            onValueChange = viewModel::setSubtitle,
            label = { Text("Subtitle link (optional: .srt, .zip, .rar)") },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = { viewModel.setSubtitle(clipText) }) { Text("Paste") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(Modifier.fillMaxWidth()) {
            Text("Save into", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    folder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = pickFolder) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  Change")
                }
            }
        }
        Text(
            "Plays straight from Real-Debrid and uses almost no storage. " +
                "Releases packed as RAR archives are downloaded and unpacked automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { if (viewModel.enqueue(stream = true)) onStarted() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Stream") }
        OutlinedButton(
            onClick = { viewModel.setMagnet(""); viewModel.setSubtitle("") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear") }
    }
}
