package app.swiftdrop.ui.screens.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.UltraHotspotPhase
import app.swiftdrop.UltraHotspotState
import app.swiftdrop.UltraLanState
import app.swiftdrop.UltraLanPhase
import app.swiftdrop.ui.screens.qr.ReceiverQrCard

/**
 * Hotspot setup UI only: the native socket transport has not yet been connected.
 * Do not display fabricated throughput or imply that Nearby uses this AP.
 */
@Composable
fun UltraHotspotPanel(
    hotspot: UltraHotspotState,
    start: () -> Unit,
    stop: () -> Unit,
    transport: UltraLanState = UltraLanState(),
    inviteQr: String? = null
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                app.swiftdrop.ui.components.IconBadge(R.drawable.ic_nearby)
                Text(
                    stringResource(R.string.ultra_hotspot_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                stringResource(R.string.ultra_hotspot_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            when (hotspot.phase) {
                UltraHotspotPhase.Idle -> Button(
                    onClick = start,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.ultra_hotspot_start)) }
                UltraHotspotPhase.Starting -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(stop, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ultra_hotspot_stop))
                    }
                }
                UltraHotspotPhase.Active -> {
                    Text(stringResource(R.string.ultra_hotspot_active), style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.ultra_hotspot_ssid, hotspot.ssid))
                            Text(stringResource(R.string.ultra_hotspot_password, hotspot.passphrase))
                        }
                    }
                    if (inviteQr != null) {
                        ReceiverQrCard(inviteQr,R.string.ultra_qr_title,R.string.ultra_qr_hint)
                    }
                    if (transport.phase == UltraLanPhase.Receiving || transport.phase == UltraLanPhase.Complete) {
                        Text(stringResource(R.string.ultra_status_receiving),
                            style = MaterialTheme.typography.titleSmall)
                        transport.progress?.let { p ->
                            LinearProgressIndicator(progress = { p.fraction.coerceIn(0f, 1f) },modifier = Modifier.fillMaxWidth())
                            Text("${p.percent}% — ${p.bytes} / ${p.total} bytes")
                        }
                    }
                    Text(
                        stringResource(R.string.ultra_hotspot_join),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(stop, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ultra_hotspot_stop))
                    }
                }
                UltraHotspotPhase.Failed -> {
                    Text(
                        stringResource(R.string.ultra_hotspot_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(stop, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ultra_hotspot_use_nearby))
                    }
                }
            }
            transport.error?.let { error ->
                Text(error,color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.ultra_hotspot_preview_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
