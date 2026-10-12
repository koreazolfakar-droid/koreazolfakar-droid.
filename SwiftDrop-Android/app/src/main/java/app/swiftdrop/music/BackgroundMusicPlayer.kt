package app.swiftdrop.music

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.swiftdrop.LocalMedia
import app.swiftdrop.R
import app.swiftdrop.ui.components.AppIcon
import app.swiftdrop.ui.components.categoryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale


/** The controller is short-lived; playback belongs to MediaSessionService. */
@Composable
internal fun rememberMusicSessionController(): MediaController? {
    val context=LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val future=MediaController.Builder(context,
            SessionToken(context,ComponentName(context,SwiftDropMusicService::class.java))
        ).buildAsync()
        var disposed=false
        future.addListener({
            if(!disposed) controller=runCatching { future.get() }.getOrNull()
        },ContextCompat.getMainExecutor(context))
        onDispose {
            disposed=true
            controller=null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

internal fun cleanMusicTitle(filename:String):String =
    filename.trim().replace(Regex("(?i)\\.(mp3|m4a|aac|flac|wav|ogg|opus|wma)$"),"")
        .trim().ifBlank { filename }

/** MediaSession strips LocalConfiguration from remote controllers. SwiftDrop's stable
 * mediaId is the real local URI, so other UI controllers can still resolve artwork/player UI. */
internal fun localMusicUri(item:MediaItem?):Uri? {
    item?.localConfiguration?.uri?.let { return it }
    val id=item?.mediaId?.takeIf(String::isNotBlank) ?: return null
    val uri=runCatching { Uri.parse(id) }.getOrNull() ?: return null
    return uri.takeIf { it.scheme=="content" || it.scheme=="file" }
}

/** Reopening an existing controller must never restart a paused or progressing song. */
internal fun startLocalMusic(controller:MediaController,item:LocalMedia) {
    val id=item.uri.toString()
    if(controller.currentMediaItem?.mediaId == id) return
    controller.setMediaItem(
        MediaItem.Builder()
            .setUri(item.uri)
            .setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(cleanMusicTitle(item.name))
                .setArtist(item.artist).setAlbumTitle(item.album)
                .setIsPlayable(true).build())
            .build()
    )
    controller.prepare()
    controller.play()
}

/** Upgrade early single-item playback to a real playlist when the gallery becomes ready. */
internal fun shouldUpgradeMusicQueue(currentCount:Int,availableCount:Int):Boolean =
    currentCount<=1 && availableCount>1

/** Replace queue only on explicit user selection; avoid destroying an existing seek position. */
internal fun startLocalPlaylist(controller:MediaController,items:List<LocalMedia>,chosen:LocalMedia) {
    val playable = items.take(600)
    val index = playable.indexOfFirst { it.uri==chosen.uri }
    if(index<0) { startLocalMusic(controller,chosen);return }
    val sameSong=controller.currentMediaItem?.mediaId==chosen.uri.toString()
    // Another screen may have started a single item before this controller connected.
    // Expand it into the actual queue without losing playback position.
    if(sameSong && !shouldUpgradeMusicQueue(controller.mediaItemCount,playable.size)) {
        controller.play()
        return
    }
    val resumeAt=if(sameSong) controller.currentPosition.coerceAtLeast(0L) else 0L
    val mediaItems = playable.map { file ->
        MediaItem.Builder()
            .setUri(file.uri)
            .setMediaId(file.uri.toString())
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(cleanMusicTitle(file.name))
                .setArtist(file.artist).setAlbumTitle(file.album)
                .setIsPlayable(true).build())
            .build()
    }
    controller.setMediaItems(mediaItems,index,resumeAt)
    controller.prepare()
    controller.play()
}

