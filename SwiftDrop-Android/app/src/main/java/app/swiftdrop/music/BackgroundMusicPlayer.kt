package app.swiftdrop.music

import android.content.ComponentName
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.swiftdrop.LocalMedia
import app.swiftdrop.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Controllers belong to Compose; playback belongs to SwiftDropMusicService. */
@Composable
internal fun rememberMusicSessionController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val future = MediaController.Builder(context,
            SessionToken(context, ComponentName(context, SwiftDropMusicService::class.java))
        ).buildAsync()
        var disposed = false
        future.addListener({
            if(!disposed) {
                controller = try { future.get() } catch (_: Exception) { null }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

internal fun cleanMusicTitle(filename: String): String =
    filename.replace(Regex("(?i)\\.(mp3|m4a|aac|flac|wav|ogg|opus|wma)$"), "")
        .trim().ifBlank { filename }

internal fun startLocalMusic(controller: MediaController,item: LocalMedia) {
    val id = item.uri.toString()
    if(controller.currentMediaItem?.mediaId != id) {
        controller.setMediaItem(
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(id)
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(cleanMusicTitle(item.name))
                    .setIsPlayable(true)
                    .build())
                .build()
        )
        controller.prepare()
    }
    controller.play()
}

@Composable
internal fun BackgroundMusicControls(
    item: LocalMedia,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val player = rememberMusicSessionController()
    var playing by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seekPosition by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(player,item.uri) {
        player?.let { startLocalMusic(it,item) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
            }
        }
        player?.addListener(listener)
        playing = player?.isPlaying == true
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while(isActive) {
            playing = player?.isPlaying == true
            position = player?.currentPosition?.coerceAtLeast(0) ?: 0L
            duration = player?.duration?.coerceAtLeast(0) ?: 0L
            delay(if(compact) 500L else 250L)
        }
    }

    if(compact) {
        Surface(tonalElevation=5.dp,color=MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(cleanMusicTitle(item.name),
                            style=MaterialTheme.typography.labelMedium,maxLines=1)
                        Text("${formatPosition(position)} / ${formatPosition(duration)}",
                            style=MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick={player?.let { p -> if(p.isPlaying) p.pause() else p.play() }; Unit},
                        enabled=player!=null) {
                        Text(stringResource(if(playing) R.string.media_pause else R.string.media_play))
                    }
                    if(onClose!=null) TextButton(onClick={
                        player?.stop()
                        player?.clearMediaItems()
                        onClose()
                    }) { Text(stringResource(R.string.media_close)) }
                }
                LinearProgressIndicator(progress={
                    if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f
                },modifier=Modifier.fillMaxWidth().height(3.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth().padding(horizontal=24.dp),
            verticalArrangement=Arrangement.Center,
            horizontalAlignment=Alignment.CenterHorizontally) {
            Text("♫",style=MaterialTheme.typography.displayLarge,
                color=Color.White)
            Spacer(Modifier.height(22.dp))
            Text(cleanMusicTitle(item.name),style=MaterialTheme.typography.titleLarge,color=Color.White)
            Spacer(Modifier.height(12.dp))
            Slider(
                value=if(seekPosition>=0f) seekPosition else
                    if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f,
                onValueChange={seekPosition=it},
                onValueChangeFinished={
                    if(duration>0) player?.seekTo((seekPosition*duration).toLong())
                    seekPosition=-1f
                },
                enabled=player!=null && duration>0,
                colors=SliderDefaults.colors(activeTrackColor=Color(0xFF428BFF),
                    inactiveTrackColor=Color(0xFF4D607D),thumbColor=Color.White)
            )
            Text("${formatPosition(position)} / ${formatPosition(duration)}",color=Color.White)
            Spacer(Modifier.height(18.dp))
            Button(onClick={ player?.let { p -> if(p.isPlaying) p.pause() else p.play() }; Unit },
                enabled=player!=null) {
                Text(stringResource(if(playing) R.string.media_pause else R.string.media_play))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.music_background_hint),
                style=MaterialTheme.typography.bodySmall,
                color=Color(0xFFBECDE2))
        }
    }
}

private fun formatPosition(ms: Long): String {
    val seconds=ms.coerceAtLeast(0)/1000
    return if(seconds>=3600) "%d:%02d:%02d".format(
        java.util.Locale.ROOT,seconds/3600,(seconds/60)%60,seconds%60)
    else "%d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)
}
