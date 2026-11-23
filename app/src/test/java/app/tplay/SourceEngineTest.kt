package app.tplay

import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.source.DetailDef
import app.tplay.data.source.ListDef
import app.tplay.data.source.SourceDef
import app.tplay.data.source.isChallenge
import app.tplay.ui.browse.blockRequest
import app.tplay.data.source.SourceEngine
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceEngineTest {
    private fun resource(name: String) = javaClass.getResource("/discover/$name")!!.readText()

    private val def: SourceDef = Json { ignoreUnknownKeys = true }
        .decodeFromString<SourceDef>(File("src/main/assets/sources/yts.json").readText())
        .copy(site = "https://site.test")

    @Test fun builtInDefinitionIsValid() {
        assertNull(SourceEngine.validate(def))
    }

    @Test fun buildsUrlWithOnlyTheChosenFilters() {
        val filters = DiscoverFilters(keyword = "blue sky").with("genre", "Sci-Fi").with("year", "2020-2029")
        assertEquals(
            "https://site.test/movies?keyword=blue+sky&genre=Sci-Fi&year=2020-2029&page=2",
            SourceEngine.listUrl(def, filters, 2),
        )
        assertEquals("https://site.test/movies", SourceEngine.listUrl(def, DiscoverFilters(), 1))
    }

    @Test fun placeholdersInThePathAreFilled() {
        val pathDef = def.copy(list = def.list.copy(path = "/search/{keyword}/{page}/"))
        val url = SourceEngine.listUrl(pathDef, DiscoverFilters("blue sky"), 1)
        assertEquals("https://site.test/search/blue%20sky/1/", url)
    }

    @Test fun countsActiveFiltersButNotTheSearchWord() {
        assertEquals(0, DiscoverFilters(keyword = "x").activeCount)
        assertEquals(2, DiscoverFilters().with("genre", "Action").with("rating", "7").activeCount)
        assertEquals(1, DiscoverFilters().with("genre", "Action").with("genre", "").with("year", "2025").activeCount)
        assertEquals(1, DiscoverFilters(onlySeeded = true).activeCount)
        assertEquals(0, DiscoverFilters(onlySeeded = true).cleared().activeCount)
    }

    @Test fun readsCardsFromACataloguePage() {
        val page = SourceEngine.parseList(def, resource("movies.html"), "https://site.test/movies")
        assertEquals(3, page.cards.size)
        val first = page.cards.first()
        assertTrue(first.title.isNotBlank())
        assertNotNull(first.year)
        assertTrue(first.pageUrl.startsWith("https://"))
        assertTrue(first.posterUrl.orEmpty().startsWith("http"))
        assertNotNull(first.rating)
        assertTrue(page.hasNext)
    }

    @Test fun unknownPagesGiveNoCards() {
        val page = SourceEngine.parseList(def, "<html><body>Not a catalogue</body></html>", "https://site.test/")
        assertTrue(page.cards.isEmpty())
        assertFalse(page.hasNext)
    }

    @Test fun relativePosterAddressesBecomeAbsolute() {
        val html = """<div class="card group" data-kind="movies"><a href="/movies/a" class="block" title="A">
            <img src="/img/gen/movie-1.jpg" data-fb="/img/w342/x.jpg" alt="A"></a>
            <h3>A</h3><p><span>2020</span><span>Drama</span></p></div>"""
        val card = SourceEngine.parseList(def, html, "https://site.test/movies").cards.single()
        assertEquals("https://site.test/img/gen/movie-1.jpg", card.posterUrl)
        assertEquals("https://site.test/img/w342/x.jpg", card.fallbackPosterUrl)
        assertEquals("https://site.test/movies/a", card.pageUrl)
    }

    @Test fun readsDistinctMagnetLinksWithDetails() {
        val detail = SourceEngine.parseDetail(def, resource("movie.html"), "https://site.test/movies/example")
        val releases = detail.releases
        assertTrue(releases.isNotEmpty())
        assertEquals(releases.size, releases.map { it.magnet.substringAfter("btih:").take(40).lowercase() }.toSet().size)
        val first = releases.first()
        assertTrue(first.magnet.startsWith("magnet:?xt=urn:btih:"))
        assertFalse(first.magnet.contains("&amp;"))
        assertTrue(first.name.isNotBlank())
        assertTrue(first.size.isNotBlank())
        assertTrue(first.quality != "?")
        assertNotNull(first.seeds)
    }

    @Test fun readsSynopsisWithoutTheSiteBoilerplate() {
        val text = SourceEngine.parseDetail(def, resource("movie.html"), "https://site.test/m").synopsis
        assertTrue(text.isNotBlank())
        assertFalse(text.contains("on YTS."))
    }

    @Test fun rejectsBrokenDefinitions() {
        assertNotNull(SourceEngine.validate(def.copy(id = "Bad Id!")))
        assertNotNull(SourceEngine.validate(def.copy(site = "http://site.test")))
        assertNotNull(SourceEngine.validate(def.copy(list = def.list.copy(path = "movies"))))
        assertNotNull(SourceEngine.validate(def.copy(list = def.list.copy(card = "div[[["))))
        assertNotNull(SourceEngine.validate(def.copy(list = def.list.copy(fields = mapOf("title" to "h3")))))
        assertNotNull(SourceEngine.validate(def.copy(detail = def.detail.copy(fields = emptyMap()))))
    }

    // A torrent-table site: no posters, no filters, a browse page for an empty search, magnet on the detail page.
    private val tableDef = SourceDef(
        id = "table-site", name = "Table site", site = "https://t.test",
        list = ListDef(
            path = "/search/{keyword}/{page}/",
            emptyPath = "/browse/{page}/",
            card = "table.list tr:has(td.name)",
            next = "div.pager a:contains(>>)",
            infoLabel = "seeds",
            fields = mapOf(
                "title" to "td.name a[href^=/t/]", "year" to "td.name a[href^=/t/]",
                "link" to "td.name a[href^=/t/]@href", "info" to "td.seeds",
            ),
        ),
        detail = DetailDef(
            release = "body",
            fields = mapOf(
                "name" to "h1", "size" to "li:has(strong:containsOwn(Size)) span",
                "seeds" to "span.seeds", "magnet" to "a[href^=magnet:]@href",
            ),
        ),
    )

    @Test fun browsePathIsUsedWhenThereIsNoSearchWord() {
        assertEquals("https://t.test/browse/1/", SourceEngine.listUrl(tableDef, DiscoverFilters(), 1))
        assertEquals("https://t.test/browse/3/", SourceEngine.listUrl(tableDef, DiscoverFilters(), 3))
        val searched = SourceEngine.listUrl(tableDef, DiscoverFilters("blue sky"), 2)
        assertEquals("https://t.test/search/blue%20sky/2/", searched)
        assertNull(SourceEngine.validate(tableDef))
        val badBrowse = tableDef.copy(list = tableDef.list.copy(emptyPath = "browse"))
        assertNotNull(SourceEngine.validate(badBrowse))
    }

    @Test fun readsATorrentTableWithoutPosters() {
        val html = """<table class="list"><thead><tr><th>name</th></tr></thead><tbody>
            <tr><td class="name"><a href="/x/1" class="icon"></a>
            <a href="/t/7/Big-Movie-2019-1080p/">Big Movie 2019 1080p</a></td>
            <td class="seeds">42</td></tr>
            <tr><td class="name"><a href="/t/8/Old/">Old Film</a></td><td class="seeds">3</td></tr></tbody></table>
            <div class="pager"><a href="/browse/2/">&gt;&gt;</a></div>"""
        val page = SourceEngine.parseList(tableDef, html, "https://t.test/browse/1/")
        assertEquals(2, page.cards.size)
        val first = page.cards.first()
        assertEquals("Big Movie 2019 1080p", first.title)
        assertEquals(2019, first.year)
        assertEquals("42 seeds", first.info)
        assertEquals("https://t.test/t/7/Big-Movie-2019-1080p/", first.pageUrl)
        assertNull(first.posterUrl)
        assertTrue(page.hasNext)
    }

    @Test fun readsASingleReleaseFromADetailPage() {
        val html = """<html><body><h1> Big Movie 2019 1080p </h1><ul>
            <li><strong>Total Size</strong> <span>4.8 GB</span></li>
            <li><strong>Seeders</strong> <span class="seeds">42</span></li></ul>
            <a href="magnet:?xt=urn:btih:ABC123&amp;dn=Big+Movie">Magnet</a></body></html>"""
        val release = SourceEngine.parseDetail(tableDef, html, "https://t.test/t/7/").releases.single()
        assertEquals("Big Movie 2019 1080p", release.name)
        assertEquals("1080p", release.quality)
        assertEquals(42, release.seeds)
        assertTrue(release.magnet.startsWith("magnet:?xt=urn:btih:ABC123"))
    }

    @Test fun recognisesAHumanCheckWall() {
        assertTrue(isChallenge(403, null, "<title>Just a moment...</title>"))
        assertTrue(isChallenge(200, "challenge", ""))
        assertTrue(isChallenge(503, null, "<div id=\"cf-chl-widget\"></div>"))
        assertFalse(isChallenge(403, null, "Forbidden"))
        assertFalse(isChallenge(200, null, "<html>Just a moment</html>"))
    }

    @Test fun adBlockerLetsTheCloudflareCheckRun() {
        assertFalse(blockRequest("challenges.cloudflare.com", "1337x.to", false, "*/*"))
        assertTrue(blockRequest("ads.unknown-ads.xyz", "1337x.to", false, "*/*"))
    }
}
