package app.tplay.work

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadUnpackerTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = createTempDir()
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun fullDownloadWritesBody() {
        server.enqueue(MockResponse().setBody("hello"))
        val target = File(directory, "file")
        FileDownloader(OkHttpClient()).download(server.url("/").toString(), target)
        assertEquals("hello", target.readText())
    }

    @Test
    fun fullDownloadReportsProgress() {
        server.enqueue(MockResponse().setBody("hello"))
        val progress = mutableListOf<Long>()
        FileDownloader().download(server.url("/").toString(), File(directory, "file")) { bytes, _ ->
            progress += bytes
        }
        assertEquals(5L, progress.last())
    }

    @Test
    fun resumeSendsRangeAndAppends() {
        val target = File(directory, "file").apply { writeText("hel") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-4/5").setBody("lo"))
        FileDownloader().download(server.url("/").toString(), target)
        assertEquals("hel", server.takeRequest().getHeader("Range")?.let { "hel" })
        assertEquals("hello", target.readText())
    }

    @Test
    fun resumeRangeStartsAtExistingLength() {
        val target = File(directory, "file").apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setBody("d"))
        FileDownloader().download(server.url("/").toString(), target)
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun notModifiedOn416KeepsFile() {
        val target = File(directory, "file").apply { writeText("done") }
        server.enqueue(MockResponse().setResponseCode(416))
        FileDownloader().download(server.url("/").toString(), target)
        assertEquals("done", target.readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun httpErrorIsRejected() {
        server.enqueue(MockResponse().setResponseCode(500))
        FileDownloader().download(server.url("/").toString(), File(directory, "file"))
    }

    @Test
    fun zipExtractsNestedFile() {
        val archive = zip("folder/file.txt" to "value")
        Unpacker().unpack(archive, File(directory, "out"))
        assertEquals("value", File(directory, "out/folder/file.txt").readText())
    }

    @Test
    fun zipCreatesDirectories() {
        val archive = zip("a/b/c.txt" to "x")
        val output = File(directory, "out")
        Unpacker().unpack(archive, output)
        assertTrue(File(output, "a/b").isDirectory)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zipSlipIsRejected() {
        Unpacker().unpack(zip("../escape.txt" to "bad"), File(directory, "out"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun absoluteZipPathIsRejected() {
        Unpacker().unpack(zip("/escape.txt" to "bad"), File(directory, "out"))
    }

    @Test(expected = IllegalStateException::class)
    fun unknownArchiveIsRejected() {
        val file = File(directory, "file.tar").apply { writeText("x") }
        Unpacker().unpack(file, File(directory, "out"))
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File(directory, "archive.zip")
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, value) ->
                output.putNextEntry(ZipEntry(name))
                output.write(value.toByteArray())
                output.closeEntry()
            }
        }
        return file
    }
}
