package app.swiftdrop

import android.app.Application
import androidx.lifecycle.AndroidViewModel

enum class PendingNearbyAction { Discover, Receive, UltraSend }

/** Keeps the authenticated session, permissions action and history through rotation. */
class SwiftDropViewModel(application: Application) : AndroidViewModel(application) {
    val manager = NearbyTransferManager(application)
    val settings = AppSettings(application)
    val apps = InstalledAppsController(application,manager)
    init { if(!settings.state.value.onboardingComplete) manager.setPage("welcome") }
    var pendingAction: PendingNearbyAction? = null
    var pendingUltraInvite: UltraInvitation? = null
    override fun onCleared() { apps.close();manager.close() }
}
