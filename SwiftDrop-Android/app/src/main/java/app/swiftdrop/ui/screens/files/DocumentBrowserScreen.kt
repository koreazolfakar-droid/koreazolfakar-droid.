package app.swiftdrop.ui.screens.files

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.components.AppIcon
import app.swiftdrop.ui.components.sizeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped user-chosen directory. Does not request MANAGE_EXTERNAL_STORAGE. */
private data class BrowserItem(
    val id:String,val name:String,val type:String,val size:Long,val uri:Uri
) {
    val folder:Boolean get() = type==DocumentsContract.Document.MIME_TYPE_DIR
}
private data class BrowserFolder(val id:String,val name:String)

private fun listDocuments(
    context:android.content.Context,tree:Uri,folderId:String
):List<BrowserItem> {
    val uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,folderId)
    val projection=arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    )
    val entries=ArrayList<BrowserItem>()
    context.contentResolver.query(uri,projection,null,null,null)?.use { cursor ->
        val idCol=cursor.getColumnIndexOrThrow(projection[0])
        val nameCol=cursor.getColumnIndexOrThrow(projection[1])
        val mimeCol=cursor.getColumnIndexOrThrow(projection[2])
        val sizeCol=cursor.getColumnIndexOrThrow(projection[3])
        while(cursor.moveToNext() && entries.size<1200) {
            val id=cursor.getString(idCol) ?: continue
            val mime=cursor.getString(mimeCol) ?: "application/octet-stream"
            entries.add(BrowserItem(
                id,cursor.getString(nameCol) ?: "File",mime,
                if(cursor.isNull(sizeCol)) -1L else cursor.getLong(sizeCol),
                DocumentsContract.buildDocumentUriUsingTree(tree,id)
            ))
        }
    }
    return entries.sortedWith(compareByDescending<BrowserItem> { it.folder }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

@Composable
fun DocumentBrowserScreen(back:()->Unit,onSend:(List<Uri>)->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) {
        context.getSharedPreferences("swiftdrop_document_browser",android.content.Context.MODE_PRIVATE)
    }
    var rootUri by remember { mutableStateOf(
        prefs.getString("last_tree",null)?.let(Uri::parse)
    ) }
    val folders=remember { mutableStateListOf<BrowserFolder>() }
    val selected=remember { mutableStateMapOf<Uri,BrowserItem>() }
    var refresh by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    fun goBack() {
        if(folders.size>1) folders.removeAt(folders.lastIndex) else back()
    }
    BackHandler(folders.size>1) { goBack() }

    val chooseTree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { chosen ->
        if(chosen!=null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    chosen,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("last_tree",chosen.toString()).apply()
                rootUri=chosen
                selected.clear()
                folders.clear()
                failed=false
                refresh++
            } catch (_:SecurityException) { failed=true }
        }
    }
    val chooseDocuments=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()) { result ->
        if(result.isNotEmpty()) onSend(result)
    }

    LaunchedEffect(rootUri) {
        folders.clear()
        rootUri?.let { uri ->
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.onSuccess { id ->
                folders.add(BrowserFolder(id,context.getString(R.string.documents)))
            }.onFailure { failed=true }
        }
    }
    val current=folders.lastOrNull()
    val listing by produceState<Result<List<BrowserItem>>?>(null,rootUri,current?.id,refresh) {
        value=null
        val uri=rootUri
        val dir=current
        if(uri!=null && dir!=null) {
            value=withContext(Dispatchers.IO) {
                runCatching { listDocuments(context,uri,dir.id) }
            }
        }
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={goBack()}) {
                AppIcon(R.drawable.ic_back,description=stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.documents),style=MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.doc_browser_subtitle),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={chooseTree.launch(null)},modifier=Modifier.weight(1f)
                .heightIn(min=48.dp)) {
                Text(stringResource(R.string.doc_choose_folder),maxLines=1)
            }
            OutlinedButton(onClick={chooseDocuments.launch(arrayOf("*/*"))},
                modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                Text(stringResource(R.string.doc_choose_files),maxLines=1)
            }
        }
        if(failed || listing?.isFailure==true) {
            Text(stringResource(R.string.doc_browser_error),Modifier.padding(horizontal=16.dp),
                color=MaterialTheme.colorScheme.error)
            TextButton(onClick={refresh++},modifier=Modifier.padding(horizontal=16.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        if(rootUri==null) {
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,
                modifier=Modifier.padding(16.dp)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.doc_folder_intro),
                        style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.doc_folder_privacy),
                        style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text(folders.joinToString(" / ") { it.name },
                modifier=Modifier.padding(horizontal=16.dp),
                style=MaterialTheme.typography.labelMedium,maxLines=2,
                overflow=TextOverflow.Ellipsis)
            when {
                listing==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                listing?.isSuccess==true -> {
                    val entries=listing?.getOrNull().orEmpty()
                    if(entries.isEmpty()) Text(stringResource(R.string.doc_empty_folder),
                        modifier=Modifier.padding(16.dp))
                    LazyColumn(Modifier.weight(1f),
                        contentPadding=PaddingValues(horizontal=16.dp,vertical=4.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(entries,key={it.id}) { entry ->
                            Surface(onClick={
                                if(entry.folder) {
                                    folders.add(BrowserFolder(entry.id,entry.name))
                                } else {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(entry.uri,entry.type)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        })
                                    } catch (_:Exception) {
                                        Toast.makeText(context,R.string.doc_no_viewer,Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },shape=MaterialTheme.shapes.medium,
                                color=MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    AppIcon(if(entry.folder) R.drawable.ic_folder
                                        else R.drawable.ic_documents,Modifier.size(26.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name,maxLines=2,overflow=TextOverflow.Ellipsis,
                                            style=MaterialTheme.typography.titleSmall)
                                        if(!entry.folder) Text(sizeLabel(entry.size),
                                            style=MaterialTheme.typography.bodySmall,
                                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if(!entry.folder) Checkbox(
                                        checked=selected.containsKey(entry.uri),
                                        onCheckedChange={ checked ->
                                            if(checked) selected[entry.uri]=entry
                                            else selected.remove(entry.uri)
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
        if(selected.isNotEmpty()) {
            Surface(tonalElevation=4.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.doc_selected_count,selected.size),
                        modifier=Modifier.weight(1f))
                    TextButton(onClick={selected.clear()}) {
                        Text(stringResource(R.string.media_clear))
                    }
                    Button(onClick={onSend(selected.keys.toList())}) {
                        Text(stringResource(R.string.media_send_selected))
                    }
                }
            }
        }
    }
}
