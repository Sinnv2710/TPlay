package app.tplay.player

import android.net.Uri

/** Request accepted by player. rdLink is already unrestricted by caller. */
data class PlayerRequest(
    val titleId: Long,
    val uri: Uri? = null,
    val rdLink: String? = null,
    val title: String = ""
) {
    fun source(): Uri = uri ?: Uri.parse(rdLink ?: error("A stream URI or rdLink is required"))
}

fun interface ProgressSink {
    suspend fun save(titleId: Long, positionMs: Long, durationMs: Long)
}

fun interface StreamResolver {
    suspend fun resolve(titleId: Long): String
}
