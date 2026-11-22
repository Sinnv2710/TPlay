package app.tplay

import app.tplay.data.rd.RdTorrentSummary
import app.tplay.domain.Title
import app.tplay.ui.library.remoteItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteItemsTest {
    private fun torrent(id: String, name: String, status: String = "downloaded", links: List<String> = listOf("l")) =
        RdTorrentSummary(id = id, filename = name, bytes = 10, status = status, links = links)

    private fun title(name: String, year: Int?) = Title(1, name, year, null, null, 0, 0, null, null, true)

    @Test fun onlyFinishedTorrentsWithLinksAreListed() {
        val result = remoteItems(
            listOf(
                torrent("a", "Movie.A.2020.1080p"),
                torrent("b", "Movie.B.2021", status = "downloading"),
                torrent("c", "Movie.C.2022", links = emptyList()),
            ),
            emptyList(),
        )
        assertEquals(listOf("Movie A"), result.map { it.name })
        assertEquals(2020, result.single().year)
    }

    @Test fun titlesAlreadyInTheLibraryAreMarked() {
        val result = remoteItems(listOf(torrent("a", "Big.Buck.Bunny.2008.mp4")), listOf(title("Big Buck Bunny", 2008)))
        assertTrue(result.single().inLibrary)
    }

    @Test fun differentYearIsADifferentMovie() {
        val result = remoteItems(listOf(torrent("a", "Dune.2021")), listOf(title("Dune", 1984)))
        assertFalse(result.single().inLibrary)
    }

    @Test fun sameMovieOnTheAccountTwiceShowsOnce() {
        val result = remoteItems(listOf(torrent("a", "Dune.2021.720p"), torrent("b", "Dune.2021.1080p")), emptyList())
        assertEquals(1, result.size)
    }
}
