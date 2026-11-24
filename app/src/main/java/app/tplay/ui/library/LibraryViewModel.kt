package app.tplay.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.data.rd.RdResult
import app.tplay.data.rd.RealDebridClient
import app.tplay.data.tmdb.TmdbClient
import app.tplay.domain.LibraryRepository
import app.tplay.domain.SettingsRepository
import app.tplay.player.PlaybackResolver
import app.tplay.player.PlayerLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val resolver: PlaybackResolver,
    private val tmdb: TmdbClient,
    private val rd: RealDebridClient,
    private val subtitles: app.tplay.domain.SubtitleRepository,
) : ViewModel() {
    val tab = MutableStateFlow(LibraryTab.CONTINUE)
    val query = MutableStateFlow("")

    private val items = combine(library.titles(), library.progress(), settings.cleanupPolicy()) { t, p, c ->
        buildItems(t, p, c, System.currentTimeMillis())
    }

    val visible: StateFlow<List<LibraryItem>> = combine(items, tab, query) { all, selected, text ->
        if (selected == LibraryTab.REAL_DEBRID) emptyList() else filterItems(all, selected, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val torrents = MutableStateFlow<List<app.tplay.data.rd.RdTorrentSummary>>(emptyList())
    val loading = MutableStateFlow(false)
    val remoteError = MutableStateFlow<String?>(null)

    val remote: StateFlow<List<RemoteItem>> = combine(torrents, library.titles(), query) { list, titles, text ->
        remoteItems(list, titles).filter { text.isBlank() || it.name.contains(text.trim(), ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reads the account's torrent list from Real-Debrid. */
    fun refreshRemote() {
        viewModelScope.launch {
            loading.value = true
            remoteError.value = null
            when (val result = rd.list()) {
                is RdResult.Ok -> torrents.value = result.value
                is RdResult.Failure -> remoteError.value = "Could not read Real-Debrid. Check the token in Settings."
            }
            loading.value = false
        }
    }

    /** Adds the title to the library (stream only) and opens it. */
    fun playRemote(context: Context, item: RemoteItem, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val title = library.addRemote(item.name, item.year, item.sizeBytes, item.torrentId, item.link)
            val progress = library.progress().first()[title.id]
            play(context, LibraryItem(title, progress, null), onMessage)
        }
    }

    fun addRemote(item: RemoteItem) {
        viewModelScope.launch {
            library.addRemote(item.name, item.year, item.sizeBytes, item.torrentId, item.link)
        }
    }

    private val tried = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            library.titles().collect { titles ->
                titles.filter { it.posterUrl == null && tried.add(it.id) }.forEach { title ->
                    val url = tmdb.lookup(title.name, title.year)?.posterUrl ?: return@forEach
                    library.setPoster(title.id, url)
                }
            }
        }
    }

    fun delete(item: LibraryItem) {
        viewModelScope.launch { library.delete(item.title.id, removeFromRd = false) }
    }

    /** Resolves the title to something playable and opens the chosen player at the saved position. */
    fun play(context: Context, item: LibraryItem, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = resolver.resolve(item.title)) {
                is PlaybackResolver.Result.Failed -> onMessage(result.reason)
                is PlaybackResolver.Result.Ready -> {
                    val progress = item.progress
                    val resume = if (progress == null || progress.finished) 0L else progress.positionMs
                    val preferred = settings.preferredPlayer().first()
                    val subsFolder = app.tplay.player.SubtitleFetcher(subtitles, context.filesDir)
                        .ensure(item.title, settings.subtitleLanguage().first())
                    PlayerLauncher.launch(
                        context,
                        item.title.id,
                        item.title.name,
                        result.uri,
                        resume,
                        preferred,
                        subsFolder.path,
                    )
                }
            }
        }
    }
}
