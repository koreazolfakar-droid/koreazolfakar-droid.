package app.swiftdrop.ui.screens.discovery

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import app.swiftdrop.ui.screens.qr.ReceiverQrCard

// SwiftMotion: one recognisable orbit for sending and receiving, driven by discovery state.
@Composable
fun DiscoveryRadar(active: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 116.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SignalOrbit(fraction = null, active = active, modifier = Modifier.size(84.dp)) {
                AppIcon(
                    R.drawable.ic_nearby, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(if (active) R.string.nearby_active else R.string.nearby_devices),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(if (active) R.string.searching else R.string.discovery_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun DiscoveryScreen(state: TransferUiState, connect: (Peer) -> Unit, retry: () -> Unit, cancel: () -> Unit, openRecoverySettings: () -> Unit) {
    val report = state.diagnostics
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { PageHeading(stringResource(R.string.nearby_devices),stringResource(R.string.discovery_caption)) }
        item { DiscoveryRadar(state.isDiscovering) }
        item { if(state.hasError) ErrorNotice(messageText(state.status)) else Text(messageText(state.status),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(state.hasError && report != null) item {
            DiagnosticDetails(report)
        }
        if(state.peers.isEmpty()) item {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppIcon(if(state.hasError) R.drawable.ic_error else R.drawable.ic_nearby)
                    Text(stringResource(if(state.hasError) R.string.discovery_unavailable else R.string.no_devices),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.discovery_caption),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(state.peers,key={it.id}) { peer ->
            Card(onClick={connect(peer)},enabled=state.connectingId==null && state.pending==null,
                shape=MaterialTheme.shapes.large,
                colors=CardDefaults.cardColors(containerColor=if(state.connectingId==peer.id)
                    MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IconBadge(R.drawable.ic_nearby)
                    Column(Modifier.weight(1f)) {
                        Text(peer.name,style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(if(state.connectingId==peer.id) R.string.connecting else R.string.available,peer.name),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AnimatedVisibility(visible=state.connectingId==peer.id) {
                        CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
                    }
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
fun ReceiveScreen(
    state: TransferUiState,
    retry: () -> Unit,
    cancel: () -> Unit,
    openRecoverySettings: () -> Unit,
    hotspot: UltraHotspotState = UltraHotspotState(),
    startUltra: () -> Unit = {},
    stopUltra: () -> Unit = {},
    transport: UltraLanState = UltraLanState(),
    inviteQr: String? = null
) {
    val report = state.diagnostics
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageHeading(stringResource(R.string.receive_files),stringResource(R.string.receive_caption)) }
        item { UltraHotspotPanel(hotspot, startUltra, stopUltra, transport, inviteQr) }
        if (hotspot.phase == UltraHotspotPhase.Idle) {
        item { DiscoveryRadar(state.isAdvertising) }
        item { Text(stringResource(when {state.hasError -> R.string.receive_unavailable;state.isAdvertising -> R.string.waiting_sender;else -> R.string.making_visible}),style=MaterialTheme.typography.titleLarge) }
        if(state.isAdvertising) item {Text(stringResource(R.string.device_visible,android.os.Build.MODEL),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        if(state.isAdvertising && state.receiverQrPayload != null) item {
            ReceiverQrCard(requireNotNull(state.receiverQrPayload))
        }
        item { if(state.hasError) ErrorNotice(messageText(state.status)) else Text(messageText(state.status),color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(state.hasError && report!=null) item { DiagnosticDetails(report) }
        item {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AppIcon(R.drawable.ic_security,tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.security_hint),Modifier.weight(1f),
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
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
}

@Composable
fun SelectionScreen(state: TransferUiState, discover: () -> Unit, chooseAgain: () -> Unit,
    scanQr: () -> Unit, scanUltra: () -> Unit = {}) {
    val known = !state.loadingSelection && state.selectedFiles.size==state.selected.size && state.selectedFiles.all { it.bytes >= 0 }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.selection_title),stringResource(R.string.selection_caption)) }
        item { Text(pluralStringResource(R.plurals.selected_summary,state.selected.size,state.selected.size,
            if(known) sizeLabel(state.selectedFiles.sumOf { it.bytes }) else stringResource(R.string.unknown)),
            style=MaterialTheme.typography.titleMedium) }
        if(state.loadingSelection) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(state.hasError) item { Text(messageText(state.status),color=MaterialTheme.colorScheme.error) }
        items(state.selectedFiles,key={it.uri.toString()}) { file ->
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    app.swiftdrop.ui.screens.files.FilePreviewBadge(
                        file.uri,file.name,Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                OutlinedButton(scanQr,Modifier.fillMaxWidth().heightIn(min=48.dp),
                    enabled=!state.loadingSelection && !state.hasError && state.selectedFiles.isNotEmpty()) {
                    AppIcon(R.drawable.ic_qr,tint=LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_scan))
                }
                OutlinedButton(scanUltra,Modifier.fillMaxWidth().heightIn(min=48.dp),
                    enabled=!state.loadingSelection && !state.hasError && state.selectedFiles.isNotEmpty()) {
                    Text(stringResource(R.string.ultra_scan_qr))
                }
                OutlinedButton(chooseAgain,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.change_selection)) }
            }
        }
    }
}

@Composable
private fun DiagnosticDetails(report: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(if(expanded) R.string.hide_diagnostics else R.string.show_diagnostics))
            }
            if(expanded) {
                Text(report,style=MaterialTheme.typography.bodySmall)
                OutlinedButton({clipboard.setText(AnnotatedString(report))},
                    Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(stringResource(R.string.copy_diagnostics))
                }
            }
        }
    }
}
