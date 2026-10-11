package app.swiftdrop

import android.app.Application
import androidx.lifecycle.AndroidViewModel

enum class PendingNearbyAction { Discover, Receive }

/** Keeps the authenticated session, permissions action and history through rotation. */
class SwiftDropViewModel(application: Application) : AndroidViewModel(application) {
    val manager = NearbyTransferManager(application)
    var pendingAction: PendingNearbyAction? = null
    override fun onCleared() { manager.close() }
}
