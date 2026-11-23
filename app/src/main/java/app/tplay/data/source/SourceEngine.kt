package app.tplay.data.source

import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.Release
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class ListPage(val cards: List<MovieCard>, val hasNext: Boolean)

data class DetailPage(val synopsis: String, val releases: List<Release>)

/** Reads a site as described by a [SourceDef]. Pure functions: no network, no code from the source runs. */
object SourceEngine {
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
    private val PARAM = Regex("[A-Za-z0-9_]{1,30}")
    private val YEAR = Regex("(19|20)\\d{2}")
    private val NUMBER = Regex("[0-9]+(?:\\.[0-9]+)?")
    private val DIGITS = Regex("[0-9]+")
    private val RESOLUTION = Regex("(2160p|1080p|720p|480p|3D)", RegexOption.IGNORE_CASE)
    private val INFO_HASH = Regex("btih:([A-Za-z0-9]+)")
    private const val MAX_OPTIONS = 100

    /** Address of one list page. Query pairs whose value is blank are dropped; page 1 omits `page` in the query. */
    fun listUrl(def: SourceDef, filters: DiscoverFilters, page: Int): String {
        val values = filters.values + ("keyword" to filters.keyword.trim())
        val path = def.list.path.substringBefore('?')
        val query = def.list.path.substringAfter('?', "")
        val builtPath = fill(path, values, page, forPath = true)
        val pairs = query.split('&').filter { it.isNotBlank() }.mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val filled = fill(pair.substringAfter('=', ""), values, page, forPath = false)
            if (filled.isBlank() || (pair.contains("{page}") && page <= 1)) null else "$key=$filled"
        }
        return def.site.trimEnd('/') + builtPath + if (pairs.isEmpty()) "" else "?" + pairs.joinToString("&")
    }

    private fun fill(template: String, values: Map<String, String>, page: Int, forPath: Boolean): String =
        Regex("\\{([A-Za-z0-9_]+)\\}").replace(template) { match ->
            val raw = if (match.groupValues[1] == "page") page.toString() else values[match.groupValues[1]].orEmpty()
            val encoded = URLEncoder.encode(raw, "UTF-8")
            if (forPath) encoded.replace("+", "%20") else encoded
        }

    fun parseList(def: SourceDef, html: String, baseUrl: String): ListPage {
        val doc = Jsoup.parse(html, baseUrl)
        val fields = def.list.fields
        val cards = doc.select(def.list.card).mapNotNull { card ->
            val title = pick(card, fields["title"]) ?: return@mapNotNull null
            val link = pick(card, fields["link"]) ?: return@mapNotNull null
            MovieCard(
                title = title,
                year = pick(card, fields["year"])?.let { YEAR.find(it)?.value?.toIntOrNull() },
                genres = pick(card, fields["genres"]).orEmpty(),
                posterUrl = pick(card, fields["poster"]),
                fallbackPosterUrl = pick(card, fields["posterFallback"]),
                rating = pick(card, fields["rating"])?.let { NUMBER.find(it)?.value },
                qualities = pickAll(card, fields["quality"]).distinct(),
                pageUrl = link,
            )
        }
        val next = def.list.next?.let { doc.selectFirst(it) != null } ?: false
        return ListPage(cards, next)
    }

    fun parseDetail(def: SourceDef, html: String, baseUrl: String): DetailPage {
        val doc = Jsoup.parse(html, baseUrl)
        val fields = def.detail.fields
        val seen = mutableSetOf<String>()
        val releases = doc.select(def.detail.release).mapNotNull { row ->
            val magnet = pick(row, fields["magnet"])?.takeIf { it.startsWith("magnet:", ignoreCase = true) }
                ?: return@mapNotNull null
            val hash = INFO_HASH.find(magnet)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            if (!seen.add(hash)) return@mapNotNull null
            val name = pick(row, fields["name"]) ?: dnOf(magnet) ?: hash
            Release(
                name = name,
                quality = pick(row, fields["quality"]) ?: RESOLUTION.find(name)?.value?.lowercase() ?: "?",
                size = pick(row, fields["size"]).orEmpty(),
                seeds = pick(row, fields["seeds"])?.replace(",", "")?.let { DIGITS.find(it)?.value?.toIntOrNull() },
                magnet = magnet,
            )
        }.sortedByDescending { it.seeds ?: -1 }
        val synopsis = def.detail.synopsis?.let { pick(doc, it) }.orEmpty()
            .let { text -> def.detail.synopsisAfter?.let { text.substringAfter(it, text) } ?: text }.trim()
        return DetailPage(synopsis, releases)
    }

    private fun dnOf(magnet: String): String? = Regex("[?&]dn=([^&]+)").find(magnet)?.groupValues?.get(1)
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }

    private fun split(expr: String): Pair<String, String?> {
        val at = expr.lastIndexOf('@')
        val attr = if (at > 0) expr.substring(at + 1) else null
        return if (attr != null && Regex("[A-Za-z-]+").matches(attr)) expr.substring(0, at) to attr else expr to null
    }

    private fun pick(root: Element, expr: String?): String? {
        if (expr.isNullOrBlank()) return null
        val (selector, attr) = split(expr)
        val element = (if (selector.isBlank()) root else root.selectFirst(selector)) ?: return null
        return read(element, attr)
    }

    private fun pickAll(root: Element, expr: String?): List<String> {
        if (expr.isNullOrBlank()) return emptyList()
        val (selector, attr) = split(expr)
        return root.select(selector).mapNotNull { read(it, attr) }
    }

    private fun read(element: Element, attr: String?): String? {
        val value = if (attr == null) {
            element.text()
        } else {
            val raw = element.attr(attr)
            when {
                raw.startsWith("magnet:", ignoreCase = true) -> raw
                attr in URL_ATTRS -> element.absUrl(attr).ifEmpty { raw }
                else -> raw
            }
        }
        return value.trim().takeIf { it.isNotEmpty() }
    }

    private val URL_ATTRS = setOf("href", "src", "data-src", "data-fb", "data-original")

    /** Returns why a definition cannot be used, or null when it is fine. */
    fun validate(def: SourceDef): String? {
        if (!ID.matches(def.id)) return "The id must be 1 to 40 characters: a-z, 0-9 and dashes"
        if (def.name.isBlank() || def.name.length > 60) return "The name is missing or too long"
        val host = runCatching { URI(def.site) }.getOrNull()
        if (host?.scheme != "https" || host.host.isNullOrBlank()) return "The site must be an https address"
        if (!def.list.path.startsWith("/")) return "The list path must start with /"
        if ("title" !in def.list.fields || "link" !in def.list.fields) return "The list needs title and link fields"
        if ("magnet" !in def.detail.fields) return "The detail needs a magnet field"
        val fieldSelectors = (def.list.fields.values + def.detail.fields.values + listOfNotNull(def.detail.synopsis))
            .map { split(it).first }
        val selectors = listOfNotNull(def.list.card, def.list.next, def.detail.release) + fieldSelectors
        val probe = Jsoup.parse("<p></p>")
        selectors.filter { it.isNotBlank() }.forEach { selector ->
            if (runCatching { probe.select(selector) }.isFailure) return "Not a valid selector: $selector"
        }
        def.filters.forEach { filter ->
            if (!PARAM.matches(filter.param)) return "Bad filter parameter: ${filter.param}"
            if (filter.options.size > MAX_OPTIONS) return "Too many options in ${filter.name}"
        }
        return null
    }
}
