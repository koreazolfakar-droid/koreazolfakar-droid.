package app.swiftdrop.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.HistoryEntry
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

@Composable
fun HistoryScreen(history: List<HistoryEntry>) {
    var filter by rememberSaveable {mutableStateOf("All")}
    var query by rememberSaveable {mutableStateOf("")}
    val shown=remember(history,filter,query) {history.filter {
        (filter=="All" || it.direction==filter) && (it.name.contains(query,true) || it.peer.contains(query,true))
    }}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {PageHeading(stringResource(R.string.history_title),stringResource(R.string.history_caption))}
        item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.search_history))},leadingIcon={AppIcon(R.drawable.ic_search)},singleLine=true)}
        item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("All" to R.string.all,"Sent" to R.string.sent,"Received" to R.string.received).forEach { (value,label) ->
                FilterChip(filter==value,{filter=value},label={Text(stringResource(label))},modifier=Modifier.heightIn(min=48.dp))
            }
        }}
        if(history.isEmpty()) item {EmptyTransfers()}
        else if(shown.isEmpty()) item {Text(stringResource(R.string.no_matches),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(shown) {TransferHistoryItem(it)}
    }
}
