package app.swiftdrop.ui.screens.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

@Composable
fun TransferScreen(state: TransferUiState,disconnect: () -> Unit,sendMore: (() -> Unit)? = null,history: (() -> Unit)? = null) {
    val p=state.progress
    val complete=p?.outcome==TransferOutcome.Complete
    val terminal=complete || p?.outcome==TransferOutcome.Failed || p?.outcome==TransferOutcome.Cancelled
    val status=when(p?.outcome) {
        TransferOutcome.Complete -> R.string.complete;TransferOutcome.Failed -> R.string.failed
        TransferOutcome.Cancelled -> R.string.cancelled;TransferOutcome.Saving -> R.string.saving
        TransferOutcome.Running -> if(p.direction=="Sending") R.string.sending else R.string.receiving
        null -> R.string.waiting
    }
    val progress by animateFloatAsState(p?.fraction ?: 0f,tween(SwiftDropDesign.MotionMillis),label="measured progress")
    val success=MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth()) {PageHeading(stringResource(R.string.file_transfer),state.connectedName ?: stringResource(R.string.nearby_device))}
        Box(Modifier.size(184.dp),contentAlignment=Alignment.Center) {
            if(p?.outcome==TransferOutcome.Running || p?.outcome==TransferOutcome.Saving) {
                val animation=rememberInfiniteTransition(label="active file")
                val rotation by animation.animateFloat(0f,360f,infiniteRepeatable(tween(2400,easing=LinearEasing)),label="activity arc")
                val accent=MaterialTheme.colorScheme.secondary
                Canvas(Modifier.fillMaxSize()) {drawArc(accent.copy(alpha=.7f),rotation,42f,false,style=Stroke(2.dp.toPx()))}
            }
            if(p==null || (p.total<=0 && !terminal)) CircularProgressIndicator(Modifier.fillMaxSize().padding(8.dp),strokeWidth=7.dp)
            else CircularProgressIndicator(progress={if(complete) 1f else progress},modifier=Modifier.fillMaxSize().padding(8.dp),strokeWidth=7.dp,
                color=if(p.outcome==TransferOutcome.Failed) MaterialTheme.colorScheme.error else if(complete) success else MaterialTheme.colorScheme.primary,
                trackColor=MaterialTheme.colorScheme.primaryContainer)
            Column {
                AnimatedVisibility(complete) {
                    Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.tertiaryContainer) {
                        AppIcon(R.drawable.ic_check,Modifier.padding(20.dp).size(48.dp),description=stringResource(R.string.complete),tint=success)
                    }
                }
            }
            if(!complete && p!=null && p.total>0) Text("${p.percent}%",style=MaterialTheme.typography.headlineLarge)
        }
        Text(stringResource(status),style=MaterialTheme.typography.titleLarge,color=if(p?.outcome==TransferOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(sizeLabel(p?.bytes ?: 0)+" / "+sizeLabel(p?.total ?: -1),style=MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.per_file),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CategoryBadge(fileIcon(p?.name ?: ""))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(p?.name ?: stringResource(R.string.ready_files),style=MaterialTheme.typography.titleMedium)
                    Text(state.connectedName ?: stringResource(R.string.nearby_device),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(p!=null && !terminal && p.outcome==TransferOutcome.Running) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),maxItemsInEachRow=if(LocalDensity.current.fontScale>=1.3f) 1 else 2) {
                Metric(stringResource(R.string.speed),p.measurement.bytesPerSecond?.let {stringResource(R.string.speed_value,sizeLabel(it.toLong()))} ?: stringResource(R.string.measuring),Modifier.weight(1f).widthIn(min=144.dp))
                p.measurement.remainingSeconds?.let {
                    Metric(stringResource(R.string.remaining),pluralStringResource(R.plurals.eta_seconds,it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),it),Modifier.weight(1f).widthIn(min=144.dp))
                }
            }
        }
        if(state.hasError) ErrorNotice(messageText(state.status))
        else Text(messageText(state.status),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.engine_value),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(complete && sendMore!=null) Button(sendMore,Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.send_more))}
        if(complete && history!=null) OutlinedButton(history,Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.view_history))}
        TextButton(disconnect,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(if(complete) R.string.done else if(terminal || p==null) R.string.disconnect else R.string.cancel_transfer))
        }
    }
}

@Composable
private fun Metric(label: String,value: String,modifier: Modifier) {
    Surface(modifier,shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(value,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
