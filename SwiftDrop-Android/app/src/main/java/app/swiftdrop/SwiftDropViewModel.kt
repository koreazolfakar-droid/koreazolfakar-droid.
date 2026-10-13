package app.swiftdrop

import android.app.Application
import androidx.lifecycle.AndroidViewModel

enum class PendingNearbyAction { Discover, Receive }

/** Keeps the authenticated session, permissions action and history through rotation. */
class SwiftDropViewModel(application: Application) : AndroidViewModel(application) {
    // SwiftDrop @refactor Codex 13/10/2026 Nearby remains the only operational transport.
    val manager = TransferCoordinator(listOf(NearbyTransport(NearbyTransferManager(application))))
    val settings = AppSettings(application)
    val apps = InstalledAppsController(application,manager)
    init { if(!settings.state.value.onboardingComplete) manager.setPage("welcome") }
    var pendingAction: PendingNearbyAction? = null
    override fun onCleared() { apps.close();manager.close() }
}
