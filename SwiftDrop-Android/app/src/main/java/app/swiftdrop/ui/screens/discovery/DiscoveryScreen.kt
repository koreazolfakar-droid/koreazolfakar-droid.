package app.swiftdrop.ui.screens.discovery

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

@Composable
fun DiscoveryRadar(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "discovery")
    val pulse = if (active) transition.animateFloat(.65f,1f,
        infiniteRepeatable(tween(SwiftDropDesign.RadarMillis),RepeatMode.Reverse),label="pulse").value else .8f
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(12.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.size(172.dp)) {
            val center = Offset(size.width/2,size.height/2)
            listOf(1f,.75f,.5f).forEach {ratio ->
                drawCircle(color.copy(alpha=.045f+.055f*(1-ratio)*pulse),size.minDimension/2*ratio,center)
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.14f*pulse),color.copy(alpha=0f))),size.minDimension/2*pulse,center)
            if(active) drawArc(color.copy(alpha=.35f*pulse),-90f,90f*pulse,false,style=Stroke(2.dp.toPx()))
        }
        IconBadge(R.drawable.ic_nearby)
    }
}

@Composable
fun DiscoveryScreen(state: TransferUiState, connect: (Peer) -> Unit, retry: () -> Unit, cancel: () -> Unit, openRecoverySettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { PageHeading(stringResource(R.string.nearby_devices),stringResource(R.string.discovery_caption)) }
        item { DiscoveryRadar(state.isDiscovering) }
        item { if(state.hasError) ErrorNotice(messageText(state.status)) else Text(messageText(state.status),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(state.peers.isEmpty()) item {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppIcon(if(state.hasError) R.drawable.ic_error else R.drawable.ic_nearby)
                    Text(stringResource(if(state.hasError) R.string.discovery_unavailable else R.string.no_devices),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.discovery_caption),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(state.peers,key={it.id}) { peer ->
            Card(onClick={connect(peer)},enabled=state.connectingId==null && state.pending==null,
                colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IconBadge(R.drawable.ic_nearby)
                    Column(Modifier.weight(1f)) {
                        Text(peer.name,style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(if(state.connectingId==peer.id) R.string.connecting else R.string.available,peer.name),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(state.connectingId==peer.id) CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                }
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if (state.hasError || !state.isDiscovering) Button(retry,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    AppIcon(R.drawable.ic_retry,tint=LocalContentColor.current);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.retry))
                }
                if(state.hasError && state.recoveryAction != NearbyRecoveryAction.None) {
                    FilledTonalButton(openRecoverySettings,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(stringResource(if(state.recoveryAction == NearbyRecoveryAction.LocationSettings)
                            R.string.open_location_settings else R.string.open_settings))
                    }
                }
                OutlinedButton(cancel,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun ReceiveScreen(state: TransferUiState, retry: () -> Unit, cancel: () -> Unit, openRecoverySettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { PageHeading(stringResource(R.string.receive_files),stringResource(R.string.receive_caption)) }
        item { DiscoveryRadar(state.isAdvertising) }
        item { Text(stringResource(when {state.hasError -> R.string.receive_unavailable;state.isAdvertising -> R.string.waiting_sender;else -> R.string.making_visible}),style=MaterialTheme.typography.titleLarge) }
        if(state.isAdvertising) item {Text(stringResource(R.string.device_visible,android.os.Build.MODEL),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        item { if(state.hasError) ErrorNotice(messageText(state.status)) else Text(messageText(state.status),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    AppIcon(R.drawable.ic_security,tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.security_hint),color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(state.hasError) Button(retry,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.retry)) }
                if(state.hasError && state.recoveryAction != NearbyRecoveryAction.None) {
                    FilledTonalButton(openRecoverySettings,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(stringResource(if(state.recoveryAction == NearbyRecoveryAction.LocationSettings)
                            R.string.open_location_settings else R.string.open_settings))
                    }
                }
                OutlinedButton(cancel,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.stop_receiving)) }
            }
        }
    }
}

@Composable
fun SelectionScreen(state: TransferUiState, discover: () -> Unit, chooseAgain: () -> Unit) {
    val known = !state.loadingSelection && state.selectedFiles.size==state.selected.size && state.selectedFiles.all { it.bytes >= 0 }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.selection_title),stringResource(R.string.selection_caption)) }
        item { Text(pluralStringResource(R.plurals.selected_summary,state.selected.size,state.selected.size,
            if(known) sizeLabel(state.selectedFiles.sumOf { it.bytes }) else stringResource(R.string.unknown)),
            style=MaterialTheme.typography.titleMedium) }
        if(state.loadingSelection) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(state.hasError) item { Text(messageText(state.status),color=MaterialTheme.colorScheme.error) }
        items(state.selectedFiles,key={it.uri.toString()}) { file ->
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    CategoryBadge(fileIcon(file.name))
                    Column(Modifier.weight(1f)) {
                        Text(file.name,style=MaterialTheme.typography.titleMedium)
                        Text(sizeLabel(file.bytes),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(discover,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.loadingSelection && !state.hasError && state.selectedFiles.isNotEmpty()) {
                    AppIcon(R.drawable.ic_nearby,tint=LocalContentColor.current);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.find_receiver))
                }
                OutlinedButton(chooseAgain,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.change_selection)) }
            }
        }
    }
}
