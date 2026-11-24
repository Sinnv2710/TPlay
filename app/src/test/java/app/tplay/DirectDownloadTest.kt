package app.tplay

import app.tplay.data.rd.RealDebridClient
import app.tplay.domain.Job
import app.tplay.domain.JobStatus
import app.tplay.domain.SubtitleHit
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.Title
import app.tplay.work.PipelineRunner
import app.tplay.work.PipelineStore
import app.tplay.work.TorrentEngine
import app.tplay.work.TorrentException
import app.tplay.work.TorrentProgress
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDownloadTest {
    private class MemoryStore(job: Job) : PipelineStore {
        var current = job
        val titles = mutableListOf<Title>()
        override suspend fun job(id: Long) = current
        override suspend fun update(job: Job) {
            current = job
        }
        override suspend fun addTitle(title: Title) {
            titles += title
        }
    }

    private object NoSubtitles : SubtitleRepository {
        override suspend fun search(title: String, year: Int?, language: String, fileHash: String?) =
            emptyList<SubtitleHit>()
        override suspend fun download(hit: SubtitleHit, intoFolder: String) = ""
    }

    private fun job(parent: File) = Job(
        id = 1, titleId = null,
        magnet = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Sintel.2010.1080p.BluRay",
        subtitleUrl = null, parentFolder = parent.path, status = JobStatus.FAILED, progress = 0,
        speedBytesPerSec = 0, step = "Failed", error = "Real-Debrid error 451", createdAt = 0,
    )

    private fun runner(store: PipelineStore, engine: TorrentEngine) =
        PipelineRunner(mockk<RealDebridClient>(relaxed = true), NoSubtitles, store, engine = engine)

    @Test fun downloadsIntoTheDirectFolderAndAddsATitle() {
        val parent = Files.createTempDirectory("movies").toFile()
        val store = MemoryStore(job(parent))
        val engine = object : TorrentEngine {
            override suspend fun download(magnet: String, dir: File, onProgress: (TorrentProgress) -> Unit) {
                onProgress(TorrentProgress(40, 1_000_000, 8, "Downloading"))
                File(dir, "Sintel.2010.1080p.mp4").apply { parentFile.mkdirs() }.writeBytes(ByteArray(2048))
            }
        }
        val ok = runBlocking { runner(store, engine).runDirect(1) }
        assertTrue(ok)
        assertEquals(JobStatus.READY, store.current.status)
        val title = store.titles.single()
        assertEquals("Sintel", title.name)
        assertEquals(2010, title.year)
        assertEquals(File(File(parent, "Direct"), "Sintel (2010)").path, title.folderPath)
        assertEquals(2048L, title.sizeBytes)
        assertFalse(title.filesDeleted)
        assertNull(title.rdLink)
        assertNull(title.rdTorrentId)
    }

    @Test fun failureShowsTheReasonAndRemovesPartialFiles() {
        val parent = Files.createTempDirectory("movies").toFile()
        val store = MemoryStore(job(parent))
        val engine = object : TorrentEngine {
            override suspend fun download(magnet: String, dir: File, onProgress: (TorrentProgress) -> Unit) {
                File(dir, "part.bin").apply { parentFile.mkdirs() }.writeBytes(ByteArray(10))
                throw TorrentException("No one is sharing this torrent right now (no peers found).")
            }
        }
        val ok = runBlocking { runner(store, engine).runDirect(1) }
        assertFalse(ok)
        assertEquals(JobStatus.FAILED, store.current.status)
        assertTrue(store.current.error.orEmpty().contains("no peers found"))
        assertTrue(store.titles.isEmpty())
        assertFalse(File(File(parent, "Direct"), "Sintel (2010)").exists())
    }

    @Test fun cancellingRemovesWhatWasDownloadedSoFar() {
        val parent = Files.createTempDirectory("movies").toFile()
        val store = MemoryStore(job(parent))
        val engine = object : TorrentEngine {
            override suspend fun download(magnet: String, dir: File, onProgress: (TorrentProgress) -> Unit) {
                File(dir, "half.mkv").apply { parentFile.mkdirs() }.writeBytes(ByteArray(10))
                throw kotlinx.coroutines.CancellationException("stopped")
            }
        }
        val runner = PipelineRunner(
            mockk<RealDebridClient>(relaxed = true), NoSubtitles, store, engine = engine, cleanupGraceMs = 10,
        )
        val outcome = runCatching { runBlocking { runner.runDirect(1) } }
        assertTrue(outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertFalse(File(File(parent, "Direct"), "Sintel (2010)").exists())
        assertTrue("no private folder is left", File(parent, "Direct").listFiles().orEmpty().isEmpty())
        assertTrue(store.titles.isEmpty())
    }

    @Test fun twoDownloadsOfTheSameMovieNeverShareAFolder() {
        val parent = Files.createTempDirectory("movies").toFile()
        val engine = object : TorrentEngine {
            override suspend fun download(magnet: String, dir: File, onProgress: (TorrentProgress) -> Unit) {
                File(dir, "Sintel.2010.mp4").apply { parentFile.mkdirs() }.writeBytes(ByteArray(100))
            }
        }
        val first = MemoryStore(job(parent))
        val second = MemoryStore(job(parent))
        runBlocking {
            runner(first, engine).runDirect(1)
            runner(second, engine).runDirect(1)
        }
        val a = first.titles.single().folderPath
        val b = second.titles.single().folderPath
        assertTrue(a != b)
        assertTrue(File(a!!).isDirectory && File(b!!).isDirectory)
        assertTrue(b!!.endsWith("Sintel (2010) (2)"))
    }

    @Test fun withoutAnEngineTheJobFailsWithAClearMessage() {
        val parent = Files.createTempDirectory("movies").toFile()
        val store = MemoryStore(job(parent))
        val ok = runBlocking { PipelineRunner(mockk(relaxed = true), NoSubtitles, store).runDirect(1) }
        assertFalse(ok)
        assertTrue(store.current.error.orEmpty().contains("not available"))
    }
}
