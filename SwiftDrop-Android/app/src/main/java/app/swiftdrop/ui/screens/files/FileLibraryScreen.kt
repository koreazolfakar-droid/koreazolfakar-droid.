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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.AppsState
import app.swiftdrop.ShareableApp
import app.swiftdrop.ui.components.*

@Composable
fun FileLibraryScreen(choose: (Array<String>) -> Unit,apps: () -> Unit) {
    val categories=listOf(Triple(R.drawable.ic_photos,R.string.photos,arrayOf("image/*")),
        Triple(R.drawable.ic_videos,R.string.videos,arrayOf("video/*")),
        Triple(R.drawable.ic_applications,R.string.applications,emptyArray<String>()),
        Triple(R.drawable.ic_documents,R.string.documents,arrayOf("application/pdf","text/*","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
        Triple(R.drawable.ic_music,R.string.music,arrayOf("audio/*")),
        Triple(R.drawable.ic_folder,R.string.all_files,arrayOf("*/*")))
    val storage=remember { runCatching { StatFs(Environment.getDataDirectory().path).let { it.totalBytes to it.availableBytes } }.getOrNull() }
    LazyVerticalGrid(GridCells.Adaptive(144.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)),Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {PageHeading(stringResource(R.string.file_library),stringResource(R.string.library_caption))}
        item(span={GridItemSpan(maxLineSpan)}) {FileTabs(false,{},apps)}
        items(categories) { (icon,label,mime) ->
            Card(onClick={if(mime.isEmpty()) apps() else choose(mime)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(icon)
                    Text(stringResource(label),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(if(mime.isEmpty()) R.string.select_installed else R.string.open_picker),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(storage!=null && storage.first>0) item(span={GridItemSpan(maxLineSpan)}) {
            val used=(storage.first-storage.second).coerceAtLeast(0)
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.device_storage),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.storage_available,sizeLabel(storage.second),sizeLabel(storage.first)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress={used.toDouble().div(storage.first).toFloat().coerceIn(0f,1f)},Modifier.fillMaxWidth())
                }
            }
        }
    }
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
fun InstalledAppsScreen(state: AppsState,refresh: () -> Unit,export: (List<ShareableApp>) -> Unit,chooseApk: () -> Unit,openFiles: () -> Unit = chooseApk) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    val shown=remember(state.apps,search) {state.apps.filter {it.name.contains(search,true) || it.packageName.contains(search,true)}}
    val selectedApps = state.apps.filter { it.packageName in selected }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.applications),stringResource(R.string.apps_caption)) }
        item {FileTabs(true,openFiles,{})}
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
                    CategoryBadge(R.drawable.ic_applications)
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
