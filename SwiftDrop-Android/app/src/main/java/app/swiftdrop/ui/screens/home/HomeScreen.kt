package app.swiftdrop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.TransferUiState
import app.swiftdrop.ui.components.*

@Composable
fun HomeScreen(state: TransferUiState, send: (Array<String>) -> Unit, receive: () -> Unit,
               seeAll: () -> Unit, resume: () -> Unit, status: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), null,
                    Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("SwiftDrop", style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (state.connectedName != null) R.drawable.ic_security else R.drawable.ic_nearby)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.connection_status), style = MaterialTheme.typography.labelLarge)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // FlowRow stacks actions on narrow windows and at large font scales.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2) {
            ActionCard(R.drawable.ic_send, stringResource(R.string.send_files), stringResource(R.string.choose_files),
                Modifier.weight(1f).widthIn(min = 145.dp)) { send(arrayOf("*/*")) }
            ActionCard(R.drawable.ic_receive, stringResource(R.string.receive_files), stringResource(R.string.make_visible),
                Modifier.weight(1f).widthIn(min = 145.dp), secondary = true, onClick = receive)
        }
        if (state.isDiscovering || state.isAdvertising || state.connectedName != null || state.progress != null || state.selected.isNotEmpty()) {
            OutlinedButton(resume, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                AppIcon(R.drawable.ic_progress)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.active_session))
            }
        }
        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(R.drawable.ic_photos,R.string.photos,arrayOf("image/*")),
                Triple(R.drawable.ic_videos,R.string.videos,arrayOf("video/*")),
                Triple(R.drawable.ic_applications,R.string.applications,arrayOf("application/vnd.android.package-archive")),
                Triple(R.drawable.ic_documents,R.string.documents,arrayOf("application/pdf","text/*","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                Triple(R.drawable.ic_music,R.string.music,arrayOf("audio/*")),
                Triple(R.drawable.ic_folder,R.string.all_files,arrayOf("*/*"))).forEach { (icon,label,mime) ->
                OutlinedButton({ send(mime) }, Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    AppIcon(icon)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label))
                }
            }
        }
        Text(stringResource(R.string.apk_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_security, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.private_sharing), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(stringResource(R.string.private_caption), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(seeAll, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.see_all)) }
        }
        if (state.history.isEmpty()) EmptyTransfers() else state.history.take(3).forEach { TransferHistoryItem(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_qr, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.qr_upcoming), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(icon: Int, title: String, caption: String, modifier: Modifier,
                       secondary: Boolean = false, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (secondary) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primary, contentColor = if (secondary) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimary)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(icon, tint = LocalContentColor.current)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(caption, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
