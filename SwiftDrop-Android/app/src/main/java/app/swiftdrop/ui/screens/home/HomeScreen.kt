package app.swiftdrop.ui.screens.home

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.TransferUiState
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign
import androidx.compose.runtime.*
import app.swiftdrop.music.HomeMusicMiniPlayer
import app.swiftdrop.music.MusicPlayerDialog
import app.swiftdrop.music.rememberMusicSessionController
import app.swiftdrop.LocalMedia
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * v0.3.1 — a compact, accessible home screen.
 * All actions still route to existing file pickers and Nearby transfer flows.
 */
@Composable
fun HomeScreen(
    state: TransferUiState,
    send: (Array<String>) -> Unit,
    receive: () -> Unit,
    seeAll: () -> Unit,
    resume: () -> Unit,
    status: String,
    openFiles: () -> Unit = { send(arrayOf("*/*")) },
    openApps: () -> Unit = { send(arrayOf("application/vnd.android.package-archive")) },
    onSettings: (() -> Unit)? = null,
    openMedia: ((app.swiftdrop.MediaKind) -> Unit)? = null,
    openDocuments: () -> Unit = { send(arrayOf("application/pdf","text/*")) }
) {
    var showMusic by remember { mutableStateOf(false) }
    val musicController=rememberMusicSessionController()
    var activeMedia by remember { mutableStateOf<LocalMedia?>(null) }
    LaunchedEffect(musicController) {
        while(isActive) {
            val current=musicController?.currentMediaItem
            val uri=current?.localConfiguration?.uri
            activeMedia=if(uri!=null) LocalMedia(uri,current.mediaMetadata.title?.toString()
                ?: uri.lastPathSegment ?: "Audio",0,0) else null
            delay(750L)
        }
    }
    if(showMusic && activeMedia!=null) {
        MusicPlayerDialog(requireNotNull(activeMedia),close={showMusic=false})
    }
    val hasActiveSession = state.isDiscovering || state.isAdvertising ||
        state.connectedName != null || state.progress != null || state.selected.isNotEmpty()

    val categories = listOf(
        HomeCategory(R.drawable.ic_photos, R.string.photos, {
            openMedia?.invoke(app.swiftdrop.MediaKind.Photos) ?: send(arrayOf("image/*"))
        }),
        HomeCategory(R.drawable.ic_videos, R.string.videos, {
            openMedia?.invoke(app.swiftdrop.MediaKind.Videos) ?: send(arrayOf("video/*"))
        }),
        HomeCategory(R.drawable.ic_music, R.string.music, {
            openMedia?.invoke(app.swiftdrop.MediaKind.Music) ?: send(arrayOf("audio/*"))
        }),
        HomeCategory(R.drawable.ic_applications, R.string.home_apps_short, openApps),
        HomeCategory(R.drawable.ic_documents, R.string.home_docs_short, openDocuments)
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal=18.dp, vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)
    ) {
        BrandHeader(onSettings)

        CompactConnectionStatus(state, status)
        HomeMusicMiniPlayer(onOpen={showMusic=true})

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 335.dp || LocalDensity.current.fontScale >= 1.3f
            if (stacked) {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    MainTransferAction(R.drawable.ic_send, stringResource(R.string.send_files),
                        stringResource(R.string.choose_files), openFiles,
                        modifier=Modifier.fillMaxWidth().height(134.dp))
                    MainTransferAction(R.drawable.ic_receive, stringResource(R.string.receive_files),
                        stringResource(R.string.make_visible), receive,
                        modifier=Modifier.fillMaxWidth().height(134.dp), receiving=true)
                }
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    MainTransferAction(R.drawable.ic_send, stringResource(R.string.send_files),
                        stringResource(R.string.choose_files), openFiles,
                        modifier=Modifier.weight(1f).height(162.dp))
                    MainTransferAction(R.drawable.ic_receive, stringResource(R.string.receive_files),
                        stringResource(R.string.make_visible), receive,
                        modifier=Modifier.weight(1f).height(162.dp), receiving=true)
                }
            }
        }

        if (hasActiveSession) {
            FilledTonalButton(resume, Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                AppIcon(R.drawable.ic_progress, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.active_session))
            }
        }

        // QR uses the existing flow: choose files -> Selection -> Scan receiver QR.
        Card(
            onClick=openFiles,
            colors=CardDefaults.cardColors(
                containerColor=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.5f)),
            shape=MaterialTheme.shapes.medium
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(R.drawable.ic_qr, Modifier.size(28.dp),
                    tint=MaterialTheme.colorScheme.onSecondaryContainer)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.home_qr_title),
                        style=MaterialTheme.typography.titleMedium,
                        color=MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(stringResource(R.string.home_qr_hint),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.home_your_files),Modifier.weight(1f),
                style=MaterialTheme.typography.titleLarge)
            TextButton(openFiles,Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.home_browse_all))
            }
        }

        // All five categories stay visible: no hidden fifth tile behind a horizontal swipe.
        // Three columns normally; two columns on narrow screens or with large accessibility fonts.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth<305.dp || LocalDensity.current.fontScale>=1.35f) 2 else 3
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                categories.chunked(columns).forEach { group ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        group.forEach { category ->
                            FileCategoryAction(category,Modifier.weight(1f))
                        }
                        repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.recent),Modifier.weight(1f),
                style=MaterialTheme.typography.titleLarge)
            TextButton(seeAll,Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.see_all))
            }
        }

        if (state.history.isEmpty()) {
            Surface(shape=MaterialTheme.shapes.medium,
                color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    IconBadge(R.drawable.ic_history)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.home_no_recent),
                            style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.home_recent_hint),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            state.history.take(3).forEach { TransferHistoryItem(it) }
        }

        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_security,Modifier.size(18.dp))
            Text(stringResource(R.string.private_caption),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactConnectionStatus(state: TransferUiState, errorText: String) {
    val text = when {
        state.hasError -> errorText
        state.connectedName != null -> stringResource(R.string.home_connected_to, state.connectedName)
        state.isDiscovering -> stringResource(R.string.searching)
        state.isAdvertising -> stringResource(R.string.waiting_sender)
        else -> stringResource(R.string.home_ready_to_share)
    }
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=RoundedCornerShape(12.dp),
                color=if(state.hasError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(42.dp),contentAlignment=Alignment.Center) {
                    AppIcon(if(state.connectedName!=null) R.drawable.ic_security else R.drawable.ic_nearby,
                        Modifier.size(21.dp),
                        tint=if(state.hasError) MaterialTheme.colorScheme.onErrorContainer
                             else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.connection_status_title),
                    style=MaterialTheme.typography.titleMedium)
                Text(text,style=MaterialTheme.typography.bodySmall,
                    color=if(state.hasError) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Text(Build.MODEL.take(18),style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines=1,overflow=TextOverflow.Ellipsis,
                modifier=Modifier.widthIn(max=104.dp))
        }
    }
}

@Composable
private fun MainTransferAction(@DrawableRes icon: Int,title: String,subtitle: String,
    onClick: () -> Unit,modifier: Modifier,receiving: Boolean=false) {
    val colors = if(receiving)
        listOf(Color(0xFF09869D),Color(0xFF056D88))
        else listOf(SwiftDropDesign.Blue,Color(0xFF0752D8))
    Card(onClick=onClick,modifier=modifier,shape=MaterialTheme.shapes.medium,
        colors=CardDefaults.cardColors(containerColor=Color.Transparent),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(16.dp),
            verticalArrangement=Arrangement.SpaceBetween) {
            AppIcon(icon,Modifier.size(30.dp),tint=Color.White)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,color=Color.White,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,
                    color=Color.White.copy(alpha=.94f),maxLines=2,
                    overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private data class HomeCategory(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    val action: () -> Unit
)

@Composable
private fun FileCategoryAction(category: HomeCategory,modifier: Modifier) {
    Card(onClick=category.action,modifier=modifier.heightIn(min=92.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        shape=MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(horizontal=3.dp,vertical=10.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CategoryBadge(category.icon,Modifier.size(42.dp))
            Text(stringResource(category.title),
                style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
