package app.tplay

import app.tplay.data.discover.DiscoverClient
import app.tplay.data.source.Fetcher
import app.tplay.data.source.HealthLevel
import app.tplay.data.source.SourceDef
import app.tplay.data.source.SourceStore
import app.tplay.data.source.StoreResult
import app.tplay.data.source.selfTest
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceStoreTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val builtIn: SourceDef = json
        .decodeFromString<SourceDef>(File("src/main/assets/sources/yts.json").readText())
        .copy(site = "https://site.test")
    private val extra = builtIn.copy(id = "extra", name = "Extra", site = "https://extra.test")

    private fun page(name: String) = javaClass.getResource("/discover/$name")!!.readText()
    private fun index(version: Int) =
        """{"name":"My sources","sources":[
            {"id":"extra","name":"Extra","version":$version,"url":"https://r.test/extra.json"}]}"""

    private fun store(dir: File, pages: MutableMap<String, String> = mutableMapOf()): SourceStore =
        SourceStore(dir, Fetcher { url, _ -> pages[url] }, listOf(builtIn))

    private fun repoPages() = mutableMapOf(
        "https://r.test/index.json" to index(1),
        "https://r.test/extra.json" to json.encodeToString(extra),
    )

    private fun tmp(): File = Files.createTempDirectory("sources").toFile()

    @Test fun builtInSourceIsAlwaysThereAndCurrent() {
        val s = store(tmp())
        assertEquals(listOf("yts"), s.sources().map { it.def.id })
        assertTrue(s.sources().single().builtIn)
        assertEquals("yts", s.current()?.id)
    }

    @Test fun repositoryIsCheckedThenSourceInstalledAndRemembered() = runBlocking {
        val dir = tmp()
        val pages = repoPages()
        val s = store(dir, pages)
        val repo = (s.checkRepo("https://r.test/index.json") as StoreResult.Ok).value
        assertEquals("My sources", repo.name)
        s.addRepo(repo)
        assertTrue(s.install(repo.url, repo.sources.single()) is StoreResult.Ok)
        assertEquals(setOf("yts", "extra"), s.sources().map { it.def.id }.toSet())
        assertEquals("extra", s.state.value.selected)
        val again = store(dir, pages)
        assertEquals(setOf("yts", "extra"), again.sources().map { it.def.id }.toSet())
    }

    @Test fun newerVersionInTheRepositoryOffersAnUpdate() = runBlocking {
        val pages = repoPages()
        val s = store(tmp(), pages)
        val repo = (s.checkRepo("https://r.test/index.json") as StoreResult.Ok).value
        s.addRepo(repo)
        s.install(repo.url, repo.sources.single())
        assertNull(s.sources().first { it.def.id == "extra" }.updateTo)
        pages["https://r.test/index.json"] = index(2)
        pages["https://r.test/extra.json"] = json.encodeToString(extra.copy(version = 2))
        s.refreshRepos()
        assertEquals(2, s.sources().first { it.def.id == "extra" }.updateTo)
        assertTrue(s.update("extra") is StoreResult.Ok)
        assertEquals(2, s.sources().first { it.def.id == "extra" }.def.version)
        assertNull(s.sources().first { it.def.id == "extra" }.updateTo)
    }

    @Test fun badRepositoriesAndFilesAreRefused() = runBlocking {
        val s = store(tmp(), mutableMapOf("https://r.test/bad.json" to "not json", "https://r.test/http.json" to index(1)))
        assertTrue(s.checkRepo("http://r.test/index.json") is StoreResult.Failed)
        assertTrue(s.checkRepo("https://r.test/missing.json") is StoreResult.Failed)
        assertTrue(s.checkRepo("https://r.test/bad.json") is StoreResult.Failed)
        assertTrue(s.importJson("{}") is StoreResult.Failed)
        assertTrue(s.importJson(json.encodeToString(extra.copy(site = "http://extra.test"))) is StoreResult.Failed)
        assertEquals(listOf("yts"), s.sources().map { it.def.id })
    }

    @Test fun pastedSourceIsLocalWithoutUpdates() = runBlocking {
        val s = store(tmp())
        assertTrue(s.importJson(json.encodeToString(extra)) is StoreResult.Ok)
        val info = s.sources().first { it.def.id == "extra" }
        assertNull(info.repoUrl)
        assertNull(info.updateTo)
        assertTrue(s.update("extra") is StoreResult.Failed)
    }

    @Test fun switchedOffSourceIsSkippedAndRemovedSourceIsGone() = runBlocking {
        val s = store(tmp())
        s.importJson(json.encodeToString(extra))
        s.select("extra")
        assertEquals("extra", s.current()?.id)
        s.setEnabled("extra", false)
        assertEquals("yts", s.current()?.id)
        s.setEnabled("extra", true)
        s.remove("extra")
        assertEquals(listOf("yts"), s.sources().map { it.def.id })
        assertEquals("yts", s.current()?.id)
    }

    @Test fun selfTestReportsWhatItCouldRead() = runBlocking {
        val fetch = Fetcher { url, _ -> if (url.contains("/movies/")) page("movie.html") else page("movies.html") }
        val ok = selfTest(DiscoverClient(fetch), builtIn)
        assertEquals(HealthLevel.OK, ok.level)
        assertTrue(ok.message.contains("3 cards"))
        val changed = selfTest(DiscoverClient { _, _ -> "<html>redesigned</html>" }, builtIn)
        assertEquals(HealthLevel.WARN, changed.level)
        val down = selfTest(DiscoverClient { _, _ -> null }, builtIn)
        assertEquals(HealthLevel.FAIL, down.level)
        assertFalse(down.message.isBlank())
    }
}
