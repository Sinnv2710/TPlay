package app.tplay.player

import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature

/** True when the device is half-folded with a horizontal hinge: video above, controls below. */
fun isTabletop(features: List<DisplayFeature>): Boolean {
    val fold = features.filterIsInstance<FoldingFeature>().firstOrNull() ?: return false
    return fold.state == FoldingFeature.State.HALF_OPENED &&
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL
}
