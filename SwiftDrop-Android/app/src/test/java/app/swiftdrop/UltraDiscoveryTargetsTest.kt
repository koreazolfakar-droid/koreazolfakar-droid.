package app.swiftdrop

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UltraDiscoveryTargetsTest {
    private fun ip(v: String) = InetAddress.getByName(v)

    @Test fun prefersGatewayUnicastBeforeBroadcastFallback() {
        val candidates = UltraDiscoveryTargets.candidates(
            listOf(ip("192.168.49.1")),
            listOf(ip("192.168.49.57") to 24)
        ).map { it.hostAddress }
        assertEquals(
            listOf("192.168.49.1","192.168.49.255","255.255.255.255"),
            candidates
        )
    }

    @Test fun avoidsDuplicateGatewayAndBrokenPrefix() {
        val targets = UltraDiscoveryTargets.candidates(
            listOf(ip("192.168.4.255"),ip("192.168.4.255")),
            listOf(ip("192.168.4.12") to 24,ip("10.1.2.3") to 0)
        ).map { it.hostAddress }
        assertEquals(listOf("192.168.4.255","255.255.255.255"),targets)
        assertFalse("10.255.255.255" in targets)
    }

    @Test fun toleratesNoDhcpGateway() {
        assertEquals(
            listOf("255.255.255.255"),
            UltraDiscoveryTargets.candidates(emptyList(), emptyList())
                .map { it.hostAddress }
        )
    }
}
