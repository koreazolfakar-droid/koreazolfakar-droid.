package app.swiftdrop

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Looper
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises production callbacks and file-send sequencing without radios/Play Services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NearbyLifecycleTest {
    private val io = Executors.newSingleThreadExecutor()
    private val lifecycle = mutableListOf<ConnectionLifecycleCallback>()
    private val discovery = mutableListOf<EndpointDiscoveryCallback>()
    private val payloadCallbacks = mutableListOf<PayloadCallback>()
    private val sent = mutableListOf<Payload>()
    private val cancelled = mutableListOf<Long>()
    private val metrics = mutableListOf<TransferDiagnostic>()
    private var deferMetadata: TaskCompletionSource<Void>? = null
    private var throwOnPayloadType: Int? = null
    private lateinit var manager: NearbyTransferManager
    private val temporaryProvider = object : ContentProvider() {
        val files = mutableMapOf<Uri, File>()
        val deleted = mutableListOf<Uri>()
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(requireNotNull(files[uri]), ParcelFileDescriptor.MODE_READ_ONLY)
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted += uri
            return if (files.remove(uri)?.delete() == true) 1 else 0
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor = error("Query not supported by temporary payload fixture")
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Insert not supported by temporary payload fixture")
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = error("Update not supported by temporary payload fixture")
    }
    private fun incomingFile(id: Long, content: String): Pair<Payload, Uri> {
        val source = File.createTempFile("incoming-", ".tmp", RuntimeEnvironment.getApplication().cacheDir)
        source.writeText(content)
        val uri = Uri.parse("content://app.swiftdrop.testtemporary/${source.name}")
        temporaryProvider.files[uri] = source
        val file = mock(Payload.File::class.java)
        `when`(file.asUri()).thenReturn(uri)
        val payload = mock(Payload::class.java)
        `when`(payload.id).thenReturn(id)
        `when`(payload.type).thenReturn(Payload.Type.FILE)
        `when`(payload.asFile()).thenReturn(file)
        return payload to uri
    }
    private fun downloads() = File(RuntimeEnvironment.getApplication().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SwiftDrop")

    private val client = Proxy.newProxyInstance(ConnectionsClient::class.java.classLoader,
        arrayOf(ConnectionsClient::class.java)) { _, method, arguments ->
        when (method.name) {
            "startAdvertising" -> lifecycle += arguments!![2] as ConnectionLifecycleCallback
            "startDiscovery" -> discovery += arguments!![1] as EndpointDiscoveryCallback
            "requestConnection" -> lifecycle += arguments!![2] as ConnectionLifecycleCallback
            "acceptConnection" -> payloadCallbacks += arguments!![1] as PayloadCallback
            "sendPayload" -> {
                val payload = arguments!![1] as Payload
                if (payload.type == throwOnPayloadType) error("Nearby send unavailable")
                sent += payload
                if (payload.type == Payload.Type.BYTES && deferMetadata != null) return@newProxyInstance deferMetadata!!.task
            }
            "cancelPayload" -> cancelled += arguments!![0] as Long
            "toString" -> return@newProxyInstance "Fake Nearby client"
            "hashCode" -> return@newProxyInstance 1
            "equals" -> return@newProxyInstance false
        }
        if (method.returnType == com.google.android.gms.tasks.Task::class.java) Tasks.forResult<Void>(null) else null
    } as ConnectionsClient

    @Before fun setup() {
        temporaryProvider.attachInfo(RuntimeEnvironment.getApplication(), ProviderInfo().apply {
            authority = "app.swiftdrop.testtemporary"
        })
        ShadowContentResolver.registerProviderInternal("app.swiftdrop.testtemporary", temporaryProvider)
        manager = NearbyTransferManager(RuntimeEnvironment.getApplication(), TransferDiagnosticSink { if (it is TransferDiagnostic) metrics += it }, client, io)
    }
    @After fun cleanup() {
        manager.close()
        io.awaitTermination(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun drain() {
        repeat(4) {
            io.submit {}.get(5, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun connectReceiving(waitForIo: Boolean = true) {
        manager.advertise()
        shadowOf(Looper.getMainLooper()).idle()
        lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", true))
        assertNotNull(manager.state.value.pending)
        manager.answerConnection(true)
        lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
        if (waitForIo) drain()
        assertEquals("Peer", manager.state.value.connectedName)
    }
    private fun update(id: Long, status: Int, bytes: Long = 10, total: Long = 10) =
        PayloadTransferUpdate.Builder().setPayloadId(id).setStatus(status)
            .setBytesTransferred(bytes).setTotalBytes(total).build()

    @Test fun staleLifecycleAndDiscoveryCallbacksCannotCorruptNewSearch() {
        manager.advertise()
        val old = lifecycle.last()
        manager.discover(listOf(Uri.parse("content://test/selected")))
        val previousDiscovery = discovery.last()
        manager.advertise()
        shadowOf(Looper.getMainLooper()).idle()
        old.onConnectionInitiated("stale", ConnectionInfo("Old", "1234", true))
        old.onConnectionResult("stale", ConnectionResolution(Status.RESULT_SUCCESS))
        previousDiscovery.onEndpointFound("stale", DiscoveredEndpointInfo("Old", "app.swiftdrop.nearby"))
        assertNull(manager.state.value.pending)
        assertNull(manager.state.value.connectedName)
        assertTrue(manager.state.value.peers.isEmpty())
        assertTrue(manager.state.value.isAdvertising)
    }
    @Test fun unapprovedConnectionAndSecondPeerNeverBecomeConnected() {
        manager.advertise()
        val callback = lifecycle.last()
        callback.onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", true))
        callback.onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
        assertNull(manager.state.value.connectedName)
        manager.advertise()
        lifecycle.last().onConnectionInitiated("one", ConnectionInfo("One", "1234", true))
        lifecycle.last().onConnectionInitiated("two", ConnectionInfo("Two", "1234", true))
        assertEquals("one", manager.state.value.pending?.endpointId)
        manager.answerConnection(false)
        lifecycle.last().onConnectionResult("one", ConnectionResolution(Status.RESULT_SUCCESS))
        assertNull(manager.state.value.connectedName)
    }
    @Test fun failedConnectionReportsFailureAndCanStartFreshSession() {
        manager.advertise()
        lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", true))
        manager.answerConnection(true)
        lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status(13)))
        assertTrue(manager.state.value.hasError)
        assertNull(manager.state.value.connectedName)
        connectReceiving()
    }
    @Test fun receiveCancellationAndLateSuccessAreTerminal() {
        connectReceiving()
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "private.txt", 10)))
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.IN_PROGRESS, 5))
        assertEquals(5L, manager.state.value.progress?.bytes)
        manager.cancelTransfer()
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS))
        assertEquals(TransferOutcome.Cancelled, manager.state.value.progress?.outcome)
        assertTrue(manager.state.value.history.isEmpty())
        manager.advertise()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "old.txt", 10)))
        assertNull(manager.state.value.progress)
    }
    @Test fun staleFileReleasesDescriptorWithoutDeletingUntrackedUri() {
        connectReceiving()
        val oldCallback = payloadCallbacks.last()
        val (payload, temporary) = incomingFile(42, "untracked")
        manager.advertise()
        oldCallback.onPayloadReceived("peer", payload)
        drain()
        verify(payload).close()
        assertFalse(temporaryProvider.deleted.contains(temporary))
        assertNull(manager.state.value.progress)
    }
    @Test fun duplicateReceiveCompletionPublishesOnceAndKeepsExistingFiles() {
        connectReceiving()
        downloads().mkdirs()
        val previous = File(downloads(), "same.txt").apply { writeText("existing") }
        val (payload, temporary) = incomingFile(42, "received")
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", payload)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 8, 8))
        // Metadata can arrive after network completion.
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "same.txt", 8)))
        drain()
        callback.onPayloadReceived("peer", payload)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 8, 8))
        drain()
        assertEquals("existing", previous.readText())
        assertEquals("received", File(downloads(), "same (1).txt").readText())
        assertEquals(1, manager.state.value.history.size)
        assertEquals(TransferOutcome.Complete, manager.state.value.progress?.outcome)
        assertEquals(listOf(temporary), temporaryProvider.deleted)
        assertFalse(downloads().listFiles()!!.any { it.name.endsWith(".partial") })
    }
    @Test fun invalidReceivedLengthCleansOnlyPendingResources() {
        connectReceiving()
        downloads().mkdirs()
        val previous = File(downloads(), "valid.txt").apply { writeText("keep") }
        val (payload, temporary) = incomingFile(42, "short")
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "new.txt", 10)))
        callback.onPayloadReceived("peer", payload)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 10, 10))
        drain()
        assertEquals("keep", previous.readText())
        assertEquals(TransferOutcome.Failed, manager.state.value.progress?.outcome)
        assertEquals(TransferFailureCategory.Storage, metrics.last().failureCategory)
        assertTrue(manager.state.value.history.isEmpty())
        assertEquals(listOf(temporary), temporaryProvider.deleted)
        assertEquals(listOf("valid.txt"), downloads().listFiles()!!.map { it.name })
    }
    @Test fun cancellationWhileSavingNeverPublishesDestination() {
        connectReceiving()
        val gate = CountDownLatch(1)
        io.submit { gate.await(30, TimeUnit.SECONDS) }
        val (payload, temporary) = incomingFile(42, "received")
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "cancelled.txt", 8)))
        callback.onPayloadReceived("peer", payload)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 8, 8))
        assertEquals(TransferOutcome.Saving, manager.state.value.progress?.outcome)
        manager.cancelTransfer()
        gate.countDown()
        drain()
        assertEquals(TransferOutcome.Cancelled, manager.state.value.progress?.outcome)
        assertTrue(manager.state.value.history.isEmpty())
        assertFalse(File(downloads(), "cancelled.txt").exists())
        assertFalse(downloads().listFiles()?.any { it.name.endsWith(".partial") } == true)
        assertEquals(listOf(temporary), temporaryProvider.deleted)
    }
    @Test fun savingCanFinishAfterRemoteDisconnect() {
        connectReceiving()
        val gate = CountDownLatch(1)
        io.submit { gate.await(30, TimeUnit.SECONDS) }
        val (payload, _) = incomingFile(42, "received")
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "received.txt", 8)))
        callback.onPayloadReceived("peer", payload)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 8, 8))
        lifecycle.last().onDisconnected("peer")
        gate.countDown()
        drain()
        assertEquals(TransferOutcome.Complete, manager.state.value.progress?.outcome)
        assertEquals(1, manager.state.value.history.size)
        assertEquals("received", File(downloads(), "received.txt").readText())
    }
    @Test fun oldSaveCleanupCannotRemoveNewSessionsReusedPayloadId() {
        connectReceiving()
        val gate = CountDownLatch(1)
        io.submit { gate.await(30, TimeUnit.SECONDS) }
        val (oldPayload, oldTemporary) = incomingFile(42, "old")
        val oldCallback = payloadCallbacks.last()
        oldCallback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "old.txt", 3)))
        oldCallback.onPayloadReceived("peer", oldPayload)
        oldCallback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 3, 3))
        manager.advertise()
        connectReceiving(waitForIo = false)
        val (newPayload, newTemporary) = incomingFile(42, "new")
        val newCallback = payloadCallbacks.last()
        newCallback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "new.txt", 3)))
        newCallback.onPayloadReceived("peer", newPayload)
        newCallback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS, 3, 3))
        gate.countDown()
        drain()
        assertEquals(listOf("new.txt"), manager.state.value.history.map { it.name })
        assertEquals("new", File(downloads(), "new.txt").readText())
        assertFalse(File(downloads(), "old.txt").exists())
        assertEquals(listOf(oldTemporary, newTemporary), temporaryProvider.deleted)
    }
    @Test fun terminalFailureWithResetCounterIsStillReported() {
        connectReceiving()
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "private.txt", 10)))
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.IN_PROGRESS, 5))
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.FAILURE, 0))
        assertEquals(TransferOutcome.Failed, manager.state.value.progress?.outcome)
        assertEquals(5L, manager.state.value.progress?.bytes)
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS))
        assertEquals(TransferOutcome.Failed, manager.state.value.progress?.outcome)
        assertTrue(manager.state.value.history.isEmpty())
    }
    @Test fun disconnectFailsPartialReceiveAndOldEndpointProgressIsIgnored() {
        connectReceiving()
        val callback = payloadCallbacks.last()
        callback.onPayloadReceived("peer", Payload.fromBytes(FileMetadataProtocol.encode(42, "private.txt", 10)))
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.IN_PROGRESS, 5))
        lifecycle.last().onDisconnected("peer")
        callback.onPayloadTransferUpdate("peer", update(42, PayloadTransferUpdate.Status.SUCCESS))
        assertEquals(TransferOutcome.Failed, manager.state.value.progress?.outcome)
        assertTrue(manager.state.value.history.isEmpty())
        assertEquals(TransferFailureCategory.ConnectionLost, metrics.last().failureCategory)
    }
    @Test fun sequentialSendingAndDuplicateCompletionPreserveHistoryAndSelection() {
        val context = RuntimeEnvironment.getApplication()
        val files = (1..2).map { File(context.cacheDir, "send-$it.txt").apply { writeText("0123456789") } }
        val uris = files.map { Uri.fromFile(it) }
        manager.selectFiles(uris)
        drain()
        assertEquals(2, manager.state.value.selectedFiles.size)
        manager.discover(uris)
        manager.connect(Peer("peer", "Peer"))
        lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", false))
        manager.answerConnection(true)
        lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
        drain()
        assertEquals(2, manager.state.value.selectedFiles.size)
        assertEquals(listOf(Payload.Type.BYTES, Payload.Type.FILE), sent.map { it.type })
        val first = sent.last().id
        payloadCallbacks.last().onPayloadTransferUpdate("peer", update(first, PayloadTransferUpdate.Status.SUCCESS))
        payloadCallbacks.last().onPayloadTransferUpdate("peer", update(first, PayloadTransferUpdate.Status.SUCCESS))
        drain()
        assertEquals(1, manager.state.value.history.size)
        assertEquals(listOf(Payload.Type.BYTES, Payload.Type.FILE, Payload.Type.BYTES, Payload.Type.FILE), sent.map { it.type })
        val second = sent.last().id
        assertNotEquals(first, second)
        payloadCallbacks.last().onPayloadTransferUpdate("peer", update(second, PayloadTransferUpdate.Status.SUCCESS))
        drain()
        assertEquals(2, manager.state.value.history.size)
        assertEquals(2, metrics.count { it.transferState == FileTransferState.Complete })
        assertFalse(metrics.toString().contains("send-"))
    }
    @Test fun cancellationBetweenFilesStopsTheNextPreparation() {
        val files = (1..2).map { File(RuntimeEnvironment.getApplication().cacheDir, "gap-$it.txt").apply { writeText("data") } }
        manager.discover(files.map { Uri.fromFile(it) })
        manager.connect(Peer("peer", "Peer"))
        lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", false))
        manager.answerConnection(true)
        lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
        drain()
        val first = sent.last().id
        payloadCallbacks.last().onPayloadTransferUpdate("peer", update(first, PayloadTransferUpdate.Status.SUCCESS, 4, 4))
        manager.cancelTransfer()
        drain()
        assertEquals(2, sent.size)
        assertEquals(1, manager.state.value.history.size)
        assertEquals(TransferOutcome.Cancelled, manager.state.value.progress?.outcome)
    }
    @Test fun synchronousNearbySendFailuresBecomeTerminalWithoutCrashingCallbacks() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "failure.txt").apply { writeText("data") }
        for (type in listOf(Payload.Type.BYTES, Payload.Type.FILE)) {
            throwOnPayloadType = type
            manager.discover(listOf(Uri.fromFile(file)))
            manager.connect(Peer("peer", "Peer"))
            lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", false))
            manager.answerConnection(true)
            lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
            drain()
            assertEquals(TransferOutcome.Failed, manager.state.value.progress?.outcome)
            assertTrue(manager.state.value.hasError)
            assertTrue(manager.state.value.history.isEmpty())
            assertEquals(if (type == Payload.Type.BYTES) TransferFailureCategory.Metadata else TransferFailureCategory.Transport,
                metrics.last().failureCategory)
        }
    }
    @Test fun cancelledMetadataTaskCannotSendIntoReplacementSession() {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "cancel.txt").apply { writeText("data") }
        deferMetadata = TaskCompletionSource()
        manager.discover(listOf(Uri.fromFile(file)))
        manager.connect(Peer("peer", "Peer"))
        lifecycle.last().onConnectionInitiated("peer", ConnectionInfo("Peer", "1234", false))
        manager.answerConnection(true)
        lifecycle.last().onConnectionResult("peer", ConnectionResolution(Status.RESULT_SUCCESS))
        drain()
        assertEquals(1, sent.size)
        manager.cancelTransfer()
        assertEquals(1, cancelled.size)
        manager.advertise()
        deferMetadata!!.setResult(null)
        drain()
        assertEquals(1, sent.size)
        assertTrue(manager.state.value.isAdvertising)
        assertNull(manager.state.value.progress)
    }
}
