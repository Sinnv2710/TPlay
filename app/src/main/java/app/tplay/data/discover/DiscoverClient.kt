package app.tplay.data.discover

import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface PageResult {
    data class Ok(val cards: List<MovieCard>, val hasNext: Boolean) : PageResult
    data class Failure(val message: String) : PageResult

    /** The page loaded but holds no cards the reader understands (another site layout). */
    data object Unsupported : PageResult
}

sealed interface ReleasesResult {
    data class Ok(val synopsis: String, val releases: List<Release>) : ReleasesResult
    data class Failure(val message: String) : ReleasesResult
}

/** Reads a movie catalogue site (the page layout of en.yts-official.com). One request per page, never more. */
@Singleton
class DiscoverClient @Inject constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val details = object : LinkedHashMap<String, ReleasesResult.Ok>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReleasesResult.Ok>) = size > CACHE_SIZE
    }

    suspend fun page(site: String, filters: DiscoverFilters, page: Int): PageResult = withContext(Dispatchers.IO) {
        val html = fetch(listUrl(site, filters, page)) ?: return@withContext PageResult.Failure("Could not reach the site")
        val cards = parseCards(html, site)
        when {
            cards.isNotEmpty() -> PageResult.Ok(cards, hasNextPage(html))
            page == 1 && filters == DiscoverFilters() -> PageResult.Unsupported
            else -> PageResult.Ok(emptyList(), false)
        }
    }

    suspend fun releases(site: String, pageUrl: String): ReleasesResult = withContext(Dispatchers.IO) {
        if (!sameHost(site, pageUrl)) return@withContext ReleasesResult.Failure("Not a page of this site")
        synchronized(details) { details[pageUrl] }?.let { return@withContext it }
        val html = fetch(pageUrl) ?: return@withContext ReleasesResult.Failure("Could not reach the site")
        val releases = parseReleases(html)
        if (releases.isEmpty()) return@withContext ReleasesResult.Failure("No magnet links on this page")
        ReleasesResult.Ok(parseSynopsis(html), releases).also { synchronized(details) { details[pageUrl] = it } }
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    private fun sameHost(a: String, b: String): Boolean =
        runCatching { URI(a).host.equals(URI(b).host, ignoreCase = true) }.getOrDefault(false)

    private companion object {
        const val CACHE_SIZE = 24
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0 Mobile Safari/537.36"
    }
}
