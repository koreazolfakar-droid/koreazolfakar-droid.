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

// Uses ComponentActivity directly; the old transitive Fragment artifact is never used.
@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private lateinit var model: SwiftDropViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[SwiftDropViewModel::class.java]
        setContent {
            SwiftDropApp(manager,model.settings,model.apps,send={mime -> pickFiles.launch(mime)},
                receive={withNearbyPermissions(PendingNearbyAction.Receive)},
                discover={withNearbyPermissions(PendingNearbyAction.Discover)},
                openSettings={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))},
                 openLocationSettings={startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))})
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
            val request = if (Build.VERSION.SDK_INT in 31..32 &&
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
