package app.tplay.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.ui.common.PosterTile
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.SectionLabel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(onMessage: (String) -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        ScreenTitle("Calendar")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.shiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        monthGrid(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        isToday = date == today,
                        isSelected = date != null && date == selected,
                        watched = date != null && entries.containsKey(date.toEpochDay()),
                        onClick = { if (date != null) viewModel.selected.value = date },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val day = selected?.let { entries[it.toEpochDay()] }.orEmpty()
        SectionLabel(selected?.toString() ?: "")
        if (day.isEmpty()) {
            Text("Nothing watched or added this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                day.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.play(context, entry.titleId, onMessage) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterTile(entry.name, entry.posterUrl, width = 48.dp, height = 70.dp)
                        Column {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (entry.kind == EntryKind.WATCHED) "Watched" else "Added to library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    watched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val fill = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        modifier
            .height(52.dp)
            .padding(2.dp)
            .background(if (date == null) MaterialTheme.colorScheme.background else fill, RoundedCornerShape(10.dp))
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (watched) {
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
        }
    }
}
