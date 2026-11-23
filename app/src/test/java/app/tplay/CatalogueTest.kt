package app.tplay

import app.tplay.data.discover.DiscoverFilters
import app.tplay.data.discover.hasNextPage
import app.tplay.data.discover.listUrl
import app.tplay.data.discover.parseCards
import app.tplay.data.discover.parseReleases
import app.tplay.data.discover.parseSynopsis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueTest {
    private fun page(name: String) = javaClass.getResource("/discover/$name")!!.readText()

    @Test fun buildsUrlWithOnlyTheChosenFilters() {
        val filters = DiscoverFilters(keyword = "blue sky", genre = "Sci-Fi", year = "2020-2029", orderBy = "rating")
        assertEquals(
            "https://x.test/movies?keyword=blue+sky&genre=Sci-Fi&year=2020-2029&order_by=rating&page=2",
            listUrl("https://x.test/", filters, 2),
        )
        assertEquals("https://x.test/movies", listUrl("https://x.test", DiscoverFilters(), 1))
    }

    @Test fun countsActiveFiltersButNotTheSearchWord() {
        assertEquals(0, DiscoverFilters(keyword = "x").activeCount)
        assertEquals(2, DiscoverFilters(genre = "Action", rating = "7").activeCount)
    }

    @Test fun readsCardsFromACataloguePage() {
        val cards = parseCards(page("movies.html"))
        assertEquals(3, cards.size)
        val first = cards.first()
        assertTrue(first.title.isNotBlank())
        assertNotNull(first.year)
        assertTrue(first.pageUrl.startsWith("https://"))
        assertTrue(first.posterUrl.orEmpty().contains("tmdb.org"))
        assertNotNull(first.rating)
        assertTrue(hasNextPage(page("movies.html")))
    }

    @Test fun emptyOrUnknownPagesGiveNoCards() {
        assertTrue(parseCards("<html><body>Not a catalogue</body></html>").isEmpty())
        assertFalse(hasNextPage("<html></html>"))
    }

    @Test fun readsDistinctMagnetLinksWithDetails() {
        val releases = parseReleases(page("movie.html"))
        assertTrue(releases.isNotEmpty())
        assertEquals(releases.size, releases.map { it.magnet.substringAfter("btih:").take(40).lowercase() }.toSet().size)
        val first = releases.first()
        assertTrue(first.magnet.startsWith("magnet:?xt=urn:btih:"))
        assertFalse(first.magnet.contains("&amp;"))
        assertTrue(first.name.isNotBlank())
        assertTrue(first.size.isNotBlank())
        assertNotEquals("?", first.quality)
    }

    @Test fun readsSynopsisWithoutTheSiteBoilerplate() {
        val text = parseSynopsis(page("movie.html"))
        assertTrue(text.isNotBlank())
        assertFalse(text.contains("on YTS."))
    }

    private fun assertNotEquals(a: Any, b: Any) = org.junit.Assert.assertNotEquals(a, b)
}
