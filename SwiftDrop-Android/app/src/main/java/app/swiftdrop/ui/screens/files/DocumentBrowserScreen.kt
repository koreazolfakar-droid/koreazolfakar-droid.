package app.swiftdrop.ui.screens.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.components.fileIcon
import app.swiftdrop.ui.components.sizeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** File browser that never reads private Android/data, requests location, or requires blanket
 * storage permission: users can always fall back to the system's scoped document tree picker.
 * Direct browsing is only enabled after the user explicitly grants access in Android Settings. */
private data class BrowserEntry(
    val key: String, val name: String, val isFolder: Boolean, val uri: Uri?,
    val size: Long = -1L, val modified: Long = 0L, val children: Int = -1,
    val mimeType: String? = null
)
private data class TreeFolder(val id: String, val name: String)
private sealed interface FolderResult {
    data object Loading : FolderResult
    data object Unavailable : FolderResult
    data class Ready(val entries: List<BrowserEntry>) : FolderResult
}
private enum class FileSort { Name, Newest, Largest, Type }

// SwiftDrop @feature Codex 12/10/2026 Functional document types shared by filtering and SAF.
internal fun documentGroup(name: String, mimeType: String? = null): String? =
    app.swiftdrop.ui.components.fileCategory(name, mimeType).let { category ->
        category.filterKey.takeIf { app.swiftdrop.ui.components.isDocumentCategory(category) }
    }

/** Block traversal outside the selected public-storage root, including symbolic links. */
internal fun withinStorageRoot(root: File, candidate: File): Boolean {
    val base = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
    val path = candidate.canonicalPath
    return path == root.canonicalPath || path.startsWith(base)
}

private fun listLocalDirectory(context: Context, root: File, folder: File): List<BrowserEntry> {
    if (!withinStorageRoot(root, folder)) throw SecurityException("Outside shared storage")
    if (!folder.isDirectory || !folder.canRead()) throw IOException("Folder not readable")
    val files = folder.listFiles() ?: throw IOException("Cannot list folder")
    return files.asSequence().filter { it.canRead() && withinStorageRoot(root, it) }
        .take(2000).mapNotNull { child ->
            runCatching {
                val directory = child.isDirectory
                BrowserEntry(
                    key = child.canonicalPath,
                    name = child.name,
                    isFolder = directory,
                    uri = if (directory) null else FileProvider.getUriForFile(
                        context, context.packageName + ".sharedfiles", child),
                    size = if (directory) -1L else child.length(),
                    modified = child.lastModified(),
                    children = if (directory) child.list()?.size ?: -1 else -1
                )
            }.getOrNull()
        }.toList()
}

private fun listTreeDirectory(context: Context, tree: Uri, folderId: String): List<BrowserEntry> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
    val columns = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    val entries = ArrayList<BrowserEntry>()
    context.contentResolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(columns[0])
        val nameIndex = cursor.getColumnIndex(columns[1])
        val typeIndex = cursor.getColumnIndex(columns[2])
        val sizeIndex = cursor.getColumnIndex(columns[3])
        val timeIndex = cursor.getColumnIndex(columns[4])
        while (cursor.moveToNext() && entries.size < 2000) {
            val id = cursor.getString(idIndex) ?: continue
            val type = if (typeIndex < 0) "" else cursor.getString(typeIndex).orEmpty()
            val folder = type == DocumentsContract.Document.MIME_TYPE_DIR
            entries += BrowserEntry(
                key = id,
                name = if (nameIndex < 0) id else cursor.getString(nameIndex) ?: id,
                isFolder = folder,
                uri = if (folder) null else DocumentsContract.buildDocumentUriUsingTree(tree, id),
                size = if (sizeIndex < 0 || cursor.isNull(sizeIndex)) -1L else cursor.getLong(sizeIndex),
                modified = if (timeIndex < 0 || cursor.isNull(timeIndex)) 0L else cursor.getLong(timeIndex),
                mimeType = type
            )
        }
    } ?: throw IOException("Document provider returned no folder contents")
    return entries
}

