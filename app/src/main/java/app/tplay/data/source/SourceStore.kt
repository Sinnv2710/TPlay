package app.tplay.data.source

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RepoEntry(val url: String, val name: String, val sources: List<RepoSource>, val checkedAt: Long)

@Serializable
data class Installed(val def: SourceDef, val repoUrl: String? = null, val enabled: Boolean = true)

@Serializable
data class StoreState(
    val repos: List<RepoEntry> = emptyList(),
    val installed: List<Installed> = emptyList(),
    val selected: String? = null,
)

enum class HealthLevel { OK, WARN, FAIL }

data class Health(val level: HealthLevel, val message: String, val at: Long)

/** A source as the screens see it. */
data class SourceInfo(
    val def: SourceDef,
    val builtIn: Boolean,
    val enabled: Boolean,
    val repoUrl: String?,
    val health: Health?,
    /** Newer version offered by a repository, or null. */
    val updateTo: Int?,
)

sealed interface StoreResult<out T> {
    data class Ok<T>(val value: T) : StoreResult<T>
    data class Failed(val reason: String) : StoreResult<Nothing>
}

/**
 * Keeps the sources the user added, their repositories and the chosen source, in one small file.
 * [builtIn] sources ship with the app and cannot be removed.
 */
class SourceStore(
    private val dir: File,
    private val fetcher: Fetcher,
    private val builtIn: List<SourceDef>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(dir, "state.json")
    private val lock = Mutex()
    private val _state = MutableStateFlow(load())
    private val _health = MutableStateFlow<Map<String, Health>>(emptyMap())

    val state: StateFlow<StoreState> = _state.asStateFlow()
    val health: StateFlow<Map<String, Health>> = _health.asStateFlow()

    private fun load(): StoreState = runCatching { json.decodeFromString<StoreState>(file.readText()) }
        .getOrDefault(StoreState())

    private suspend fun change(edit: (StoreState) -> StoreState) = lock.withLock {
        val next = edit(_state.value)
        dir.mkdirs()
        file.writeText(json.encodeToString(StoreState.serializer(), next))
        _state.value = next
    }

    /** Every source: the built-in ones, with any newer installed copy of the same id replacing them. */
    fun sources(state: StoreState = _state.value, health: Map<String, Health> = _health.value): List<SourceInfo> {
        val installedById = state.installed.associateBy { it.def.id }
        val offered = state.repos.flatMap { it.sources }.groupBy { it.id }.mapValues { (_, v) -> v.maxOf { it.version } }
        val builtInInfos = builtIn.filter { installedById[it.id]?.def?.version?.let { v -> v >= it.version } != true }
            .map { SourceInfo(it, true, true, null, health[it.id], offered[it.id]?.takeIf { v -> v > it.version }) }
        val installedInfos = state.installed.map {
            SourceInfo(
                def = it.def,
                builtIn = false,
                enabled = it.enabled,
                repoUrl = it.repoUrl,
                health = health[it.def.id],
                updateTo = offered[it.def.id]?.takeIf { v -> v > it.def.version },
            )
        }
        return builtInInfos + installedInfos
    }

    /** The source Discover should use: the chosen one if it is enabled, otherwise the first enabled one. */
    fun current(): SourceDef? {
        val all = sources().filter { it.enabled }
        return (all.firstOrNull { it.def.id == _state.value.selected } ?: all.firstOrNull())?.def
    }

    suspend fun select(id: String) = change { it.copy(selected = id) }

    suspend fun setEnabled(id: String, enabled: Boolean) = change { state ->
        state.copy(installed = state.installed.map { if (it.def.id == id) it.copy(enabled = enabled) else it })
    }

    suspend fun remove(id: String) = change { state ->
        state.copy(
            installed = state.installed.filterNot { it.def.id == id },
            selected = state.selected.takeIf { it != id },
        )
    }

    fun report(id: String, level: HealthLevel, message: String) {
        _health.update { it + (id to Health(level, message, clock())) }
    }

    /** Reads and checks a repository index without saving it. */
    suspend fun checkRepo(url: String): StoreResult<RepoEntry> {
        if (!url.trim().startsWith("https://")) return StoreResult.Failed("The address must start with https://")
        val text = fetcher.get(url.trim(), MAX_INDEX) ?: return StoreResult.Failed("Could not read the repository")
        val index = runCatching { json.decodeFromString<RepoIndex>(text) }.getOrNull()
            ?: return StoreResult.Failed("This is not a repository index")
        val bad = index.sources.firstOrNull { !it.url.startsWith("https://") || it.id.isBlank() }
        if (bad != null) return StoreResult.Failed("Source ${bad.id.ifBlank { "?" }} has no https address")
        return StoreResult.Ok(RepoEntry(url.trim(), index.name.ifBlank { url.trim() }, index.sources, clock()))
    }

    suspend fun addRepo(entry: RepoEntry) = change { state ->
        state.copy(repos = state.repos.filterNot { it.url == entry.url } + entry)
    }

    suspend fun removeRepo(url: String) = change { state -> state.copy(repos = state.repos.filterNot { it.url == url }) }

    suspend fun refreshRepos() {
        for (repo in _state.value.repos) {
            (checkRepo(repo.url) as? StoreResult.Ok)?.let { addRepo(it.value) }
        }
    }

    /** Downloads one source of a repository, checks it and installs it (or updates it). */
    suspend fun install(repoUrl: String, entry: RepoSource): StoreResult<SourceDef> {
        val text = fetcher.get(entry.url, MAX_DEF) ?: return StoreResult.Failed("Could not read ${entry.name}")
        return save(text, repoUrl, expectedId = entry.id)
    }

    /** Installs a definition pasted or picked from a file. It has no repository, so it never shows an update. */
    suspend fun importJson(text: String): StoreResult<SourceDef> =
        if (text.length > MAX_DEF) StoreResult.Failed("The file is too large") else save(text, null, null)

    suspend fun update(id: String): StoreResult<SourceDef> {
        val info = sources().firstOrNull { it.def.id == id } ?: return StoreResult.Failed("Unknown source")
        val repo = _state.value.repos.firstOrNull { r -> r.url == info.repoUrl }
            ?: return StoreResult.Failed("This source has no repository to update from")
        val entry = repo.sources.firstOrNull { it.id == id } ?: return StoreResult.Failed("The repository dropped it")
        return install(repo.url, entry)
    }

    private suspend fun save(text: String, repoUrl: String?, expectedId: String?): StoreResult<SourceDef> {
        val def = runCatching { json.decodeFromString<SourceDef>(text) }.getOrNull()
            ?: return StoreResult.Failed("This is not a valid source file")
        SourceEngine.validate(def)?.let { return StoreResult.Failed(it) }
        if (expectedId != null && def.id != expectedId) return StoreResult.Failed("The file is for another source")
        change { state ->
            val keepEnabled = state.installed.firstOrNull { it.def.id == def.id }?.enabled ?: true
            state.copy(
                installed = state.installed.filterNot { it.def.id == def.id } + Installed(def, repoUrl, keepEnabled),
                selected = state.selected ?: def.id,
            )
        }
        return StoreResult.Ok(def)
    }

    private companion object {
        const val MAX_INDEX = 256 * 1024
        const val MAX_DEF = 64 * 1024
    }
}
