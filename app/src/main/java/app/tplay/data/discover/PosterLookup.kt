package app.tplay.data.discover

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Finds a poster for cards whose site shows none. [find] asks a movie database for (title, year).
 * Answers are remembered, and at most [parallel] questions run at once.
 */
class PosterLookup(parallel: Int = 4, private val find: suspend (String, Int?) -> String?) {
    private val gate = Semaphore(parallel)
    private val known = ConcurrentHashMap<String, String>()

    /** Poster address for [card], or null when none is found. A miss is remembered too. */
    suspend fun posterFor(card: MovieCard): String? {
        val clean = cleanReleaseTitle(card.title)
        val year = card.year ?: clean.year
        val key = clean.title.lowercase() + "|" + (year ?: 0)
        known[key]?.let { return it.ifEmpty { null } }
        val url = gate.withPermit { find(clean.title, year) ?: if (year != null) find(clean.title, null) else null }
        known[key] = url.orEmpty()
        return url
    }
}
