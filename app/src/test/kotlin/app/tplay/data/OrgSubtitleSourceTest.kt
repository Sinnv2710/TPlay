package app.tplay.data

import app.tplay.data.subs.OrgSubtitleSource
import app.tplay.domain.SubtitleHit
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrgSubtitleSourceTest {
    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().apply { start() }
    }

    @After fun stop() {
        server.shutdown()
    }

    private fun source() = OrgSubtitleSource(baseUrl = server.url("/").toString().trimEnd('/'))

    private fun row(name: String, format: String = "srt", link: String = "http://x/dl") =
        """{"SubLanguageID":"eng","SubFileName":"$name","SubDownloadsCnt":"12",""" +
            """"SubDownloadLink":"$link","SubFormat":"$format"}"""

    @Test fun titleSearchUsesTemporaryUserAgentAndThreeLetterLanguage() {
        server.enqueue(MockResponse().setBody("[${row("a.srt")}]"))
        val hits = source().search("Big Buck Bunny", 2008, "en", null)
        assertEquals(1, hits.size)
        val request = server.takeRequest()
        assertEquals("TemporaryUserAgent", request.getHeader("X-User-Agent"))
        assertTrue(request.path!!.contains("query-big%20buck%20bunny/sublanguageid-eng"))
    }

    @Test fun hashSearchComesFirstInThePath() {
        server.enqueue(MockResponse().setBody("[]"))
        source().search("x", null, "vi", "abcdef0123456789")
        assertTrue(server.takeRequest().path!!.contains("moviehash-abcdef0123456789/sublanguageid-vie"))
    }

    @Test fun unsupportedFormatsAndEmptyLinksAreSkipped() {
        server.enqueue(MockResponse().setBody("[${row("a.zip", "zip")},${row("b.srt", link = "")},${row("c.srt")}]"))
        assertEquals(listOf("c.srt"), source().search("x", null, "en", null).map { it.release })
    }

    @Test fun serverErrorReturnsEmpty() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(source().search("x", null, "en", null).isEmpty())
    }

    @Test fun downloadUnzipsGzipPayload() {
        val gz = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write("1\n00:00:01,000 --> 00:00:02,000\nHi\n".toByteArray()) }
        }.toByteArray()
        server.enqueue(MockResponse().setBody(Buffer().write(gz)))
        val folder = File.createTempFile("org", "").apply { delete(); mkdirs() }
        val hit = SubtitleHit(server.url("/dl").toString(), "en", "r", 1, "OpenSubtitles.org")
        val path = source().download(hit, folder.path)
        assertTrue(File(path).readText().contains("Hi"))
        folder.deleteRecursively()
    }

    @Test fun languageCodesMapToThreeLetters() {
        assertEquals("eng", OrgSubtitleSource.toIso3("en"))
        assertEquals("vie", OrgSubtitleSource.toIso3("VI"))
        assertEquals("pob", OrgSubtitleSource.toIso3("pob"))
    }
}
