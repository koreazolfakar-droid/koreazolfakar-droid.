package app.swiftdrop.ui.screens.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.UltraHotspotPhase
import app.swiftdrop.UltraHotspotState

/**
 * Hotspot setup UI only: the native socket transport has not yet been connected.
 * Do not display fabricated throughput or imply that Nearby uses this AP.
 */
@Composable
fun UltraHotspotPanel(
    hotspot: UltraHotspotState,
    start: () -> Unit,
    stop: () -> Unit
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.ultra_hotspot_title), style = MaterialTheme.typography.titleMedium)
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
            Text(
                stringResource(R.string.ultra_hotspot_preview_notice),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
