package app.swiftdrop.ui.screens.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import app.swiftdrop.music.cleanMusicTitle
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
internal fun PhotoTile(item: LocalMedia,checked:Boolean,onOpen:()->Unit,onToggle:()->Unit,
    selectionMode:Boolean=false) {
    val scale by animateFloatAsState(if(checked) .96f else 1f,tween(130),label="photo selection")
    Card(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer(scaleX=scale,scaleY=scale)
        .semantics { contentDescription=item.name }
        .combinedClickable(onClick=onOpen,onLongClick=onToggle),
        shape=RoundedCornerShape(10.dp),
        border=if(checked) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxSize()) {
            MediaArt(item.uri,MediaKind.Photos,Modifier.fillMaxSize())
            if(selectionMode || checked) {
                Checkbox(checked=checked,onCheckedChange={onToggle()},
                    modifier=Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VideoTile(item: LocalMedia,checked:Boolean,onOpen:()->Unit,onToggle:()->Unit,
    selectionMode:Boolean=false) {
    val scale by animateFloatAsState(if(checked) .97f else 1f,tween(130),label="video selection")
    Card(Modifier.fillMaxWidth().graphicsLayer(scaleX=scale,scaleY=scale)
        .semantics { contentDescription=item.name }
        .combinedClickable(onClick=onOpen,onLongClick=onToggle),
        shape=RoundedCornerShape(10.dp),
        border=if(checked) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                MediaArt(item.uri,MediaKind.Videos,Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    shape=RoundedCornerShape(5.dp),color=Color.Black.copy(alpha=.72f)) {
                    Text(formatMediaDuration(item.duration),Modifier.padding(horizontal=5.dp,vertical=2.dp),
                        style=MaterialTheme.typography.labelSmall,color=Color.White)
                }
                if(selectionMode || checked) {
                    Checkbox(checked=checked,onCheckedChange={onToggle()},
                        modifier=Modifier.align(Alignment.TopEnd))
                }
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
                Text(cleanMusicTitle(item.name),maxLines=1,overflow=TextOverflow.Ellipsis,
                    style=MaterialTheme.typography.titleSmall)
                Text(formatMediaDuration(item.duration)+" · "+sizeLabel(item.size),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked=checked,onCheckedChange={onToggle()})
        }
    }
}

/** Music playback now lives in a single MediaSessionService, not in a tab-owned ExoPlayer. */
@Composable
internal fun MusicMiniPlayer(item:LocalMedia,onClose:()->Unit) {
    app.swiftdrop.music.BackgroundMusicControls(item,compact=true,onClose=onClose)
}
