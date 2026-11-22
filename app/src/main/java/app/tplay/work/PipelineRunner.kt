package app.tplay.work

import android.util.Log
import app.tplay.data.rd.RdResult
import app.tplay.data.rd.RealDebridClient
import app.tplay.data.rd.RdError
import app.tplay.data.rd.TorrentInfo
import app.tplay.data.tmdb.TmdbClient
import app.tplay.data.subs.SubtitleFiles
import app.tplay.domain.Job
import app.tplay.domain.JobStatus
import app.tplay.domain.SubtitleRepository
import app.tplay.domain.Title
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Storage the pipeline needs; the data layer implements it. */
interface PipelineStore {
    suspend fun job(id: Long): Job?
    suspend fun update(job: Job)
    suspend fun addTitle(title: Title)
}

class PipelineRunner(
    private val rd: RealDebridClient,
    private val subtitles: SubtitleRepository,
    private val store: PipelineStore,
    private val downloader: FileDownloader = FileDownloader(),
    private val unpacker: Unpacker = Unpacker(),
    private val tmdb: TmdbClient? = null,
    private val http: OkHttpClient = OkHttpClient(),
    private val pollDelayMs: Long = 3_000L,
) {
    /** Runs the whole job. Returns true when the title is ready to play. */
    suspend fun run(jobId: Long, stream: Boolean = false): Boolean {
        var job = store.job(jobId) ?: return false
        try {
            job = step(job, JobStatus.SENT, 5, "Sent to Real-Debrid")
            val added = rd.addMagnet(job.magnet)
            val torrentId = (added as? RdResult.Ok)?.value?.id ?: return fail(job, added.describe())
            val info = waitUntilDownloaded(job, torrentId) ?: return false
            job = store.job(jobId) ?: return false
            return finish(job, torrentId, info, stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("TPlayPipeline", "job $jobId failed", e)
            return fail(job, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun waitUntilDownloaded(start: Job, torrentId: String): TorrentInfo? {
        var job = start
        var selected = false
        while (true) {
            val result = rd.info(torrentId)
            val info = (result as? RdResult.Ok)?.value
            if (info == null) {
                fail(job, result.describe())
                return null
            }
            when (info.status) {
                "waiting_files_selection" -> if (!selected) selected = selectFiles(job, torrentId, info)
                "downloaded" -> return info
                "error", "virus", "dead", "magnet_error" -> {
                    fail(job, "Real-Debrid reported: ${info.status}")
                    return null
                }
                else -> {
                    val percent = 10 + (info.progress * 0.2).toInt()
                    job = step(job, JobStatus.CACHING, percent, "Cached on Real-Debrid: ${info.progress.toInt()}%")
                }
            }
            delay(pollDelayMs)
        }
    }

    private suspend fun selectFiles(job: Job, torrentId: String, info: TorrentInfo): Boolean {
        val chosen = chooseFiles(info.files)
        if (chosen.isEmpty()) {
            fail(job, "No files in torrent")
            return false
        }
        val result = rd.selectFiles(torrentId, chosen.joinToString(",") { it.id.toString() })
        return result is RdResult.Ok
    }

    private suspend fun finish(start: Job, torrentId: String, info: TorrentInfo, stream: Boolean): Boolean {
        val parsed = parseTitleName(info.filename ?: "Movie")
        val folder = File(start.parentFolder, folderNameFor(parsed))
        folder.mkdirs()
        if (stream && canStream(info)) return finishStreaming(start, torrentId, info, parsed, folder)
        var job = step(start, JobStatus.DOWNLOADING, 30, "Downloading")
        for ((index, link) in info.links.withIndex()) {
            val unrestricted = (rd.unrestrict(link) as? RdResult.Ok)?.value
                ?: return fail(job, "Real-Debrid could not unlock the link")
            val name = unrestricted.filename ?: "part$index"
            val url = unrestricted.download ?: unrestricted.link
            download(job, url, File(folder, name), index, info.links.size)
        }
        job = step(job, JobStatus.UNPACKING, 90, "Unpack archives")
        withContext(Dispatchers.IO) { unpackAll(folder) }
        job = step(job, JobStatus.SUBTITLES, 94, "Add subtitles")
        withContext(Dispatchers.IO) { addSubtitles(job, folder, parsed) }
        val title = Title(
            id = System.currentTimeMillis(),
            name = parsed.title,
            year = parsed.year,
            posterUrl = poster(parsed),
            folderPath = folder.path,
            sizeBytes = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            createdAt = System.currentTimeMillis(),
            rdTorrentId = torrentId,
            rdLink = info.links.firstOrNull(),
            filesDeleted = false,
        )
        store.addTitle(title)
        store.update(job.copy(status = JobStatus.READY, progress = 100, step = "Ready to play", titleId = title.id))
        return true
    }

    private suspend fun poster(parsed: ParsedName): String? =
        runCatching { tmdb?.lookup(parsed.title, parsed.year)?.posterUrl }.getOrNull()

    /** A plain video file can be played straight from Real-Debrid. Archives must be downloaded and unpacked. */
    private fun canStream(info: TorrentInfo): Boolean {
        val selected = info.files.filter { it.selected == 1 }
        return selected.isNotEmpty() && selected.all { isVideo(it.path) } && info.links.isNotEmpty()
    }

    private suspend fun finishStreaming(
        start: Job,
        torrentId: String,
        info: TorrentInfo,
        parsed: ParsedName,
        folder: File,
    ): Boolean {
        var job = step(start, JobStatus.SUBTITLES, 80, "Add subtitles")
        withContext(Dispatchers.IO) { addStreamSubtitles(job, folder, parsed) }
        val title = Title(
            id = System.currentTimeMillis(),
            name = parsed.title,
            year = parsed.year,
            posterUrl = poster(parsed),
            folderPath = folder.path,
            sizeBytes = 0L,
            createdAt = System.currentTimeMillis(),
            rdTorrentId = torrentId,
            rdLink = info.links.first(),
            filesDeleted = true,
        )
        store.addTitle(title)
        job = job.copy(status = JobStatus.READY, progress = 100, step = "Ready to stream", titleId = title.id)
        store.update(job)
        return true
    }

    private suspend fun addStreamSubtitles(job: Job, folder: File, parsed: ParsedName) {
        val explicit = job.subtitleUrl
        if (explicit != null) {
            saveSubtitleLink(explicit, File(folder, "${parsed.title}.mp4"))
            return
        }
        val hits = runCatching { subtitles.search(parsed.title, parsed.year, "en", null) }
            .onFailure { Log.w(TAG, "subtitle search failed", it) }
            .getOrDefault(emptyList())
        val best = hits.maxByOrNull { it.downloads }
        if (best == null) {
            Log.i(TAG, "no subtitles found for ${parsed.title}")
            return
        }
        runCatching { subtitles.download(best, folder.path) }
            .onSuccess { Log.i(TAG, "subtitle saved from ${best.source}") }
            .onFailure { Log.w(TAG, "subtitle download failed (${best.source})", it) }
    }

    private suspend fun download(job: Job, url: String, target: File, index: Int, count: Int) {
        coroutineScope {
            var done = 0L
            var total = 0L
            val ticker = launch {
                var last = 0L
                while (isActive) {
                    delay(1_000)
                    val share = if (total > 0) done.toDouble() / total else 0.0
                    val percent = 30 + (((index + share) / count) * 60).toInt()
                    val speed = done - last
                    last = done
                    store.update(job.copy(status = JobStatus.DOWNLOADING, progress = percent, speedBytesPerSec = speed,
                        step = "Downloading ${target.name}"))
                }
            }
            try {
                withContext(Dispatchers.IO) {
                    downloader.download(url, target) { bytes, all ->
                        done = bytes
                        total = all ?: 0L
                    }
                }
            } finally {
                ticker.cancel()
            }
        }
    }

    private fun unpackAll(folder: File) {
        val archives = folder.listFiles().orEmpty().filter { isArchivePart(it.name) }
        val starts = archives.filter { isFirstArchivePart(it.name) }
        for (archive in starts) unpacker.unpack(archive, folder)
        archives.forEach { it.delete() }
    }

    private fun isFirstArchivePart(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".zip")) return true
        val part = Regex("\\.part0*1\\.rar$").containsMatchIn(lower)
        val single = lower.endsWith(".rar") && !Regex("\\.part\\d+\\.rar$").containsMatchIn(lower)
        return part || single
    }

    private suspend fun addSubtitles(job: Job, folder: File, parsed: ParsedName) {
        val video = folder.walkTopDown().filter { it.isFile && isVideo(it.name) }.maxByOrNull { it.length() }
            ?: return
        val explicit = job.subtitleUrl
        if (explicit != null) {
            saveSubtitleLink(explicit, video)
            return
        }
        val hash = runCatching { RandomAccessFile(video, "r").use(SubtitleFiles::openSubtitlesHash) }.getOrNull()
        val hits = runCatching { subtitles.search(parsed.title, parsed.year, "en", hash) }.getOrDefault(emptyList())
        val best = hits.maxByOrNull { it.downloads } ?: return
        runCatching { subtitles.download(best, video.parent ?: folder.path) }
    }

    private fun saveSubtitleLink(url: String, video: File) {
        val request = Request.Builder().url(url).build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return
            response.body?.bytes() ?: return
        }
        val name = url.substringBefore('?').substringAfterLast('/')
        val extension = name.substringAfterLast('.', "srt").lowercase()
        val base = video.nameWithoutExtension
        when (extension) {
            "zip", "rar" -> saveSubtitleArchive(bytes, extension, video.parentFile ?: return, base)
            else -> File(video.parentFile, "$base.$extension").writeBytes(bytes)
        }
    }

    private fun saveSubtitleArchive(bytes: ByteArray, extension: String, folder: File, base: String) {
        val temp = File.createTempFile("subs", ".$extension", folder)
        val out = File(folder, ".subs-${System.nanoTime()}")
        try {
            temp.writeBytes(bytes)
            unpacker.unpack(temp, out)
            val found = out.walkTopDown().firstOrNull { it.isFile && it.extension.lowercase() in SUB_EXT }
            found?.copyTo(File(folder, "$base.${found.extension.lowercase()}"), overwrite = true)
        } finally {
            temp.delete()
            out.deleteRecursively()
        }
    }

    private suspend fun step(job: Job, status: JobStatus, progress: Int, text: String): Job {
        val next = job.copy(status = status, progress = progress, step = text, speedBytesPerSec = 0)
        store.update(next)
        return next
    }

    private suspend fun fail(job: Job, message: String): Boolean {
        store.update(job.copy(status = JobStatus.FAILED, error = message, step = "Failed", speedBytesPerSec = 0))
        return false
    }

    private fun RdResult<*>.describe(): String = when (this) {
        is RdResult.Ok -> "unexpected reply"
        is RdResult.Failure -> when (val e = error) {
            is RdError.Http -> "Real-Debrid error ${e.code}: ${e.message}"
            is RdError.Network -> "Network problem: ${e.cause.message}"
            is RdError.Api -> e.message
        }
    }

    private companion object {
        const val TAG = "TPlayPipeline"
        val SUB_EXT = setOf("srt", "ass", "vtt", "sub")
    }
}