/** Real local file metadata only. Embedded art is decoded to a bounded size on IO. */
internal data class SongArtwork(val artist:String?,val album:String?,val picture:Bitmap?)
private val songArtCache=object:android.util.LruCache<String,SongArtwork>(8*1024*1024) {
    override fun sizeOf(key:String,value:SongArtwork)=value.picture?.byteCount ?: 256
}
internal fun readSongArtwork(context:android.content.Context,uri:Uri):SongArtwork {
    songArtCache.get(uri.toString())?.let { return it }
    val retriever=MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context,uri)
        val artist=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf(String::isNotBlank)
        val album=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ?.takeIf(String::isNotBlank)
        val raw=retriever.embeddedPicture?.takeIf { it.size<=2*1024*1024 }
        val bitmap=raw?.let {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(it,0,it.size,bounds)
            val sample=(maxOf(bounds.outWidth,bounds.outHeight)/520).coerceAtLeast(1)
            BitmapFactory.decodeByteArray(it,0,it.size,BitmapFactory.Options().apply {
                inSampleSize=sample
            })
        }
        SongArtwork(artist,album,bitmap).also { songArtCache.put(uri.toString(),it) }
    } catch (_: Exception) {
        SongArtwork(null,null,null)
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
internal fun AlbumCover(uri:Uri?,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val info by produceState<SongArtwork?>(null,uri) {
        value=if(uri==null) null else withContext(Dispatchers.IO) {
            readSongArtwork(context,uri)
        }
    }
    val accent=categoryColor(R.drawable.ic_music)
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha=.10f)),
        contentAlignment=Alignment.Center) {
        if(info?.picture!=null) Image(info!!.picture!!.asImageBitmap(),
            contentDescription=stringResource(R.string.music_album_art),
            modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else AppIcon(R.drawable.ic_music,Modifier.fillMaxSize(.26f),tint=accent)
    }
}

private fun formatPosition(ms:Long):String {
    val seconds=ms.coerceAtLeast(0)/1000
    return if(seconds>=3600) "%d:%02d:%02d".format(Locale.ROOT,seconds/3600,(seconds/60)%60,seconds%60)
    else "%d:%02d".format(Locale.ROOT,seconds/60,seconds%60)
}

