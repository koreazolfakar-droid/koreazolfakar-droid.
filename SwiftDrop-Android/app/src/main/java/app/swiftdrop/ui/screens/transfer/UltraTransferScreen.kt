package app.swiftdrop.ui.screens.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.UltraLanPhase
import app.swiftdrop.UltraLanState
import app.swiftdrop.ui.components.PageHeading

@Composable
fun UltraTransferScreen(state: UltraLanState, cancel: () -> Unit) {
    val label=when (state.phase) {
        UltraLanPhase.Idle -> R.string.ultra_status_idle
        UltraLanPhase.Waiting -> R.string.ultra_status_waiting
        UltraLanPhase.Joining -> R.string.ultra_status_joining
        UltraLanPhase.Connecting -> R.string.ultra_status_connecting
        UltraLanPhase.Sending -> R.string.ultra_status_sending
        UltraLanPhase.Receiving -> R.string.ultra_status_receiving
        UltraLanPhase.Complete -> R.string.ultra_status_complete
        UltraLanPhase.Failed -> R.string.ultra_status_failed
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp)) {
        PageHeading(stringResource(R.string.ultra_hotspot_title),stringResource(R.string.ultra_transfer_caption))
        Text(stringResource(label),style=MaterialTheme.typography.headlineSmall)
        if(state.phase in setOf(UltraLanPhase.Joining,UltraLanPhase.Connecting)) {
            LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        }
        state.progress?.let { p ->
            Text(p.name,style=MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress={ p.fraction },modifier=Modifier.fillMaxWidth())
            Text("${p.percent}% — ${p.bytes} / ${p.total} bytes",
                style=MaterialTheme.typography.bodyMedium)
            p.measurement.bytesPerSecond?.let { speed ->
                Text(stringResource(R.string.ultra_measured_speed,speed / (1024*1024)),
                    style=MaterialTheme.typography.bodyMedium)
            }
        }
        state.error?.let {
            Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        }
        Button(cancel,Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(if(state.phase in setOf(UltraLanPhase.Complete,UltraLanPhase.Failed))
                R.string.done else R.string.cancel))
        }
    }
}
