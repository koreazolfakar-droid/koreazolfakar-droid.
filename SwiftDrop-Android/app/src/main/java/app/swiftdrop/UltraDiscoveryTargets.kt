package app.swiftdrop

import java.net.Inet4Address
import java.net.InetAddress

/**
 * Restrict Ultra discovery to the Wi-Fi link supplied by Android.
 * Unicast to the DHCP gateway (usually the local-only hotspot host) before
 * trying that interface's broadcast address. No LAN scanning or DNS lookup.
 */
internal object UltraDiscoveryTargets {
    fun candidates(
        gateways: List<InetAddress>,
        addresses: List<Pair<InetAddress, Int>>
    ): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        gateways.filterIsInstance<Inet4Address>().forEach(result::add)
        addresses.forEach { (address, prefix) ->
            val ipv4 = address as? Inet4Address ?: return@forEach
            if (prefix !in 1..30) return@forEach
            val octets = ipv4.address
            val network = octets.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff) }
            val mask = -1 shl (32 - prefix)
            val broadcast = network or mask.inv()
            result += InetAddress.getByAddress(ByteArray(4) { index ->
                (broadcast ushr (24 - 8 * index)).toByte()
            })
        }
        result += InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))
        return result.toList()
    }
}
