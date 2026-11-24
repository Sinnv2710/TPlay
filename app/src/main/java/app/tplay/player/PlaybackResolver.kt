package app.tplay.player

import app.tplay.data.rd.RdResult
import app.tplay.data.rd.RealDebridClient
import app.tplay.domain.Title
import app.tplay.work.isVideo
import app.tplay.work.rdFailureText
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Finds something playable for a library title: the local file, else a fresh Real-Debrid stream link. */
@Singleton
class PlaybackResolver @Inject constructor(private val rd: RealDebridClient) {
    sealed class Result {
        data class Ready(val uri: String, val local: Boolean) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun resolve(title: Title): Result {
        localVideo(title)?.let { return Result.Ready(android.net.Uri.fromFile(it).toString(), true) }
        val link = title.rdLink ?: return Result.Failed("Files were cleaned and no Real-Debrid link is stored")
        return when (val result = rd.unrestrict(link)) {
            is RdResult.Ok -> {
                val url = result.value.download ?: result.value.link
                Result.Ready(url, false)
            }
            is RdResult.Failure -> Result.Failed(rdFailureText(result.error))
        }
    }

    private fun localVideo(title: Title): File? {
        val folder = title.folderPath?.let(::File)?.takeIf { it.isDirectory } ?: return null
        return folder.walkTopDown().filter { it.isFile && isVideo(it.name) }.maxByOrNull { it.length() }
    }
}
