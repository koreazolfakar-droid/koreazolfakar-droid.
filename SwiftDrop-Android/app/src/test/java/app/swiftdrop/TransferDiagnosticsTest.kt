package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class TransferDiagnosticsTest {
    @Test fun metricsUseMeasuredBytesAndDurationWithoutSensitiveFields() {
        val transfer = FileTransferSession(payloadId = 99, direction = TransferDirection.Sending,
            metadata = TransferFileMetadata("sensitive-name", 1000), startedMillis = 100,
            bytes = 500, state = FileTransferState.Failed, failure = TransferFailureCategory.Transport)
        val metric = TransferDiagnostic.from(transfer, SessionState.Connected, 1100)
        assertEquals(1000, metric.durationMillis)
        assertEquals(500.0, metric.bytesPerSecond!!, 0.01)
        assertEquals(TransferFailureCategory.Transport, metric.failureCategory)
        assertFalse(metric.toString().contains("sensitive-name"))
        assertFalse(metric.toString().contains(transfer.transferId))
        assertNull(TransferDiagnostic.from(transfer, SessionState.Connected, 100).bytesPerSecond)
    }
}
