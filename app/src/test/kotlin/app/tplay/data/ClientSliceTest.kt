package app.tplay.data

import app.tplay.data.rd.RealDebridClient
import app.tplay.data.rd.RdError
import app.tplay.data.rd.RdResult
import app.tplay.data.subs.SubtitleFiles
import app.tplay.domain.ServiceIds
import app.tplay.domain.VaultRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.file.Files

class ClientSliceTest {
    private lateinit var server: MockWebServer
    private val vault = mockk<VaultRepository>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        coEvery { vault.get(ServiceIds.REAL_DEBRID) } returns "secret-token"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(): RealDebridClient {
        return RealDebridClient(vault, OkHttpClient(), server.url("/").toString())
    }

    @Test
    fun addMagnetPostsMagnet() = runTest {
        server.enqueue(MockResponse().setBody("{\"id\":\"t1\"}"))
        assertEquals("t1", (client().addMagnet("magnet:?xt=urn") as RdResult.Ok).value.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("magnet="))
    }

    @Test
    fun infoParsesTorrent() = runTest {
        server.enqueue(MockResponse().setBody("{\"id\":\"t1\",\"status\":\"downloaded\"}"))
        val result = client().info("t1")
        assertEquals("downloaded", (result as RdResult.Ok).value.status)
    }

    @Test
    fun selectFilesPostsSelection() = runTest {
        server.enqueue(MockResponse().setBody(""))
        client().selectFiles("t1", "1,2")
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("selectFiles/t1"))
        assertTrue(request.body.readUtf8().contains("files=1%2C2"))
    }

    @Test
    fun unrestrictParsesLink() = runTest {
        server.enqueue(MockResponse().setBody("{\"link\":\"https://cdn/file\"}"))
        assertEquals("https://cdn/file", (client().unrestrict("rd-link") as RdResult.Ok).value.link)
    }

    @Test
    fun authorizationHeaderIsSent() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        client().user()
        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun unauthorizedMapsToHttp401() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
        assertEquals(401, ((client().user() as RdResult.Failure).error as RdError.Http).code)
    }

    @Test
    fun forbiddenMapsToHttp403() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("no"))
        assertEquals(403, ((client().user() as RdResult.Failure).error as RdError.Http).code)
    }

    @Test
    fun rateLimitRetriesThenSucceeds() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(client().user() is RdResult.Ok)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimitEventuallyMapsTo429() = runTest {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0")) }
        val result = client().user()
        assertEquals(429, ((result as RdResult.Failure).error as RdError.Http).code)
    }

    @Test
    fun hashUsesPublishedSmallFileVector() {
        val file = Files.createTempFile("hash", ".bin").toFile()
        file.writeBytes(ByteArray(16) { it.toByte() })
        RandomAccessFile(file, "r").use {
            assertEquals("161412100e0c0a18", SubtitleFiles.openSubtitlesHash(it))
        }
        file.delete()
    }

    @Test
    fun hashIncludesBothEndsOfLargeFile() {
        val file = Files.createTempFile("hash", ".bin").toFile()
        file.writeBytes(ByteArray(70000) { 1 })
        assertTrue(SubtitleFiles.openSubtitlesHash(RandomAccessFile(file, "r")).length == 16)
        file.delete()
    }

    @Test
    fun decodeUtf8Bom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals("hello", SubtitleFiles.decode(bom + "hello".toByteArray()))
    }

    @Test
    fun decodeUtf16Bom() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        assertEquals("hello", SubtitleFiles.decode(bom + "hello".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun decodeLegacyCharset() {
        assertEquals("café", SubtitleFiles.decode(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())))
    }
}
