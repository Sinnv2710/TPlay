package app.tplay.ui.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.domain.Job
import app.tplay.domain.JobStatus
import app.tplay.share.MagnetParser
import app.tplay.ui.common.EmptyState
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.Tag
import app.tplay.ui.common.formatSpeed

private val RUNNING = setOf(
    JobStatus.QUEUED, JobStatus.SENT, JobStatus.CACHING,
    JobStatus.DOWNLOADING, JobStatus.UNPACKING, JobStatus.SUBTITLES,
)

@Composable
fun ActiveScreen(viewModel: ActiveViewModel = hiltViewModel()) {
    val jobs by viewModel.list.collectAsStateWithLifecycle()
    Column(Modifier.padding(horizontal = 20.dp)) {
        ScreenTitle("Active")
        if (jobs.isEmpty()) {
            EmptyState("Nothing downloading", "Add a magnet link on the first tab.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(jobs, key = { it.id }) { job ->
                JobCard(job, onCancel = { viewModel.cancel(job) }, onRetry = { viewModel.retry(job) })
            }
        }
    }
}

@Composable
private fun JobCard(job: Job, onCancel: () -> Unit, onRetry: () -> Unit) {
    val name = MagnetParser.parse(job.magnet)?.displayName ?: "Movie"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.weight(1f))
                Tag(job.status.name.lowercase().replaceFirstChar { it.uppercase() })
            }
            Text(job.step, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (job.status in RUNNING) {
                LinearProgressIndicator({ job.progress / 100f }, Modifier.fillMaxWidth())
                if (job.speedBytesPerSec > 0) {
                    Text(formatSpeed(job.speedBytesPerSec), style = MaterialTheme.typography.bodySmall)
                }
            }
            job.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row {
                if (job.status in RUNNING) TextButton(onClick = onCancel) { Text("Cancel") }
                if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}
