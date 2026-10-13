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
    requestMedia: (app.swiftdrop.MediaKind) -> Unit,initialTab:Int=0,
    standaloneCategory:Boolean=false,
    openDocuments: (() -> Unit)? = null, openStorage: (() -> Unit)? = null) {
    MediaGalleryScreen(choose, apps, appsState, refreshApps, exportApps, sendSelected,
        mediaPermissionRevision, requestMedia,initialTab,standaloneCategory,
        openDocuments,openStorage)
}

// SwiftDrop @tweak Codex 12/10/2026 Real app rows and a reachable persistent selection action.
@Composable
fun InstalledAppsScreen(state: AppsState,refresh: () -> Unit,export: (List<ShareableApp>) -> Unit,
    chooseApk: () -> Unit,openFiles: () -> Unit = chooseApk,embedded: Boolean = false) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var largest by rememberSaveable { mutableStateOf(false) }
    val shown=remember(state.apps,search,largest) {
        val found=state.apps.filter { it.name.contains(search,true) || it.packageName.contains(search,true) }
        if(largest) found.sortedByDescending { it.bytes } else found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
    val selectedApps=state.apps.filter { it.packageName in selected }
    val accent=categoryColor(R.drawable.ic_applications)
    Column(Modifier.fillMaxSize()) {
        if(!embedded) Box(Modifier.padding(16.dp)) {
            PageHeading(stringResource(R.string.applications),stringResource(R.string.apps_caption))
        }
        SwiftSearchField(search,{search=it},stringResource(R.string.search_apps),
            Modifier.fillMaxWidth().padding(horizontal=16.dp))
        FlowRow(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(chooseApk,enabled=!state.exporting) { Text(stringResource(R.string.choose_apk)) }
            if(!embedded) TextButton(openFiles) { Text(stringResource(R.string.home_browse_all)) }
        }
        Text(stringResource(R.string.split_apk_notice),Modifier.padding(horizontal=16.dp),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.browser_item_count,shown.size,shown.size),Modifier.weight(1f),
                style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={largest=!largest}) { Text(stringResource(
                if(largest) R.string.media_sort_largest else R.string.media_sort_name)) }
            IconButton(refresh,enabled=!state.loading && !state.exporting) {
                AppIcon(R.drawable.ic_retry,description=stringResource(R.string.refresh_apps),tint=accent)
            }
        }
        if(state.loading || state.exporting) {
            LinearProgressIndicator(Modifier.fillMaxWidth(),color=accent)
            Text(stringResource(if(state.exporting) R.string.preparing_apps else R.string.loading_apps),
                Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
        }
        if(state.failed) Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.apps_failed),Modifier.weight(1f),color=MaterialTheme.colorScheme.error)
            TextButton(refresh) { Text(stringResource(R.string.retry)) }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)) {
            if(!state.loading && shown.isEmpty()) item {
                Text(stringResource(if(search.isBlank()) R.string.no_apps else R.string.no_matches),
                    Modifier.padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown,key={it.packageName}) { app ->
                val checked=app.packageName in selected
                Surface(onClick={selected=if(checked) selected-app.packageName else selected+app.packageName},
                    enabled=!state.exporting,shape=MaterialTheme.shapes.small,
                    color=if(checked) accent.copy(alpha=.12f) else androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(horizontal=4.dp,vertical=8.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        InstalledAppIcon(app.packageName)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(app.name,style=MaterialTheme.typography.titleSmall,maxLines=1,
                                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(sizeLabel(app.bytes)+(if(app.apkPaths.size>1) " · "+stringResource(R.string.split_app) else ""),
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(app.packageName,style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,
                                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Checkbox(checked,onCheckedChange=null,colors=CheckboxDefaults.colors(checkedColor=accent))
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
            }
        }
        if(selectedApps.isNotEmpty()) Surface(color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.doc_selected_count,selectedApps.size),Modifier.weight(1f),
                    style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={selected=emptyList()},enabled=!state.exporting) { Text(stringResource(R.string.media_clear)) }
                Button({export(selectedApps)},enabled=!state.exporting) { Text(stringResource(R.string.media_send_selected)) }
            }
        }
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
