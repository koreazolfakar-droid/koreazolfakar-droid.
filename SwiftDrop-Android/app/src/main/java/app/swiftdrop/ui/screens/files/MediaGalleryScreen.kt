package app.swiftdrop.ui.screens.files

import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive

// SwiftDrop @feature Codex 12/10/2026 Local MediaStore gallery with scoped grants and provider paging.
@Composable
fun MediaGalleryScreen(choose: (Array<String>) -> Unit, apps: () -> Unit,
    appsState: AppsState, refreshApps: () -> Unit, exportApps: (List<ShareableApp>) -> Unit,
    sendSelected: (List<Uri>) -> Unit, permissionRevision: Int, requestMedia: (MediaKind) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected = remember { mutableStateMapOf<Uri, MediaKind>() }
    val context = LocalContext.current
    LaunchedEffect(permissionRevision) {
        selected.keys.toList().forEach { uri ->
            val kind = selected[uri] ?: return@forEach
            if (!mediaAccess(kind, Build.VERSION.SDK_INT) {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }.allowed) selected.remove(uri)
        }
        clearMediaThumbnails()
    }
    val storage = remember { runCatching {
        StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes }
    }.getOrNull() }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            PageHeading(stringResource(R.string.file_library), stringResource(R.string.library_caption))
            Spacer(Modifier.height(12.dp))
            if (storage != null && storage.first > 0) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.device_storage), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.storage_available, sizeLabel(storage.second), sizeLabel(storage.first)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = {
                            (storage.first - storage.second).toFloat().div(storage.first).coerceIn(0f, 1f)
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        val labels = listOf(R.string.photos, R.string.videos, R.string.music, R.string.applications)
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            labels.forEachIndexed { index, label -> Tab(selected = tab == index, onClick = {
                tab = index
                if (index == 3) refreshApps()
            }, text = { Text(stringResource(label)) }) }
        }
        if (tab == 3) {
            InstalledAppsScreen(appsState, refreshApps, exportApps,
                { choose(arrayOf("application/vnd.android.package-archive")) }, apps, embedded = true)
        } else {
            MediaGrid(MediaKind.entries[tab], selected, sendSelected, permissionRevision, requestMedia,
                { choose(arrayOf("application/pdf", "text/*", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                { choose(arrayOf("*/*")) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(kind: MediaKind, selected: SnapshotStateMap<Uri, MediaKind>, sendSelected: (List<Uri>) -> Unit,
    permissionRevision: Int, requestMedia: (MediaKind) -> Unit, chooseDocuments: () -> Unit,
    chooseFiles: () -> Unit) {
    val context = LocalContext.current
    val access = remember(kind, permissionRevision) {
        mediaAccess(kind, Build.VERSION.SDK_INT) {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }
    val library = remember(context) { MediaLibrary(context.contentResolver) }
    var revision by remember { mutableIntStateOf(0) }
    var search by rememberSaveable(kind) { mutableStateOf("") }
    var query by remember(kind) { mutableStateOf("") }
    var rows by remember(kind) { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(false) }
    var failed by remember(kind) { mutableStateOf(false) }
    var preview by remember { mutableStateOf<LocalMedia?>(null) }
    val grid = rememberLazyGridState()

    LaunchedEffect(library) { library.changes().collect { revision++ } }
    LaunchedEffect(search) { delay(250); query = search.trim() }
    LaunchedEffect(kind, query, permissionRevision, revision, access.allowed) {
        rows = emptyList(); failed = false
        if (!access.allowed) { preview = null; return@LaunchedEffect }
        grid.scrollToItem(0)
        var offset = 0
        while (isActive) {
            loading = true
            val page = try { library.page(kind, query, offset) }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failed = true; loading = false; break
                }
            rows = rows + page
            offset += page.size
            loading = false
            if (page.size < 48) break
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .first { it >= rows.size - 7 }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (selected.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.media_selected_count, selected.size, selected.size), Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton({ selected.clear() }) { Text(stringResource(R.string.media_clear)) }
                Button({ sendSelected(selected.keys.toList()) }) { Text(stringResource(R.string.media_send_selected)) }
            }
        }
        LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.fillMaxSize(), state = grid,
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(chooseDocuments, Modifier.weight(1f)) { Text(stringResource(R.string.documents)) }
                    OutlinedButton(chooseFiles, Modifier.weight(1f)) { Text(stringResource(R.string.all_files)) }
                }
            }
            if (!access.allowed) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                PermissionCard(kind, requestMedia)
            } else {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.media_search)) },
                            leadingIcon = { AppIcon(R.drawable.ic_search) })
                        Text(stringResource(R.string.media_select_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (access.partial) {
                            Text(stringResource(R.string.media_partial), style = MaterialTheme.typography.bodySmall)
                            TextButton({ requestMedia(kind) }) { Text(stringResource(R.string.media_reselect)) }
                        }
                    }
                }
                if (failed) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(stringResource(R.string.media_unavailable), color = MaterialTheme.colorScheme.error)
                        TextButton({ revision++ }) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (!loading && !failed && rows.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(if (query.isBlank()) R.string.media_empty else R.string.no_matches))
                }
                items(rows, key = { it.uri.toString() }) { item ->
                    val checked = item.uri in selected
                    Card(Modifier.combinedClickable(onClick = {
                        if (selected.isNotEmpty()) {
                            if (checked) selected.remove(item.uri) else selected[item.uri] = kind
                        } else preview = item
                    }, onLongClick = {
                        if (checked) selected.remove(item.uri) else selected[item.uri] = kind
                    }), colors = CardDefaults.cardColors(containerColor =
                        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                                if (kind == MediaKind.Music) CategoryBadge(R.drawable.ic_music)
                                else MediaArt(item.uri, kind, Modifier.fillMaxSize())
                                if (checked) Checkbox(true, null, Modifier.align(Alignment.TopEnd))
                            }
                            Column(Modifier.padding(10.dp)) {
                                Text(item.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text(sizeLabel(item.size), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (loading) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
    preview?.let { item ->
        if (access.allowed) MediaPreview(item, kind, { preview = null },
            { if (item.uri !in selected) selected[item.uri] = kind; preview = null })
    }
}

@Composable
private fun PermissionCard(kind: MediaKind, requestMedia: (MediaKind) -> Unit) {
    val context = LocalContext.current
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.media_permission_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.media_permission_body), style = MaterialTheme.typography.bodyMedium)
            Button({ requestMedia(kind) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.media_allow)) }
            TextButton({ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.media_settings))
            }
        }
    }
}
