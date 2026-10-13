package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class TransferCapabilitiesTest {
    private val future = TransferCapabilities(setOf(1, 2), setOf(TransportType.Nearby, TransportType.LocalTcp), true, true)
    @Test fun unknownAndLegacyPeersOnlyUseLegacyNearby() {
        for (peer in listOf(null, TransferCapabilities.LEGACY)) {
            val result = CapabilityNegotiation.negotiate(future, peer)
            assertEquals(NegotiatedCapabilities(1, setOf(TransportType.Nearby), false, false), result)
            assertFalse(CapabilityNegotiation.maySendCapabilityHandshake(peer))
        }
        assertFalse(UltraEngineFeatures.UltraEngineEnabled)
        assertFalse(CapabilityNegotiation.maySendCapabilityHandshake(future))
    }
    @Test fun unsupportedVersionsAndTransportsAreRejected() {
        assertNull(CapabilityNegotiation.negotiate(TransferCapabilities.LEGACY,
            TransferCapabilities(setOf(2), setOf(TransportType.Nearby))))
        assertNull(CapabilityNegotiation.negotiate(TransferCapabilities.LEGACY,
            TransferCapabilities(setOf(1), setOf(TransportType.LocalTcp))))
    }
    @Test fun negotiationUsesIntersectionAndRequiresBothSidesForOptionalFeatures() {
        val peer = TransferCapabilities(setOf(2, 3), setOf(TransportType.LocalTcp), false, true)
        assertEquals(NegotiatedCapabilities(2, setOf(TransportType.LocalTcp), false, true),
            CapabilityNegotiation.negotiate(future, peer))
    }
    @Test fun schedulerBudgetRejectsUnboundedOrUndersizedConfigurations() {
        for (config in listOf<() -> UltraTransferEngineConfig>(
            { UltraTransferEngineConfig(maximumStreams = 0) },
            { UltraTransferEngineConfig(maximumStreams = 5) },
            { UltraTransferEngineConfig(minimumChunkBytes = 0) },
            { UltraTransferEngineConfig(bufferBudgetBytes = 1) })) {
            try { config(); fail() } catch (_: IllegalArgumentException) {}
        }
        assertEquals(1, UltraTransferEngineConfig().maximumStreams)
    }
}
