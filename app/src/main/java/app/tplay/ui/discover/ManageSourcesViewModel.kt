package app.tplay.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tplay.data.discover.DiscoverClient
import app.tplay.data.source.RepoEntry
import app.tplay.data.source.RepoSource
import app.tplay.data.source.SourceInfo
import app.tplay.data.source.SourceStore
import app.tplay.data.source.StoreResult
import app.tplay.data.source.selfTest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ManageSourcesViewModel @Inject constructor(
    private val store: SourceStore,
    private val client: DiscoverClient,
) : ViewModel() {
    val sources: StateFlow<List<SourceInfo>> = combine(store.state, store.health) { s, h -> store.sources(s, h) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.sources())
    val repos: StateFlow<List<RepoEntry>> = store.state.map { it.repos }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.state.value.repos)
    val found = MutableStateFlow<RepoEntry?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val testing = MutableStateFlow<Set<String>>(emptySet())

    private fun <T> StoreResult<T>.report(success: String): Boolean = when (this) {
        is StoreResult.Ok -> true.also { message.value = success }
        is StoreResult.Failed -> false.also { message.value = reason }
    }

    fun check(url: String) {
        viewModelScope.launch {
            busy.value = true
            found.value = null
            when (val result = store.checkRepo(url)) {
                is StoreResult.Ok -> found.value = result.value
                is StoreResult.Failed -> message.value = result.reason
            }
            busy.value = false
        }
    }

    fun install(repo: RepoEntry, source: RepoSource) {
        viewModelScope.launch {
            busy.value = true
            store.addRepo(repo)
            store.install(repo.url, source).report("Added ${source.name}")
            busy.value = false
        }
    }

    fun saveRepo(repo: RepoEntry) {
        viewModelScope.launch {
            store.addRepo(repo)
            found.value = null
            message.value = "Saved ${repo.name}"
        }
    }

    fun importJson(text: String) {
        viewModelScope.launch { store.importJson(text).report("Source added") }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(id, enabled) }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            store.remove(id)
            message.value = "Source removed"
        }
    }

    fun update(id: String) {
        viewModelScope.launch {
            busy.value = true
            store.update(id).report("Updated")
            busy.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            busy.value = true
            store.refreshRepos()
            message.value = "Repositories refreshed"
            busy.value = false
        }
    }

    fun removeRepo(url: String) {
        viewModelScope.launch { store.removeRepo(url) }
    }

    fun runSelfTest(id: String) {
        val def = sources.value.firstOrNull { it.def.id == id }?.def ?: return
        viewModelScope.launch {
            testing.update { it + id }
            val result = selfTest(client, def)
            store.report(id, result.level, result.message)
            testing.update { it - id }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
