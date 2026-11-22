package app.tplay.player

import android.content.Context
import android.content.Intent
import android.net.Uri

object PlayerLauncher {
    const val EXTRA_URI = "uri"
    const val EXTRA_TITLE_ID = "titleId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_POSITION = "position"
    const val EXTRA_SUBS_DIR = "subsDir"

    /** Opens the built-in player, or an external one (vlc, mx) when [preferred] says so. */
    fun launch(
        context: Context,
        titleId: Long,
        title: String,
        uri: String,
        positionMs: Long,
        preferred: String,
        subsDir: String? = null,
    ) {
        if (preferred != "builtin" && ExternalPlayer.launch(context, Uri.parse(uri), positionMs, title)) return
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_URI, uri)
            .putExtra(EXTRA_TITLE_ID, titleId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_POSITION, positionMs)
            .putExtra(EXTRA_SUBS_DIR, subsDir)
        context.startActivity(intent)
    }
}
