package app.tplay.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.WindowInfoTracker
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.tplay.domain.ProgressRepository
import app.tplay.ui.theme.TPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Built-in player host. Declared configChanges keep ExoPlayer alive across fold, unfold and rotation. */
@UnstableApi
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    @Inject lateinit var progress: ProgressRepository

    private var player: ExoPlayer? = null
    private var titleId = 0L
    private val inPip = mutableStateOf(false)
    private val tabletop = mutableStateOf(false)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val uri = intent.getStringExtra(PlayerLauncher.EXTRA_URI)
        if (uri == null) {
            finish()
            return
        }
        titleId = intent.getLongExtra(PlayerLauncher.EXTRA_TITLE_ID, 0L)
        val title = intent.getStringExtra(PlayerLauncher.EXTRA_TITLE).orEmpty()
        val start = intent.getLongExtra(PlayerLauncher.EXTRA_POSITION, 0L)
        val exo = ExoPlayer.Builder(this).build()
        exo.setMediaItem(buildItem(uri, intent.getStringExtra(PlayerLauncher.EXTRA_SUBS_DIR)), start)
        exo.prepare()
        exo.playWhenReady = true
        player = exo
        lifecycleScope.launch {
            while (true) {
                delay(SAVE_EVERY_MS)
                savePosition()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@PlayerActivity)
                    .windowLayoutInfo(this@PlayerActivity)
                    .collect { info -> tabletop.value = isTabletop(info.displayFeatures) }
            }
        }
        setContent {
            TPlayTheme {
                PlayerScreen(
                    player = exo,
                    title = title,
                    inPip = inPip.value,
                    tabletop = tabletop.value,
                    onBack = { finish() },
                    onPip = { enterPip() },
                )
            }
        }
    }

    private fun buildItem(uri: String, subsDir: String?): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        val file = Uri.parse(uri).takeIf { it.scheme == "file" }?.path?.let(::File)
        val folder = file?.parentFile ?: subsDir?.let(::File)
        if (folder != null) {
            val subtitles = folder.listFiles().orEmpty()
                .filter { it.extension.lowercase() in SUBTITLE_MIME }
                .sortedBy { if (file != null && it.nameWithoutExtension == file.nameWithoutExtension) 0 else 1 }
                .mapIndexed { index, sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                        .setMimeType(SUBTITLE_MIME.getValue(sub.extension.lowercase()))
                        .setLanguage("und")
                        .setLabel(sub.name)
                        .setSelectionFlags(if (index == 0) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }
            builder.setSubtitleConfigurations(subtitles)
        }
        return builder.build()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        enterPictureInPictureMode(params)
    }

    override fun onUserLeaveHint() {
        if (player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(active: Boolean, config: Configuration) {
        super.onPictureInPictureModeChanged(active, config)
        inPip.value = active
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (active) controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onStop() {
        savePosition()
        if (!isInPictureInPictureMode) player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun savePosition() {
        val current = player ?: return
        if (titleId == 0L) return
        val duration = current.duration.coerceAtLeast(0)
        val position = current.currentPosition
        if (duration <= 0) return
        lifecycleScope.launch { progress.save(titleId, position, duration) }
    }

    private companion object {
        const val SAVE_EVERY_MS = 10_000L
        val SUBTITLE_MIME = mapOf(
            "srt" to MimeTypes.APPLICATION_SUBRIP,
            "vtt" to MimeTypes.TEXT_VTT,
            "ass" to MimeTypes.TEXT_SSA,
            "ssa" to MimeTypes.TEXT_SSA,
        )
    }
}
