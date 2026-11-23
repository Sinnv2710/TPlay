package app.tplay.data.discover

import java.net.URLEncoder

/** What the user chose in the filter sheet. Blank means "any". */
data class DiscoverFilters(
    val keyword: String = "",
    val genre: String = "",
    val year: String = "",
    val quality: String = "",
    val rating: String = "",
    val orderBy: String = "",
) {
    val activeCount: Int get() = listOf(genre, year, quality, rating, orderBy).count { it.isNotBlank() }
}

data class MovieCard(
    val title: String,
    val year: Int?,
    val genres: String,
    val posterUrl: String?,
    val rating: String?,
    val qualities: List<String>,
    val pageUrl: String,
)

/** One downloadable release on a movie page. [magnet] is the full magnet link. */
data class Release(val name: String, val quality: String, val size: String, val seeds: Int?, val magnet: String)

/** Address of one catalogue page, for example `https://site/movies?genre=Action&year=2020-2029&page=2`. */
fun listUrl(site: String, filters: DiscoverFilters, page: Int): String {
    fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    val params = listOf(
        "keyword" to filters.keyword.trim(),
        "genre" to filters.genre,
        "year" to filters.year,
        "quality" to filters.quality,
        "rating" to filters.rating,
        "order_by" to filters.orderBy,
        "page" to if (page > 1) page.toString() else "",
    ).filter { it.second.isNotBlank() }
    val query = params.joinToString("&") { "${it.first}=${enc(it.second)}" }
    return site.trimEnd('/') + "/movies" + if (query.isEmpty()) "" else "?$query"
}

private val CARD_SPLIT = Regex("""<div class="card group"""")
private val CARD_LINK = Regex("""<a href="([^"]+)" class="block" title="""")
private val CARD_TITLE = Regex("""<h3[^>]*>(.*?)</h3>""", RegexOption.DOT_MATCHES_ALL)
private val CARD_META = Regex("""<p class="mt-0\.5[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
private val SPAN = Regex("""<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
private val CARD_POSTER = Regex("""<img src="(https?://[^"]+)"""")
private val CARD_RATING = Regex("""★\s*([0-9]+(?:\.[0-9]+)?)""")
private val CARD_QUALITY = Regex("""class="pill q-[^"]*"[^>]*>([^<]+)<""")

/** Reads the movie cards out of a catalogue page. Cards that cannot be read are skipped. */
fun parseCards(html: String): List<MovieCard> =
    html.split(CARD_SPLIT).drop(1).mapNotNull { chunk ->
        val link = CARD_LINK.find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
        val title = CARD_TITLE.find(chunk)?.groupValues?.get(1)?.let(::cleanText)?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        val spans = CARD_META.find(chunk)?.groupValues?.get(1)
            ?.let { meta -> SPAN.findAll(meta).map { cleanText(it.groupValues[1]) }.toList() }
            .orEmpty()
        MovieCard(
            title = title,
            year = spans.firstOrNull()?.toIntOrNull(),
            genres = spans.drop(1).joinToString(", "),
            posterUrl = CARD_POSTER.find(chunk)?.groupValues?.get(1),
            rating = CARD_RATING.find(chunk)?.groupValues?.get(1),
            qualities = CARD_QUALITY.findAll(chunk).map { it.groupValues[1].trim() }.distinct().toList(),
            pageUrl = decodeEntities(link),
        )
    }

/** True when the page offers a next page of results. */
fun hasNextPage(html: String): Boolean = html.contains("""rel="next"""")

private val MAGNET = Regex("""href="(magnet:[^"]+)"""")
private val RELEASE_NAME = Regex("""title="Release name">([^<]+)""")
private val RELEASE_SIZE = Regex("""class="rsize">([^<]+)<""")
private val RELEASE_SEEDS = Regex("""<b class="num[^"]*">([0-9,]+)</b>\s*seeds""")
private val RESOLUTION = Regex("""(2160p|1080p|720p|480p|3D)""", RegexOption.IGNORE_CASE)
private val INFO_HASH = Regex("""btih:([A-Za-z0-9]+)""")

/** Reads every distinct magnet link on a movie page, with the release name, size and seeds shown next to it. */
fun parseReleases(html: String): List<Release> {
    val seen = mutableSetOf<String>()
    var previousEnd = 0
    val result = mutableListOf<Release>()
    for (match in MAGNET.findAll(html)) {
        val magnet = decodeEntities(match.groupValues[1])
        val hash = INFO_HASH.find(magnet)?.groupValues?.get(1)?.lowercase() ?: continue
        val start = maxOf(previousEnd, match.range.first - WINDOW)
        val window = html.substring(start, match.range.first)
        previousEnd = match.range.last
        if (!seen.add(hash)) continue
        val name = RELEASE_NAME.findAll(window).lastOrNull()?.groupValues?.get(1)?.let(::cleanText)
            ?: dnOf(magnet) ?: hash
        result += Release(
            name = name,
            quality = RESOLUTION.find(name)?.value?.lowercase() ?: "?",
            size = RELEASE_SIZE.findAll(window).lastOrNull()?.groupValues?.get(1)?.trim().orEmpty(),
            seeds = RELEASE_SEEDS.findAll(window).lastOrNull()?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            magnet = magnet,
        )
    }
    return result.sortedByDescending { it.seeds ?: -1 }
}

private const val WINDOW = 2_600

private fun dnOf(magnet: String): String? =
    Regex("""[?&]dn=([^&]+)""").find(magnet)?.groupValues?.get(1)
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }

private val META_DESCRIPTION = Regex("""name="description" content="([^"]+)"""")

/** One or two sentences about the movie, taken from the page description. */
fun parseSynopsis(html: String): String {
    val text = META_DESCRIPTION.find(html)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
    return text.substringAfter(" on YTS. ", text).trim()
}

private val TAGS = Regex("""<[^>]+>""")

private fun cleanText(raw: String): String = decodeEntities(TAGS.replace(raw, "")).trim()

private fun decodeEntities(text: String): String = text
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
