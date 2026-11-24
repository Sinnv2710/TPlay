package app.tplay

import app.tplay.domain.SubtitleHit
import app.tplay.domain.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMatchTest {
    private fun hit(movie: String = "", year: Int? = null, release: String = "x.srt") =
        SubtitleHit("1", "vi", release, 10, "OpenSubtitles.org", movie, year)

    @Test fun sameMovieMatchesIgnoringCaseAndPunctuation() {
        assertTrue(hit("Tears Of Steel", 2012).matches("Tears of Steel", 2012))
        assertTrue(hit("Spider-Man", 2002).matches("Spider Man", 2002))
    }

    @Test fun otherFilmsFromAFuzzySearchAreRejected() {
        assertFalse(hit("Batman v Superman: Dawn of Justice", 2016).matches("Tears of Steel", 2012))
        assertFalse(hit("Tears of Steel", 1999).matches("Tears of Steel", 2012))
    }

    @Test fun withoutAMovieNameTheReleaseNameDecides() {
        assertTrue(hit(release = "Sintel.2010.1080p.srt").matches("Sintel", 2010))
        assertFalse(hit(release = "Future.Cops.1993.srt").matches("Street Fighter", 2026))
    }
}
