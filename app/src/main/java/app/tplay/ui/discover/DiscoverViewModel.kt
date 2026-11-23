package app.tplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.data.discover.DiscoverClient
import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.PageResult
import app.tplay.data.discover.Release
import app.tplay.data.discover.ReleasesResult
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
)

sealed interface ReleasesState {
    data object Idle : ReleasesState
    data object Loading : ReleasesState
    data class Ready(val synopsis: String, val releases: List<Release>) : ReleasesState
    data class Failed(val message: String) : ReleasesState
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val client: DiscoverClient,
    private val settings: SettingsRepository,
    private val jobs: JobRepository,
    library: LibraryRepository,
) : ViewModel() {
    val filters = MutableStateFlow(DiscoverFilters())
    val state = MutableStateFlow(DiscoverState())
    val releases = MutableStateFlow<ReleasesState>(ReleasesState.Idle)
    val showAdult: StateFlow<Boolean> = settings.showAdult()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val site: StateFlow<String> = settings.discoverSite()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val inLibrary: StateFlow<Set<String>> = library.titles().map(::libraryKeys)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var page = 1
    private var loadJob: Job? = null
    private var loadedSite: String? = null
    private var loadedAdult: Boolean? = null

    init {
        viewModelScope.launch {
            combine(settings.discoverSite(), settings.showAdult()) { s, adult -> s to adult }.collect { (s, adult) ->
                if (s != loadedSite || adult != loadedAdult) reload()
            }
        }
    }

    fun search(text: String) = apply { it.copy(keyword = text) }

    fun setGenre(value: String) = apply { it.copy(genre = value) }
    fun setYear(value: String) = apply { it.copy(year = value) }
    fun setQuality(value: String) = apply { it.copy(quality = value) }
    fun setRating(value: String) = apply { it.copy(rating = value) }
    fun setOrder(value: String) = apply { it.copy(orderBy = value) }
    fun reset() = apply { it.copy(genre = "", year = "", quality = "", rating = "", orderBy = "") }

    private fun apply(change: (DiscoverFilters) -> DiscoverFilters) {
        val next = change(filters.value)
        if (next == filters.value) return
        filters.value = next
        reload()
    }

    fun reload() {
        page = 1
        state.update { it.copy(cards = emptyList(), hasNext = false, error = null, unsupported = false) }
        load()
    }

    fun loadMore() {
        val current = state.value
        if (current.loading || !current.hasNext) return
        page += 1
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.update { it.copy(loading = true) }
            val site = settings.discoverSite().first()
            loadedSite = site
            loadedAdult = settings.showAdult().first()
            when (val result = client.page(site, filters.value, page)) {
                is PageResult.Ok -> {
                    val adult = settings.showAdult().first()
                    state.update {
                        val merged = (it.cards + result.cards.visibleFor(adult)).distinctBy { c -> c.pageUrl }
                        it.copy(cards = merged, loading = false, hasNext = result.hasNext, error = null)
                    }
                }
                PageResult.Unsupported -> state.update { it.copy(loading = false, unsupported = true) }
                is PageResult.Failure -> state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun openCard() {
        releases.value = ReleasesState.Idle
    }

    fun loadReleases(card: MovieCard) {
        releases.value = ReleasesState.Loading
        viewModelScope.launch {
            val site = settings.discoverSite().first()
            releases.value = when (val result = client.releases(site, card.pageUrl)) {
                is ReleasesResult.Ok -> ReleasesState.Ready(result.synopsis, result.releases)
                is ReleasesResult.Failure -> ReleasesState.Failed(result.message)
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
