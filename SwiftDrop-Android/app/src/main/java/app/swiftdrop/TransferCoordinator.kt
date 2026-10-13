package app.swiftdrop

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

// SwiftDrop @feature Codex 13/10/2026 Select a transport before a session, never during delivery.
class TransferCoordinator(transports: List<TransferTransport>) : TransferController {
    private val registered = transports.toList()
    private var selected = requireNotNull(registered.singleOrNull { it.type == TransportType.Nearby }) {
        "Exactly one Nearby fallback is required"
    }
    init { require(registered.map { it.type }.distinct().size == registered.size) }
    override val state: StateFlow<TransferUiState> get() = selected.state
    val transportType: TransportType get() = selected.type

    /** No mid-session migration: a future transport needs an authenticated resume protocol. */
    fun selectTransport(preferred: TransportType, authenticatedPeer: TransferCapabilities? = null): TransportType {
        check(registered.none { it.state.value.let { s ->
            s.sessionState in setOf(SessionState.Advertising, SessionState.Discovering,
                SessionState.AwaitingApproval, SessionState.Connecting, SessionState.Connected) ||
                s.connectedName != null || s.connectingId != null || s.pending != null ||
                s.isAdvertising || s.isDiscovering || s.progress?.outcome in
                setOf(TransferOutcome.Running, TransferOutcome.Saving)
        } }) { "Transport selection requires an idle session" }
        val candidate = registered.find { it.type == preferred }
        val agreement = candidate?.let { CapabilityNegotiation.negotiate(it.capabilities, authenticatedPeer) }
        selected = if (UltraEngineFeatures.UltraEngineEnabled && candidate?.isAvailable() == true &&
            agreement?.transports?.contains(preferred) == true) candidate
        else registered.single { it.type == TransportType.Nearby }
        check(selected.isAvailable()) { "No available transport" }
        return selected.type
    }

    override fun setPage(page: String) = selected.setPage(page)
    override fun armQrTarget(token: String?) = selected.armQrTarget(token)
    override fun advertise() = selected.advertise()
    override fun discover(uris: List<Uri>) = selected.discover(uris)
    override fun connect(peer: Peer) = selected.connect(peer)
    override fun answerConnection(accept: Boolean) = selected.answerConnection(accept)
    override fun selectFiles(uris: List<Uri>) = selected.selectFiles(uris)
    override fun cancelTransfer() = selected.cancelTransfer()
    override fun disconnect() = selected.disconnect()
    override fun close() { registered.forEach { it.close() } }
}
