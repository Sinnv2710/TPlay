package app.tplay

import app.tplay.data.discover.MovieCard
import app.tplay.ui.browse.siteOf
import app.tplay.ui.discover.DiscoverOptions
import app.tplay.ui.discover.inLibrary
import app.tplay.ui.discover.libraryKey
import app.tplay.ui.discover.visibleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverModelTest {
    private fun card(title: String, year: Int?, genres: String = "Drama") =
        MovieCard(title, year, genres, null, "7.0", listOf("1080p"), "https://x.test/movies/a")

    @Test fun libraryTagMatchesNameAndYearIgnoringCase() {
        val keys = setOf(libraryKey("Sintel", 2010))
        assertTrue(card("sintel ", 2010).inLibrary(keys))
        assertFalse(card("Sintel", 2011).inLibrary(keys))
        assertTrue(card("Sin-tel!", 2010).inLibrary(keys))
        assertTrue(card("Sintel", 2010).inLibrary(setOf(libraryKey("Sintel", null))))
    }

    @Test fun adultGenreIsOffUnlessAllowed() {
        assertFalse(DiscoverOptions.ADULT_GENRE in DiscoverOptions.genresFor(false))
        assertEquals(DiscoverOptions.ADULT_GENRE, DiscoverOptions.genresFor(true).first())
        val cards = listOf(card("A", 2020), card("B", 2020, "18+, Drama"))
        assertEquals(listOf("A"), cards.visibleFor(false).map { it.title })
        assertEquals(2, cards.visibleFor(true).size)
    }

    @Test fun siteIsSchemeAndHostOnly() {
        assertEquals("https://en.example.com", siteOf("https://EN.example.com/movies/x?page=2"))
        assertNull(siteOf("magnet:?xt=urn:btih:abc"))
        assertNull(siteOf("javascript:alert(1)"))
        assertNull(siteOf(null))
    }
}
