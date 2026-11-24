package app.tplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.domain.CleanupPolicy
import app.tplay.domain.CleanupPreset
import app.tplay.domain.LibraryRepository
import app.tplay.domain.ServiceIds
import app.tplay.domain.ServiceToken
import app.tplay.domain.SettingsRepository
import app.tplay.domain.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServiceRow(val id: String, val label: String, val hasValue: Boolean)

val KNOWN_SERVICES = listOf(
    ServiceRow(ServiceIds.REAL_DEBRID, "Real-Debrid API token", false),
    ServiceRow(ServiceIds.OPEN_SUBTITLES, "OpenSubtitles API key", false),
    ServiceRow("opensubtitles-login", "OpenSubtitles login (username:password)", false),
    ServiceRow(ServiceIds.SUBDL, "SubDL API key", false),
    ServiceRow(ServiceIds.TMDB, "TMDB API key", false),
    ServiceRow(ServiceIds.TRAKT, "Trakt client id", false),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val vault: VaultRepository,
    library: LibraryRepository,
) : ViewModel() {
    private fun <T> share(initial: T, flow: kotlinx.coroutines.flow.Flow<T>): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val policy = share(CleanupPolicy(CleanupPreset.NEVER, 7, false, true), settings.cleanupPolicy())
    val folder = share("", settings.parentFolder())
    val wifiOnly = share(false, settings.wifiOnly())
    val player = share("builtin", settings.preferredPlayer())
    val startPage = share("", settings.browseStartPage())
    val showAdult = share(false, settings.showAdult())
    val subtitleLanguage = share("vi", settings.subtitleLanguage())
    val usedBytes = share(0L, library.titles().map { list -> list.filter { !it.filesDeleted }.sumOf { it.sizeBytes } })
    /** Titles that were downloaded straight onto the phone (not through Real-Debrid). */
    private val directTitles = library.titles().map { list ->
        list.filter { it.folderPath?.contains("/Direct/") == true && !it.filesDeleted }
    }
    val directCount = share(0, directTitles.map { it.size })
    val directBytes = share(0L, directTitles.map { list -> list.sumOf { it.sizeBytes } })
    private val libraryRepo = library
    val services: StateFlow<List<ServiceRow>> = share(
        KNOWN_SERVICES,
        vault.services().map { stored -> mergeServices(stored) },
    )
    val cacheBytes = MutableStateFlow(0L)
    val message = MutableStateFlow<String?>(null)

    init {
        refreshCache()
    }

    fun refreshCache() {
        viewModelScope.launch { cacheBytes.value = settings.cacheBytes() }
    }

    fun setPreset(preset: CleanupPreset) = updatePolicy { it.copy(preset = preset) }

    fun setCustomDays(days: Int) = updatePolicy {
        it.copy(customDays = days.coerceIn(1, 365), preset = CleanupPreset.CUSTOM)
    }

    fun setKeepHistory(keep: Boolean) = updatePolicy { it.copy(keepHistory = keep) }

    private fun updatePolicy(change: (CleanupPolicy) -> CleanupPolicy) {
        viewModelScope.launch { settings.setCleanupPolicy(change(settings.cleanupPolicy().first())) }
    }

    fun setFolder(path: String) {
        viewModelScope.launch { settings.setParentFolder(path) }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch { settings.setWifiOnly(value) }
    }

    fun setStartPage(value: String) {
        viewModelScope.launch { settings.setBrowseStartPage(value) }
    }

    /** Removes every direct download: its files and its library entry. */
    fun deleteDirectDownloads() {
        viewModelScope.launch {
            val titles = directTitles.first()
            titles.forEach { libraryRepo.delete(it.id, removeFromRd = false) }
            message.value = "Deleted ${titles.size} direct downloads"
            refreshCache()
        }
    }

    fun setShowAdult(value: Boolean) {
        viewModelScope.launch { settings.setShowAdult(value) }
    }

    fun setSubtitleLanguage(value: String) {
        viewModelScope.launch { settings.setSubtitleLanguage(value) }
    }

    fun setPlayer(value: String) {
        viewModelScope.launch { settings.setPreferredPlayer(value) }
    }

    fun clearCache() {
        viewModelScope.launch {
            settings.clearCache()
            refreshCache()
            message.value = "Cache cleared"
        }
    }

    fun cleanOlderThan(days: Int) {
        viewModelScope.launch {
            val count = settings.cleanOlderThan(days)
            message.value = if (count == 0) "Nothing to delete" else "Deleted files of $count title(s)"
        }
    }

    fun saveToken(id: String, label: String, value: String) {
        viewModelScope.launch {
            vault.put(id, label, value.trim())
            message.value = "Saved"
        }
    }

    fun removeToken(id: String) {
        viewModelScope.launch { vault.remove(id) }
    }

    private fun mergeServices(stored: List<ServiceToken>): List<ServiceRow> {
        val byId = stored.associateBy { it.serviceId }
        val known = KNOWN_SERVICES.map { it.copy(hasValue = byId[it.id]?.hasValue == true) }
        val custom = stored.filter { token -> KNOWN_SERVICES.none { it.id == token.serviceId } }
            .map { ServiceRow(it.serviceId, it.label, it.hasValue) }
        return known + custom
    }
}
