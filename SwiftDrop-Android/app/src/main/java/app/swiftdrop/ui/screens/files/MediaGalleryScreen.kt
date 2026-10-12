package app.swiftdrop.ui.screens.files

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

private data class PickedMedia(val kind:MediaKind,val bytes:Long)

private sealed interface GalleryDisplay {
    data class DateHeading(val day: String,val addedAtSeconds: Long) : GalleryDisplay
    data class Media(val file: LocalMedia) : GalleryDisplay
}

/** Stable, paged grouping: a day gets one header, even across page boundaries. */
private fun galleryDisplay(rows: List<LocalMedia>,sort: MediaSort): List<GalleryDisplay> {
    val grouped=groupedByDay(sort)
    val result=ArrayList<GalleryDisplay>(rows.size + rows.size/12)
    var previousDay: String? = null
    for(file in rows) {
        val day=if(grouped) mediaDayKey(file.addedAtSeconds) else null
        if(grouped && day!=previousDay) {
            result.add(GalleryDisplay.DateHeading(requireNotNull(day),file.addedAtSeconds))
            previousDay=day
        }
        result.add(GalleryDisplay.Media(file))
    }
    return result
}

/** Unified, horizontally scrollable categories and compact layouts for each media type. */
@Composable
fun MediaGalleryScreen(choose: (Array<String>) -> Unit, apps: () -> Unit,
    appsState: AppsState, refreshApps: () -> Unit, exportApps: (List<ShareableApp>) -> Unit,
    sendSelected: (List<Uri>) -> Unit, permissionRevision: Int, requestMedia: (MediaKind) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val galleryStates = rememberSaveableStateHolder()
    val selected = remember { mutableStateMapOf<Uri,PickedMedia>() }
    val context = LocalContext.current
    // Returning from the player or another Activity must not discard every thumbnail.
    // When permission changes (or is partial/reselected), clear cached pixels for privacy.
    var previousGrants by remember { mutableStateOf<List<MediaAccess>?>(null) }
    LaunchedEffect(permissionRevision) {
        val current = MediaKind.entries.map { kind ->
            mediaAccess(kind,Build.VERSION.SDK_INT) {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        }
        selected.entries.toList().forEach { (uri,picked) ->
            if (!current[picked.kind.ordinal].allowed) selected.remove(uri)
        }
        if (previousGrants != null &&
            (previousGrants != current || current.any { it.partial })) {
            clearMediaThumbnails()
        }
        previousGrants = current
    }
    val storage=remember { runCatching {
        StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes }
    }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        // Compact header frees screen space for the actual files.
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=18.dp,top=8.dp,bottom=4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            LocalPageBack.current?.let { back ->
                IconButton(onClick=back,modifier=Modifier.size(48.dp)) {
                    AppIcon(R.drawable.ic_back,description=stringResource(R.string.back))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.file_library),
                    style=MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.media_gallery_caption),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(storage!=null && storage.first>0) {
            Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AppIcon(R.drawable.ic_folder,modifier=Modifier.size(18.dp),
                        tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.media_storage_free,sizeLabel(storage.second)),
                        style=MaterialTheme.typography.labelMedium,maxLines=1)
                    LinearProgressIndicator(progress={
                        ((storage.first-storage.second).toDouble()/storage.first).toFloat().coerceIn(0f,1f)
                    },modifier=Modifier.weight(1f).height(5.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal=16.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            val labels=listOf(R.string.photos,R.string.videos,R.string.music,R.string.applications)
            labels.forEachIndexed { index,label ->
                FilterChip(selected=tab==index,onClick={
                    tab=index
                    if(index==3)refreshApps()
                },label={Text(stringResource(label))})
            }
            AssistChip(onClick={choose(arrayOf("application/pdf","text/*","application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))},
                label={Text(stringResource(R.string.documents))})
            AssistChip(onClick={choose(arrayOf("*/*"))},
                label={Text(stringResource(R.string.all_files))})
        }
        Box(Modifier.weight(1f)) {
            if(tab==3) {
                InstalledAppsScreen(appsState,refreshApps,exportApps,
                    {choose(arrayOf("application/vnd.android.package-archive"))},
                    apps,embedded=true)
            } else {
                val kind=MediaKind.entries[tab]
                galleryStates.SaveableStateProvider(kind.name) {
                    MediaGrid(kind,selected,permissionRevision,requestMedia)
                }
            }
        }
        if(selected.isNotEmpty()) {
            val known=selected.values.all { it.bytes>=0 }
            val total=selected.values.sumOf { it.bytes.coerceAtLeast(0L) }
            Surface(tonalElevation=3.dp,color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.media_selected_count,selected.size,selected.size),
                            style=MaterialTheme.typography.labelMedium,
                            color=MaterialTheme.colorScheme.onPrimaryContainer)
                        if(known) Text(sizeLabel(total),style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick={selected.clear()}) { Text(stringResource(R.string.media_clear)) }
                    Button(onClick={sendSelected(selected.keys.toList())}) {
                        Text(stringResource(R.string.media_send_selected))
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(kind:MediaKind,selected:SnapshotStateMap<Uri,PickedMedia>,
    permissionRevision:Int,requestMedia:(MediaKind)->Unit) {
    val context=LocalContext.current
    val access=remember(kind,permissionRevision) {
        mediaAccess(kind,Build.VERSION.SDK_INT) {
            context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED
        }
    }
    val library=remember(context) { MediaLibrary(context.contentResolver) }
    var revision by remember { mutableIntStateOf(0) }
    var search by rememberSaveable(kind) { mutableStateOf("") }
    var query by remember(kind) { mutableStateOf("") }
    var sort by rememberSaveable(kind) { mutableStateOf(MediaSort.Newest) }
    var sortingMenu by remember { mutableStateOf(false) }
    var rows by remember(kind) { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(false) }
    var failed by remember(kind) { mutableStateOf(false) }
    var preview by remember(kind) { mutableStateOf<LocalMedia?>(null) }
    var miniPlayer by remember(kind) { mutableStateOf<LocalMedia?>(null) }
    val grid=rememberLazyGridState()
    val list=rememberLazyListState()
    var lastFilter by rememberSaveable(kind) { mutableStateOf("") }
    val entries=remember(rows,sort) { galleryDisplay(rows,sort) }

    fun toggle(item:LocalMedia) {
        if(item.uri in selected) selected.remove(item.uri)
        else selected[item.uri]=PickedMedia(kind,item.size)
    }
    fun open(item:LocalMedia) {
        if(selected.isNotEmpty()) toggle(item)
        else preview=item
    }
    LaunchedEffect(library) { library.changes().collect { revision++ } }
    LaunchedEffect(search) { delay(220); query=search.trim() }
    LaunchedEffect(kind,query,sort,permissionRevision,revision,access.allowed) {
        rows=emptyList();failed=false
        if(!access.allowed) { preview=null;miniPlayer=null;return@LaunchedEffect }
        val filterId=kind.name+"|"+query+"|"+sort.name
        if(lastFilter != filterId) {
            if(kind==MediaKind.Music) list.scrollToItem(0) else grid.scrollToItem(0)
            lastFilter=filterId
        }
        var offset=0
        while(isActive) {
            loading=true
            val page=try { library.page(kind,query,offset,sort=sort) }
                catch (e:Exception) {
                    if(e is CancellationException)throw e
                    failed=true;loading=false;break
                }
            rows=rows+page
            offset+=page.size
            loading=false
            if(page.size<48)break
            snapshotFlow {
                if(kind==MediaKind.Music) list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:0
                else grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:0
            }.first { it>=rows.size-7 }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if(!access.allowed) {
            Box(Modifier.padding(16.dp)) { PermissionCard(kind,requestMedia) }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=search,onValueChange={search=it},
                    modifier=Modifier.weight(1f),singleLine=true,
                    placeholder={Text(stringResource(R.string.media_search))},
                    leadingIcon={AppIcon(R.drawable.ic_search)},
                    textStyle=MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(onClick={sortingMenu=true},modifier=Modifier.heightIn(min=48.dp)) {
                        Text(stringResource(R.string.media_sort))
                    }
                    DropdownMenu(expanded=sortingMenu,onDismissRequest={sortingMenu=false}) {
                        MediaSort.entries.forEach { option ->
                            DropdownMenuItem(text={Text(stringResource(when(option) {
                                MediaSort.Newest -> R.string.media_sort_newest
                                MediaSort.Oldest -> R.string.media_sort_oldest
                                MediaSort.Name -> R.string.media_sort_name
                                MediaSort.Largest -> R.string.media_sort_largest
                            }))},onClick={ sort=option; sortingMenu=false })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=3.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.media_select_hint),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(access.partial) TextButton(onClick={requestMedia(kind)}) {
                    Text(stringResource(R.string.media_reselect))
                }
            }
            if(failed) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.media_unavailable),
                        color=MaterialTheme.colorScheme.error,modifier=Modifier.weight(1f))
                    TextButton(onClick={revision++}) {Text(stringResource(R.string.retry))}
                }
            }
            if(kind==MediaKind.Music) {
                LazyColumn(Modifier.weight(1f),state=list,
                    contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!loading && !failed && rows.isEmpty()) item {
                        Text(stringResource(if(query.isBlank())R.string.media_empty else R.string.no_matches))
                    }
                    items(rows,key={it.uri.toString()}) { item ->
                        MusicRow(item,item.uri in selected,
                            onOpen={open(item)},onToggle={toggle(item)},onPlay={miniPlayer=item})
                    }
                    if(loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
                }
                miniPlayer?.let { item ->
                    MusicMiniPlayer(item,onClose={miniPlayer=null})
                }
            } else {
                LazyVerticalGrid(columns=GridCells.Adaptive(if(kind==MediaKind.Photos) 100.dp else 166.dp),
                    modifier=Modifier.weight(1f),state=grid,
                    contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp),
                    horizontalArrangement=Arrangement.spacedBy(6.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!loading && !failed && rows.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                        Text(stringResource(if(query.isBlank())R.string.media_empty else R.string.no_matches),
                            modifier=Modifier.padding(12.dp))
                    }
                    items(entries,key={entry ->
                        when(entry) {
                            is GalleryDisplay.DateHeading -> "day_${entry.day}"
                            is GalleryDisplay.Media -> entry.file.uri.toString()
                        }
                    },span={entry ->
                        if(entry is GalleryDisplay.DateHeading) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    }) { entry ->
                        when(entry) {
                            is GalleryDisplay.DateHeading -> {
                                val heading = if(entry.addedAtSeconds <= 0) {
                                    stringResource(R.string.media_unknown_date)
                                } else {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(
                                        Date(entry.addedAtSeconds * 1000L))
                                }
                                Text(heading,modifier=Modifier.fillMaxWidth().padding(
                                    start=6.dp,top=10.dp,bottom=4.dp),
                                    style=MaterialTheme.typography.titleSmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            is GalleryDisplay.Media -> {
                                val item=entry.file
                                val checked=item.uri in selected
                                if(kind==MediaKind.Photos)
                                    PhotoTile(item,checked,onOpen={open(item)},onToggle={toggle(item)})
                                else VideoTile(item,checked,onOpen={open(item)},onToggle={toggle(item)})
                            }
                        }
                    }
                    if(loading) item(span={GridItemSpan(maxLineSpan)}) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    preview?.let { item ->
        if(access.allowed) {
            val idx=rows.indexOfFirst { it.uri==item.uri }
            MediaPreview(item,kind,{preview=null},{
                if(item.uri !in selected) selected[item.uri]=PickedMedia(kind,item.size)
                preview=null
            },
            previous=if(kind==MediaKind.Photos && idx>0) ({ preview=rows[idx-1] }) else null,
            next=if(kind==MediaKind.Photos && idx>=0 && idx<rows.lastIndex)
                ({ preview=rows[idx+1] }) else null)
        }
    }
}

@Composable
private fun PermissionCard(kind:MediaKind,requestMedia:(MediaKind)->Unit) {
    val context=LocalContext.current
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.media_permission_title),
                style=MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.media_permission_body),
                style=MaterialTheme.typography.bodyMedium)
            Button(onClick={requestMedia(kind)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(stringResource(R.string.media_allow))
            }
            TextButton(onClick={
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Text(stringResource(R.string.media_settings))
            }
        }
    }
}
