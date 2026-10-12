package app.swiftdrop.ui.screens.home

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

// SwiftDrop @feature Codex 12/10/2026 Reference-led home hierarchy, six equal categories.
@Composable
fun HomeScreen(state: TransferUiState, send: (Array<String>) -> Unit, receive: () -> Unit,
    seeAll: () -> Unit, resume: () -> Unit, status: String,
    openFiles: () -> Unit = { send(arrayOf("*/*")) },
    openApps: () -> Unit = { send(arrayOf("application/vnd.android.package-archive")) },
    onSettings: (() -> Unit)? = null, openMedia: ((MediaKind) -> Unit)? = null,
    openDocuments: () -> Unit = { send(arrayOf("application/pdf","text/*")) },
    openStorage: () -> Unit = openDocuments) {
    val active=state.isDiscovering || state.isAdvertising || state.connectingId!=null ||
        state.connectedName!=null || state.progress!=null || state.selected.isNotEmpty()
    val categories=listOf(
        HomeCategory(R.drawable.ic_photos,R.string.photos) {
            openMedia?.invoke(MediaKind.Photos) ?: send(arrayOf("image/*")) },
        HomeCategory(R.drawable.ic_videos,R.string.videos) {
            openMedia?.invoke(MediaKind.Videos) ?: send(arrayOf("video/*")) },
        HomeCategory(R.drawable.ic_music,R.string.music) {
            openMedia?.invoke(MediaKind.Music) ?: send(arrayOf("audio/*")) },
        HomeCategory(R.drawable.ic_applications,R.string.home_apps_short,openApps),
        HomeCategory(R.drawable.ic_folder,R.string.home_storage,openStorage),
        HomeCategory(R.drawable.ic_documents,R.string.home_docs_short,openDocuments))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal=SwiftDropDesign.ScreenPadding).padding(top=16.dp,bottom=20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        BrandHeader(onSettings)
        Spacer(Modifier.height(4.dp))
        ConnectionStatus(state,status,if(active) resume else receive)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked=maxWidth<320.dp || LocalDensity.current.fontScale>=1.35f
            if(stacked) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                TransferAction(R.drawable.ic_send,R.string.send_files,R.string.choose_files,
                    openFiles,Modifier.fillMaxWidth(),compact=true)
                TransferAction(R.drawable.ic_receive,R.string.receive_files,R.string.make_visible,
                    receive,Modifier.fillMaxWidth(),receiving=true,compact=true,
                    busy=state.isAdvertising || state.connectingId!=null)
            } else Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                TransferAction(R.drawable.ic_send,R.string.send_files,R.string.choose_files,
                    openFiles,Modifier.weight(1f).fillMaxHeight())
                TransferAction(R.drawable.ic_receive,R.string.receive_files,R.string.make_visible,
                    receive,Modifier.weight(1f).fillMaxHeight(),receiving=true,
                    busy=state.isAdvertising || state.connectingId!=null)
            }
        }
        if(active) TextButton(resume,Modifier.fillMaxWidth()) {
            AppIcon(R.drawable.ic_progress,Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp));Text(stringResource(R.string.active_session))
        }
        val qrPress=remember { MutableInteractionSource() }
        Surface(onClick=openFiles,modifier=Modifier.then(Modifier.pressScale(qrPress)),
            interactionSource=qrPress,shape=MaterialTheme.shapes.medium,
            color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().heightIn(min=68.dp).padding(12.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconBadge(R.drawable.ic_qr)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.home_qr_title),style=MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.home_qr_hint),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIcon(R.drawable.ic_chevron,Modifier.size(18.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionHeading(stringResource(R.string.home_your_files),
            stringResource(R.string.home_browse_all),openFiles)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns=if(maxWidth<300.dp || LocalDensity.current.fontScale>=1.5f) 2 else 3
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                categories.chunked(columns).forEach { group ->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        group.forEach { CategoryTile(it,Modifier.weight(1f)) }
                    }
                }
            }
        }
        SectionHeading(stringResource(R.string.recent),stringResource(R.string.see_all),seeAll)
        if(state.history.isEmpty()) Surface(shape=MaterialTheme.shapes.medium,
            color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconBadge(R.drawable.ic_history)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.home_no_recent),style=MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.home_recent_hint),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else state.history.take(3).forEach { TransferHistoryItem(it) }
    }
}

