package app.tplay.data

import app.tplay.data.subs.OPEN_SUBTITLES_LOGIN
import app.tplay.data.subs.OrgSubtitleSource
import app.tplay.data.subs.SubtitleRepositoryImpl
import app.tplay.domain.ServiceIds
import app.tplay.domain.ServiceToken
import app.tplay.domain.SubtitleHit
import app.tplay.domain.VaultRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun blocking(block: suspend () -> Unit) = runBlocking { block() }

private class MapVault(private val values: Map<String, String>) : VaultRepository {
    override fun services(): Flow<List<ServiceToken>> = flowOf(emptyList())
    override suspend fun put(serviceId: String, label: String, value: String) = Unit
    override suspend fun get(serviceId: String): String? = values[serviceId]
    override suspend fun remove(serviceId: String) = Unit
}

class SubtitleRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().apply { start() }
    }

    @After fun stop() {
        server.shutdown()
    }

    private fun repo(vararg pairs: Pair<String, String>): SubtitleRepositoryImpl {
        val root = server.url("/").toString().trimEnd('/')
        return SubtitleRepositoryImpl(
            MapVault(pairs.toMap()),
            baseUrl = "$root/api/v1",
            org = OrgSubtitleSource(baseUrl = root),
        )
    }

    @Test fun searchWithoutKeySkipsComAndAsksOrg() = blocking {
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(repo().search("Movie", 2020, "en", null).isEmpty())
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().path!!.startsWith("/search/"))
    }

    @Test fun emptyComResultFallsBackToOrg() = blocking {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """[{"SubLanguageID":"eng","SubFileName":"x.srt","SubDownloadsCnt":"3",
                |"SubDownloadLink":"http://x/dl","SubFormat":"srt"}]""".trimMargin(),
            ),
        )
        val hits = repo(ServiceIds.OPEN_SUBTITLES to "KEY").search("M", null, "en", null)
        assertEquals(listOf("OpenSubtitles.org"), hits.map { it.source })
    }

    @Test fun searchParsesFileIdAndSendsApiKey() = blocking {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"1","attributes":{"language":"en","release":"Movie.2020","download_count":5,
                |"files":[{"file_id":777,"file_name":"a.srt"}]}}]}""".trimMargin(),
            ),
        )
        val hits = repo(ServiceIds.OPEN_SUBTITLES to "KEY").search("Movie", 2020, "en", "abc123")
        assertEquals(listOf("777"), hits.map { it.id })
        val request = server.takeRequest()
        assertEquals("KEY", request.getHeader("Api-Key"))
        assertTrue(request.path!!.contains("moviehash=abc123"))
    }

    @Test fun emptyHashSearchRetriesByTitle() = blocking {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(
            MockResponse().setBody(
                """[{"SubLanguageID":"eng","SubFileName":"t.srt","SubDownloadsCnt":"1",
                |"SubDownloadLink":"http://x/dl","SubFormat":"srt"}]""".trimMargin(),
            ),
        )
        val hits = repo().search("Title", null, "en", "0123456789abcdef")
        assertEquals(1, hits.size)
        assertTrue(server.takeRequest().path!!.contains("moviehash-"))
        assertTrue(server.takeRequest().path!!.contains("query-title"))
    }

    @Test fun searchSkipsEntriesWithoutFiles() = blocking {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"1","attributes":{"language":"en"}}]}"""))
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(repo(ServiceIds.OPEN_SUBTITLES to "KEY").search("M", null, "en", null).isEmpty())
    }

    @Test fun searchFailureReturnsEmptyList() = blocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repo(ServiceIds.OPEN_SUBTITLES to "KEY").search("M", null, "en", null).isEmpty())
    }

    @Test fun downloadLogsInRequestsLinkAndSavesFile() = blocking {
        server.enqueue(MockResponse().setBody("""{"token":"JWT"}"""))
        server.enqueue(MockResponse().setBody("""{"link":"${server.url("/file.srt")}"}"""))
        server.enqueue(MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,000\nHi\n"))
        val folder = File.createTempFile("subs", "").apply { delete(); mkdirs() }
        val path = repo(ServiceIds.OPEN_SUBTITLES to "KEY", OPEN_SUBTITLES_LOGIN to "user:pw")
            .download(SubtitleHit("777", "en", "r", 1, "OpenSubtitles"), folder.path)
        assertTrue(File(path).readText().contains("Hi"))
        server.takeRequest()
        assertEquals("Bearer JWT", server.takeRequest().getHeader("Authorization"))
        folder.deleteRecursively()
    }

    @Test(expected = IllegalStateException::class)
    fun downloadWithoutLoginExplainsWhat() {
        runBlocking {
            repo(ServiceIds.OPEN_SUBTITLES to "KEY")
                .download(SubtitleHit("1", "en", "r", 1, "OpenSubtitles"), "/tmp")
        }
    }
}
