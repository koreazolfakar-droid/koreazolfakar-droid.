package app.swiftdrop

/** Measurements use monotonic callback time, never display an assumed radio speed. */
data class TransferMeasurement(val bytesPerSecond: Double? = null, val remainingSeconds: Long? = null)

class TransferMeasurements {
    private var payloadId: Long? = null
    private var startMillis = 0L
    private var startBytes = 0L
    private var lastBytes = 0L

    @Synchronized
    fun sample(id: Long, bytes: Long, total: Long, nowMillis: Long): TransferMeasurement {
        if (payloadId != id || bytes < lastBytes || nowMillis < startMillis) {
            payloadId = id
            startMillis = nowMillis
            startBytes = bytes
            lastBytes = bytes
            return TransferMeasurement()
        }
        lastBytes = bytes
        val elapsed = nowMillis - startMillis
        val delta = bytes - startBytes
        if (elapsed < 1000 || delta <= 0) return TransferMeasurement()
        val speed = delta.toDouble() * 1000 / elapsed
        // Wait for a longer observation before claiming an ETA.
        val eta = if (elapsed >= 2000 && total > 0 && bytes < total)
            kotlin.math.ceil((total - bytes) / speed).toLong() else null
        return TransferMeasurement(speed, eta)
    }

    @Synchronized
    fun reset() { payloadId = null }
}
