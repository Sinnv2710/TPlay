package app.tplay.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.ui.common.EmptyState
import app.tplay.ui.common.PosterTile
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.Tag
import app.tplay.ui.common.formatAge
import app.tplay.ui.common.formatBytes
import app.tplay.ui.common.formatDuration

@Composable
fun LibraryScreen(onMessage: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val items by viewModel.visible.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val remote by viewModel.remote.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val remoteError by viewModel.remoteError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }

    Column(Modifier.padding(horizontal = 20.dp)) {
        ScreenTitle("Library")
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TabRow(tab.ordinal, Modifier.padding(top = 8.dp)) {
            LibraryTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = {
                        viewModel.tab.value = entry
                        if (entry == LibraryTab.REAL_DEBRID) viewModel.refreshRemote()
                    },
                    text = { Text(tabLabel(entry)) },
                )
            }
        }
        if (tab == LibraryTab.REAL_DEBRID) {
            RemoteList(
                items = remote,
                loading = loading,
                error = remoteError,
                onRefresh = viewModel::refreshRemote,
                onPlay = { viewModel.playRemote(context, it, onMessage) },
                onAdd = viewModel::addRemote,
            )
            return@Column
        }
        if (items.isEmpty()) EmptyState("Nothing here yet", "Finished downloads show up here.")
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(items, key = { it.title.id }) { item ->
                ItemCard(
                    item,
                    onPlay = { viewModel.play(context, item, onMessage) },
                    onDelete = { deleting = item },
                )
            }
        }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${item.title.name}?") },
            text = { Text("Removes the downloaded files and the library entry.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(item); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ItemCard(item: LibraryItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    val title = item.title
    val progress = item.progress
    Card(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterTile(title.name, title.posterUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val year = title.year?.let { " ($it)" }.orEmpty()
                Text(title.name + year, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                when {
                    progress == null -> Text("Not watched yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    progress.finished -> Text("Finished", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(
                        "Stopped at ${formatDuration(progress.positionMs)} of ${formatDuration(progress.durationMs)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (progress != null && !progress.finished) {
                    LinearProgressIndicator({ progressFraction(progress) }, Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (title.filesDeleted) Tag("stream only") else Tag(formatBytes(title.sizeBytes))
                    if (title.folderPath?.contains("/Direct/") == true) Tag("direct download")
                    item.daysLeft?.let { Tag("deleted in $it d") }
                    progress?.let { Tag(formatAge(it.updatedAt)) }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun tabLabel(tab: LibraryTab): String = when (tab) {
    LibraryTab.CONTINUE -> "Continue"
    LibraryTab.ALL -> "All"
    LibraryTab.FINISHED -> "Finished"
    LibraryTab.REAL_DEBRID -> "Real-Debrid"
}

@Composable
private fun RemoteList(
    items: List<RemoteItem>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onPlay: (RemoteItem) -> Unit,
    onAdd: (RemoteItem) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            if (loading) "Reading your Real-Debrid account..." else "${items.size} on your account",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh) { Text("Refresh") }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!loading && error == null && items.isEmpty()) {
        EmptyState("Nothing on Real-Debrid yet", "Streamed and finished torrents of your account show up here.")
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        items(items, key = { it.torrentId }) { item ->
            Card(Modifier.fillMaxWidth().clickable { onPlay(item) }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosterTile(item.name)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val year = item.year?.let { " ($it)" }.orEmpty()
                        Text(item.name + year, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(formatBytes(item.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.inLibrary) Tag("in library")
                    }
                    if (!item.inLibrary) TextButton(onClick = { onAdd(item) }) { Text("Add") }
                }
            }
        }
    }
}
