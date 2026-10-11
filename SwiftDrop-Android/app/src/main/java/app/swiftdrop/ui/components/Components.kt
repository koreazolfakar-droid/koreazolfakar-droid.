package app.swiftdrop.ui.components

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.HistoryEntry
import app.swiftdrop.R
import java.util.Date
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat as JavaDateFormat
import java.util.Locale

@Composable
fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null,
            tint: Color = MaterialTheme.colorScheme.primary) {
    Icon(painterResource(icon), description, modifier.size(24.dp), tint = tint)
}

@Composable
fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IconBadge(@DrawableRes icon: Int, secondary: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small,
        color = if (secondary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            AppIcon(icon, tint = if (secondary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun EmptyTransfers() {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(R.drawable.ic_history)
            Text(stringResource(R.string.no_transfers), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.no_transfers_caption), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun fileIcon(name: String): Int = when(name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "jpg", "jpeg", "png", "webp", "gif", "heic" -> R.drawable.ic_photos
    "mp4", "mkv", "mov", "webm" -> R.drawable.ic_videos
    "mp3", "wav", "flac", "ogg", "m4a" -> R.drawable.ic_music
    "apk" -> R.drawable.ic_applications
    else -> R.drawable.ic_documents
}

@Composable
fun TransferHistoryItem(entry: HistoryEntry) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(fileIcon(entry.name))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(if (entry.direction == "Sent") R.drawable.ic_send else R.drawable.ic_receive,
                        modifier = Modifier.size(16.dp))
                    Text(stringResource(if (entry.direction == "Sent") R.string.sent else R.string.received) + " · " + entry.peer,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val locale = LocalConfiguration.current.locales[0]
                Text(JavaDateFormat.getDateTimeInstance(JavaDateFormat.MEDIUM,JavaDateFormat.SHORT,locale).format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sizeLabel(entry.bytes), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun sizeLabel(bytes: Long): String = if (bytes < 0) stringResource(R.string.unknown) else android.text.format.Formatter.formatShortFileSize(LocalContext.current,bytes)

@Composable
fun messageText(message: app.swiftdrop.UiMessage): String = stringResource(message.resource,*message.arguments.toTypedArray())
