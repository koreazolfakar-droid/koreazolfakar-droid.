package app.swiftdrop.ui.screens.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.AppsState
import app.swiftdrop.ShareableApp
import app.swiftdrop.ui.components.*

@Composable
fun FileLibraryScreen(choose: (Array<String>) -> Unit,apps: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.file_library),stringResource(R.string.library_caption)) }
        items(listOf(Triple(R.drawable.ic_photos,R.string.photos,arrayOf("image/*")),
            Triple(R.drawable.ic_videos,R.string.videos,arrayOf("video/*")),
            Triple(R.drawable.ic_applications,R.string.applications,emptyArray<String>()),
            Triple(R.drawable.ic_documents,R.string.documents,arrayOf("application/pdf","text/*","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Triple(R.drawable.ic_music,R.string.music,arrayOf("audio/*")),
            Triple(R.drawable.ic_folder,R.string.all_files,arrayOf("*/*")))) { (icon,label,mime) ->
            Card(onClick={if(mime.isEmpty()) apps() else choose(mime)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    IconBadge(icon)
                    Text(stringResource(label),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun InstalledAppsScreen(state: AppsState,refresh: () -> Unit,export: (List<ShareableApp>) -> Unit,chooseApk: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selectedApps = state.apps.filter { it.packageName in selected }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeading(stringResource(R.string.applications),stringResource(R.string.apps_caption)) }
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
        if(selectedApps.isNotEmpty()) item {
            Button({export(selectedApps)},Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=!state.exporting) {
                Text(stringResource(R.string.prepare_selected,selectedApps.size))
            }
        }
        items(state.apps,key={it.packageName}) { app ->
            val checked = app.packageName in selected
            Card(onClick={selected=if(checked) selected-app.packageName else selected+app.packageName},enabled=!state.exporting,
                colors=CardDefaults.cardColors(containerColor=if(checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    IconBadge(R.drawable.ic_applications)
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
