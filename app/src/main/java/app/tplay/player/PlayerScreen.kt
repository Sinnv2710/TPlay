package app.tplay.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.tplay.ui.common.formatDuration
import kotlinx.coroutines.delay

private const val HIDE_AFTER_MS = 3_500L
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@UnstableApi
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    inPip: Boolean,
    tabletop: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.playWhenReady) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var fill by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val controller = remember(player) { PlayerController(player) }
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            if (controlsVisible && playing && System.currentTimeMillis() - lastTouch > HIDE_AFTER_MS) {
                controlsVisible = false
            }
            delay(500)
        }
    }

    fun touch() {
        lastTouch = System.currentTimeMillis()
        controlsVisible = true
    }

    val controlsContent: @Composable () -> Unit = {
        Controls(
            title = title,
            playing = playing,
            position = position,
            duration = duration,
            landscape = landscape,
            tracks = tracks,
            controller = controller,
            player = player,
            fill = fill,
            onFill = { fill = !fill; touch() },
            onBack = onBack,
            onPip = onPip,
            onPlayPause = {
                if (player.isPlaying) player.pause() else player.play()
                touch()
            },
            onSeekStart = { dragging = true; touch() },
            onSeek = { position = it },
            onSeekEnd = { player.seekTo(position); dragging = false; touch() },
            onInteract = ::touch,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (controlsVisible) controlsVisible = false else touch() },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) controller.seekLeft() else controller.seekRight()
                        touch()
                    },
                )
            },
    ) {
        if (tabletop) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { VideoSurface(player, fill) }
                Box(Modifier.weight(1f).fillMaxWidth()) { controlsContent() }
            }
        } else {
            VideoSurface(player, fill)
            AnimatedVisibility(
                visible = controlsVisible && !inPip,
                enter = fadeIn(),
                exit = fadeOut(),
            ) { controlsContent() }
        }
    }
}

@UnstableApi
@Composable
private fun VideoSurface(player: ExoPlayer, fill: Boolean) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = false
            }
        },
        update = {
            it.resizeMode = if (fill) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@UnstableApi
@Composable
private fun Controls(
    title: String,
    playing: Boolean,
    position: Long,
    duration: Long,
    landscape: Boolean,
    tracks: Tracks,
    controller: PlayerController,
    player: ExoPlayer,
    fill: Boolean,
    onFill: () -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    onInteract: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent, Color(0xCC000000)))),
    ) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(title, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = onFill) {
                Text(if (fill) "Fit" else "Fill", color = Color.White)
            }
            IconButton(onClick = onPip) {
                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
            }
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(if (landscape) 56.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.seekLeft(); onInteract() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
            IconButton(onClick = { controller.seekRight(); onInteract() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp)) {
            Slider(
                value = position.toFloat(),
                onValueChange = { onSeekStart(); onSeek(it.toLong()) },
                onValueChangeFinished = onSeekEnd,
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatDuration(position)} / ${formatDuration(duration)}", color = Color.White)
                Box(Modifier.weight(1f))
                SpeedMenu(controller, player)
                TrackMenu(Icons.Filled.Audiotrack, "Audio", C.TRACK_TYPE_AUDIO, tracks, controller, onInteract)
                TrackMenu(Icons.Filled.Subtitles, "Subtitles", C.TRACK_TYPE_TEXT, tracks, controller, onInteract)
            }
        }
    }
}

@UnstableApi
@Composable
private fun SpeedMenu(controller: PlayerController, player: ExoPlayer) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Speed, contentDescription = "Speed", tint = Color.White)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${speed}x" + if (player.playbackParameters.speed == speed) "  ✓" else "") },
                    onClick = { controller.setSpeed(speed); open = false },
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun TrackMenu(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    type: Int,
    tracks: Tracks,
    controller: PlayerController,
    onInteract: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val groups = tracks.groups.filter { it.type == type }
    Box {
        IconButton(onClick = { open = true; onInteract() }) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (groups.isEmpty()) {
                DropdownMenuItem(text = { Text("No $label tracks") }, onClick = { open = false })
            }
            groups.forEachIndexed { index, group ->
                val format = group.getTrackFormat(0)
                val name = format.label ?: format.language?.takeIf { it != "und" } ?: "$label ${index + 1}"
                DropdownMenuItem(
                    text = { Text(name + if (group.isSelected) "  ✓" else "") },
                    onClick = {
                        if (type == C.TRACK_TYPE_AUDIO) {
                            controller.selectAudio(index, 0)
                        } else {
                            controller.selectSubtitle(index, 0)
                        }
                        open = false
                    },
                )
            }
        }
    }
}
