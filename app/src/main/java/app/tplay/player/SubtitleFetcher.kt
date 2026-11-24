package app.tplay.player

import app.tplay.domain.SubtitleRepository
import app.tplay.domain.Title
import app.tplay.domain.matches
import app.tplay.data.subs.SubtitleFiles
import app.tplay.work.isVideo
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Makes sure a title has a subtitle file in the wanted language before it plays, also when it is stream only. */
class SubtitleFetcher(private val subtitles: SubtitleRepository, private val filesDir: File) {
    /** Returns the folder to look for subtitles in. Silent on failure: no subtitle must never block playback. */
    suspend fun ensure(title: Title, language: String): File {
        val folder = title.folderPath?.let(::File)?.takeIf { it.isDirectory } ?: File(filesDir, "subs/${title.id}")
        withContext(Dispatchers.IO) { folder.mkdirs() }
        val have = folder.listFiles().orEmpty()
            .any { it.name.contains(".$language.") || it.name.startsWith("subtitle.picked") }
        if (have) return folder
        withTimeoutOrNull(TIMEOUT_MS) {
            for (lang in listOf(language, "en").distinct()) {
                if (folder.listFiles().orEmpty().any { it.name.contains(".$lang.") }) break
                val hash = withContext(Dispatchers.IO) {
                    folder.walkTopDown().filter { it.isFile && isVideo(it.name) }.maxByOrNull { it.length() }
                        ?.let { runCatching { RandomAccessFile(it, "r").use(SubtitleFiles::openSubtitlesHash) }.getOrNull() }
                }
                val hits = runCatching { subtitles.search(title.name, title.year, lang, hash) }.getOrDefault(emptyList())
                val best = hits.filter { it.matches(title.name, title.year) }.maxByOrNull { it.downloads } ?: continue
                if (runCatching { subtitles.download(best, folder.path) }.isSuccess) break
            }
        }
        return folder
    }

    private companion object { const val TIMEOUT_MS = 12_000L }
}
