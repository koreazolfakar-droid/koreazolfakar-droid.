package app.swiftdrop.ui.screens.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

@Composable
fun TransferScreen(state: TransferUiState, disconnect: () -> Unit) {
    val p = state.progress
    val completed = p?.outcome == TransferOutcome.Complete
    val terminal = completed || p?.outcome == TransferOutcome.Failed || p?.outcome == TransferOutcome.Cancelled
    val status = when(p?.outcome) {
        TransferOutcome.Complete -> R.string.complete
        TransferOutcome.Failed -> R.string.failed
        TransferOutcome.Cancelled -> R.string.cancelled
        TransferOutcome.Saving -> R.string.saving
        TransferOutcome.Running -> R.string.running
        null -> R.string.waiting
    }
    val animated by animateFloatAsState(p?.fraction ?: 0f,tween(SwiftDropDesign.MotionMillis),label="transfer progress")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth()) { PageHeading(stringResource(R.string.file_transfer),state.connectedName ?: stringResource(R.string.nearby_device)) }
        Box(Modifier.sizeIn(maxWidth=208.dp,maxHeight=208.dp).size(208.dp),contentAlignment=Alignment.Center) {
            if(p?.outcome==TransferOutcome.Running || p?.outcome==TransferOutcome.Saving) {
                val motion=rememberInfiniteTransition(label="active transfer")
                val rotation by motion.animateFloat(0f,360f,infiniteRepeatable(tween(2400,easing=LinearEasing)),label="transfer rotation")
                val accent=MaterialTheme.colorScheme.secondary
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(accent.copy(alpha=.7f),rotation,42f,false,style=Stroke(2.dp.toPx()))
                }
            }
            if(p==null || (p.total<=0 && !terminal)) CircularProgressIndicator(Modifier.fillMaxSize(),strokeWidth=8.dp)
            else CircularProgressIndicator(progress={if(completed) 1f else animated},modifier=Modifier.fillMaxSize().padding(8.dp),strokeWidth=8.dp,
                trackColor=MaterialTheme.colorScheme.primaryContainer,
                color=if(p.outcome==TransferOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                AnimatedVisibility(completed) { AppIcon(R.drawable.ic_check,description=stringResource(R.string.complete),modifier=Modifier.size(40.dp)) }
                if(!completed && p!=null && p.total>0) Text("${p.percent}%",style=MaterialTheme.typography.headlineLarge)
                if(p?.outcome==TransferOutcome.Running) Text(stringResource(if(p.direction=="Sending") R.string.sending else R.string.receiving),
                    style=MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(status),style=MaterialTheme.typography.titleLarge,
            color=if(p?.outcome==TransferOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(p?.name ?: stringResource(R.string.ready_files),style=MaterialTheme.typography.titleMedium)
        Text(messageText(state.status),style=MaterialTheme.typography.bodyMedium,
            color=if(state.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Detail(stringResource(R.string.transferred),sizeLabel(p?.bytes ?: 0)+" / "+sizeLabel(p?.total ?: -1))
                if(p!=null && !terminal && p.outcome==TransferOutcome.Running) {
                    Detail(stringResource(R.string.speed),p.measurement.bytesPerSecond?.let { stringResource(R.string.speed_value,sizeLabel(it.toLong())) }
                        ?: stringResource(R.string.measuring))
                    p.measurement.remainingSeconds?.let { Detail(stringResource(R.string.remaining),pluralStringResource(R.plurals.eta_seconds,it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),it)) }
                }
                Detail(stringResource(R.string.connection),stringResource(R.string.engine_value))
            }
        }
        OutlinedButton(disconnect,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(if(completed) R.string.done else if(terminal || p==null) R.string.disconnect else R.string.cancel_transfer))
        }
    }
}

@Composable
private fun Detail(label: String,value: String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
