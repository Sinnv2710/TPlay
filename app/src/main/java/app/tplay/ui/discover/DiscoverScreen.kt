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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import app.tplay.data.discover.cleanReleaseTitle
import app.tplay.data.source.CHECK_NEEDED
import app.tplay.data.source.SourceDef
import app.tplay.data.source.SourceInfo
import androidx.compose.material.icons.filled.ArrowDropDown
import app.tplay.ui.common.EmptyState
import app.tplay.ui.common.HealthDot
import app.tplay.ui.common.ScreenTitle
import app.tplay.ui.common.Tag
import coil.compose.AsyncImage

@Composable
fun DiscoverScreen(
    onOpenBrowser: (String?) -> Unit,
    onManageSources: () -> Unit,
    onStarted: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val showAdult by viewModel.showAdult.collectAsStateWithLifecycle()
    val def by viewModel.current.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    val keys by viewModel.inLibrary.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    var query by remember(filters.keyword) { mutableStateOf(filters.keyword) }
    var sheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MovieCard?>(null) }
    val gridState = rememberLazyGridState()

    val cards = remember(state.cards, def, showAdult, filters.onlySeeded) {
        state.cards.visibleFor(def?.adultOptions.orEmpty(), def?.adultWords.orEmpty(), showAdult)
            .withSeedsOnly(filters.onlySeeded)
    }
    EndlessScroll(gridState, cards.size) { viewModel.loadMore() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.challengeUrl != null) viewModel.reload() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Discover")
                val health = sources.firstOrNull { it.def.id == def?.id }?.health
                Row(
                    Modifier.clickable { picker = true }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HealthDot(health?.level)
                    Text(
                        def?.name ?: "No source",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose source")
                }
            }
            IconButton(onClick = { onOpenBrowser(null) }) {
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
        ActiveFilters(def, filters, onOpenSheet = { sheet = true }, viewModel = viewModel)
        Box(Modifier.fillMaxSize()) {
            when {
                state.challengeUrl != null -> CheckPanel(state.challengeUrl.orEmpty(), onOpenBrowser, viewModel::reload)
                state.unsupported -> EmptyState(
                    "This source stopped matching its site",
                    "The page layout probably changed. Open Manage sources to check for an update, or pick another source.",
                )
                state.error != null && state.cards.isEmpty() -> EmptyState("Could not load movies", state.error.orEmpty())
                cards.isEmpty() && !state.loading -> EmptyState("No movies match", "Try removing a filter.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cards, key = { it.pageUrl }) { card ->
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

    if (picker) {
        SourceSheet(
            sources = sources.filter { it.enabled },
            selectedId = def?.id,
            onPick = {
                viewModel.select(it)
                picker = false
            },
            onManage = {
                picker = false
                onManageSources()
            },
            onClose = { picker = false },
        )
    }
    if (sheet) def?.let { FilterSheet(it, filters, showAdult, viewModel) { sheet = false } }
    selected?.let { chosen ->
        val card = state.cards.firstOrNull { it.pageUrl == chosen.pageUrl } ?: chosen
        DetailSheet(
            card = card,
            inLibrary = card.inLibrary(keys),
            releases = releases,
            onChoose = { viewModel.loadReleases(card) },
            onOpenBrowser = onOpenBrowser,
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
    def: SourceDef?,
    filters: DiscoverFilters,
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
        if (filters.onlySeeded) {
            FilterChip(selected = true, onClick = { viewModel.setOnlySeeded(false) }, label = { Text("With seeds  ✕") })
        }
        def?.filters?.forEach { filter ->
            val value = filters.values[filter.param].orEmpty()
            if (value.isNotBlank()) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.setFilter(filter.param, "") },
                    label = { Text("${filter.labelOf(value)}  ✕") },
                )
            }
        }
    }
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
        // Sites that list raw release names (they carry a seeds line) show the movie name only.
        val shown = if (card.info != null) cleanReleaseTitle(card.title).title else card.title
        Text(shown, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(
            listOfNotNull(card.year?.toString(), card.info).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterSheet(
    def: SourceDef,
    filters: DiscoverFilters,
    showAdult: Boolean,
    viewModel: DiscoverViewModel,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Show adult titles", "Hidden by default", showAdult, viewModel::setShowAdult)
            if (def.list.fields.containsKey("info")) {
                ToggleRow(
                    "Only titles with seeds",
                    "Hide releases nobody is sharing",
                    filters.onlySeeded,
                    viewModel::setOnlySeeded,
                )
            }
            if (def.filters.isEmpty()) {
                Text(
                    "This source has no genre or year filters. Use the search box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            def.filters.forEach { filter ->
                val options = filter.options.filter { showAdult || it !in def.adultOptions }
                ChoiceGroup(
                    filter.name,
                    options.map { it to filter.labelOf(it) },
                    filters.values[filter.param].orEmpty(),
                ) { viewModel.setFilter(filter.param, it) }
            }
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
private fun SourceSheet(
    sources: List<SourceInfo>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Source", style = MaterialTheme.typography.titleMedium)
            sources.forEach { info ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(info.def.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HealthDot(info.health?.level)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(info.def.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            info.health?.message ?: info.def.site.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (info.def.id == selectedId) Text("✓")
                }
            }
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Manage sources…")
            }
        }
    }
}

@Composable
private fun DetailSheet(
    card: MovieCard,
    inLibrary: Boolean,
    releases: ReleasesState,
    onChoose: () -> Unit,
    onOpenBrowser: (String?) -> Unit,
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
                is ReleasesState.Challenge -> Column(Modifier.padding(top = 16.dp)) {
                    Text(CHECK_NEEDED, color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpenBrowser(releases.url) }) { Text("Open browser") }
                        TextButton(onClick = onChoose) { Text("Try again") }
                    }
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

/** Shown when the site wants a human check: the user passes it in the in-app browser, then the list loads. */
@Composable
private fun CheckPanel(url: String, onOpenBrowser: (String?) -> Unit, onTryAgain: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This site wants a quick check", style = MaterialTheme.typography.titleMedium)
        Text(
            "Open the browser, pass the check, then come back. The list loads on its own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = { onOpenBrowser(url) }) { Text("Open browser") }
        TextButton(onClick = onTryAgain) { Text("Try again") }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked, onChange)
    }
}