@Composable
fun DocumentBrowserScreen(back: () -> Unit, onSend: (List<Uri>) -> Unit, documentsOnly: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences("swiftdrop_document_browser", Context.MODE_PRIVATE)
    }
    val root = remember { Environment.getExternalStorageDirectory() }
    var treeText by rememberSaveable { mutableStateOf(prefs.getString("last_tree", null).orEmpty()) }
    val tree = remember(treeText) { treeText.takeIf { it.isNotBlank() }?.let(Uri::parse) }
    var useTree by rememberSaveable { mutableStateOf(false) }
    var localPath by rememberSaveable { mutableStateOf(if(documentsOnly)
        File(root,"Documents").takeIf { it.isDirectory }?.path ?: root.path else root.path) }
    var typeFilter by rememberSaveable { mutableStateOf("All") }
    val treeFolders=rememberSaveable(saver=listSaver<SnapshotStateList<TreeFolder>,String>(
        save={ folders -> folders.flatMap { listOf(it.id,it.name) } },
        restore={ values -> values.chunked(2).map { TreeFolder(it[0],it[1]) }.toMutableStateList() }
    )) { mutableStateListOf<TreeFolder>() }
    var initializedTree by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateMapOf<String, BrowserEntry>() }
    var refresh by remember { mutableIntStateOf(0) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(FileSort.Name) }
    var sortMenu by remember { mutableStateOf(false) }
    var folderState by remember { mutableStateOf<FolderResult>(FolderResult.Loading) }

    // Android's All-files-access grant is not a runtime dialog; it changes in Settings.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val fullAccess = remember(permissionRefresh) {
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
    val browsingLocal = fullAccess && !useTree
    val treeFolder = treeFolders.lastOrNull()
    val storageTitle = stringResource(R.string.doc_storage_title)

    val chooseTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { picked ->
        if (picked != null) {
            val success = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            if (success) {
                prefs.edit().putString("last_tree", picked.toString()).apply()
                initializedTree=""
                treeFolders.clear()
                treeText = picked.toString()
                useTree = true
                search = ""
                selected.clear()
                refresh++
            } else Toast.makeText(context, R.string.doc_browser_error, Toast.LENGTH_LONG).show()
        }
    }
    val chooseFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { picked -> if (picked.isNotEmpty()) onSend(picked) }
    val requestLegacyAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionRefresh++ }

    fun requestAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            requestLegacyAccess.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            return
        }
        val specific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val generic = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(specific) }.isFailure &&
            runCatching { context.startActivity(generic) }.isFailure) {
            Toast.makeText(context, R.string.doc_access_settings_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun goBack() {
        when {
            browsingLocal && localPath != root.path -> {
                val parent = File(localPath).parentFile
                localPath = if (parent != null && runCatching {
                    withinStorageRoot(root, parent)
                }.getOrDefault(false)) parent.path else root.path
            }
            !browsingLocal && treeFolders.size > 1 -> treeFolders.removeAt(treeFolders.lastIndex)
            else -> back()
        }
        search = ""
    }
    BackHandler(
        enabled = (browsingLocal && localPath != root.path) ||
            (!browsingLocal && treeFolders.size > 1)
    ) { goBack() }

    LaunchedEffect(tree,initializedTree) {
        if(treeText!=initializedTree || (tree!=null && treeFolders.isEmpty())) {
            treeFolders.clear()
            if(tree!=null) {
                val rootFolder=withContext(Dispatchers.IO) { runCatching {
                    val id=DocumentsContract.getTreeDocumentId(tree)
                    val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
                    val name=context.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,null,null)?.use { cursor ->
                        if(cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    TreeFolder(id,name ?: storageTitle)
                }.getOrNull() }
                rootFolder?.let(treeFolders::add)
            }
            initializedTree=treeText
        }
    }

    LaunchedEffect(browsingLocal, tree, treeFolder?.id, localPath, refresh, permissionRefresh) {
        folderState = FolderResult.Loading
        val path = localPath
        val folder = treeFolder?.id
        folderState = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    browsingLocal -> FolderResult.Ready(listLocalDirectory(context, root, File(path)))
                    tree != null && folder != null -> {
                        val allowed = context.contentResolver.persistedUriPermissions.any {
                            it.isReadPermission && it.uri == tree
                        }
                        if (!allowed) throw SecurityException("Expired document tree grant")
                        FolderResult.Ready(listTreeDirectory(context, tree, folder))
                    }
                    else -> FolderResult.Ready(emptyList())
                }
            }.getOrElse { FolderResult.Unavailable }
        }
    }

    val entries = (folderState as? FolderResult.Ready)?.entries.orEmpty()
    val visible = remember(entries, search, sort, documentsOnly, typeFilter) {
        val byName = compareBy<BrowserEntry> { !it.isFolder }.thenBy(
            String.CASE_INSENSITIVE_ORDER) { it.name }
        entries.filter { (search.isBlank() || it.name.contains(search.trim(), true)) &&
            (it.isFolder || fileFilterMatches(it.name,it.mimeType,typeFilter,documentsOnly)) }
            .sortedWith(when (sort) {
                FileSort.Name -> byName
                FileSort.Newest -> compareBy<BrowserEntry> { !it.isFolder }
                    .thenByDescending { it.modified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                FileSort.Type -> compareBy<BrowserEntry> { !it.isFolder }
                    .thenBy { it.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                FileSort.Largest -> compareBy<BrowserEntry> { !it.isFolder }
                    .thenByDescending { it.size }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            })
    }

    fun open(entry: BrowserEntry) {
        if (entry.isFolder) {
            if (browsingLocal) localPath = entry.key
            else treeFolders.add(TreeFolder(entry.key, entry.name))
            search = ""
        } else {
            val uri = entry.uri ?: return
            val type = entry.mimeType?.takeUnless { it == "application/octet-stream" } ?:
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    entry.name.substringAfterLast('.', "").lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isFailure)
                Toast.makeText(context, R.string.doc_no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    val trail = if (browsingLocal) {
        val relative = localPath.removePrefix(root.path).trimStart(File.separatorChar)
        listOf(storageTitle) + relative.split(File.separatorChar).filter { it.isNotBlank() }
    } else treeFolders.map { it.name }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { goBack() }) {
                AppIcon(R.drawable.ic_back, description = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(if(documentsOnly) R.string.documents else R.string.home_storage),
                    style = MaterialTheme.typography.headlineMedium)
                val free = remember(root, permissionRefresh) {
                    runCatching { StatFs(root.path).availableBytes }.getOrNull()
                }
                Text(
                    if (free == null) stringResource(R.string.doc_browser_subtitle)
                    else stringResource(R.string.media_storage_free,
                        Formatter.formatShortFileSize(context, free)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refresh++ }) {
                AppIcon(R.drawable.ic_retry, description = stringResource(R.string.retry))
            }
        }

        StorageCapacity(root,permissionRefresh)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val categories = listOf("All" to R.string.all,
                "PDF" to R.string.document_pdf,
                "Word" to R.string.document_word,
                "Sheets" to R.string.document_sheets,
                "Slides" to R.string.document_slides,
                "Text" to R.string.document_text) +
                if(documentsOnly) emptyList() else listOf(
                    "Images" to R.string.photos,
                    "Videos" to R.string.videos,
                    "Audio" to R.string.music,
                    "Apps" to R.string.applications,
                    "Archives" to R.string.file_archives,
                    "Other" to R.string.file_other)
            categories.forEach { (value,label) ->
                FilterChip(selected=typeFilter==value,onClick={typeFilter=value;search=""},
                    label={Text(stringResource(label))})
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { chooseTree.launch(null) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.doc_choose_folder))
            }
            OutlinedButton(onClick = {
                chooseFiles.launch(mimeTypesForFileFilter(typeFilter,documentsOnly))
            },
                modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.doc_choose_files))
            }
        }

        if (fullAccess && useTree) {
            TextButton(onClick = {
                useTree = false
                localPath = root.path
                search = ""
                selected.clear()
            }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.doc_open_internal_storage))
            }
        }

        if (!browsingLocal && tree == null) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBadgeForFiles()
                Text(stringResource(R.string.doc_folder_intro),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.doc_access_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { requestAccess() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.doc_allow_storage))
                }
                OutlinedButton(onClick = { chooseTree.launch(null) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.doc_choose_folder))
                }
                Text(stringResource(R.string.doc_folder_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                trail.forEachIndexed { index,name ->
                    if(index>0) AppIcon(R.drawable.ic_chevron,Modifier.size(16.dp),
                        tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick={
                        if(browsingLocal) {
                            localPath=trail.drop(1).take(index).fold(root) { folder,part -> File(folder,part) }.path
                        } else while(treeFolders.size>index+1) treeFolders.removeAt(treeFolders.lastIndex)
                        search=""
                    }) { Text(name,maxLines=1) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SwiftSearchField(search,{search=it},stringResource(R.string.doc_search_files),
                    Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenu = true }) {
                        Text(stringResource(R.string.media_sort))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf(FileSort.Name to R.string.media_sort_name,
                            FileSort.Newest to R.string.media_sort_newest,
                            FileSort.Largest to R.string.media_sort_largest,
                            FileSort.Type to R.string.doc_sort_type).forEach { (choice, textId) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(textId)) },
                                onClick = { sort = choice; sortMenu = false })
                        }
                    }
                }
            }
            when (folderState) {
                FolderResult.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                FolderResult.Unavailable -> {
                    Column(Modifier.weight(1f).padding(18.dp)) {
                        Text(stringResource(R.string.doc_browser_error),
                            color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.retry)) }
                        TextButton(onClick = {
                            treeText = ""
                            useTree = false
                            selected.clear()
                            prefs.edit().remove("last_tree").apply()
                        }) { Text(stringResource(R.string.doc_reset_folder)) }
                    }
                }
                is FolderResult.Ready -> {
                    if (visible.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(if (search.isBlank()) R.string.doc_empty_folder
                                else R.string.no_matches))
                        }
                    } else LazyColumn(Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(visible, key = { it.key }) { entry ->
                            Surface(onClick = {
                                if(selected.isNotEmpty() && !entry.isFolder) {
                                    if(selected.containsKey(entry.key)) selected.remove(entry.key) else selected[entry.key]=entry
                                } else open(entry)
                            }, shape = MaterialTheme.shapes.extraSmall,
                                color = if(selected.containsKey(entry.key)) MaterialTheme.colorScheme.primaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal=4.dp,vertical=8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FileTypeBadge(entry.name, mimeType=entry.mimeType,
                                        folder=entry.isFolder)
                                    Column(Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(entry.name, style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val meta = when {
                                            entry.isFolder && entry.children >= 0 ->
                                                androidx.compose.ui.res.pluralStringResource(R.plurals.browser_item_count,entry.children,entry.children)
                                            entry.isFolder -> stringResource(R.string.doc_folder_label)
                                            else -> sizeLabel(entry.size)
                                        }
                                        val date = if (entry.modified > 0) DateFormat.getDateInstance(
                                            DateFormat.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(Date(entry.modified))
                                            else ""
                                        Text(if (date.isEmpty()) meta else meta + " · " + date,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!entry.isFolder && entry.uri != null) {
                                        Checkbox(
                                            checked = selected.containsKey(entry.key),
                                            onCheckedChange = { checked ->
                                                if (checked) selected[entry.key] = entry
                                                else selected.remove(entry.key)
                                            })
                                    }
                                }
                            }
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.6f))
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Surface(color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.doc_selected_count, selected.size),
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected.clear() }) {
                        Text(stringResource(R.string.media_clear))
                    }
                    IconButton(onClick={
                        val uris=ArrayList(selected.values.mapNotNull { it.uri }.distinct())
                        val intent=Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type="*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            if(uris.isNotEmpty()) clipData=android.content.ClipData.newUri(context.contentResolver,"SwiftDrop",uris.first()).apply {
                                uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
                            }
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent,null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { Toast.makeText(context,R.string.doc_no_viewer,Toast.LENGTH_SHORT).show() }
                    }) { AppIcon(R.drawable.ic_share,description=stringResource(R.string.share_files)) }
                    Button(onClick = { onSend(selected.values.mapNotNull { it.uri }.distinct()) }) {
                        Text(stringResource(R.string.media_send_selected))
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBadgeForFiles() {
    Surface(shape = MaterialTheme.shapes.large,
        color = categoryColor(R.drawable.ic_folder).copy(alpha=.12f)) {
        AppIcon(R.drawable.ic_folder, Modifier.padding(16.dp).size(32.dp),tint=categoryColor(R.drawable.ic_folder))
    }
}

// SwiftDrop @feature Codex 12/10/2026 Capacity is available independently of file grants.
@Composable
private fun StorageCapacity(root:File,revision:Int) {
    val capacity=remember(root,revision) { runCatching {
        StatFs(root.path).let { it.totalBytes to it.availableBytes }
    }.getOrNull() } ?: return
    if(capacity.first<=0) return
    val used=(capacity.first-capacity.second).coerceAtLeast(0L)
    val fraction=(used.toDouble()/capacity.first).toFloat().coerceIn(0f,1f)
    Surface(Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=MaterialTheme.shapes.medium,
        color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(52.dp),contentAlignment=Alignment.Center) {
                CircularProgressIndicator(progress={fraction},modifier=Modifier.fillMaxSize(),
                    strokeWidth=4.dp,trackColor=MaterialTheme.colorScheme.surfaceVariant)
                Text("${(fraction*100).toInt()}%",style=MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.doc_storage_title),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.storage_used,sizeLabel(used),sizeLabel(capacity.first)),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
