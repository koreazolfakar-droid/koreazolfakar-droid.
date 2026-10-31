package app.swiftdrop.ui.screens.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.UltraLanPhase
import app.swiftdrop.UltraLanState
import app.swiftdrop.ui.components.ErrorNotice
import app.swiftdrop.ui.components.PageHeading
import app.swiftdrop.ui.components.SignalOrbit
import app.swiftdrop.ui.components.sizeLabel

/** A measured SwiftMotion experience for Ultra, sharing the same language as Nearby. */
@Composable
fun UltraTransferScreen(state: UltraLanState, cancel: () -> Unit) {
    val label = when (state.phase) {
        UltraLanPhase.Idle -> R.string.ultra_status_idle
        UltraLanPhase.Waiting -> R.string.ultra_status_waiting
        UltraLanPhase.Joining -> R.string.ultra_status_joining
        UltraLanPhase.Connecting -> R.string.ultra_status_connecting
        UltraLanPhase.Sending -> R.string.ultra_status_sending
        UltraLanPhase.Receiving -> R.string.ultra_status_receiving
        UltraLanPhase.Complete -> R.string.ultra_status_complete
        UltraLanPhase.Failed -> R.string.ultra_status_failed
    }
    val terminal = state.phase == UltraLanPhase.Complete || state.phase == UltraLanPhase.Failed
    val active = state.phase != UltraLanPhase.Idle && !terminal
    val progress = state.progress
    val fraction = if (state.phase == UltraLanPhase.Complete) 1f
        else progress?.takeIf { it.total > 0 }?.fraction

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeading(
                stringResource(R.string.ultra_hotspot_title),
                stringResource(R.string.ultra_transfer_caption)
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SignalOrbit(
                        fraction = fraction,
                        active = active,
                        complete = state.phase == UltraLanPhase.Complete,
                        failed = state.phase == UltraLanPhase.Failed,
                        modifier = Modifier.size(174.dp)
                    ) {
                        Text(
                            if (progress != null && progress.total > 0) "${progress.percent}%"
                            else if (state.phase == UltraLanPhase.Complete) "100%" else "—",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            progress?.let { p ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            p.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sizeLabel(p.bytes), style = MaterialTheme.typography.labelMedium)
                            Text(
                                sizeLabel(p.total), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (p.total > 0) {
                            LinearProgressIndicator(
                                progress = { p.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        p.measurement.bytesPerSecond?.let { speed ->
                            Text(
                                stringResource(
                                    R.string.ultra_measured_speed, speed / (1024 * 1024)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            state.error?.let { ErrorNotice(it) }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Button(
                onClick = cancel,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                    .heightIn(min = 52.dp)
            ) {
                Text(
                    stringResource(
                        if (terminal) R.string.done else R.string.cancel
                    )
                )
            }
        }
    }
}
