package app.swiftdrop.ui.screens.files

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.AppsState
import app.swiftdrop.ShareableApp
import app.swiftdrop.ui.components.*

// SwiftDrop @feature Codex 12/10/2026 Keep SAF actions alongside the integrated media gallery.
@Composable
fun FileLibraryScreen(choose: (Array<String>) -> Unit, apps: () -> Unit,
    appsState: AppsState, refreshApps: () -> Unit, exportApps: (List<ShareableApp>) -> Unit,
    sendSelected: (List<android.net.Uri>) -> Unit, mediaPermissionRevision: Int,
    requestMedia: (app.swiftdrop.MediaKind) -> Unit,initialTab:Int=0) {
    MediaGalleryScreen(choose, apps, appsState, refreshApps, exportApps, sendSelected,
        mediaPermissionRevision, requestMedia,initialTab)
}

@Composable
private fun FileTabs(appsSelected: Boolean,files: () -> Unit,apps: () -> Unit) {
    Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.6f)) {
        Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf(Triple(R.string.files,!appsSelected,files),Triple(R.string.applications,appsSelected,apps)).forEach { (label,selected,action) ->
                TextButton(action,Modifier.weight(1f).heightIn(min=48.dp),shape=MaterialTheme.shapes.small,
                    colors=ButtonDefaults.textButtonColors(containerColor=if(selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                    Text(stringResource(label),color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun InstalledAppsScreen(state: AppsState,refresh: () -> Unit,export: (List<ShareableApp>) -> Unit,chooseApk: () -> Unit,openFiles: () -> Unit = chooseApk, embedded: Boolean = false) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    val shown=remember(state.apps,search) {state.apps.filter {it.name.contains(search,true) || it.packageName.contains(search,true)}}
    val selectedApps = state.apps.filter { it.packageName in selected }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(if(embedded) 12.dp else 20.dp),
        verticalArrangement=Arrangement.spacedBy(if(embedded) 8.dp else 12.dp)) {
        if(!embedded) {
            item { PageHeading(stringResource(R.string.applications),stringResource(R.string.apps_caption)) }
            item {FileTabs(true,openFiles,{})}
        }
        item {OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.search_apps))},leadingIcon={AppIcon(R.drawable.ic_search)},singleLine=true)}
        item { Text(stringResource(R.string.split_apk_notice),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedButton(chooseApk,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.exporting) { Text(stringResource(R.string.choose_apk)) } }
        if(state.loading || state.exporting) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(if(state.exporting) R.string.preparing_apps else R.string.loading_apps))
        }
        if(state.failed) item {
            Text(stringResource(R.string.apps_failed),color=MaterialTheme.colorScheme.error)
            TextButton(refresh,Modifier.heightIn(min=48.dp)) { Text(stringResource(R.string.retry)) }
        }
        if(!state.loading && state.apps.isEmpty()) item { Text(stringResource(R.string.no_apps)) }
        if(search.isNotEmpty() && shown.isEmpty() && !state.loading) item {Text(stringResource(R.string.no_matches))}
        if(selectedApps.isNotEmpty()) item {
            Button({export(selectedApps)},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.exporting) {
                Text(stringResource(R.string.prepare_selected,selectedApps.size))
            }
        }
        items(shown,key={it.packageName}) { app ->
            val checked = app.packageName in selected
            Card(onClick={selected=if(checked) selected-app.packageName else selected+app.packageName},enabled=!state.exporting,
                colors=CardDefaults.cardColors(containerColor=if(checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    InstalledAppIcon(app.packageName)
                    Column(Modifier.weight(1f)) {
                        Text(app.name,style=MaterialTheme.typography.titleMedium)
                        Text(app.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sizeLabel(app.bytes),style=MaterialTheme.typography.bodyMedium)
                        if(app.apkPaths.size>1) Text(stringResource(R.string.split_app),style=MaterialTheme.typography.labelSmall)
                    }
                    Checkbox(checked,onCheckedChange=null)
                }
            }
        }
        item { TextButton(refresh,Modifier.heightIn(min=48.dp),enabled=!state.exporting && !state.loading) { Text(stringResource(R.string.refresh_apps)) } }
    }
}

// SwiftDrop @feature Codex 12/10/2026 Render the installed package icon in place of a generic badge.
private val appIconCache = object : android.util.LruCache<String,android.graphics.Bitmap>(4*1024*1024) {
    override fun sizeOf(key: String,value: android.graphics.Bitmap) = value.byteCount
}

@Composable
private fun InstalledAppIcon(packageName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(appIconCache.get(packageName), packageName) {
        if(value==null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888).also {
                    drawable.setBounds(0,0,96,96)
                    drawable.draw(android.graphics.Canvas(it))
                    appIconCache.put(packageName,it)
                }
            }.getOrNull()
        }
    }
    if(bitmap==null) CategoryBadge(R.drawable.ic_applications)
    else androidx.compose.foundation.Image(bitmap!!.asImageBitmap(),null,Modifier.size(44.dp))
}
