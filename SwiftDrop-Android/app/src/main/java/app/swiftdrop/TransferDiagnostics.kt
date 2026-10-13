package app.swiftdrop

// SwiftDrop @feature Codex 13/10/2026 Structured local metrics exclude names, IDs and exception text.
sealed interface TransferDiagnosticEvent

data class TransferDiagnostic(
    val transport: TransportType,
    val sessionState: SessionState,
    val transferState: FileTransferState,
    val durationMillis: Long,
    val bytesTransferred: Long,
    val bytesPerSecond: Double?,
    val failureCategory: TransferFailureCategory,
    val reconnectionAttempts: Int = 0
) : TransferDiagnosticEvent {
    companion object {
        fun from(transfer: FileTransferSession, session: SessionState, nowMillis: Long): TransferDiagnostic {
            val duration = (nowMillis - transfer.startedMillis).coerceAtLeast(0)
            return TransferDiagnostic(TransportType.Nearby, session, transfer.state, duration,
                transfer.bytes, if (duration > 0) transfer.bytes.toDouble() * 1000 / duration else null,
                transfer.failure)
        }
    }
}

data class ConnectionDiagnostic(
    val transport: TransportType,
    val sessionState: SessionState,
    val failureCategory: TransferFailureCategory = TransferFailureCategory.None,
    val reconnectionAttempts: Int = 0
) : TransferDiagnosticEvent

fun interface TransferDiagnosticSink { fun record(event: TransferDiagnosticEvent) }
