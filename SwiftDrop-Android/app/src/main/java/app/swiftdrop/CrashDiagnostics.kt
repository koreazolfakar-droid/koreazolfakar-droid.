package app.swiftdrop

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.util.Date
import kotlin.system.exitProcess

/**
 * Last fatal Java/Kotlin crash is stored ONLY on the device in app-private storage.
 * This report never leaves the phone unless the user explicitly copies it.
 * Native process deaths / low-memory kills may not invoke an uncaught handler.
 */
object CrashDiagnostics {
    private const val fileName = "last-fatal-crash.txt"
    @Volatile private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            try {
                val report = buildString {
                    appendLine("SwiftDrop crash report")
                    appendLine("When: ${Date()}")
                    appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android API: ${Build.VERSION.SDK_INT}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(Log.getStackTraceString(failure).take(24000))
                }
                File(app.filesDir, fileName).writeText(report)
            } catch (_: Throwable) { /* Preserve the system's crash handling path. */ }
            if (previous != null) previous.uncaughtException(thread, failure)
            else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        installed = true
    }

    fun read(context: Context): String? = runCatching {
        File(context.filesDir, fileName).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, fileName).delete() }
    }
}
