package app.tplay

import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.source.SourceDef
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
}
