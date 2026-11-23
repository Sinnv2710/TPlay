package app.tplay

import app.tplay.data.discover.MovieCard
import app.tplay.ui.discover.inLibrary
import app.tplay.ui.discover.libraryKey
import app.tplay.ui.discover.visibleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverModelTest {
    private fun card(title: String, year: Int?, genres: String = "Drama") =
        MovieCard(
            title = title, year = year, genres = genres, posterUrl = null, rating = "7.0",
            qualities = listOf("1080p"), pageUrl = "https://x.test/movies/a",
        )

    @Test fun libraryTagMatchesNameAndYearIgnoringCase() {
        val keys = setOf(libraryKey("Sintel", 2010))
        assertTrue(card("sintel ", 2010).inLibrary(keys))
        assertFalse(card("Sintel", 2011).inLibrary(keys))
        assertTrue(card("Sin-tel!", 2010).inLibrary(keys))
        assertTrue(card("Sintel", 2010).inLibrary(setOf(libraryKey("Sintel", null))))
    }

    @Test fun adultOptionsAreHiddenUnlessAllowed() {
        val cards = listOf(card("A", 2020), card("B", 2020, "18+, Drama"))
        assertEquals(listOf("A"), cards.visibleFor(listOf("18+"), false).map { it.title })
        assertEquals(2, cards.visibleFor(listOf("18+"), true).size)
        assertEquals(2, cards.visibleFor(emptyList(), false).size)
    }
}