/** No independent ExoPlayer: notification, lock screen and both UIs use one session. */
@Composable
internal fun BackgroundMusicControls(
    item:LocalMedia,modifier:Modifier=Modifier,compact:Boolean=false,
    onClose:(()->Unit)?=null,autoStart:Boolean=true,onOpen:(()->Unit)?=null
) {
    val player=rememberMusicSessionController()
    val MusicBlue=categoryColor(R.drawable.ic_music)
    val SecondaryInk=MaterialTheme.colorScheme.onSurfaceVariant
    val playerInk=MaterialTheme.colorScheme.onSurface
    var showQueue by remember { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(false) }
    var position by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var seeking by remember { mutableFloatStateOf(-1f) }
    var currentUri by remember(item.uri) { mutableStateOf(item.uri) }
    var currentTitle by remember(item.uri) { mutableStateOf(cleanMusicTitle(item.name)) }
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var playbackError by remember { mutableStateOf(false) }
    val context=LocalContext.current
    val artwork by produceState<SongArtwork?>(null,currentUri) {
        value=withContext(Dispatchers.IO) { readSongArtwork(context,currentUri) }
    }

    LaunchedEffect(player,item.uri,autoStart) {
        player?.let {
            if(autoStart || it.currentMediaItem == null) startLocalMusic(it,item)
        }
    }
    LaunchedEffect(player) {
        while(isActive) {
            playing=player?.isPlaying == true
            val mediaItem=player?.currentMediaItem
            currentUri=localMusicUri(mediaItem) ?: item.uri
            currentTitle=mediaItem?.mediaMetadata?.title?.toString() ?: cleanMusicTitle(item.name)
            hasPrevious=player?.hasPreviousMediaItem()==true
            hasNext=player?.hasNextMediaItem()==true
            shuffleEnabled=player?.shuffleModeEnabled==true
            repeatMode=player?.repeatMode ?: Player.REPEAT_MODE_OFF
            playbackError=player?.playerError!=null
            position=player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration=player?.duration?.takeIf { it>0L } ?: 0L
            delay(if(compact) 500L else 250L)
        }
    }
    val toggle:()->Unit={
        player?.let { if(it.isPlaying)it.pause() else it.play() }
    }
    if(compact) {
        Surface(onClick={onOpen?.invoke()},enabled=onOpen!=null,
            color=MaterialTheme.colorScheme.surface,
            shape=MaterialTheme.shapes.medium) {
            Column(modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AlbumCover(currentUri,Modifier.size(46.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(currentTitle,maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.titleSmall)
                        Text(artwork?.artist ?: player?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.music_local_audio),
                            maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick=toggle,enabled=player!=null) {
                        AppIcon(if(playing)R.drawable.ic_player_pause else R.drawable.ic_player_play,
                            Modifier.size(24.dp),description=stringResource(if(playing) R.string.media_pause else R.string.media_play),tint=MusicBlue)
                    }
                    if(onClose!=null) IconButton(onClick={
                        player?.stop()
                        player?.clearMediaItems()
                        onClose()
                    }) { AppIcon(R.drawable.ic_close,Modifier.size(20.dp),description=stringResource(R.string.media_close)) }
                }
                LinearProgressIndicator(progress={
                    if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f
                },modifier=Modifier.fillMaxWidth().height(3.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=22.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            AlbumCover(currentUri,Modifier.widthIn(max=280.dp).fillMaxWidth().aspectRatio(1f))
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(currentTitle,color=playerInk,
                    style=MaterialTheme.typography.headlineMedium,maxLines=2,
                    overflow=TextOverflow.Ellipsis)
                Text(artwork?.artist ?: player?.mediaMetadata?.artist?.toString() ?: stringResource(R.string.music_local_audio),
                    color=SecondaryInk,style=MaterialTheme.typography.bodyLarge)
                if(!artwork?.album.isNullOrEmpty()) Text(artwork!!.album!!,
                    color=SecondaryInk,style=MaterialTheme.typography.bodySmall,maxLines=1)
            }
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Slider(value=if(seeking>=0f)seeking else if(duration>0)
                        (position.toFloat()/duration).coerceIn(0f,1f) else 0f,
                    onValueChange={seeking=it},onValueChangeFinished={
                        if(duration>0 && seeking>=0f) player?.seekTo((duration*seeking).toLong())
                        seeking=-1f
                    },enabled=player!=null && duration>0,
                    colors=SliderDefaults.colors(activeTrackColor=MusicBlue,
                        thumbColor=MusicBlue,inactiveTrackColor=MaterialTheme.colorScheme.surfaceVariant))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(formatPosition(position),color=SecondaryInk,style=MaterialTheme.typography.labelMedium)
                    Text(formatPosition(duration),color=SecondaryInk,style=MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.Center, ) {
                IconButton(onClick={player?.seekToPreviousMediaItem();Unit},enabled=hasPrevious,
                    modifier=Modifier.size(60.dp)) {
                    AppIcon(R.drawable.ic_player_previous,Modifier.size(30.dp),description=stringResource(R.string.media_previous),tint=if(hasPrevious) playerInk else SecondaryInk.copy(alpha=.38f))
                }
                Spacer(Modifier.width(18.dp))
                FilledIconButton(onClick=toggle,enabled=player!=null,
                    modifier=Modifier.size(74.dp),
                    colors=IconButtonDefaults.filledIconButtonColors(
                        containerColor=MusicBlue,contentColor=Color.White)) {
                    AppIcon(if(playing)R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        Modifier.size(34.dp),description=stringResource(if(playing) R.string.media_pause else R.string.media_play),tint=Color.White)
                }
                Spacer(Modifier.width(18.dp))
                IconButton(onClick={player?.seekToNextMediaItem();Unit},enabled=hasNext,
                    modifier=Modifier.size(60.dp)) {
                    AppIcon(R.drawable.ic_player_next,Modifier.size(30.dp),description=stringResource(R.string.media_next),tint=if(hasNext) playerInk else SecondaryInk.copy(alpha=.38f))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; Unit},
                    enabled=player!=null) {
                    AppIcon(R.drawable.ic_player_shuffle,Modifier.size(20.dp),
                        tint=if(shuffleEnabled) MusicBlue else SecondaryInk)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.music_shuffle),color=if(shuffleEnabled)
                        MusicBlue else SecondaryInk)
                }
                TextButton(onClick={
                    player?.let { it.repeatMode=when(it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    } }
                },enabled=player!=null) {
                    AppIcon(R.drawable.ic_player_repeat,Modifier.size(20.dp),
                        tint=if(repeatMode!=Player.REPEAT_MODE_OFF) MusicBlue else SecondaryInk)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(when(repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.music_repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.music_repeat_all
                        else -> R.string.music_repeat
                    }),color=if(repeatMode!=Player.REPEAT_MODE_OFF)
                        MusicBlue else SecondaryInk)
                }
            }
            if(playbackError) Text(stringResource(R.string.media_playback_failed),
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={showQueue=true},enabled=player!=null) {
                AppIcon(R.drawable.ic_queue,tint=MusicBlue)
                Spacer(Modifier.width(8.dp));Text(stringResource(R.string.music_queue),color=MusicBlue)
            }
        }
    }
    if(showQueue) Dialog(onDismissRequest={showQueue=false}) {
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max=520.dp).padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.music_queue),Modifier.weight(1f),
                        style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick={showQueue=false}) {
                        AppIcon(R.drawable.ic_close,description=stringResource(R.string.media_close))
                    }
                }
                val queue=player?.let { controller -> (0 until controller.mediaItemCount).map(controller::getMediaItemAt) }.orEmpty()
                if(queue.isEmpty()) Text(stringResource(R.string.music_queue_empty))
                LazyColumn {
                    itemsIndexed(queue,key={index,media -> "$index:${media.mediaId}"}) { index,media ->
                        Surface(onClick={player?.seekToDefaultPosition(index);player?.play();showQueue=false},
                            color=if(player?.currentMediaItemIndex==index) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,shape=MaterialTheme.shapes.small) {
                            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(8.dp),
                                verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("${index+1}",style=MaterialTheme.typography.labelMedium)
                                Column(Modifier.weight(1f)) {
                                    Text(media.mediaMetadata.title?.toString().orEmpty(),maxLines=1,
                                        overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                                    media.mediaMetadata.artist?.toString()?.let {
                                        Text(it,style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Activity-wide mini player reads the service; mounting never starts or pauses audio. */
@Composable
fun HomeMusicMiniPlayer(onOpen:()->Unit) {
    val player=rememberMusicSessionController()
    var item by remember { mutableStateOf<LocalMedia?>(null) }
    DisposableEffect(player) {
        fun update() {
            val media=player?.currentMediaItem
            val uri=localMusicUri(media)
            item=if(uri==null || media==null) null else LocalMedia(uri,
                media.mediaMetadata.title?.toString().orEmpty(),0,0,
                artist=media.mediaMetadata.artist?.toString(),album=media.mediaMetadata.albumTitle?.toString())
        }
        val listener=object:Player.Listener {
            override fun onEvents(player:Player,events:Player.Events) { update() }
        }
        player?.addListener(listener)
        update()
        onDispose { player?.removeListener(listener) }
    }
    item?.let { BackgroundMusicControls(it,compact=true,autoStart=false,onOpen=onOpen) }
}

/** Stable activity-level host survives all navigation destinations. */
@Composable
fun PersistentMusicPlayer() {
    val controller=rememberMusicSessionController()
    var show by remember { mutableStateOf(false) }
    HomeMusicMiniPlayer(onOpen={show=true})
    val media=controller?.currentMediaItem
    val uri=localMusicUri(media)
    if(show && uri!=null && media!=null) MusicPlayerDialog(LocalMedia(uri,
        media.mediaMetadata.title?.toString().orEmpty(),0,0),close={show=false})
}

/** Standalone modal with modern top bar; closes the UI without stopping background playback. */
@Composable
fun MusicPlayerDialog(item:LocalMedia,close:()->Unit,select:(()->Unit)?=null) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick=close) {
                    Text(stringResource(R.string.media_close),color=MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.music_now_playing),
                    modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,
                    color=MaterialTheme.colorScheme.onSurface,textAlign=TextAlign.Center)
                if(select!=null) TextButton(onClick=select) {
                    Text(stringResource(R.string.media_select),color=MaterialTheme.colorScheme.onSurface)
                } else Spacer(Modifier.width(60.dp))
            }
            BackgroundMusicControls(item,Modifier.weight(1f),autoStart=false)
        }
    }
}
