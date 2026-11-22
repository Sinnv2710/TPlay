package app.tplay.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** Testable player commands shared by player UI and playback engine. */
@UnstableApi
class PlayerController(
    private val player: ExoPlayer,
    private val seekAmountMs: Long = 10_000L,
) {
    fun seekLeft() {
        player.seekTo((player.currentPosition - seekAmountMs).coerceAtLeast(0L))
    }

    fun seekRight() {
        player.seekTo(player.currentPosition + seekAmountMs)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun selectAudio(trackGroupIndex: Int, trackIndex: Int) {
        selectTrack(C.TRACK_TYPE_AUDIO, trackGroupIndex, trackIndex)
    }

    fun selectSubtitle(trackGroupIndex: Int, trackIndex: Int) {
        selectTrack(C.TRACK_TYPE_TEXT, trackGroupIndex, trackIndex)
    }

    fun sideloadSubtitle(uri: Uri, language: String = "und") {
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val current = player.currentMediaItem ?: return
        val updated = current.buildUpon()
            .setSubtitleConfigurations(
                current.localConfiguration?.subtitleConfigurations.orEmpty() + subtitle,
            )
            .build()
        player.setMediaItem(updated, player.currentPosition)
        player.prepare()
    }

    private fun selectTrack(type: Int, groupIndex: Int, trackIndex: Int) {
        val groups = player.currentTracks.groups.filter { it.type == type }
        val group = groups.getOrNull(groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }
}
