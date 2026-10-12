package app.swiftdrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode

// Uses ComponentActivity directly; the old transitive Fragment artifact is never used.
@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private lateinit var model: SwiftDropViewModel
    private var mediaPermissionRevision by mutableIntStateOf(0)
    private var previousCrash by mutableStateOf<String?>(null)
    private val manager get() = model.manager
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = model.pendingAction
        model.pendingAction = null
        if (result.values.all { it } && action != null) perform(action)
        else manager.setPage("permissions")
    }
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) manager.selectFiles(uris)
    }
    private val mediaPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mediaPermissionRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashDiagnostics.install(this)
        previousCrash = CrashDiagnostics.read(this)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[SwiftDropViewModel::class.java]
        setContent {
            SwiftDropApp(manager,model.settings,model.apps,send={mime -> pickFiles.launch(mime)},
                mediaPermissionRevision=mediaPermissionRevision,
                requestMedia={kind -> mediaPermissions.launch(requestedMediaPermissions(kind,Build.VERSION.SDK_INT))},
                receive={withNearbyPermissions(PendingNearbyAction.Receive)},
                discover={withNearbyPermissions(PendingNearbyAction.Discover)},
                openSettings={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))},
                 openLocationSettings={startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))},
                 scanQr={scanReceiverQr()},overlay={
            previousCrash?.let { report ->
                AlertDialog(
                    onDismissRequest = { previousCrash = null; CrashDiagnostics.clear(this@MainActivity) },
                    title = { Text(stringResource(R.string.crash_report_title)) },
                    text = {
                        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.crash_report_info))
                            Spacer(Modifier.height(12.dp))
                            Text(report.take(900), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SwiftDrop crash report", report))
                            Toast.makeText(this@MainActivity, R.string.crash_report_copied, Toast.LENGTH_SHORT).show()
                            previousCrash = null
                            CrashDiagnostics.clear(this@MainActivity)
                        }) { Text(stringResource(R.string.crash_report_copy)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            previousCrash = null
                            CrashDiagnostics.clear(this@MainActivity)
                        }) { Text(stringResource(R.string.crash_report_close)) }
                    }
                )
            }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        mediaPermissionRevision++
    }

    private fun scanReceiverQr() {
        if (manager.state.value.selected.isEmpty()) {
            manager.setPage("files")
            return
        }
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this,options).startScan()
            .addOnSuccessListener { barcode ->
                val token = QrPairing.parsePayload(barcode.rawValue)
                if (token == null) {
                    Toast.makeText(this, R.string.qr_invalid, Toast.LENGTH_LONG).show()
                } else {
                    manager.armQrTarget(token)
                    withNearbyPermissions(PendingNearbyAction.Discover)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this,R.string.qr_scan_unavailable,Toast.LENGTH_LONG).show()
            }
    }

    private fun perform(action: PendingNearbyAction) {
        when(action) {
            PendingNearbyAction.Receive -> manager.advertise()
            PendingNearbyAction.Discover -> manager.discover(manager.state.value.selected)
        }
    }

    private fun withNearbyPermissions(action: PendingNearbyAction) {
        val names = requiredNearbyPermissions(Build.VERSION.SDK_INT)
        val missing = names.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            perform(action)
        } else {
            // Android 12+ requires COARSE and FINE to be requested in a single
            // runtime call whenever FINE is requested. Preserve that invariant.
            val request = if (Build.VERSION.SDK_INT >= 31 &&
                missing.any { it == Manifest.permission.ACCESS_COARSE_LOCATION ||
                    it == Manifest.permission.ACCESS_FINE_LOCATION }
            ) {
                (missing + Manifest.permission.ACCESS_COARSE_LOCATION +
                    Manifest.permission.ACCESS_FINE_LOCATION).distinct()
            } else missing
            model.pendingAction = action
            permissions.launch(request.toTypedArray())
        }
    }
}
