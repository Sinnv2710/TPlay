package app.tplay.data.discover

import app.tplay.data.source.ChallengeRequired
import app.tplay.data.source.Fetcher
import app.tplay.data.source.SourceDef
import app.tplay.data.source.SourceEngine
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PageResult {
    data class Ok(val cards: List<MovieCard>, val hasNext: Boolean) : PageResult
    data class Failure(val message: String) : PageResult

    /** The page loaded but holds no cards the source understands (the site probably changed). */
    data object Unsupported : PageResult

    /** The site wants a human check first; [url] is the page to open in the browser. */
    data class Challenge(val url: String) : PageResult
}

sealed interface ReleasesResult {
    data class Ok(val synopsis: String, val releases: List<Release>) : ReleasesResult
    data class Failure(val message: String) : ReleasesResult
    data class Challenge(val url: String) : ReleasesResult
}

/** Reads a movie site through its [SourceDef]. One request per page, never more. */
@Singleton
class DiscoverClient @Inject constructor(private val fetcher: Fetcher) {
    private val details = object : LinkedHashMap<String, ReleasesResult.Ok>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReleasesResult.Ok>) = size > CACHE_SIZE
    }

    suspend fun page(def: SourceDef, filters: DiscoverFilters, page: Int): PageResult {
        val url = SourceEngine.listUrl(def, filters, page)
        val html = try {
            fetcher.get(url, MAX_PAGE)
        } catch (e: ChallengeRequired) {
            return PageResult.Challenge(e.url)
        } ?: return PageResult.Failure("Could not reach ${hostOf(def.site)}")
        val list = SourceEngine.parseList(def, html, url)
        return when {
            list.cards.isNotEmpty() -> PageResult.Ok(list.cards, list.hasNext)
            page == 1 && filters == DiscoverFilters() -> PageResult.Unsupported
            else -> PageResult.Ok(emptyList(), false)
        }
    }

    suspend fun releases(def: SourceDef, pageUrl: String): ReleasesResult {
        if (!sameSite(def.site, pageUrl)) return ReleasesResult.Failure("Not a page of this site")
        synchronized(details) { details[pageUrl] }?.let { return it }
        val html = try {
            fetcher.get(pageUrl, MAX_PAGE)
        } catch (e: ChallengeRequired) {
            return ReleasesResult.Challenge(e.url)
        } ?: return ReleasesResult.Failure("Could not reach the site")
        val detail = SourceEngine.parseDetail(def, html, pageUrl)
        if (detail.releases.isEmpty()) return ReleasesResult.Failure("No magnet links on this page")
        return ReleasesResult.Ok(detail.synopsis, detail.releases).also { synchronized(details) { details[pageUrl] = it } }
    }

    private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url

    private fun sameSite(a: String, b: String): Boolean {
        fun root(url: String) = hostOf(url).lowercase().split('.').takeLast(2).joinToString(".")
        return root(a) == root(b)
    }

    private companion object {
        const val CACHE_SIZE = 24
        const val MAX_PAGE = 2 * 1024 * 1024
    }
}
