package app.tplay.domain

import kotlinx.coroutines.flow.Flow

/** Contracts shared by all workers. Change only through the leader. */

enum class JobStatus { QUEUED, SENT, CACHING, DOWNLOADING, UNPACKING, SUBTITLES, READY, FAILED, CANCELLED }

data class Title(
    val id: Long,
    val name: String,
    val year: Int?,
    val posterUrl: String?,
    val folderPath: String?,          // null once files were cleaned
    val sizeBytes: Long,
    val createdAt: Long,
    val rdTorrentId: String?,         // Real-Debrid torrent id (for re-fetching a link)
    val rdLink: String?,              // restricted RD link, unrestrict again to stream
    val filesDeleted: Boolean,
)

data class Job(
    val id: Long,
    val titleId: Long?,
    val magnet: String,
    val subtitleUrl: String?,
    val parentFolder: String,
    val status: JobStatus,
    val progress: Int,                // 0..100
    val speedBytesPerSec: Long,
    val step: String,                 // human readable current step
    val error: String?,
    val createdAt: Long,
)

data class WatchProgress(
    val titleId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
    val watchCount: Int,
)

data class HistoryDay(val epochDay: Long, val titleIds: List<Long>)

data class UpcomingEpisode(val showName: String, val season: Int, val episode: Int, val airEpochDay: Long)

enum class CleanupPreset { NEVER, ONE_WEEK, ONE_MONTH, CUSTOM }

data class CleanupPolicy(
    val preset: CleanupPreset,
    val customDays: Int,
    val alsoRemoveFromRd: Boolean,
    val keepHistory: Boolean,
)

data class ServiceToken(val serviceId: String, val label: String, val hasValue: Boolean)

data class SubtitleHit(val id: String, val language: String, val release: String, val downloads: Int, val source: String)

interface LibraryRepository {
    fun titles(): Flow<List<Title>>
    fun progress(): Flow<Map<Long, WatchProgress>>
    fun historyByDay(): Flow<List<HistoryDay>>
    suspend fun delete(titleId: Long, removeFromRd: Boolean)

    /** Saves the TMDB poster address for a title. */
    suspend fun setPoster(titleId: Long, posterUrl: String)

    /** Adds a stream-only title (files stay on Real-Debrid). Returns the saved title. */
    suspend fun addRemote(name: String, year: Int?, sizeBytes: Long, torrentId: String, link: String): Title
}

interface JobRepository {
    fun jobs(): Flow<List<Job>>
    /** Starts the pipeline (send to RD, download, unpack, subtitles). Returns the job id. */
    suspend fun enqueue(magnet: String, subtitleUrl: String?, parentFolder: String, playWhenReady: Boolean): Long
    suspend fun cancel(jobId: Long)
}

interface ProgressRepository {
    suspend fun save(titleId: Long, positionMs: Long, durationMs: Long)
    suspend fun get(titleId: Long): WatchProgress?
}

interface SettingsRepository {
    fun cleanupPolicy(): Flow<CleanupPolicy>
    suspend fun setCleanupPolicy(p: CleanupPolicy)
    fun parentFolder(): Flow<String>
    suspend fun setParentFolder(path: String)
    fun wifiOnly(): Flow<Boolean>
    suspend fun setWifiOnly(v: Boolean)
    fun preferredPlayer(): Flow<String>   // "builtin" | "vlc" | "mx"
    suspend fun setPreferredPlayer(v: String)

    /** Address the Browse tab opens first. Empty means a blank page with a hint. */
    fun browseStartPage(): Flow<String>
    suspend fun setBrowseStartPage(value: String)

    /** Whether the 18+ genre is offered in Discover. Off by default. */
    fun showAdult(): Flow<Boolean>
    suspend fun setShowAdult(value: Boolean)
    suspend fun cacheBytes(): Long
    suspend fun clearCache()
    /** Deletes downloaded files older than [days]. Returns number of titles cleaned. */
    suspend fun cleanOlderThan(days: Int): Int
}

interface VaultRepository {
    fun services(): Flow<List<ServiceToken>>
    suspend fun put(serviceId: String, label: String, value: String)
    suspend fun get(serviceId: String): String?     // callers must never log it
    suspend fun remove(serviceId: String)
}

interface SubtitleRepository {
    suspend fun search(title: String, year: Int?, language: String, fileHash: String?): List<SubtitleHit>
    suspend fun download(hit: SubtitleHit, intoFolder: String): String   // returns the saved file path
}

interface TraktRepository {
    suspend fun upcoming(fromEpochDay: Long, days: Int): List<UpcomingEpisode>
    suspend fun scrobble(titleId: Long, progressPercent: Float, state: String) // start|pause|stop
}

/** Well-known vault ids. */
object ServiceIds {
    const val REAL_DEBRID = "real-debrid"
    const val OPEN_SUBTITLES = "opensubtitles"
    const val SUBDL = "subdl"
    const val TMDB = "tmdb"
    const val TRAKT = "trakt"
}
