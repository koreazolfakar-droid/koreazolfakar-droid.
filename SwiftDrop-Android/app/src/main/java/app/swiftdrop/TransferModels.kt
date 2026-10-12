package app.swiftdrop

import android.net.Uri
import androidx.annotation.StringRes

data class Peer(val id: String, val name: String)
data class PendingConnection(val endpointId: String, val peerName: String, val code: String)
data class UiMessage(@param:StringRes val resource: Int = R.string.ready, val arguments: List<String> = emptyList())
data class SelectedFile(val uri: Uri, val name: String, val bytes: Long)
enum class TransferOutcome { Running, Saving, Complete, Failed, Cancelled }
enum class NearbyRecoveryAction { None, LocationSettings, AppSettings }
data class TransferProgress(
    val name: String,
    val direction: String,
    val bytes: Long,
    val total: Long,
    val finished: Boolean = false,
    val outcome: TransferOutcome = if (finished) TransferOutcome.Complete else TransferOutcome.Running,
    val measurement: TransferMeasurement = TransferMeasurement(),
    val payloadId: Long? = null,
    val fileIndex: Int = 0,
    val fileCount: Int = 0
) {
    val fraction: Float get() = if (total <= 0) 0f else (bytes.coerceIn(0,total).toDouble() / total).toFloat()
    val percent: Int get() = (fraction * 100).toInt()
}
data class HistoryEntry(val name: String, val peer: String, val direction: String, val bytes: Long, val timestamp: Long)
data class TransferUiState(
    val page: String = "home",
    val status: UiMessage = UiMessage(),
    val peers: List<Peer> = emptyList(),
    val pending: PendingConnection? = null,
    val connectingId: String? = null,
    val connectedName: String? = null,
    val selected: List<Uri> = emptyList(),
    val selectedFiles: List<SelectedFile> = emptyList(),
    val loadingSelection: Boolean = false,
    val progress: TransferProgress? = null,
    val history: List<HistoryEntry> = emptyList(),
    val isAdvertising: Boolean = false,
    val receiverQrPayload: String? = null,
    val isDiscovering: Boolean = false,
    val recoveryAction: NearbyRecoveryAction = NearbyRecoveryAction.None,
    val diagnostics: String? = null,
    val hasError: Boolean = false
)
