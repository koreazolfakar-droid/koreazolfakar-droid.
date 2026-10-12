package app.swiftdrop.ui.screens.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

/** A measured, never simulated transfer dashboard. All values come from the live payload. */
@Composable
fun TransferScreen(state: TransferUiState,disconnect: () -> Unit,sendMore: (() -> Unit)? = null,history: (() -> Unit)? = null) {
    val p = state.progress
    val complete = p?.outcome == TransferOutcome.Complete
    val failed = p?.outcome == TransferOutcome.Failed || p?.outcome == TransferOutcome.Cancelled
    val terminal = complete || failed
    val working = p?.outcome == TransferOutcome.Running || p?.outcome == TransferOutcome.Saving
    val status = when(p?.outcome) {
        TransferOutcome.Complete -> R.string.complete
        TransferOutcome.Failed -> R.string.failed
        TransferOutcome.Cancelled -> R.string.cancelled
        TransferOutcome.Saving -> R.string.saving
        TransferOutcome.Running -> if(p.direction == "Sending") R.string.sending else R.string.receiving
        null -> R.string.waiting
    }
    // The gate in the engine limits recompositions while this animation smooths visible updates.
    val progress by animateFloatAsState(
        targetValue = if(complete) 1f else p?.fraction ?: 0f,
        animationSpec = tween(SwiftDropDesign.MotionMillis),label="file progress")
    val success = MaterialTheme.colorScheme.tertiary

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth()) {
            PageHeading(stringResource(if(p?.direction=="Sending") R.string.send_files else if(p?.direction=="Receiving") R.string.receive_files else R.string.file_transfer),state.connectedName ?: stringResource(R.string.nearby_device))
        }

        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconBadge(R.drawable.ic_security)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.connected_device),style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.connectedName ?: stringResource(R.string.nearby_device),
                        style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(state.connectedName != null && !failed) {
                    Text(stringResource(R.string.code_confirmed),style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.tertiary)
                }
            }
        }

        Box(Modifier.size(152.dp),contentAlignment=Alignment.Center) {
            if(p==null || (p.total <= 0 && !terminal)) {
                CircularProgressIndicator(Modifier.fillMaxSize().padding(9.dp),strokeWidth=7.dp)
            } else {
                CircularProgressIndicator(progress={progress},modifier=Modifier.fillMaxSize().padding(9.dp),
                    strokeWidth=7.dp,
                    color=if(failed) MaterialTheme.colorScheme.error else if(complete) success else MaterialTheme.colorScheme.primary,
                    trackColor=MaterialTheme.colorScheme.primaryContainer)
            }
            if(complete) {
                Surface(shape=androidx.compose.foundation.shape.CircleShape,
                    color=MaterialTheme.colorScheme.tertiaryContainer) {
                    AppIcon(R.drawable.ic_check,Modifier.padding(16.dp).size(44.dp),
                        description=stringResource(R.string.complete),tint=success)
                }
            } else {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(p!=null && p.total>0) "${p.percent}%" else "—",
                        style=MaterialTheme.typography.headlineLarge)
                    Text(stringResource(if(p!=null && p.total>0) R.string.per_file else R.string.measuring),
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text(stringResource(status),style=MaterialTheme.typography.titleLarge,
            color=if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)

        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(fileIcon(p?.name ?: ""))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(p?.name ?: stringResource(R.string.ready_files),
                            style=MaterialTheme.typography.titleMedium,maxLines=2,
                            overflow=TextOverflow.Ellipsis)
                        Text(stringResource(R.string.per_file),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(sizeLabel(p?.bytes ?: 0),style=MaterialTheme.typography.labelMedium)
                    Text(sizeLabel(p?.total ?: -1),style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(p!=null && p.total>0) {
                    LinearProgressIndicator(progress={if(complete) 1f else progress},
                        modifier=Modifier.fillMaxWidth(),color=if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                } else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            }
        }

        if(working) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                DashboardMetric(R.drawable.ic_progress,stringResource(R.string.speed),
                    p?.measurement?.bytesPerSecond?.let {
                        stringResource(R.string.speed_value,sizeLabel(it.toLong()))
                    } ?: stringResource(R.string.measuring),Modifier.weight(1f))
                DashboardMetric(R.drawable.ic_history,stringResource(R.string.remaining),
                    p?.measurement?.remainingSeconds?.let {
                        pluralStringResource(R.plurals.eta_seconds,it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),it)
                    } ?: stringResource(R.string.estimating),Modifier.weight(1f))
            }
        }

        if(p != null && p.direction=="Sending" && p.fileCount > 1) {
            Text(stringResource(R.string.transfer_queue_position,p.fileIndex,p.fileCount),
                style=MaterialTheme.typography.titleMedium,
                color=MaterialTheme.colorScheme.primary)
        }
        if(state.selectedFiles.size>1 && p?.direction=="Sending") {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=240.dp),contentPadding=PaddingValues(12.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.selectedFiles,key={_,file -> file.uri.toString()}) { index,file ->
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            CategoryBadge(fileIcon(file.name),Modifier.size(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(sizeLabel(file.bytes),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(index+1==p.fileIndex && working) AppIcon(R.drawable.ic_progress,Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        if(state.selected.isNotEmpty() && p?.direction=="Sending") {
            Text(stringResource(R.string.selected_for_transfer,state.selected.size),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.hasError) ErrorNotice(messageText(state.status))
        else Text(messageText(state.status),style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AppIcon(R.drawable.ic_security,Modifier.size(18.dp))
            Text(stringResource(R.string.transfer_security_note),Modifier.weight(1f),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }

    }
    Surface(color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if((complete || failed) && sendMore!=null) Button(sendMore,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(if(failed) R.string.retry_send else R.string.send_more))
        }
        if(complete && history!=null) OutlinedButton(history,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(R.string.view_history))
        }
        OutlinedButton(disconnect,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(if(complete) R.string.done else if(terminal || p==null) R.string.disconnect else R.string.cancel_transfer))
        }
        }
    }
    }
}

@Composable
private fun DashboardMetric(icon: Int,label: String,value: String,modifier: Modifier) {
    Surface(modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            AppIcon(icon,Modifier.size(20.dp))
            Text(value,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
