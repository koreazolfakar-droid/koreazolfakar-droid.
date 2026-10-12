package app.swiftdrop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.TransferUiState
import app.swiftdrop.ui.components.*

@Composable
fun HomeScreen(state: TransferUiState,send: (Array<String>) -> Unit,receive: () -> Unit,seeAll: () -> Unit,
    resume: () -> Unit,status: String,openFiles: () -> Unit = {send(arrayOf("*/*"))},
    openApps: () -> Unit = {send(arrayOf("application/vnd.android.package-archive"))},onSettings: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        BrandHeader(onSettings)
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    IconBadge(if(state.connectedName!=null) R.drawable.ic_security else R.drawable.ic_nearby)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.connection_status_title),style=MaterialTheme.typography.titleMedium)
                        Text(when {
                            state.connectedName!=null -> stringResource(R.string.home_connected_to,state.connectedName)
                            state.hasError -> stringResource(R.string.home_connection_attention)
                            state.isDiscovering -> stringResource(R.string.searching)
                            state.isAdvertising -> stringResource(R.string.waiting_sender)
                            else -> stringResource(R.string.home_ready_to_share)
                        },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.this_device),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Build.MODEL.take(32),style=MaterialTheme.typography.bodySmall,maxLines=1)
                }
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),maxItemsInEachRow=if(LocalDensity.current.fontScale>=1.3f) 1 else 2) {
            GradientAction(R.drawable.ic_send,stringResource(R.string.send_files),stringResource(R.string.choose_files),openFiles,Modifier.weight(1f).widthIn(min=144.dp))
            GradientAction(R.drawable.ic_receive,stringResource(R.string.receive_files),stringResource(R.string.make_visible),receive,Modifier.weight(1f).widthIn(min=144.dp),cyan=true)
        }
        if(state.isDiscovering || state.isAdvertising || state.connectedName!=null || state.progress!=null || state.selected.isNotEmpty()) {
            OutlinedButton(resume,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                AppIcon(R.drawable.ic_progress);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.active_session))
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=if(LocalDensity.current.fontScale>=1.3f) 1 else 2) {
            Shortcut(R.drawable.ic_folder,R.string.file_library,openFiles,Modifier.weight(1f).widthIn(min=144.dp))
            Shortcut(R.drawable.ic_history,R.string.history,seeAll,Modifier.weight(1f).widthIn(min=144.dp))
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            AssistChip({send(arrayOf("audio/*"))},{Text(stringResource(R.string.music))},leadingIcon={AppIcon(R.drawable.ic_music,tint=categoryColor(R.drawable.ic_music))})
            AssistChip(openApps,{Text(stringResource(R.string.applications))},leadingIcon={AppIcon(R.drawable.ic_applications,tint=categoryColor(R.drawable.ic_applications))})
            AssistChip({send(arrayOf("image/*"))},{Text(stringResource(R.string.photos))},leadingIcon={AppIcon(R.drawable.ic_photos,tint=categoryColor(R.drawable.ic_photos))})
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.recent),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(seeAll,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.see_all))}
        }
        if(state.history.isEmpty()) EmptyTransfers()
        else {
            Text(stringResource(R.string.completed_this_session,state.history.size),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.history.take(3).forEach {TransferHistoryItem(it)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_security,Modifier.size(18.dp))
            Text(stringResource(R.string.private_caption),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(openFiles,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            AppIcon(R.drawable.ic_qr,Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.qr_choose_files))
        }
    }
}

@Composable
private fun Shortcut(icon: Int,label: Int,action: () -> Unit,modifier: Modifier) {
    Card(action,modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AppIcon(icon)
            Text(stringResource(label),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
