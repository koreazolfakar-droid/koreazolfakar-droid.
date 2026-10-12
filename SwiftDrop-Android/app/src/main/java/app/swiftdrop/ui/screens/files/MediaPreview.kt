package app.swiftdrop.ui.screens.files

import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.swiftdrop.LocalMedia
import app.swiftdrop.MediaKind
import app.swiftdrop.R
import app.swiftdrop.music.BackgroundMusicControls
import app.swiftdrop.music.MusicPlayerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// SwiftDrop @feature Codex 12/10/2026 Full-screen local preview with gesture zoom and lifecycle-safe Media3.
@Composable
fun MediaPreview(item: LocalMedia, kind: MediaKind, close: () -> Unit, select: () -> Unit,
    previous: (() -> Unit)? = null, next: (() -> Unit)? = null) {
    if(kind==MediaKind.Music) {
        MusicPlayerDialog(item,close,select)
        return
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false,decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101216)).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(close) { Text(stringResource(R.string.media_close), color = Color.White) }
                Text(item.name, Modifier.weight(1f), maxLines = 1, color = Color.White,
                    style = MaterialTheme.typography.titleSmall,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                TextButton(select) { Text(stringResource(R.string.media_select), color = Color.White) }
            }
            val context=LocalContext.current
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                IconButton(onClick={
                    val share=android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type=if(kind==MediaKind.Photos) "image/*" else "video/*"
                        putExtra(android.content.Intent.EXTRA_STREAM,item.uri)
                        clipData=android.content.ClipData.newUri(context.contentResolver,item.name,item.uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(share,null)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { android.widget.Toast.makeText(context,R.string.doc_no_viewer,android.widget.Toast.LENGTH_SHORT).show() }
                }) { app.swiftdrop.ui.components.AppIcon(R.drawable.ic_share,
                    description=stringResource(R.string.share_files),tint=Color.White) }
            }
            when (kind) {
                MediaKind.Photos -> Crossfade(targetState=item,label="photo transition",
                    animationSpec=tween(170),modifier=Modifier.weight(1f)) { shown ->
                    ZoomPhoto(shown,Modifier.fillMaxSize())
                }
                MediaKind.Videos -> LocalPlayer(item, video = true, Modifier.weight(1f))
                MediaKind.Music -> BackgroundMusicControls(item,Modifier.weight(1f))
            }
            if(kind==MediaKind.Photos && (previous!=null || next!=null)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={previous?.invoke()},enabled=previous!=null) {
                        Text(stringResource(R.string.media_previous),color=Color.White)
                    }
                    TextButton(onClick={next?.invoke()},enabled=next!=null) {
                        Text(stringResource(R.string.media_next),color=Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomPhoto(item: LocalMedia, modifier: Modifier = Modifier) {
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var pan by remember(item.uri) { mutableStateOf(Offset.Zero) }
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().onSizeChanged { width = it.width; height = it.height }
        .pointerInput(item.uri) {
            detectTransformGestures { _, drag, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = (width * (scale - 1f) / 2f).coerceAtLeast(0f)
                val maxY = (height * (scale - 1f) / 2f).coerceAtLeast(0f)
                pan = if (scale == 1f) Offset.Zero else Offset(
                    (pan.x + drag.x).coerceIn(-maxX, maxX),
                    (pan.y + drag.y).coerceIn(-maxY, maxY))
            }
        }, contentAlignment = Alignment.Center) {
        MediaArt(item.uri, MediaKind.Photos,
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale,
                translationX = pan.x, translationY = pan.y), fullSize = true)
    }
}

@Composable
private fun LocalPlayer(item: LocalMedia, video: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumePosition by rememberSaveable(item.uri) { mutableLongStateOf(0L) }
    var resumePlaying by rememberSaveable(item.uri) { mutableStateOf(true) }
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT,true)
            setMediaItem(MediaItem.fromUri(item.uri),resumePosition)
            prepare()
            playWhenReady = resumePlaying
        }
    }
    var playbackFailed by remember(player) { mutableStateOf(player.playerError != null) }
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_STOP) {
                resumePosition=player.currentPosition.coerceAtLeast(0L)
                resumePlaying=player.playWhenReady
                player.pause()
            } else if(event==Lifecycle.Event.ON_START && resumePlaying) player.play()
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackFailed = true }
            override fun onEvents(player:Player,events:Player.Events) {
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                    resumePlaying=player.playWhenReady
            }
        }
        owner.lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player) {
        while(isActive) {
            resumePosition=player.currentPosition.coerceAtLeast(0L)
            delay(300L)
        }
    }
    if (video) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize(), update = { it.player = player })
            if (playbackFailed) Text(stringResource(R.string.media_playback_failed), color = Color.White)
        }
    } else {
        var playing by remember { mutableStateOf(player.isPlaying) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var seeking by remember { mutableStateOf(false) }
        LaunchedEffect(player) {
            while (isActive) {
                playing = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                if (!seeking) position = player.currentPosition.coerceAtLeast(0L)
                delay(250)
            }
        }
        Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("♫", style = MaterialTheme.typography.displayLarge, color = Color.White)
            Spacer(Modifier.height(24.dp))
            Text(item.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (playbackFailed) Text(stringResource(R.string.media_playback_failed), color = Color.White)
            Slider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { seeking = true; position = (it * duration).toLong() },
                onValueChangeFinished = { player.seekTo(position); seeking = false }, enabled = duration > 0)
            Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button({ if (player.isPlaying) player.pause() else player.play() }) {
                Text(stringResource(if (playing) R.string.media_pause else R.string.media_play))
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
