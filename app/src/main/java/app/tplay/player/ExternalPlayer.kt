package app.tplay.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Opens a video in VLC or MX Player. Local files go through a FileProvider so the other app can read them. */
object ExternalPlayer {
    private const val VLC = "org.videolan.vlc"
    private val MX = listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")

    fun launch(
        context: Context,
        uri: Uri,
        positionMs: Long,
        title: String,
        preferred: String = "vlc",
        subtitles: Uri? = null,
    ): Boolean {
        val shared = shareable(context, uri)
        val candidates = if (preferred == "mx") MX else listOf(VLC)
        for (pkg in candidates) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(shared, "video/*")
                .setPackage(pkg)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra("position", positionMs)
                .putExtra("title", title)
            if (pkg == VLC) intent.putExtra("subtitles_location", subtitles?.toString().orEmpty())
            if (pkg != VLC) intent.putExtra("return_result", true)
            if (intent.resolveActivity(context.packageManager) == null) continue
            context.startActivity(intent)
            return true
        }
        return false
    }

    private fun shareable(context: Context, uri: Uri): Uri {
        if (uri.scheme != "file") return uri
        val path = uri.path ?: return uri
        return FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    }
}
