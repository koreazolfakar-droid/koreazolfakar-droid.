package app.swiftdrop

import java.util.UUID

// SwiftDrop @feature Codex 13/10/2026 Explicit synchronized session and transfer state machines.
enum class SessionState { Idle, Advertising, Discovering, AwaitingApproval, Connecting, Connected, Disconnected, Failed, Cancelled, Closed }
enum class FileTransferState { Active, Saving, Complete, Failed, Cancelled }
enum class TransferDirection { Sending, Receiving }
enum class TransferFailureCategory { None, ConnectionLost, Transport, Metadata, Storage, Cancelled, SessionReplaced }
data class SessionId(val value: String = UUID.randomUUID().toString())
data class TransferFileMetadata(val name: String, val size: Long) {
    init { require(size >= -1) }
}
data class FileTransferSession(
    val transferId: String = UUID.randomUUID().toString(),
    val payloadId: Long,
    val direction: TransferDirection,
    val metadata: TransferFileMetadata?,
    val state: FileTransferState = FileTransferState.Active,
    val bytes: Long = 0,
    val startedMillis: Long,
    val updatedMillis: Long = startedMillis,
    val failure: TransferFailureCategory = TransferFailureCategory.None
)

/** Keeps only metadata/counters, with a bounded per-connection callback ledger. */
class TransferSessionManager(private val maximumTransfers: Int = 4096) {
    init { require(maximumTransfers > 0) }
    private var id = SessionId()
    private var connection = SessionState.Idle
    private val transfers = LinkedHashMap<Long, FileTransferSession>()
    @Synchronized fun currentId() = id
    @Synchronized fun state() = connection
    @Synchronized fun begin(): SessionId {
        id = SessionId()
        connection = SessionState.Idle
        transfers.clear()
        return id
    }
    @Synchronized fun isCurrent(session: SessionId) = session == id
    @Synchronized fun connection(session: SessionId, next: SessionState): Boolean {
        if (session != id || next == connection) return false
        val allowed = when (connection) {
            SessionState.Idle -> setOf(SessionState.Advertising, SessionState.Discovering)
            SessionState.Advertising, SessionState.Discovering -> setOf(SessionState.AwaitingApproval, SessionState.Connecting)
            SessionState.AwaitingApproval -> setOf(SessionState.Connecting)
            SessionState.Connecting -> setOf(SessionState.AwaitingApproval, SessionState.Connected)
            else -> emptySet()
        } + when (connection) {
            SessionState.Closed -> emptySet()
            SessionState.Cancelled, SessionState.Failed -> setOf(SessionState.Closed)
            SessionState.Disconnected -> setOf(SessionState.Cancelled, SessionState.Closed)
            else -> setOf(SessionState.Disconnected, SessionState.Failed, SessionState.Cancelled, SessionState.Closed)
        }
        if (next !in allowed) return false
        connection = next
        if (next in setOf(SessionState.Disconnected, SessionState.Failed, SessionState.Cancelled, SessionState.Closed)) {
            transfers.replaceAll { _, transfer ->
                // Fully delivered receives may finish saving after a remote disconnect.
                if (next == SessionState.Disconnected && transfer.state == FileTransferState.Saving) transfer
                else if (transfer.state in setOf(FileTransferState.Active, FileTransferState.Saving)) transfer.copy(
                    state = if (next == SessionState.Cancelled) FileTransferState.Cancelled else FileTransferState.Failed,
                    failure = when (next) {
                        SessionState.Cancelled -> TransferFailureCategory.Cancelled
                        SessionState.Disconnected -> TransferFailureCategory.ConnectionLost
                        SessionState.Failed -> TransferFailureCategory.Transport
                        else -> TransferFailureCategory.SessionReplaced
                    },
                    metadata = null)
                else transfer
            }
        }
        return true
    }
    @Synchronized fun register(session: SessionId, payloadId: Long, direction: TransferDirection,
        metadata: TransferFileMetadata?, nowMillis: Long): Boolean {
        if (session != id || connection != SessionState.Connected || transfers.containsKey(payloadId) ||
            transfers.size >= maximumTransfers) return false
        transfers[payloadId] = FileTransferSession(payloadId = payloadId, direction = direction,
            metadata = metadata, startedMillis = nowMillis)
        return true
    }
    @Synchronized fun attachMetadata(session: SessionId, payloadId: Long, metadata: TransferFileMetadata): Boolean {
        val transfer = get(session, payloadId) ?: return false
        if (transfer.direction != TransferDirection.Receiving || transfer.state !in setOf(FileTransferState.Active, FileTransferState.Saving) || transfer.metadata != null) return false
        transfers[payloadId] = transfer.copy(metadata = metadata)
        return true
    }
    @Synchronized fun get(session: SessionId, payloadId: Long): FileTransferSession? = if (session == id) transfers[payloadId] else null
    @Synchronized fun progress(session: SessionId, payloadId: Long, bytes: Long, nowMillis: Long): Boolean {
        val transfer = get(session, payloadId) ?: return false
        if (transfer.state != FileTransferState.Active || bytes < transfer.bytes || nowMillis < transfer.updatedMillis) return false
        transfers[payloadId] = transfer.copy(bytes = bytes, updatedMillis = nowMillis)
        return true
    }
    @Synchronized fun transition(session: SessionId, payloadId: Long, next: FileTransferState,
        failure: TransferFailureCategory = TransferFailureCategory.None): Boolean {
        val transfer = get(session, payloadId) ?: return false
        val allowed = when (transfer.state) {
            FileTransferState.Active -> setOf(FileTransferState.Failed, FileTransferState.Cancelled) +
                if (transfer.direction == TransferDirection.Sending) setOf(FileTransferState.Complete) else setOf(FileTransferState.Saving)
            FileTransferState.Saving -> setOf(FileTransferState.Complete, FileTransferState.Failed, FileTransferState.Cancelled)
            else -> emptySet()
        }
        if (next !in allowed) return false
        transfers[payloadId] = transfer.copy(state = next, failure = failure,
            metadata = if (next in setOf(FileTransferState.Complete, FileTransferState.Failed, FileTransferState.Cancelled)) null else transfer.metadata)
        return true
    }
    @Synchronized fun hasInFlight(session: SessionId) = session == id && transfers.values.any {
        it.state == FileTransferState.Active || it.state == FileTransferState.Saving
    }
    @Synchronized fun canSave(session: SessionId, payloadId: Long) = get(session, payloadId)?.state == FileTransferState.Saving

    /** Cancellation and destination publication serialize on the same monitor. No file copying under this lock. */
    @Synchronized fun <T> finalizeReceive(session: SessionId, payloadId: Long, publish: () -> T): T {
        check(canSave(session, payloadId)) { "Transfer session cancelled" }
        val result = publish()
        check(transition(session, payloadId, FileTransferState.Complete))
        return result
    }
}
