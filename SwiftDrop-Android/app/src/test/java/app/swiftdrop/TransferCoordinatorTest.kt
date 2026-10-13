package app.swiftdrop

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class TransferCoordinatorTest {
    private class FakeTransport(override val type: TransportType) : TransferTransport {
        override val state = MutableStateFlow(TransferUiState())
        override val capabilities = TransferCapabilities(setOf(1, 2), setOf(type))
        var calls = mutableListOf<String>()
        override fun isAvailable() = true
        override fun setPage(page: String) { calls += "page:$page" }
        override fun armQrTarget(token: String?) { calls += "qr" }
        override fun advertise() { calls += "advertise" }
        override fun discover(uris: List<Uri>) { calls += "discover" }
        override fun connect(peer: Peer) { calls += "connect" }
        override fun answerConnection(accept: Boolean) { calls += "approval:$accept" }
        override fun selectFiles(uris: List<Uri>) { calls += "select" }
        override fun cancelTransfer() { calls += "cancel" }
        override fun disconnect() { calls += "disconnect" }
        override fun close() { calls += "close" }
    }
    @Test fun disabledEngineAlwaysSelectsNearbyEvenWithCapablePeer() {
        val nearby = FakeTransport(TransportType.Nearby)
        val tcp = FakeTransport(TransportType.LocalTcp)
        val coordinator = TransferCoordinator(listOf(nearby, tcp))
        assertEquals(TransportType.Nearby, coordinator.selectTransport(TransportType.LocalTcp, tcp.capabilities))
        assertSame(nearby.state, coordinator.state)
        coordinator.selectFiles(emptyList())
        coordinator.discover(emptyList())
        coordinator.advertise()
        coordinator.armQrTarget(null)
        coordinator.connect(Peer("id", "peer"))
        coordinator.answerConnection(true)
        coordinator.cancelTransfer()
        coordinator.disconnect()
        assertEquals(listOf("select", "discover", "advertise", "qr", "connect", "approval:true", "cancel", "disconnect"), nearby.calls)
        assertTrue(tcp.calls.isEmpty())
        coordinator.close()
        assertEquals("close", nearby.calls.last())
        assertEquals(listOf("close"), tcp.calls)
    }
    @Test fun transportCannotChangeDuringConnectionOrSave() {
        val nearby = FakeTransport(TransportType.Nearby)
        val coordinator = TransferCoordinator(listOf(nearby))
        for (state in listOf(TransferUiState(sessionState = SessionState.Advertising), TransferUiState(isDiscovering = true), TransferUiState(connectingId = "id"),
            TransferUiState(pending = PendingConnection("id", "peer", "1234")),
            TransferUiState(connectedName = "peer"),
            TransferUiState(progress = TransferProgress("a", "Receiving", 0, 1, outcome = TransferOutcome.Saving)))) {
            nearby.state.value = state
            try { coordinator.selectTransport(TransportType.Nearby); fail() } catch (_: IllegalStateException) {}
        }
    }
    @Test fun registryRequiresOneUniqueNearbyFallback() {
        for (transports in listOf(emptyList(), listOf(FakeTransport(TransportType.LocalTcp)),
            listOf(FakeTransport(TransportType.Nearby), FakeTransport(TransportType.Nearby)))) {
            try { TransferCoordinator(transports); fail() } catch (_: IllegalArgumentException) {}
        }
    }
}
