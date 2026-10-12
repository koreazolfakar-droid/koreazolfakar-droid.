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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

private val MusicInk = Color(0xFF0E192D)
private val MusicBlue = Color(0xFF2577F5)
private val SecondaryInk = Color(0xFFB5C7DF)

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
    filename.replace(Regex("(?i)\\.(mp3|m4a|aac|flac|wav|ogg|opus|wma)$"),"")
        .trim().ifBlank { filename }

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
                .setIsPlayable(true).build())
            .build()
    )
    controller.prepare()
    controller.play()
}

/** Replace queue only on explicit user selection; avoid destroying an existing seek position. */
internal fun startLocalPlaylist(controller:MediaController,items:List<LocalMedia>,chosen:LocalMedia) {
    val playable = items.take(600)
    val index = playable.indexOfFirst { it.uri==chosen.uri }
    if(index<0) { startLocalMusic(controller,chosen);return }
    if(controller.currentMediaItem?.mediaId==chosen.uri.toString()) {
        controller.play()
        return
    }
    val mediaItems = playable.map { file ->
        MediaItem.Builder()
            .setUri(file.uri)
            .setMediaId(file.uri.toString())
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(cleanMusicTitle(file.name))
                .setIsPlayable(true).build())
            .build()
    }
    controller.setMediaItems(mediaItems,index,0L)
    controller.prepare()
    controller.play()
}

