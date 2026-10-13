package app.swiftdrop

// SwiftDrop @feature Codex 13/10/2026 In-memory protocol policy; no new wire messages.
object UltraEngineFeatures {
    const val UltraEngineEnabled = false
}

data class TransferCapabilities(
    val protocolVersions: Set<Int>,
    val transports: Set<TransportType>,
    val supportsResume: Boolean = false,
    val supportsSha256: Boolean = false
) {
    init {
        require(protocolVersions.isNotEmpty() && protocolVersions.all { it > 0 })
        require(transports.isNotEmpty())
    }
    companion object {
        val LEGACY = TransferCapabilities(setOf(1), setOf(TransportType.Nearby))
    }
}

data class NegotiatedCapabilities(
    val protocolVersion: Int,
    val transports: Set<TransportType>,
    val resume: Boolean,
    val sha256: Boolean
)

/** Unknown peers are legacy. Future capability evidence must be authenticated before use. */
object CapabilityNegotiation {
    fun negotiate(local: TransferCapabilities, authenticatedPeer: TransferCapabilities?): NegotiatedCapabilities? {
        val peer = authenticatedPeer ?: TransferCapabilities.LEGACY
        val version = local.protocolVersions.intersect(peer.protocolVersions).maxOrNull() ?: return null
        val transports = local.transports.intersect(peer.transports)
        if (transports.isEmpty()) return null
        return NegotiatedCapabilities(version, transports,
            version > 1 && local.supportsResume && peer.supportsResume,
            version > 1 && local.supportsSha256 && peer.supportsSha256)
    }

    // Phase 1 deliberately has no capability handshake encoder or sender.
    fun maySendCapabilityHandshake(authenticatedPeer: TransferCapabilities?): Boolean =
        UltraEngineFeatures.UltraEngineEnabled && authenticatedPeer?.protocolVersions?.any { it > 1 } == true
}

/** Budget contract for the future scheduler; no sockets, threads or chunk execution in Phase 1. */
data class UltraTransferEngineConfig(
    val minimumChunkBytes: Int = 64 * 1024,
    val maximumChunkBytes: Int = 1024 * 1024,
    val maximumStreams: Int = 1,
    val bufferBudgetBytes: Long = 4L * 1024 * 1024
) {
    init {
        require(minimumChunkBytes > 0 && maximumChunkBytes >= minimumChunkBytes)
        require(maximumStreams in 1..4)
        require(bufferBudgetBytes >= maximumChunkBytes.toLong() * maximumStreams)
    }
}
