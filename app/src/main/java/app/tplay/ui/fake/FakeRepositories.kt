package app.tplay.ui.fake

import app.tplay.domain.CleanupPolicy
import app.tplay.domain.CleanupPreset
import app.tplay.domain.HistoryDay
import app.tplay.domain.Job
import app.tplay.domain.JobRepository
import app.tplay.domain.JobStatus
import app.tplay.domain.LibraryRepository
import app.tplay.domain.ProgressRepository
import app.tplay.domain.ServiceIds
import app.tplay.domain.ServiceToken
import app.tplay.domain.SettingsRepository
import app.tplay.domain.SubtitleHit
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.Title
import app.tplay.domain.TraktRepository
import app.tplay.domain.UpcomingEpisode
import app.tplay.domain.VaultRepository
import app.tplay.domain.WatchProgress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val demoTitles = List(6) { index ->
    Title(index.toLong() + 1, "Demo Title ${index + 1}", 2021 + index, null,
        "/Movies/TPlay", 900_000_000L + index * 100_000_000L, 0L, "rd-$index",
        "https://example.invalid/$index", index == 5)
}

class FakeLibraryRepository : LibraryRepository {
    private val progress = MutableStateFlow(mapOf(1L to WatchProgress(1, 2_100_000, 5_000_000, 0, false, 1)))
    override fun titles(): Flow<List<Title>> = MutableStateFlow(demoTitles)
    override fun progress(): Flow<Map<Long, WatchProgress>> = progress
    override fun historyByDay(): Flow<List<HistoryDay>> = MutableStateFlow(listOf(HistoryDay(0, listOf(1))))
    override suspend fun delete(titleId: Long, removeFromRd: Boolean) = Unit
    override suspend fun setPoster(titleId: Long, posterUrl: String) = Unit
    override suspend fun addRemote(name: String, year: Int?, sizeBytes: Long, torrentId: String, link: String) =
        demoTitles.first()
}

class FakeJobRepository : JobRepository {
    private val nextId = AtomicLong(2)
    private val state = MutableStateFlow(listOf(
        Job(1, 1, "magnet:?demo", null, "/Movies/TPlay", JobStatus.DOWNLOADING,
            64, 38_000_000, "Downloading", null, 0),
        Job(2, null, "magnet:?queued", null, "/Movies/TPlay", JobStatus.QUEUED,
            0, 0, "Queued", null, 0)
    ))
    override fun jobs(): Flow<List<Job>> = state
    override suspend fun enqueue(magnet: String, subtitleUrl: String?, parentFolder: String,
        playWhenReady: Boolean): Long {
        val id = nextId.incrementAndGet()
        state.update { it + Job(id, null, magnet, subtitleUrl, parentFolder, JobStatus.QUEUED,
            0, 0, "Queued", null, 0) }
        return id
    }
    override suspend fun cancel(jobId: Long) {
        state.update { jobs -> jobs.map { if (it.id == jobId) it.copy(status = JobStatus.CANCELLED) else it } }
    }
}

class FakeProgressRepository : ProgressRepository {
    private val values = mutableMapOf<Long, WatchProgress>()
    override suspend fun save(titleId: Long, positionMs: Long, durationMs: Long) {
        values[titleId] = WatchProgress(titleId, positionMs, durationMs, 0,
            positionMs >= durationMs, 1)
    }
    override suspend fun get(titleId: Long): WatchProgress? = values[titleId]
}

class FakeSettingsRepository : SettingsRepository {
    private val policy = MutableStateFlow(CleanupPolicy(CleanupPreset.ONE_WEEK, 7, false, true))
    private val folder = MutableStateFlow("/Movies/TPlay")
    private val wifi = MutableStateFlow(true)
    private val player = MutableStateFlow("builtin")
    override fun browseStartPage(): Flow<String> = MutableStateFlow("")
    override suspend fun setBrowseStartPage(value: String) = Unit
    private val adult = MutableStateFlow(false)
    override fun showAdult() = adult
    override suspend fun setShowAdult(value: Boolean) { adult.value = value }
    override fun cleanupPolicy() = policy
    override suspend fun setCleanupPolicy(p: CleanupPolicy) { policy.value = p }
    override fun parentFolder() = folder
    override suspend fun setParentFolder(path: String) { folder.value = path }
    override fun wifiOnly() = wifi
    override suspend fun setWifiOnly(v: Boolean) { wifi.value = v }
    override fun preferredPlayer() = player
    override suspend fun setPreferredPlayer(v: String) { player.value = v }
    override suspend fun cacheBytes() = 38_200_000_000L
    override suspend fun clearCache() = Unit
    override suspend fun cleanOlderThan(days: Int) = 6
}

class FakeVaultRepository : VaultRepository {
    private val values = MutableStateFlow(mapOf(
        ServiceIds.REAL_DEBRID to "Real-Debrid",
        ServiceIds.OPEN_SUBTITLES to "OpenSubtitles",
        ServiceIds.SUBDL to "SubDL",
        ServiceIds.TMDB to "TMDB",
        ServiceIds.TRAKT to "Trakt"
    ))
    override fun services(): Flow<List<ServiceToken>> = values.map { entries ->
        entries.map { ServiceToken(it.key, it.value, true) }
    }
    override suspend fun put(serviceId: String, label: String, value: String) {
        values.update { it + (serviceId to label) }
    }
    override suspend fun get(serviceId: String): String? = values.value[serviceId]
    override suspend fun remove(serviceId: String) { values.update { it - serviceId } }
}

class FakeSubtitleRepository : SubtitleRepository {
    override suspend fun search(title: String, year: Int?, language: String,
        fileHash: String?) = listOf(SubtitleHit("demo", language, title, 42, "DemoSource"))
    override suspend fun download(hit: SubtitleHit, intoFolder: String) = "$intoFolder/subtitle.srt"
}

class FakeTraktRepository : TraktRepository {
    override suspend fun upcoming(fromEpochDay: Long, days: Int) = listOf(
        UpcomingEpisode("Demo Show", 2, 3, fromEpochDay + 2))
    override suspend fun scrobble(titleId: Long, progressPercent: Float, state: String) = Unit
}
