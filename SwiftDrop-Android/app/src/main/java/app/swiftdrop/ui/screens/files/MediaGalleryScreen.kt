package app.swiftdrop.ui.screens.files

import android.content.Intent
import androidx.activity.compose.BackHandler
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.music.rememberMusicSessionController
import app.swiftdrop.music.startLocalPlaylist
import app.swiftdrop.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

// SwiftDrop @bugfix Codex 12/10/2026 Keep the actual open media item through rotation.
private val previewSaver=Saver<LocalMedia?,List<String>>(
    save={ item -> item?.let { listOf(it.uri.toString(),it.name,it.size.toString(),it.duration.toString(),
        it.addedAtSeconds.toString(),it.artist.orEmpty(),it.album.orEmpty()) } },
    restore={ values -> LocalMedia(Uri.parse(values[0]),values[1],values[2].toLong(),values[3].toLong(),
        values[4].toLong(),values[5].ifEmpty { null },values[6].ifEmpty { null }) }
)

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
    sendSelected: (List<Uri>) -> Unit, permissionRevision: Int, requestMedia: (MediaKind) -> Unit,
    initialTab:Int=0,standaloneCategory:Boolean=false,
    openDocuments: (() -> Unit)? = null, openStorage: (() -> Unit)? = null) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0,3)) }
    LaunchedEffect(initialTab) { tab=initialTab.coerceIn(0,3) }
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
                Text(stringResource(
                    if(standaloneCategory) when(tab) {
                        0 -> R.string.photos
                        1 -> R.string.videos
                        2 -> R.string.music
                        else -> R.string.applications
                    } else R.string.file_library),
                    style=MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.media_gallery_caption),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        if(!standaloneCategory && storage!=null && storage.first>0) {
            Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=5.dp),
                shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
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
        if(!standaloneCategory) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal=16.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            val labels=listOf(R.string.photos,R.string.videos,R.string.music,R.string.applications)
            labels.forEachIndexed { index,label ->
                FilterChip(selected=tab==index,onClick={
                    tab=index
                    if(index==3)refreshApps()
                },label={Text(stringResource(label))}, shape=MaterialTheme.shapes.large)
            }
            AssistChip(onClick={
                if (openDocuments != null) openDocuments()
                else choose(arrayOf("application/pdf","text/*","application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            }, label={Text(stringResource(R.string.documents))},shape=MaterialTheme.shapes.large)
            AssistChip(onClick={
                if (openStorage != null) openStorage()
                else choose(arrayOf("*/*"))
            }, label={Text(stringResource(R.string.all_files))},shape=MaterialTheme.shapes.large)
        }
        Box(Modifier.weight(1f)) {
            if(tab==3) {
                InstalledAppsScreen(appsState,refreshApps,exportApps,
                    {choose(arrayOf("application/vnd.android.package-archive"))},
                    apps,embedded=true)
            } else {
                val kind=MediaKind.entries[tab]
                galleryStates.SaveableStateProvider(kind.name) {
                    val accent=categoryColor(when(kind) {
                        MediaKind.Photos -> R.drawable.ic_photos
                        MediaKind.Videos -> R.drawable.ic_videos
                        MediaKind.Music -> R.drawable.ic_music
                    })
                    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(primary=accent)) {
                        MediaGrid(kind,selected,permissionRevision,requestMedia,choose)
                    }
                }
            }
        }
        if(selected.isNotEmpty()) {
            val known = selected.values.all { it.bytes >= 0 }
            val total = selected.values.sumOf { it.bytes.coerceAtLeast(0L) }
            Surface(
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                    val compact = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.4f
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                pluralStringResource(
                                    R.plurals.media_selected_count, selected.size, selected.size
                                ) + if (known) " · " + sizeLabel(total) else "",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { selected.clear() }, modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.media_clear)) }
                                Button(
                                    onClick = { sendSelected(selected.keys.toList()) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.media_send_selected)) }
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.media_selected_count, selected.size, selected.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (known) Text(
                                    sizeLabel(total), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            TextButton(onClick = { selected.clear() }) {
                                Text(stringResource(R.string.media_clear))
                            }
                            Button(onClick = { sendSelected(selected.keys.toList()) }) {
                                Text(stringResource(R.string.media_send_selected))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(kind:MediaKind,selected:SnapshotStateMap<Uri,PickedMedia>,
    permissionRevision:Int,requestMedia:(MediaKind)->Unit,choose:(Array<String>)->Unit) {
    val context=LocalContext.current
    val access=remember(kind,permissionRevision) {
        mediaAccess(kind,Build.VERSION.SDK_INT) {
            context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED
        }
    }
    val library=remember(context) { MediaLibrary(context.contentResolver) }
    val musicController=rememberMusicSessionController()
    var revision by remember { mutableIntStateOf(0) }
    var search by rememberSaveable(kind) { mutableStateOf("") }
    var query by remember(kind) { mutableStateOf("") }
    var sort by rememberSaveable(kind) { mutableStateOf(MediaSort.Newest) }
    var sortingMenu by remember { mutableStateOf(false) }
    var rows by remember(kind) { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(false) }
    var failed by remember(kind) { mutableStateOf(false) }
    var preview by rememberSaveable(kind,stateSaver=previewSaver) { mutableStateOf<LocalMedia?>(null) }
    // A tap must work even when the asynchronous MediaController is not ready yet.
    var pendingMusic by remember(kind) { mutableStateOf<LocalMedia?>(null) }
    LaunchedEffect(musicController,pendingMusic) {
        val chosen=pendingMusic
        if(musicController!=null && chosen!=null) {
            startLocalPlaylist(musicController,rows,chosen)
            pendingMusic=null
        }
    }
    val grid=rememberLazyGridState()
    val list=rememberLazyListState()
    var lastFilter by rememberSaveable(kind) { mutableStateOf("") }
    var section by rememberSaveable(kind) { mutableStateOf("Items") }
    var groupId by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var groupTitle by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var groups by remember(kind) { mutableStateOf<List<MediaGroup>>(emptyList()) }
    var groupsLoading by remember(kind) { mutableStateOf(false) }
    var groupsFailed by remember(kind) { mutableStateOf(false) }
    val grouping=when {
        section=="Artists" -> MediaGrouping.Artist
        kind==MediaKind.Music -> MediaGrouping.Album
        else -> MediaGrouping.Folder
    }
    val showingGroups=section!="Items" && groupId==null
    BackHandler(groupId!=null) { groupId=null; groupTitle=null; search="" }
    LaunchedEffect(section,kind,access.allowed,revision,permissionRevision) {
        groups=emptyList();groupsFailed=false
        if(section=="Items" || !access.allowed) return@LaunchedEffect
        groupsLoading=true
        try { groups=library.groups(kind,grouping) }
        catch(e:Exception) { if(e is CancellationException) throw e; groupsFailed=true }
        finally { groupsLoading=false }
    }
    val entries=remember(rows,sort) { galleryDisplay(rows,sort) }

    fun toggle(item:LocalMedia) {
        if(item.uri in selected) selected.remove(item.uri)
        else selected[item.uri]=PickedMedia(kind,item.size)
    }
    fun open(item:LocalMedia) {
        if(selected.isNotEmpty()) toggle(item)
        else {
            if(kind==MediaKind.Music) {
                pendingMusic=item
            }
            preview=item
        }
    }
    LaunchedEffect(library) { library.changes().collect { revision++ } }
    LaunchedEffect(search) { delay(220); query=search.trim() }
    LaunchedEffect(kind,query,sort,permissionRevision,revision,access.allowed,groupId,showingGroups,grouping) {
        rows=emptyList();failed=false
        if(!access.allowed || showingGroups) {
            loading=false;preview=null;pendingMusic=null
            return@LaunchedEffect
        }
        val filterId=kind.name+"|"+query+"|"+sort.name+"|"+groupId
        if(lastFilter != filterId) {
            if(kind==MediaKind.Music) list.scrollToItem(0) else grid.scrollToItem(0)
            lastFilter=filterId
        }
        var offset=0
        while(isActive) {
            loading=true
            val page=try { library.page(kind,query,offset,sort=sort,
                grouping=if(groupId!=null) grouping else null,groupId=groupId) }
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
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                PermissionCard(kind,requestMedia)
                TextButton(onClick={ choose(arrayOf(when(kind) {
                    MediaKind.Photos -> "image/*"
                    MediaKind.Videos -> "video/*"
                    MediaKind.Music -> "audio/*"
                })) },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.doc_choose_files)) }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                val tabs=listOf("Items" to when(kind) {
                    MediaKind.Photos -> R.string.photos
                    MediaKind.Videos -> R.string.videos
                    MediaKind.Music -> R.string.music_songs
                },"Albums" to if(kind==MediaKind.Videos) R.string.media_folders else R.string.media_albums)+
                    if(kind==MediaKind.Music) listOf("Artists" to R.string.music_artists) else emptyList()
                tabs.forEach { (value,label) -> FilterChip(selected=section==value,
                    onClick={section=value;groupId=null;groupTitle=null;search=""},
                    label={Text(stringResource(label))}) }
            }
            if(groupId!=null) Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={groupId=null;groupTitle=null;search=""}) {
                    AppIcon(R.drawable.ic_back,description=stringResource(R.string.back))
                }
                Text(groupTitle.orEmpty(),style=MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SwiftSearchField(search,{search=it},stringResource(R.string.media_search),Modifier.weight(1f))
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
            if(failed || groupsFailed) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.media_unavailable),
                        color=MaterialTheme.colorScheme.error,modifier=Modifier.weight(1f))
                    TextButton(onClick={revision++}) {Text(stringResource(R.string.retry))}
                }
            }
            if(showingGroups) {
                val shownGroups=groups.filter { search.isBlank() || it.title.contains(search,true) }
                LazyVerticalGrid(columns=GridCells.Adaptive(144.dp),modifier=Modifier.weight(1f),
                    contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(groupsLoading) item(span={GridItemSpan(maxLineSpan)}) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if(!groupsLoading && shownGroups.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                        Text(stringResource(if(search.isBlank()) R.string.media_empty else R.string.no_matches),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(shownGroups,key={it.id}) { group ->
                        Card(onClick={groupId=group.id;groupTitle=group.title;search=""},
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                            if(kind==MediaKind.Music) app.swiftdrop.music.AlbumCover(group.cover,
                                Modifier.fillMaxWidth().aspectRatio(1f))
                            else MediaArt(group.cover,kind,Modifier.fillMaxWidth().aspectRatio(1f))
                            Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(group.title,style=MaterialTheme.typography.titleSmall,
                                    maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.browser_item_count,group.count,group.count),
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else if(kind==MediaKind.Music) {
                LazyColumn(Modifier.weight(1f),state=list,
                    contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(!loading && !failed && rows.isEmpty()) item {
                        Text(stringResource(if(query.isBlank())R.string.media_empty else R.string.no_matches))
                    }
                    items(rows,key={it.uri.toString()}) { item ->
                        MusicRow(item,item.uri in selected,
                            onOpen={open(item)},onToggle={toggle(item)},onPlay={
                                pendingMusic=item
                                            })
                    }
                    if(loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
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
                                    DateFormat.getDateInstance(DateFormat.MEDIUM,
                                        androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(
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
                                    PhotoTile(item,checked,onOpen={open(item)},onToggle={toggle(item)},
                                        selectionMode=selected.isNotEmpty())
                                else VideoTile(item,checked,onOpen={open(item)},onToggle={toggle(item)},
                                        selectionMode=selected.isNotEmpty())
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
