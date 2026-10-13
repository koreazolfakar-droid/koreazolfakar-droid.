package app.swiftdrop

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Local-only, opt-in diagnostics. Never log payload data, keys, credentials or full file paths. */
class DeveloperDiagnostics private constructor(private val app: Context) {
    private val prefs = app.getSharedPreferences("developer_diagnostics", Context.MODE_PRIVATE)
    private val directory = File(app.filesDir, "diagnostics")
    private val current get() = File(directory, "events.log")
    private val previous get() = File(directory, "events.previous.log")
    val enabled: Boolean get() = prefs.getBoolean("enabled", BuildConfig.DEBUG)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
        if (value) record("SYSTEM", "LOGGING_ENABLED")
    }

    @Synchronized fun record(category: String, event: String, detail: String = "") {
        if (!enabled) return
        val safeCategory = category.take(24).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeEvent = event.take(64).replace(Regex("[^A-Za-z0-9_-]"), "_")
        // Detail is strictly for non-sensitive, predefined numeric/status metadata.
        val safeDetail = detail.take(160).replace(Regex("[^A-Za-z0-9_ =.,:-]"), "_")
        runCatching {
            directory.mkdirs()
            if (current.length() >= MAX_BYTES) {
                previous.delete()
                current.renameTo(previous)
            }
            val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            current.appendText("$time [$safeCategory] $safeEvent $safeDetail\n")
        }
    }

    @Synchronized fun readText(): String = listOf(previous, current)
        .filter { it.isFile }.joinToString("\n") { it.readText().takeLast(MAX_BYTES.toInt()) }

    @Synchronized fun clear() {
        current.delete()
        previous.delete()
    }

    @Synchronized fun exportZip(): File {
        directory.mkdirs()
        val folder = File(app.cacheDir, "diagnostic-exports").apply { mkdirs() }
        val destination = File(folder, "SwiftDrop-Diagnostics.zip")
        ZipOutputStream(destination.outputStream()).use { zip ->
            for (log in listOf(previous, current).filter { it.isFile }) {
                zip.putNextEntry(ZipEntry(log.name))
                log.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("device-info.txt"))
            zip.write(("Android API: ${android.os.Build.VERSION.SDK_INT}\n" +
                "App version: ${BuildConfig.VERSION_NAME}\n" +
                "Device model and identifiers are intentionally excluded.\n").toByteArray())
            zip.closeEntry()
        }
        return destination
    }

    fun shareZip() {
        val file = exportZip()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.sharedfiles", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(Intent.createChooser(intent, "Share SwiftDrop diagnostics")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val MAX_BYTES = 1024L * 1024L
        @Volatile private var instance: DeveloperDiagnostics? = null
        fun get(context: Context): DeveloperDiagnostics = instance ?: synchronized(this) {
            instance ?: DeveloperDiagnostics(context.applicationContext).also { instance = it }
        }
    }
}
