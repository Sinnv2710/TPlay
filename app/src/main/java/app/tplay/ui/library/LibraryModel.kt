package app.tplay.ui.library

import app.tplay.domain.CleanupPolicy
import app.tplay.domain.CleanupPreset
import app.tplay.domain.Title
import app.tplay.domain.WatchProgress

enum class LibraryTab { CONTINUE, ALL, FINISHED, REAL_DEBRID }

data class LibraryItem(
    val title: Title,
    val progress: WatchProgress?,
    val daysLeft: Int?,
)

private const val DAY_MS = 86_400_000L

/** Days until the cleanup policy deletes the files, or null when they are kept (or already gone). */
fun daysLeft(title: Title, policy: CleanupPolicy, nowMillis: Long): Int? {
    if (title.filesDeleted) return null
    val limit = when (policy.preset) {
        CleanupPreset.NEVER -> return null
        CleanupPreset.ONE_WEEK -> 7
        CleanupPreset.ONE_MONTH -> 30
        CleanupPreset.CUSTOM -> policy.customDays
    }
    val age = ((nowMillis - title.createdAt) / DAY_MS).toInt()
    return (limit - age).coerceAtLeast(0)
}

fun buildItems(
    titles: List<Title>,
    progress: Map<Long, WatchProgress>,
    policy: CleanupPolicy,
    nowMillis: Long,
): List<LibraryItem> = titles
    .map { LibraryItem(it, progress[it.id], daysLeft(it, policy, nowMillis)) }
    .sortedByDescending { it.progress?.updatedAt ?: it.title.createdAt }

fun filterItems(items: List<LibraryItem>, tab: LibraryTab, query: String): List<LibraryItem> {
    val byTab = when (tab) {
        LibraryTab.CONTINUE -> items.filter { it.progress?.finished != true }
        LibraryTab.ALL -> items
        LibraryTab.FINISHED -> items.filter { it.progress?.finished == true }
        LibraryTab.REAL_DEBRID -> emptyList()
    }
    val needle = query.trim()
    if (needle.isEmpty()) return byTab
    return byTab.filter { it.title.name.contains(needle, ignoreCase = true) }
}

fun progressFraction(progress: WatchProgress?): Float {
    if (progress == null || progress.durationMs <= 0) return 0f
    return (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
}

/** A torrent that is already on Real-Debrid and can be streamed right away. */
data class RemoteItem(
    val torrentId: String,
    val name: String,
    val year: Int?,
    val sizeBytes: Long,
    val link: String,
    val inLibrary: Boolean,
)

/** Keeps finished torrents with a link, marks the ones already in the library (same name and year). */
fun remoteItems(
    torrents: List<app.tplay.data.rd.RdTorrentSummary>,
    titles: List<app.tplay.domain.Title>,
): List<RemoteItem> {
    val owned = titles.map { it.name.lowercase() to it.year }.toSet()
    return torrents
        .filter { it.status == "downloaded" && it.links.isNotEmpty() }
        .map {
            val parsed = app.tplay.work.parseTitleName(it.filename)
            RemoteItem(
                torrentId = it.id,
                name = parsed.title,
                year = parsed.year,
                sizeBytes = it.bytes,
                link = it.links.first(),
                inLibrary = (parsed.title.lowercase() to parsed.year) in owned,
            )
        }
        .distinctBy { it.name.lowercase() to it.year }
}
