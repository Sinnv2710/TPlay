package app.tplay.player

import android.net.Uri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlayerControllerTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val controller = PlayerController(player)

    @Test
    fun seekLeftMovesBackTenSeconds() {
        every { player.currentPosition } returns 20_000L
        controller.seekLeft()
        verify { player.seekTo(10_000L) }
    }

    @Test
    fun seekLeftDoesNotGoBelowZero() {
        every { player.currentPosition } returns 2_000L
        controller.seekLeft()
        verify { player.seekTo(0L) }
    }

    @Test
    fun seekRightMovesForwardTenSeconds() {
        every { player.currentPosition } returns 20_000L
        controller.seekRight()
        verify { player.seekTo(30_000L) }
    }

    @Test
    fun speedIsClampedToSupportedRange() {
        controller.setSpeed(3.0f)
        verify { player.setPlaybackSpeed(2.0f) }
    }

    @Test
    fun audioSelectionWithoutTracksChangesNothing() {
        controller.selectAudio(0, 1)
        verify(exactly = 0) { player.trackSelectionParameters = any() }
    }

    @Test
    fun subtitleSelectionWithoutTracksChangesNothing() {
        controller.selectSubtitle(0, 0)
        verify(exactly = 0) { player.trackSelectionParameters = any() }
    }

    @Test
    fun subtitleSideloadIsSafeWithoutMediaItem() {
        every { player.currentMediaItem } returns null
        controller.sideloadSubtitle(mockk<Uri>(relaxed = true))
        verify(exactly = 0) { player.setMediaItem(any(), any<Long>()) }
    }
}
