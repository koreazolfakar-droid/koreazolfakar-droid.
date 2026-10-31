package app.swiftdrop.ui.screens.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

/** Explain recovery without falsely suggesting a permission is already granted. */
@Composable
fun PermissionsScreen(send: () -> Unit, receive: () -> Unit, openSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconBadge(R.drawable.ic_security)
                Text(
                    stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.permissions_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(send, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    AppIcon(R.drawable.ic_send, tint = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.retry_send))
                }
                OutlinedButton(receive, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    AppIcon(R.drawable.ic_receive, tint = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.receive_files))
                }
                TextButton(openSettings, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}
