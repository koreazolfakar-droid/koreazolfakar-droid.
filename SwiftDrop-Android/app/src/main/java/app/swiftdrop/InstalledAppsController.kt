package app.swiftdrop

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

data class ShareableApp(val packageName: String,val name: String,val apkPaths: List<String>,val bytes: Long)
data class AppsState(val apps: List<ShareableApp> = emptyList(),val loading: Boolean = false,
    val exporting: Boolean = false,val failed: Boolean = false)

/** Queries launcher apps only. No QUERY_ALL_PACKAGES or storage-wide permission. */
class InstalledAppsController(private val context: Context,private val manager: NearbyTransferManager) {
    private val io = Executors.newSingleThreadExecutor()
    private val mutable = MutableStateFlow(AppsState())
    val state = mutable.asStateFlow()
    @Volatile private var closed = false
    private var loaded = false

    fun load(refresh: Boolean = false) {
        if (closed || mutable.value.loading || (loaded && !refresh)) return
        mutable.update { it.copy(loading = true,failed = false) }
        io.execute {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = context.packageManager.queryIntentActivities(intent,0)
                    .map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM)==0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0 }
                    .map { info ->
                        val paths = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
                        ShareableApp(info.packageName,context.packageManager.getApplicationLabel(info).toString(),paths,
                            paths.sumOf { File(it).length() })
                    }.sortedBy { it.name.lowercase() }
                loaded = true
                mutable.value = AppsState(apps = apps)
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false,failed = true) }
            }
        }
    }

    fun export(apps: List<ShareableApp>) {
        if (closed || apps.isEmpty() || mutable.value.exporting) return
        mutable.update { it.copy(exporting = true,failed = false) }
        io.execute {
            val root = File(context.cacheDir,"shared-apks")
            val staging = File(root,UUID.randomUUID().toString())
            try {
                check(staging.mkdirs()) { "Cannot prepare APK sharing" }
                val uris = apps.flatMap { app ->
                    app.apkPaths.mapIndexed { index,path ->
                        val source = File(path)
                        check(source.isFile && source.canRead()) { "APK is unavailable" }
                        val destination = File(staging,SafeFileName.normalize(app.packageName + "_" +
                            if(index==0) "base.apk" else "split_${index}.apk"))
                        source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output,64*1024) } }
                        FileProvider.getUriForFile(context,"${context.packageName}.sharedfiles",destination)
                    }
                }
                if (!closed) manager.selectFiles(uris)
                mutable.update { it.copy(exporting = false) }
            } catch (e: Exception) {
                // Only this failed staging directory is removed; original APKs are read-only.
                staging.deleteRecursively()
                mutable.update { it.copy(exporting = false,failed = true) }
            }
        }
    }

    fun close() { closed = true;io.shutdown() }
}
