package app.tplay

import app.tplay.data.discover.MovieCard
import app.tplay.data.discover.PosterLookup
import app.tplay.data.discover.cleanReleaseTitle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseTitleTest {
    private fun t(raw: String) = cleanReleaseTitle(raw).let { it.title to it.year }

    @Test fun cleansCommonReleaseNames() {
        assertEquals("Sintel" to 2010, t("Sintel (2010) 2160p DMRip x264 DTS - ABI"))
        assertEquals("Sintel" to 2010, t("Sintel.2010.1080p.BluRay.x264-GROUP"))
        assertEquals("Mighty Joe Young" to 1998, t("Mighty Joe Young (1998 ITA/ENG) [1080p x265] [Paso77]"))
        assertEquals("Saw II" to 2005, t("Saw II 2005 UHD BluRay 2160p HDR10 DV HEVC TrueHD Atmos 7.1 x265-E"))
        val raw = "So.Young.So.Lovely.So.Vicious.1975.(Erotica - Dual).1080p.x264-Classics"
        assertEquals("So Young So Lovely So Vicious" to 1975, t(raw))
    }

    @Test fun releasesWithoutAYearStopAtTheFirstQualityWord() {
        assertEquals("Sintel" to null, t("Sintel 4K UHD ENG FLAC ITA ENG Sub DMRip 1744p X264 ZMachine"))
        assertEquals("The Web" to null, t("The Web"))
    }

    @Test fun yearsInsideTheTitleAreKept() {
        assertEquals("Blade Runner 2049" to 2017, t("Blade Runner 2049 2017 1080p BluRay"))
        assertEquals("2001 A Space Odyssey" to 1968, t("2001 A Space Odyssey 1968 720p"))
        assertEquals("Her Fatal Flaw" to null, t("Her Fatal Flaw"))
    }

    @Test fun neverReturnsAnEmptyTitle() {
        assertEquals("1080p" to null, t("1080p"))
        assertEquals("" to null, t("").let { (a, b) -> a.trim() to b })
    }

    private fun card(title: String, year: Int? = null) = MovieCard(
        title = title, year = year, genres = "", posterUrl = null, rating = null,
        qualities = emptyList(), pageUrl = "https://x.test/$title",
    )

    @Test fun posterLookupUsesTheCleanedTitleAndRemembersAnswers() = runBlocking {
        val asked = mutableListOf<Pair<String, Int?>>()
        val lookup = PosterLookup { title, year ->
            asked += title to year
            if (title == "Sintel") "https://img/sintel.jpg" else null
        }
        assertEquals("https://img/sintel.jpg", lookup.posterFor(card("Sintel (2010) 2160p DMRip x264")))
        assertEquals("https://img/sintel.jpg", lookup.posterFor(card("Sintel.2010.1080p.WEB")))
        assertEquals(listOf("Sintel" to 2010), asked)
    }

    @Test fun posterLookupRetriesWithoutTheYearAndRemembersMisses() = runBlocking {
        val asked = mutableListOf<Pair<String, Int?>>()
        val lookup = PosterLookup { title, year ->
            asked += title to year
            if (year == null) "https://img/a.jpg" else null
        }
        assertEquals("https://img/a.jpg", lookup.posterFor(card("Some Film 1999 720p")))
        assertEquals(listOf("Some Film" to 1999, "Some Film" to null), asked)
        val none = PosterLookup { _, _ -> null }
        assertNull(none.posterFor(card("Unknown Thing 2001")))
        assertNull(none.posterFor(card("Unknown Thing 2001")))
    }
}
