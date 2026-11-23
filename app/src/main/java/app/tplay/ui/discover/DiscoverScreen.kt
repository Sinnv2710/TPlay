@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.tplay.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.Release
import app.tplay.ui.common.EmptyState
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.Tag
import coil.compose.AsyncImage

@Composable
fun DiscoverScreen(
    onOpenBrowser: () -> Unit,
    onStarted: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val showAdult by viewModel.showAdult.collectAsStateWithLifecycle()
    val site by viewModel.site.collectAsStateWithLifecycle()
    val keys by viewModel.inLibrary.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    var query by remember(filters.keyword) { mutableStateOf(filters.keyword) }
    var sheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MovieCard?>(null) }
    val gridState = rememberLazyGridState()

    EndlessScroll(gridState, state.cards.size) { viewModel.loadMore() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Discover")
                Text(
                    site.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.Language, contentDescription = "Open the browser")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { viewModel.search(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ActiveFilters(filters, showAdult, onOpenSheet = { sheet = true }, viewModel = viewModel)
        Box(Modifier.fillMaxSize()) {
            when {
                state.unsupported -> EmptyState(
                    "This site is not supported yet",
                    "Discover reads sites that look like en.yts-official.com. You can still use the browser.",
                )
                state.error != null && state.cards.isEmpty() -> EmptyState("Could not load movies", state.error.orEmpty())
                state.cards.isEmpty() && !state.loading -> EmptyState("No movies match", "Try removing a filter.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.cards, key = { it.pageUrl }) { card ->
                        MovieTile(card, card.inLibrary(keys)) {
                            viewModel.openCard()
                            selected = card
                        }
                    }
                }
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    if (sheet) FilterSheet(filters, showAdult, viewModel) { sheet = false }
    selected?.let { card ->
        DetailSheet(
            card = card,
            inLibrary = card.inLibrary(keys),
            releases = releases,
            onChoose = { viewModel.loadReleases(card) },
            onStream = { release ->
                viewModel.stream(release) {
                    selected = null
                    onStarted()
                }
            },
            onCopy = { onMessage("Magnet link copied") },
            onClose = { selected = null },
        )
    }
}

@Composable
private fun EndlessScroll(state: LazyGridState, count: Int, onEnd: () -> Unit) {
    val atEnd by remember(state) {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            count > 0 && last >= count - 6
        }
    }
    LaunchedEffect(atEnd, count) { if (atEnd) onEnd() }
}

@Composable
private fun ActiveFilters(
    filters: DiscoverFilters,
    showAdult: Boolean,
    onOpenSheet: () -> Unit,
    viewModel: DiscoverViewModel,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filters.activeCount > 0,
            onClick = onOpenSheet,
            label = { Text(if (filters.activeCount > 0) "Filters (${filters.activeCount})" else "Filters") },
            leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
        )
        if (filters.genre.isNotBlank()) RemovableChip(filters.genre) { viewModel.setGenre("") }
        if (filters.year.isNotBlank()) {
            RemovableChip(DiscoverOptions.years.firstOrNull { it.first == filters.year }?.second ?: filters.year) {
                viewModel.setYear("")
            }
        }
        if (filters.quality.isNotBlank()) RemovableChip(filters.quality) { viewModel.setQuality("") }
        if (filters.rating.isNotBlank()) RemovableChip("${filters.rating}+") { viewModel.setRating("") }
        if (filters.orderBy.isNotBlank()) {
            RemovableChip(DiscoverOptions.sorts.firstOrNull { it.first == filters.orderBy }?.second ?: filters.orderBy) {
                viewModel.setOrder("")
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    FilterChip(selected = true, onClick = onRemove, label = { Text("$label  ✕") })
}

@Composable
private fun MovieTile(card: MovieCard, inLibrary: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Poster(card, Modifier.fillMaxSize())
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                card.rating?.let { Tag("★ $it") }
                card.qualities.firstOrNull()?.let { Tag(it) }
            }
            if (inLibrary) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Tag("in library", MaterialTheme.colorScheme.primary) }
            }
        }
        Text(card.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(
            card.year?.toString().orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterSheet(filters: DiscoverFilters, showAdult: Boolean, viewModel: DiscoverViewModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            ChoiceGroup("Genre", DiscoverOptions.genresFor(showAdult).map { it to it }, filters.genre, viewModel::setGenre)
            ChoiceGroup("Year", DiscoverOptions.years, filters.year, viewModel::setYear)
            ChoiceGroup("Quality", DiscoverOptions.qualities.map { it to it }, filters.quality, viewModel::setQuality)
            ChoiceGroup("Minimum rating", DiscoverOptions.ratings, filters.rating, viewModel::setRating)
            ChoiceGroup("Sort by", DiscoverOptions.sorts, filters.orderBy, viewModel::setOrder)
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Show results") }
                OutlinedButton(onClick = viewModel::reset) { Text("Reset") }
            }
        }
    }
}

/** One group of single-choice chips. Tapping the chosen one again clears it. */
@Composable
private fun ChoiceGroup(title: String, options: List<Pair<String, String>>, chosen: String, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = chosen == value,
                onClick = { onPick(if (chosen == value) "" else value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun DetailSheet(
    card: MovieCard,
    inLibrary: Boolean,
    releases: ReleasesState,
    onChoose: () -> Unit,
    onStream: (Release) -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Poster(card, Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(card.year?.toString(), card.genres.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        card.rating?.let { Tag("★ $it") }
                        if (inLibrary) Tag("in library", MaterialTheme.colorScheme.primary)
                    }
                }
            }
            when (releases) {
                ReleasesState.Idle -> Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Stream")
                }
                ReleasesState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ReleasesState.Failed -> Column(Modifier.padding(top = 16.dp)) {
                    Text(releases.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onChoose) { Text("Try again") }
                }
                is ReleasesState.Ready -> ReleaseList(releases, onStream, onCopy)
            }
        }
    }
}

@Composable
private fun ReleaseList(ready: ReleasesState.Ready, onStream: (Release) -> Unit, onCopy: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    if (ready.synopsis.isNotBlank()) {
        Text(
            ready.synopsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Text(
        "Choose a release",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    ready.releases.forEach { release ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${release.quality} · ${release.size}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    release.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    release.seeds?.let { "$it seeds" } ?: "seeds unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(release.magnet))
                onCopy()
            }) { Text("Copy") }
            Button(onClick = { onStream(release) }) { Text("Stream") }
        }
    }
}

/** Poster image. If the first address fails, the site's backup address is tried; if both fail, the first letter shows. */
@Composable
private fun Poster(card: MovieCard, modifier: Modifier) {
    var useFallback by remember(card.pageUrl) { mutableStateOf(false) }
    var failed by remember(card.pageUrl) { mutableStateOf(false) }
    val url = if (useFallback) card.fallbackPosterUrl else card.posterUrl
    Box(modifier, contentAlignment = Alignment.Center) {
        if (url == null || failed) {
            Text(card.title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
        } else {
            AsyncImage(
                model = url,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (!useFallback && card.fallbackPosterUrl != null) useFallback = true else failed = true
                },
            )
        }
    }
}
