package app.tplay

import app.tplay.data.rd.AddMagnetResponse
import app.tplay.data.rd.RdError
import app.tplay.data.rd.RdFile
import app.tplay.data.rd.RdResult
import app.tplay.data.rd.RealDebridClient
import app.tplay.data.rd.TorrentInfo
import app.tplay.data.rd.Unrestricted
import app.tplay.domain.Job
import app.tplay.domain.JobStatus
import app.tplay.domain.SubtitleHit
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.Title
import app.tplay.work.PipelineRunner
import app.tplay.work.PipelineStore
import app.tplay.work.rdFailureText
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamValidationTest {
    private class Store(var job: Job) : PipelineStore {
        val titles = mutableListOf<Title>()
        override suspend fun job(id: Long) = job
        override suspend fun update(job: Job) {
            this.job = job
        }
        override suspend fun addTitle(title: Title) {
            titles += title
        }
        override suspend fun removeTitle(id: Long) {
            titles.removeAll { it.id == id }
        }
    }

    private object NoSubs : SubtitleRepository {
        override suspend fun search(title: String, year: Int?, language: String, fileHash: String?) =
            emptyList<SubtitleHit>()
        override suspend fun download(hit: SubtitleHit, intoFolder: String) = ""
    }

    private fun job() = Job(
        1, null, "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Sintel.2010.720p", null,
        Files.createTempDirectory("m").toFile().path, JobStatus.QUEUED, 0, 0, "Queued", null, 0,
    )

    private fun rd(unlock: RdResult<Unrestricted>): RealDebridClient {
        val rd = mockk<RealDebridClient>(relaxed = true)
        coEvery { rd.addMagnet(any()) } returns RdResult.Ok(AddMagnetResponse("T1"))
        coEvery { rd.info("T1") } returns RdResult.Ok(
            TorrentInfo(
                "T1", "Sintel.2010.720p.mp4", "downloaded", 100.0,
                files = listOf(RdFile(1, "/Sintel.mp4", 1000, 1)), links = listOf("https://real-debrid.com/d/X"),
            ),
        )
        coEvery { rd.unrestrict(any()) } returns unlock
        return rd
    }

    @Test fun aBlockedFileFailsTheJobInsteadOfSayingReady() {
        val blocked = RdResult.Failure(RdError.Http(451, "{\"error\": \"infringing_file\", \"error_code\": 35}"))
        val store = Store(job())
        val ok = runBlocking { PipelineRunner(rd(blocked), NoSubs, store).run(1, stream = true) }
        assertEquals(false, ok)
        assertEquals(JobStatus.FAILED, store.job.status)
        assertTrue(store.job.error.orEmpty().contains("blocked this file as copyrighted"))
        assertTrue(store.titles.isEmpty())
    }

    @Test fun aWorkingLinkStillBecomesReadyToStream() {
        val fine = RdResult.Ok(Unrestricted(link = "https://real-debrid.com/d/X", download = "https://cdn/x.mp4"))
        val store = Store(job())
        val ok = runBlocking { PipelineRunner(rd(fine), NoSubs, store).run(1, stream = true) }
        assertEquals(true, ok)
        assertEquals(JobStatus.READY, store.job.status)
        assertEquals(1, store.titles.size)
    }

    @Test fun playbackFailureTextsAreReadable() {
        assertTrue(rdFailureText(RdError.Http(451, "{\"error\":\"infringing_file\"}")).contains("blocked"))
        assertTrue(rdFailureText(RdError.Http(401, "{\"error\":\"bad_token\"}")).contains("token"))
        assertTrue(rdFailureText(RdError.Api("boom")) == "boom")
    }
}
