package app.tplay

import app.tplay.data.discover.MovieCard
import app.tplay.ui.discover.inLibrary
import app.tplay.ui.discover.libraryKey
import app.tplay.ui.discover.seeds
import app.tplay.ui.discover.visibleFor
import app.tplay.ui.discover.withSeedsOnly
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
        assertEquals(listOf("A"), cards.visibleFor(listOf("18+"), emptyList(), false).map { it.title })
        assertEquals(2, cards.visibleFor(listOf("18+"), emptyList(), true).size)
        assertEquals(2, cards.visibleFor(emptyList(), emptyList(), false).size)
    }

    @Test fun adultWordsMatchWholeWordsInTitleOrGenres() {
        val cards = listOf(
            card("Sussex Story", 2020),
            card("Night (Erotica - Dual) 1975", 1975),
            card("Plain", 2001, "Adult, Drama"),
        )
        val shown = cards.visibleFor(emptyList(), listOf("erotica", "adult"), false).map { it.title }
        assertEquals(listOf("Sussex Story"), shown)
        assertEquals(3, cards.visibleFor(emptyList(), listOf("erotica", "adult"), true).size)
    }

    @Test fun seedFilterHidesZeroSeedCardsButKeepsCardsWithoutSeedInfo() {
        val none = card("A", 2020)
        val zero = card("B", 2020).copy(info = "0 seeds")
        val some = card("C", 2020).copy(info = "12 seeds")
        assertEquals(listOf("A", "C"), listOf(none, zero, some).withSeedsOnly(true).map { it.title })
        assertEquals(3, listOf(none, zero, some).withSeedsOnly(false).size)
        assertEquals(12, some.seeds())
    }
}