/** Real local file metadata only. Embedded art is decoded to a bounded size on IO. */
internal data class SongArtwork(val artist:String?,val album:String?,val picture:Bitmap?)
internal fun readSongArtwork(context:android.content.Context,uri:Uri):SongArtwork {
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
        SongArtwork(artist,album,bitmap)
    } catch (_: Exception) {
        SongArtwork(null,null,null)
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun AlbumCover(uri:Uri?,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val info by produceState<SongArtwork?>(null,uri) {
        value=if(uri==null) null else withContext(Dispatchers.IO) {
            readSongArtwork(context,uri)
        }
    }
    Box(modifier.clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF25487C),Color(0xFF182F55),Color(0xFF142642)))),
        contentAlignment=Alignment.Center) {
        if(info?.picture!=null)
            Image(info!!.picture!!.asImageBitmap(),contentDescription=stringResource(R.string.music_album_art),
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Surface(shape=RoundedCornerShape(34.dp),color=Color.White.copy(alpha=.09f)) {
                AppIcon(R.drawable.ic_music,Modifier.padding(42.dp).size(64.dp),tint=Color(0xFF9AC9FF))
            }
        }
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
    item:LocalMedia,modifier:Modifier=Modifier,compact:Boolean=false,onClose:(()->Unit)?=null
) {
    val player=rememberMusicSessionController()
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

    LaunchedEffect(player,item.uri) { player?.let { startLocalMusic(it,item) } }
    LaunchedEffect(player) {
        while(isActive) {
            playing=player?.isPlaying == true
            val mediaItem=player?.currentMediaItem
            currentUri=mediaItem?.localConfiguration?.uri ?: item.uri
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
        Surface(color=MaterialTheme.colorScheme.secondaryContainer,
            shape=RoundedCornerShape(18.dp),tonalElevation=2.dp) {
            Column(modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AlbumCover(currentUri,Modifier.size(46.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(currentTitle,maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.titleSmall)
                        Text(artwork?.artist ?: stringResource(R.string.music_local_audio),
                            maxLines=1,overflow=TextOverflow.Ellipsis,
                            style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick=toggle,enabled=player!=null) {
                        AppIcon(if(playing)R.drawable.ic_player_pause else R.drawable.ic_player_play,
                            Modifier.size(24.dp))
                    }
                    if(onClose!=null) IconButton(onClick={
                        player?.stop()
                        player?.clearMediaItems()
                        onClose()
                    }) { AppIcon(R.drawable.ic_close,Modifier.size(20.dp)) }
                }
                LinearProgressIndicator(progress={
                    if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f
                },modifier=Modifier.fillMaxWidth().height(3.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=22.dp,vertical=14.dp),
            verticalArrangement=Arrangement.spacedBy(20.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            AlbumCover(currentUri,Modifier.fillMaxWidth().widthIn(max=310.dp).aspectRatio(1f))
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(currentTitle,color=Color.White,
                    style=MaterialTheme.typography.headlineMedium,maxLines=2,
                    overflow=TextOverflow.Ellipsis)
                Text(artwork?.artist ?: stringResource(R.string.music_local_audio),
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
                        thumbColor=Color.White,inactiveTrackColor=Color(0xFF435875)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(formatPosition(position),color=SecondaryInk,style=MaterialTheme.typography.labelMedium)
                    Text(formatPosition(duration),color=SecondaryInk,style=MaterialTheme.typography.labelMedium)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.Center, ) {
                IconButton(onClick={player?.seekToPreviousMediaItem();Unit},enabled=hasPrevious,
                    modifier=Modifier.size(60.dp)) {
                    AppIcon(R.drawable.ic_player_previous,Modifier.size(30.dp),tint=Color.White)
                }
                Spacer(Modifier.width(18.dp))
                FilledIconButton(onClick=toggle,enabled=player!=null,
                    modifier=Modifier.size(74.dp),
                    colors=IconButtonDefaults.filledIconButtonColors(
                        containerColor=MusicBlue,contentColor=Color.White)) {
                    AppIcon(if(playing)R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        Modifier.size(34.dp),tint=Color.White)
                }
                Spacer(Modifier.width(18.dp))
                IconButton(onClick={player?.seekToNextMediaItem();Unit},enabled=hasNext,
                    modifier=Modifier.size(60.dp)) {
                    AppIcon(R.drawable.ic_player_next,Modifier.size(30.dp),tint=Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceEvenly) {
                TextButton(onClick={player?.shuffleModeEnabled=!shuffleEnabled; Unit},
                    enabled=player!=null) {
                    AppIcon(R.drawable.ic_player_shuffle,Modifier.size(20.dp),
                        tint=if(shuffleEnabled) Color(0xFF7AB7FF) else SecondaryInk)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.music_shuffle),color=if(shuffleEnabled)
                        Color(0xFF7AB7FF) else SecondaryInk)
                }
                TextButton(onClick={
                    player?.repeatMode=when(repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                },enabled=player!=null) {
                    AppIcon(R.drawable.ic_player_repeat,Modifier.size(20.dp),
                        tint=if(repeatMode!=Player.REPEAT_MODE_OFF) Color(0xFF7AB7FF) else SecondaryInk)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(when(repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.music_repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.music_repeat_all
                        else -> R.string.music_repeat
                    }),color=if(repeatMode!=Player.REPEAT_MODE_OFF)
                        Color(0xFF7AB7FF) else SecondaryInk)
                }
            }
            if(playbackError) Text(stringResource(R.string.media_playback_failed),
                color=Color(0xFFFFA4A4),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.music_background_hint),color=SecondaryInk,
                style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,maxLines=2)
        }
    }
}

/** Uses current session state on the Home screen; never restarts the audio on mount. */
@Composable
fun HomeMusicMiniPlayer(onOpen:()->Unit) {
    val player=rememberMusicSessionController()
    var title by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while(isActive) {
            title=player?.currentMediaItem?.mediaMetadata?.title?.toString()
            playing=player?.isPlaying == true
            delay(500L)
        }
    }
    if(!title.isNullOrBlank()) {
        Surface(onClick=onOpen,color=MaterialTheme.colorScheme.secondaryContainer,
            shape=RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                AppIcon(R.drawable.ic_music,Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.music_now_playing),
                        style=MaterialTheme.typography.labelSmall)
                    Text(title!!,style=MaterialTheme.typography.titleMedium,
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick={player?.let{if(it.isPlaying)it.pause() else it.play()};Unit},
                    enabled=player!=null) {
                    AppIcon(if(playing)R.drawable.ic_player_pause else R.drawable.ic_player_play,
                        Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Standalone modal with modern top bar; closes the UI without stopping background playback. */
@Composable
fun MusicPlayerDialog(item:LocalMedia,close:()->Unit,select:(()->Unit)?=null) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(MusicInk,Color(0xFF142441),Color(0xFF172F4D))))) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick=close) {
                    Text(stringResource(R.string.media_close),color=Color.White)
                }
                Text(stringResource(R.string.music_now_playing),
                    modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,
                    color=Color.White,textAlign=TextAlign.Center)
                if(select!=null) TextButton(onClick=select) {
                    Text(stringResource(R.string.media_select),color=Color.White)
                } else Spacer(Modifier.width(60.dp))
            }
            BackgroundMusicControls(item,Modifier.weight(1f))
        }
    }
}
