package app.swiftdrop

import android.net.Uri

data class Peer(val id: String, val name: String)
data class PendingConnection(val endpointId: String, val peerName: String, val code: String)
data class TransferProgress(
    val name: String,
    val direction: String,
    val bytes: Long,
    val total: Long,
    val finished: Boolean = false
) {
    val percent: Int get() = if (total <= 0) 0 else (bytes.coerceIn(0, total) * 100L / total).toInt()
}
data class HistoryEntry(val name: String, val peer: String, val direction: String, val bytes: Long, val timestamp: Long)
data class TransferUiState(
    val page: String = "home",
    val status: String = "Ready to share",
    val peers: List<Peer> = emptyList(),
    val pending: PendingConnection? = null,
    val connectedName: String? = null,
    val selected: List<Uri> = emptyList(),
    val progress: TransferProgress? = null,
    val history: List<HistoryEntry> = emptyList(),
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false,
    val darkTheme: Boolean = false
)

