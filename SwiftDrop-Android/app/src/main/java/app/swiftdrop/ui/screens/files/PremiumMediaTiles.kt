package app.swiftdrop.ui.screens.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.swiftdrop.LocalMedia
import app.swiftdrop.MediaKind
import app.swiftdrop.R
import app.swiftdrop.ui.components.AppIcon
import app.swiftdrop.ui.components.CategoryBadge
import app.swiftdrop.ui.components.sizeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Lightweight captions; we don't need to decode a video to know its duration. */
fun formatMediaDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0) / 1000)
    val h=seconds/3600
    val m=(seconds%3600)/60
    val s=seconds%60
    return if(h>0) "%d:%02d:%02d".format(java.util.Locale.ROOT,h,m,s)
        else "%d:%02d".format(java.util.Locale.ROOT,m,s)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoTile(item: LocalMedia,checked:Boolean,onOpen:()->Unit,onToggle:()->Unit) {
    Card(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription=item.name }
        .combinedClickable(onClick=onOpen,onLongClick=onToggle),
        shape=RoundedCornerShape(10.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxSize()) {
            MediaArt(item.uri,MediaKind.Photos,Modifier.fillMaxSize())
            Checkbox(checked=checked,onCheckedChange={onToggle()},
                modifier=Modifier.align(Alignment.TopEnd))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoTile(item: LocalMedia,checked:Boolean,onOpen:()->Unit,onToggle:()->Unit) {
    Card(Modifier.fillMaxWidth().semantics { contentDescription=item.name }
        .combinedClickable(onClick=onOpen,onLongClick=onToggle),
        shape=RoundedCornerShape(10.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                MediaArt(item.uri,MediaKind.Videos,Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    shape=RoundedCornerShape(5.dp),color=Color.Black.copy(alpha=.72f)) {
                    Text(formatMediaDuration(item.duration),Modifier.padding(horizontal=5.dp,vertical=2.dp),
                        style=MaterialTheme.typography.labelSmall,color=Color.White)
                }
                Checkbox(checked=checked,onCheckedChange={onToggle()},
                    modifier=Modifier.align(Alignment.TopEnd))
            }
            Column(Modifier.padding(horizontal=8.dp,vertical=7.dp),
                verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(item.name,maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.labelMedium)
                Text(sizeLabel(item.size),style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MusicRow(item:LocalMedia,checked:Boolean,onOpen:()->Unit,onToggle:()->Unit,onPlay:()->Unit) {
    Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min=66.dp).padding(horizontal=8.dp,vertical=7.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            IconButton(onClick=onPlay,modifier=Modifier.size(46.dp)) {
                AppIcon(R.drawable.ic_music,modifier=Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).combinedClickable(onClick=onOpen,onLongClick=onToggle),
                verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(item.name,maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.titleSmall)
                Text(formatMediaDuration(item.duration)+" · "+sizeLabel(item.size),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked=checked,onCheckedChange={onToggle()})
        }
    }
}

/** One mini-player only, owned by the current Music tab; releases on leave/revocation. */
@Composable
internal fun MusicMiniPlayer(item:LocalMedia,onClose:()->Unit) {
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val player=remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady=true
        }
    }
    var playing by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    DisposableEffect(player,lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while(isActive) {
            playing=player.isPlaying
            position=player.currentPosition.coerceAtLeast(0L)
            duration=player.duration.coerceAtLeast(0L)
            delay(350)
        }
    }
    Surface(tonalElevation=5.dp,color=MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start=14.dp,end=8.dp,top=6.dp,bottom=6.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_music,modifier=Modifier.size(22.dp),
                    tint=MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(item.name,style=MaterialTheme.typography.labelMedium,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(formatMediaDuration(position)+" / "+formatMediaDuration(duration),
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSecondaryContainer)
                }
                TextButton(onClick={if(playing)player.pause() else player.play()}) {
                    Text(stringResource(if(playing) R.string.media_pause else R.string.media_play))
                }
                IconButton(onClick=onClose) {
                    AppIcon(R.drawable.ic_close,modifier=Modifier.size(20.dp))
                }
            }
            LinearProgressIndicator(
                progress={if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f},
                modifier=Modifier.fillMaxWidth().height(3.dp))
        }
    }
}
