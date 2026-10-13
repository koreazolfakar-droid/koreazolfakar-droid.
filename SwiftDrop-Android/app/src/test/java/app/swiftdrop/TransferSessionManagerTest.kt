package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TransferSessionManagerTest {
    private fun connected(manager: TransferSessionManager): SessionId {
        val id = manager.begin()
        assertTrue(manager.connection(id, SessionState.Discovering))
        assertTrue(manager.connection(id, SessionState.Connecting))
        assertTrue(manager.connection(id, SessionState.AwaitingApproval))
        assertTrue(manager.connection(id, SessionState.Connecting))
        assertTrue(manager.connection(id, SessionState.Connected))
        return id
    }
    @Test fun approvalIsRequiredBeforeConnectedAndTerminalSessionsCannotRevive() {
        val manager = TransferSessionManager()
        val id = manager.begin()
        assertFalse(manager.connection(id, SessionState.Connected))
        assertTrue(manager.connection(id, SessionState.Advertising))
        assertTrue(manager.connection(id, SessionState.AwaitingApproval))
        assertFalse(manager.connection(id, SessionState.Connected))
        assertTrue(manager.connection(id, SessionState.Cancelled))
        assertFalse(manager.connection(id, SessionState.Connecting))
    }
    @Test fun duplicateAndLateCompletionCannotProduceAnotherEvent() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        assertTrue(manager.register(id, 1, TransferDirection.Sending, TransferFileMetadata("private", 10), 0))
        assertTrue(manager.progress(id, 1, 10, 1000))
        assertTrue(manager.transition(id, 1, FileTransferState.Complete))
        assertFalse(manager.transition(id, 1, FileTransferState.Complete))
        assertFalse(manager.progress(id, 1, 9, 1001))
        assertFalse(manager.transition(id, 1, FileTransferState.Cancelled))
        assertFalse(manager.register(id, 1, TransferDirection.Receiving, null, 1001))
        assertNull(manager.get(id, 1)?.metadata)
    }
    @Test fun reorderedMetadataAndNetworkCompletionRemainSafe() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Receiving, null, 0)
        assertFalse(manager.transition(id, 1, FileTransferState.Complete))
        assertTrue(manager.transition(id, 1, FileTransferState.Saving))
        assertTrue(manager.attachMetadata(id, 1, TransferFileMetadata("name", 0)))
        assertFalse(manager.attachMetadata(id, 1, TransferFileMetadata("replacement", 100)))
        var published = 0
        manager.finalizeReceive(id, 1) { published++ }
        assertEquals(1, published)
        assertFalse(manager.canSave(id, 1))
    }
    @Test fun disconnectFailsPartialTransfersButRetainsFullyDeliveredReceives() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Receiving, null, 0)
        manager.register(id, 2, TransferDirection.Receiving, null, 0)
        manager.register(id, 3, TransferDirection.Sending, null, 0)
        manager.transition(id, 2, FileTransferState.Saving)
        manager.connection(id, SessionState.Disconnected)
        assertEquals(FileTransferState.Failed, manager.get(id, 1)?.state)
        assertEquals(FileTransferState.Failed, manager.get(id, 3)?.state)
        assertTrue(manager.canSave(id, 2))
        manager.finalizeReceive(id, 2) {}
        assertEquals(FileTransferState.Complete, manager.get(id, 2)?.state)
    }
    @Test fun explicitCancelAfterRemoteDisconnectAbortsPendingSave() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Receiving, null, 0)
        manager.transition(id, 1, FileTransferState.Saving)
        manager.connection(id, SessionState.Disconnected)
        assertTrue(manager.canSave(id, 1))
        assertTrue(manager.connection(id, SessionState.Cancelled))
        assertFalse(manager.canSave(id, 1))
        assertEquals(FileTransferState.Cancelled, manager.get(id, 1)?.state)
    }
    @Test fun cancellationBlocksPublicationAndNewSessionsRejectStaleIds() {
        val manager = TransferSessionManager()
        val old = connected(manager)
        manager.register(old, 1, TransferDirection.Receiving, null, 0)
        manager.transition(old, 1, FileTransferState.Saving)
        manager.connection(old, SessionState.Cancelled)
        var published = false
        try { manager.finalizeReceive(old, 1) { published = true }; fail() } catch (_: IllegalStateException) {}
        assertFalse(published)
        val next = connected(manager)
        assertNotEquals(old, next)
        manager.register(next, 1, TransferDirection.Receiving, null, 1)
        assertFalse(manager.transition(old, 1, FileTransferState.Complete))
        assertFalse(manager.progress(old, 1, 10, 2))
        assertNull(manager.get(old, 1))
        assertEquals(FileTransferState.Active, manager.get(next, 1)?.state)
    }
    @Test fun regressingCountersAreRejectedAndLargeFilesUseLongs() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Sending, TransferFileMetadata("large", 4_000_000_000), 10)
        assertTrue(manager.progress(id, 1, 3_000_000_000, 1000))
        assertFalse(manager.progress(id, 1, 1, 1001))
        assertFalse(manager.progress(id, 1, 4_000_000_000, 1))
        assertEquals(3_000_000_000, manager.get(id, 1)?.bytes)
    }
    @Test fun ledgerIsBoundedWithoutEvictingDuplicateGuards() {
        val manager = TransferSessionManager(1)
        val id = connected(manager)
        assertTrue(manager.register(id, 1, TransferDirection.Sending, null, 0))
        manager.transition(id, 1, FileTransferState.Complete)
        assertFalse(manager.register(id, 2, TransferDirection.Sending, null, 0))
        assertFalse(manager.register(id, 1, TransferDirection.Sending, null, 0))
        val next = connected(manager)
        assertTrue(manager.register(next, 2, TransferDirection.Sending, null, 0))
    }
    @Test fun racingCompletionCallbacksHaveExactlyOneWinner() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Sending, null, 0)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = (1..32).map { pool.submit<Boolean> {
                start.await()
                manager.transition(id, 1, FileTransferState.Complete)
            } }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
    @Test fun cancellationAndPublicationHaveOneConsistentOutcome() {
        repeat(40) {
            val manager = TransferSessionManager()
            val id = connected(manager)
            manager.register(id, 1, TransferDirection.Receiving, null, 0)
            manager.transition(id, 1, FileTransferState.Saving)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val publication = pool.submit<Boolean> {
                    start.await()
                    try { manager.finalizeReceive(id, 1) {}; true } catch (_: IllegalStateException) { false }
                }
                val cancellation = pool.submit { start.await(); manager.connection(id, SessionState.Cancelled) }
                start.countDown()
                val committed = publication.get(5, TimeUnit.SECONDS)
                cancellation.get(5, TimeUnit.SECONDS)
                assertEquals(if (committed) FileTransferState.Complete else FileTransferState.Cancelled,
                    manager.get(id, 1)?.state)
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun failedPublicationDoesNotReportSuccess() {
        val manager = TransferSessionManager()
        val id = connected(manager)
        manager.register(id, 1, TransferDirection.Receiving, null, 0)
        manager.transition(id, 1, FileTransferState.Saving)
        try { manager.finalizeReceive(id, 1) { error("disk full") }; fail() } catch (_: IllegalStateException) {}
        assertEquals(FileTransferState.Saving, manager.get(id, 1)?.state)
        assertTrue(manager.transition(id, 1, FileTransferState.Failed, TransferFailureCategory.Storage))
    }
}
