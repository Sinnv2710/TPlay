package app.tplay

import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import app.tplay.player.isTabletop
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureTest {
    private fun fold(state: FoldingFeature.State, orientation: FoldingFeature.Orientation): FoldingFeature =
        mockk<FoldingFeature>().also {
            every { it.state } returns state
            every { it.orientation } returns orientation
        }

    @Test fun halfOpenedHorizontalIsTabletop() {
        val features = listOf<DisplayFeature>(
            fold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL),
        )
        assertTrue(isTabletop(features))
    }

    @Test fun halfOpenedVerticalIsBookNotTabletop() {
        val features = listOf<DisplayFeature>(
            fold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.VERTICAL),
        )
        assertFalse(isTabletop(features))
    }

    @Test fun flatIsNotTabletop() {
        val features = listOf<DisplayFeature>(
            fold(FoldingFeature.State.FLAT, FoldingFeature.Orientation.HORIZONTAL),
        )
        assertFalse(isTabletop(features))
    }

    @Test fun noFoldFeatureIsNotTabletop() {
        assertFalse(isTabletop(emptyList()))
    }
}