@Composable
private fun ConnectionStatus(state:TransferUiState,error:String,action:()->Unit) {
    val text=when {
        state.hasError -> error
        state.progress?.outcome==TransferOutcome.Running -> stringResource(
            if(state.progress.direction=="Sending") R.string.sending else R.string.receiving)
        state.connectedName!=null -> stringResource(R.string.home_connected_to,state.connectedName)
        state.connectingId!=null -> stringResource(R.string.connecting,
            state.peers.firstOrNull { it.id==state.connectingId }?.name ?: stringResource(R.string.nearby_device))
        state.isDiscovering -> stringResource(R.string.searching)
        state.isAdvertising -> stringResource(R.string.waiting_sender)
        else -> stringResource(R.string.home_ready_to_share)
    }
    Surface(onClick=action,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min=68.dp).padding(12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconBadge(if(state.hasError) R.drawable.ic_error else R.drawable.ic_nearby)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(Build.MODEL,style=MaterialTheme.typography.titleSmall,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(Modifier.size(6.dp),shape=CircleShape,color=if(state.hasError)
                        MaterialTheme.colorScheme.error else if(state.connectingId!=null || state.isDiscovering)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) {}
                    Text(text,style=MaterialTheme.typography.bodySmall,maxLines=2,
                        color=if(state.hasError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow=TextOverflow.Ellipsis)
                }
            }
            AppIcon(R.drawable.ic_chevron,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransferAction(@DrawableRes icon:Int,@StringRes title:Int,@StringRes caption:Int,
    action:()->Unit,modifier:Modifier,receiving:Boolean=false,compact:Boolean=false,busy:Boolean=false) {
    val source=remember { MutableInteractionSource() }
    val background=if(receiving) MaterialTheme.colorScheme.secondaryContainer else SwiftDropDesign.Blue
    val ink=if(receiving) MaterialTheme.colorScheme.onSecondaryContainer else Color.White
    Card(onClick=action,modifier=modifier.then(Modifier.pressScale(source)),interactionSource=source,
        shape=MaterialTheme.shapes.medium,colors=CardDefaults.cardColors(containerColor=background)) {
        Column(Modifier.fillMaxWidth().heightIn(min=if(compact) 124.dp else 152.dp).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=CircleShape,color=ink.copy(alpha=.10f)) {
                Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) {
                    if(busy) CircularProgressIndicator(Modifier.size(22.dp),color=ink,strokeWidth=2.dp)
                    else AppIcon(icon,Modifier.size(24.dp),tint=ink)
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(title),style=MaterialTheme.typography.titleMedium,color=ink)
                    Text(stringResource(caption),style=MaterialTheme.typography.bodySmall,
                        color=actionCaptionColor(ink))
                }
                AppIcon(R.drawable.ic_chevron,Modifier.size(18.dp),tint=ink)
            }
        }
    }
}

private data class HomeCategory(val icon:Int,val title:Int,val action:()->Unit)

@Composable
private fun CategoryTile(category:HomeCategory,modifier:Modifier) {
    val source=remember { MutableInteractionSource() }
    Card(onClick=category.action,modifier=modifier.then(Modifier.pressScale(source)),interactionSource=source,
        shape=MaterialTheme.shapes.small,
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().heightIn(min=88.dp).padding(horizontal=4.dp,vertical=12.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CategoryBadge(category.icon,Modifier.size(36.dp))
            Text(stringResource(category.title),style=MaterialTheme.typography.labelMedium,
                textAlign=TextAlign.Center)
        }
    }
}
