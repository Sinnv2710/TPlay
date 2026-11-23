package app.tplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.data.discover.DiscoverClient
import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.PageResult
import app.tplay.data.discover.Release
import app.tplay.data.discover.ReleasesResult
import app.tplay.data.source.CHECK_NEEDED
import app.tplay.data.source.HealthLevel
import app.tplay.data.source.SourceDef
import app.tplay.data.source.SourceInfo
import app.tplay.data.source.SourceStore
import app.tplay.domain.JobRepository
import app.tplay.domain.LibraryRepository
import app.tplay.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverState(
    val cards: List<MovieCard> = emptyList(),
    val loading: Boolean = false,
    val hasNext: Boolean = false,
    val error: String? = null,
    val unsupported: Boolean = false,
    /** Page to open in the browser so the user can pass the site's human check. */
    val challengeUrl: String? = null,
)

sealed interface ReleasesState {
    data object Idle : ReleasesState
    data object Loading : ReleasesState
    data class Ready(val synopsis: String, val releases: List<Release>) : ReleasesState
    data class Failed(val message: String) : ReleasesState
    data class Challenge(val url: String) : ReleasesState
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val client: DiscoverClient,
    private val settings: SettingsRepository,
    private val jobs: JobRepository,
    private val store: SourceStore,
    library: LibraryRepository,
) : ViewModel() {
    val filters = MutableStateFlow(DiscoverFilters())
    val state = MutableStateFlow(DiscoverState())
    val releases = MutableStateFlow<ReleasesState>(ReleasesState.Idle)
    val showAdult: StateFlow<Boolean> = settings.showAdult()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val sources: StateFlow<List<SourceInfo>> = combine(store.state, store.health) { s, h -> store.sources(s, h) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.sources())
    val current: StateFlow<SourceDef?> = store.state.map { store.current() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.current())
    val inLibrary: StateFlow<Set<String>> = library.titles().map(::libraryKeys)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var page = 1
    private var loadJob: Job? = null
    private var loadedKey: String? = null

    init {
        viewModelScope.launch {
            combine(current, settings.showAdult()) { def, adult -> "${def?.id}:${def?.version}:$adult" }.collect { key ->
                val otherSource = loadedKey?.substringBefore(':') != key.substringBefore(':')
                if (key != loadedKey) reload(resetFilters = otherSource)
            }
        }
    }

    fun search(text: String) = change { it.copy(keyword = text) }
    fun setFilter(param: String, value: String) = change { it.with(param, value) }
    fun reset() = change { it.cleared() }
    fun select(id: String) {
        viewModelScope.launch { store.select(id) }
    }

    private fun change(edit: (DiscoverFilters) -> DiscoverFilters) {
        val next = edit(filters.value)
        if (next == filters.value) return
        filters.value = next
        reload()
    }

    fun reload(resetFilters: Boolean = false) {
        if (resetFilters) filters.value = DiscoverFilters()
        page = 1
        state.update {
            it.copy(cards = emptyList(), hasNext = false, error = null, unsupported = false, challengeUrl = null)
        }
        load()
    }

    fun loadMore() {
        val now = state.value
        if (now.loading || !now.hasNext) return
        page += 1
        load()
    }

    private fun load() {
        loadJob?.cancel()
        val def = store.current()
        if (def == null) {
            state.update { it.copy(loading = false, error = "No source is switched on") }
            return
        }
        loadJob = viewModelScope.launch {
            state.update { it.copy(loading = true) }
            val adult = settings.showAdult().first()
            loadedKey = "${def.id}:${def.version}:$adult"
            when (val result = client.page(def, filters.value, page)) {
                is PageResult.Ok -> {
                    if (result.cards.isNotEmpty()) store.report(def.id, HealthLevel.OK, "Loaded ${result.cards.size} movies")
                    val shown = result.cards.visibleFor(def.adultOptions, adult)
                    state.update {
                        val merged = (it.cards + shown).distinctBy { c -> c.pageUrl }
                        it.copy(cards = merged, loading = false, hasNext = result.hasNext, error = null)
                    }
                }
                is PageResult.Challenge -> {
                    store.report(def.id, HealthLevel.WARN, CHECK_NEEDED)
                    state.update { it.copy(loading = false, challengeUrl = result.url) }
                }
                PageResult.Unsupported -> {
                    store.report(def.id, HealthLevel.WARN, "No movie cards found. The site may have changed.")
                    state.update { it.copy(loading = false, unsupported = true) }
                }
                is PageResult.Failure -> {
                    store.report(def.id, HealthLevel.FAIL, result.message)
                    state.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun openCard() {
        releases.value = ReleasesState.Idle
    }

    fun loadReleases(card: MovieCard) {
        val def = store.current() ?: return
        releases.value = ReleasesState.Loading
        viewModelScope.launch {
            releases.value = when (val result = client.releases(def, card.pageUrl)) {
                is ReleasesResult.Ok -> ReleasesState.Ready(result.synopsis, result.releases)
                is ReleasesResult.Failure -> ReleasesState.Failed(result.message)
                is ReleasesResult.Challenge -> ReleasesState.Challenge(result.url)
            }
        }
    }

    /** Sends the chosen release to Real-Debrid and plays it from there once ready. */
    fun stream(release: Release, onStarted: () -> Unit) {
        viewModelScope.launch {
            jobs.enqueue(release.magnet, null, settings.parentFolder().first(), true)
            onStarted()
        }
    }
}
