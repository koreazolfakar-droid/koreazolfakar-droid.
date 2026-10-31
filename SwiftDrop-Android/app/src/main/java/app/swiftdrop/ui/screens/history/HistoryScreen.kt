package app.swiftdrop.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.HistoryEntry
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

/** SwiftMotion history: scannable, accessible and still driven by local transfer records. */
@Composable
fun HistoryScreen(history: List<HistoryEntry>) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(history, filter, query) {
        history.filter {
            (filter == "All" || it.direction == filter) &&
                (it.name.contains(query, true) || it.peer.contains(query, true))
        }
    }
    val totalBytes = remember(history) { history.sumOf { it.bytes.coerceAtLeast(0) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeading(
                stringResource(R.string.history_title),
                stringResource(R.string.history_caption)
            )
        }
        if (history.isNotEmpty()) item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(18.dp)) {
                    val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HistoryStat(history.size.toString(), stringResource(R.string.history_files_count))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            HistoryStat(sizeLabel(totalBytes), stringResource(R.string.history_total_size))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1f)) {
                                HistoryStat(history.size.toString(), stringResource(R.string.history_files_count))
                            }
                            Box(Modifier.weight(1f)) {
                                HistoryStat(sizeLabel(totalBytes), stringResource(R.string.history_total_size))
                            }
                        }
                    }
                }
            }
        }
        item {
            SwiftSearchField(
                query, { query = it }, stringResource(R.string.search_history),
                Modifier.fillMaxWidth()
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("All" to R.string.all, "Sent" to R.string.sent,
                    "Received" to R.string.received).forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(stringResource(label)) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }
        if (history.isEmpty()) {
            item { EmptyTransfers() }
        } else if (shown.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        stringResource(R.string.no_matches),
                        Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(shown) { TransferHistoryItem(it) }
        }
    }
}

@Composable
private fun HistoryStat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            value, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
