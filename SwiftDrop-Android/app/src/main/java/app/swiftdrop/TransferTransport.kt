package app.swiftdrop

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

// SwiftDrop @refactor Codex 13/10/2026 Keep app workflows independent of Nearby APIs.
interface TransferController {
    val state: StateFlow<TransferUiState>
    fun setPage(page: String)
    fun armQrTarget(token: String?)
    fun advertise()
    fun discover(uris: List<Uri>)
    fun connect(peer: Peer)
    fun answerConnection(accept: Boolean)
    fun selectFiles(uris: List<Uri>)
    fun cancelTransfer()
    fun disconnect()
    fun close()
}

enum class TransportType { Nearby, LocalTcp, WifiDirect, LocalOnlyHotspot }

interface TransferTransport : TransferController {
    val type: TransportType
    val capabilities: TransferCapabilities
    fun isAvailable(): Boolean
}

/** Adapter preserves the existing Nearby UI, QR, approval and metadata protocol. */
class NearbyTransport(private val manager: NearbyTransferManager) :
    TransferTransport, TransferController by manager {
    override val type = TransportType.Nearby
    override val capabilities = TransferCapabilities.LEGACY
    // Permission/radio/Play Services failures still use the existing actionable UI.
    override fun isAvailable() = true
}
