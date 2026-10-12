package app.swiftdrop

/**
 * The SDK can invoke progress callbacks far more frequently than the UI can render.
 * Keep all transport callbacks/measurements; limit only presentation updates.
 */
class ProgressEmissionGate(private val minIntervalMillis: Long = 100L) {
    private data class Snapshot(val payloadId: Long,val bytes: Long,val millis: Long)
    private var last: Snapshot? = null

    @Synchronized fun shouldEmit(id: Long,bytes: Long,timeMillis: Long,terminal: Boolean): Boolean {
        val previous=last
        val emit=terminal || previous==null || previous.payloadId!=id ||
            bytes<previous.bytes || timeMillis<previous.millis ||
            timeMillis-previous.millis>=minIntervalMillis
        if (emit) last=Snapshot(id,bytes,timeMillis)
        return emit
    }
    @Synchronized fun reset() { last=null }
}
