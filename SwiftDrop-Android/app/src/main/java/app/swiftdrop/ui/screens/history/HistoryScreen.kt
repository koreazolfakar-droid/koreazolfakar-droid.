package app.swiftdrop.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.HistoryEntry
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

@Composable
fun HistoryScreen(history: List<HistoryEntry>) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.history_title),stringResource(R.string.history_caption)) }
        if(history.isEmpty()) item { EmptyTransfers() }
        items(history) { TransferHistoryItem(it) }
    }
}
