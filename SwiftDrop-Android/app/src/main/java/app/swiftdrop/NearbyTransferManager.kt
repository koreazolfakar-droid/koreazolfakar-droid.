package app.swiftdrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicLong

/**
 * First functional transport milestone: a single Nearby Connections link.
 * Actual Wi-Fi radio choice is delegated to Google Play Services and OS.
 */
class NearbyTransferManager(private val context: Context,
    private val diagnosticSink: TransferDiagnosticSink = TransferDiagnosticSink { event ->
        android.util.Log.i("SwiftDropTransfer", event.toString())
    },
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context),
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
) : TransferController {
    override val state = MutableStateFlow(TransferUiState())
    @Volatile private var closed = false
    private val main = Handler(Looper.getMainLooper())
    private val sessions = TransferSessionManager()
    @Volatile private var session = sessions.currentId()
    private var approvedEndpoint: String? = null

    private fun current(generation: Long) = !closed && sessionGeneration.get() == generation
    private fun dispatchToMain(action: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        main.post(action)
        return true
    }
    private fun report(id: Long, token: SessionId = session) {
        sessions.get(token, id)?.let { transfer ->
            diagnosticSink.record(TransferDiagnostic.from(transfer, sessions.state(), SystemClock.elapsedRealtime()))
        }
    }
    private fun changeConnection(next: SessionState): Boolean {
        val changed = sessions.connection(session, next)
        if (changed) state.update { it.copy(sessionState = next) }
        if (changed) diagnosticSink.record(ConnectionDiagnostic(TransportType.Nearby, next,
            when (next) {
                SessionState.Disconnected -> TransferFailureCategory.ConnectionLost
                SessionState.Failed -> TransferFailureCategory.Transport
                SessionState.Cancelled -> TransferFailureCategory.Cancelled
                SessionState.Closed -> TransferFailureCategory.SessionReplaced
                else -> TransferFailureCategory.None
            }))
        return changed
    }
    private val serviceId = "app.swiftdrop.nearby"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingMetadata = ConcurrentHashMap<Long, IncomingMetadata>()
    private val incomingTransportLengths = ConcurrentHashMap<Long, Long>()
    private val completed = ConcurrentHashMap.newKeySet<Long>()
    private val persisting = ConcurrentHashMap.newKeySet<Long>()
    private val sessionGeneration = AtomicLong()
    private val incomingPeers = ConcurrentHashMap<Long, String>()
    @Volatile private var connectedId: String? = null
    private var outboundUris: List<Uri> = emptyList()
    private var outboundIndex = 0
    @Volatile private var activeOutboundId: Long? = null
    private var activeOutboundPayload: Payload? = null
    private var preparingOutbound: SessionId? = null
    private var activeOutboundName = ""
    private var outboundPeer = ""
    @Volatile private var qrTarget: String? = null
    @Volatile private var activeAdvertisedToken: String? = null

    private data class IncomingMetadata(val name: String, val size: Long)
    private val measurements = TransferMeasurements()
    private val emissionGate = ProgressEmissionGate()
    private fun info(resource: Int, vararg arguments: String, error: Boolean = false) {
        state.update { it.copy(status = UiMessage(resource, arguments.toList()), hasError = error, recoveryAction = NearbyRecoveryAction.None) }
    }

    /**
     * Some Play Services builds return 8034 when the system Location switch is
     * disabled, even though all Android 13+ Nearby permissions are granted.
     * Do not request obsolete app-location access on modern phones.
     */
    private fun isDeviceLocationEnabled(): Boolean {
        val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
            else location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { true }
    }

    // Reports only system permission state. Does not collect GPS data or filenames.
    private fun nearbyDiagnostics(code: Int?): String {
        fun allowed(permission: String) =
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val googleVersion = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("com.google.android.gms",0).versionName
        }.getOrNull() ?: "unknown"
        val playLocation = runCatching {
            context.packageManager.checkPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION, "com.google.android.gms"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrNull()
        return listOf(
            "Nearby status: " + (code?.toString() ?: "unknown"),
            "Android API: " + Build.VERSION.SDK_INT,
            "System location ON: " + isDeviceLocationEnabled(),
            "SwiftDrop coarse location granted: " + allowed(android.Manifest.permission.ACCESS_COARSE_LOCATION),
            "SwiftDrop fine location granted: " + allowed(android.Manifest.permission.ACCESS_FINE_LOCATION),
            "SwiftDrop Bluetooth scan granted: " + (Build.VERSION.SDK_INT < 31 || allowed(android.Manifest.permission.BLUETOOTH_SCAN)),
            "SwiftDrop Bluetooth connect granted: " + (Build.VERSION.SDK_INT < 31 || allowed(android.Manifest.permission.BLUETOOTH_CONNECT)),
            "SwiftDrop Bluetooth advertise granted: " + (Build.VERSION.SDK_INT < 31 || allowed(android.Manifest.permission.BLUETOOTH_ADVERTISE)),
            "SwiftDrop nearby Wi-Fi granted: " + (Build.VERSION.SDK_INT < 33 || allowed(android.Manifest.permission.NEARBY_WIFI_DEVICES)),
            "Google Play services version: " + googleVersion,
            "Google Play services coarse location allowed: " + (playLocation?.toString() ?: "unknown")
        ).joinToString("\n")
    }

    private fun radioStartFailed(defaultMessage: Int, exception: Exception) {
        changeConnection(SessionState.Failed)
        val code = (exception as? ApiException)?.statusCode
        val diagnostics = nearbyDiagnostics(code)
        android.util.Log.w("SwiftDropNearby", diagnostics)
        val (message, recovery) = when (classifyNearbyFailure(code, isDeviceLocationEnabled())) {
            NearbyFailure.LocationOff -> R.string.nearby_location_off to NearbyRecoveryAction.LocationSettings
            NearbyFailure.LocationDependency -> R.string.nearby_location_dependency to NearbyRecoveryAction.LocationSettings
            NearbyFailure.MissingPermission -> R.string.nearby_permission_problem to NearbyRecoveryAction.AppSettings
            NearbyFailure.PreciseLocationRequired -> R.string.nearby_precise_location_required to NearbyRecoveryAction.AppSettings
            NearbyFailure.RadioBusy -> R.string.nearby_radio_busy to NearbyRecoveryAction.None
            NearbyFailure.Other -> {
                state.update { it.copy(
                    status = UiMessage(defaultMessage,listOf(exception.localizedMessage ?: exception.javaClass.simpleName)),
                    isDiscovering = false, isAdvertising = false, hasError = true,
                    recoveryAction = NearbyRecoveryAction.None, diagnostics = diagnostics) }
                return
            }
        }
        state.update { it.copy(status = UiMessage(message), isDiscovering = false,
            isAdvertising = false, hasError = true, recoveryAction = recovery, diagnostics = diagnostics) }
    }

    override fun setPage(page: String) { state.update { it.copy(page = page) } }

    override fun armQrTarget(token: String?) {
        require(token == null || QrPairing.parsePayload(QrPairing.payload(token)) == token)
        qrTarget = token
    }


    private fun discoveryCallback(generation: Long) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, details: DiscoveredEndpointInfo) {
            if (!current(generation) || connectedId != null) return
            val advertised = QrPairing.parseAdvertisement(details.endpointName)
            val target = qrTarget
            // QR pairing never connects to an unrelated or spoofed textual device name.
            if (target != null && advertised?.token != target) return
            val peer = Peer(endpointId,advertised?.name ?: details.endpointName)
            state.update { it.copy(peers = (it.peers.filterNot { p -> p.id == endpointId } + peer)) }
            if (target != null && advertised?.token == target) connect(peer)
        }
        override fun onEndpointLost(endpointId: String) {
            if (!current(generation)) return
            state.update { it.copy(peers = it.peers.filterNot { p -> p.id == endpointId }) }
        }
    }

    private fun connectionCallback(generation: Long) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, details: ConnectionInfo) {
            if (!current(generation)) return
            if (connectedId != null || state.value.connectingId?.let { it != endpointId } == true ||
                state.value.pending != null) {
                client.rejectConnection(endpointId)
                return
            }
            if (!changeConnection(SessionState.AwaitingApproval)) return
            // BOTH devices must compare authentication digits and explicitly accept.
            state.update {
                it.copy(pending = PendingConnection(endpointId, details.endpointName,
                    details.authenticationDigits), status = UiMessage(R.string.compare_codes), connectingId = endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!current(generation) || state.value.connectingId != endpointId) return
            state.update { it.copy(pending = null, connectingId = null) }
            if (result.status.statusCode == com.google.android.gms.nearby.connection.ConnectionsStatusCodes.STATUS_OK) {
                if (approvedEndpoint != endpointId) {
                    client.rejectConnection(endpointId)
                    return
                }
                if (!changeConnection(SessionState.Connected)) return
                connectedId = endpointId
                client.stopAdvertising()
                client.stopDiscovery()
                val peerName = state.value.peers.find { it.id == endpointId }?.name
                    ?: state.value.connectedName ?: context.getString(R.string.nearby_device)
                outboundPeer = peerName
                state.update { it.copy(connectedName = peerName, isAdvertising = false,
                    isDiscovering = false, page = "transfer", status = UiMessage(R.string.connected), hasError = false, receiverQrPayload = null) }
                if (outboundUris.isNotEmpty()) sendNext()
            } else {
                changeConnection(SessionState.Failed)
                approvedEndpoint = null
                state.update { it.copy(connectedName = null) }
                info(R.string.connection_failed, result.status.statusCode.toString(), error = true)
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (!current(generation) || (connectedId != endpointId && state.value.connectingId != endpointId)) return
            changeConnection(SessionState.Disconnected)
            (incomingPayloads.keys + incomingMetadata.keys).forEach { if (!completed.contains(it)) report(it) }
            activeOutboundId?.let { report(it) }
            approvedEndpoint = null
            // Keep fully received files available for saving, but discard incomplete ones.
            (incomingPayloads.keys + incomingMetadata.keys).toList().filterNot { completed.contains(it) }.forEach {
                discardPartialIncoming(it)
            }
            connectedId = null
            activeOutboundId = null
            activeOutboundPayload?.close()
            activeOutboundPayload = null
            preparingOutbound = null
            outboundUris = emptyList()
            state.update { it.copy(connectedName = null, status = UiMessage(R.string.disconnected), pending = null,
                connectingId = null, progress = it.progress?.let { progress ->
                    if (progress.outcome == TransferOutcome.Running) progress.copy(outcome = TransferOutcome.Failed) else progress
                }) }
        }
    }

    private fun payloadCallback(generation: Long) = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (!current(generation) || connectedId != endpointId) {
                // Release untracked descriptors; only tracked session sources are eligible for deletion.
                if (payload.type == Payload.Type.FILE) runCatching { payload.close() }
                return
            }
            when (payload.type) {
                Payload.Type.BYTES -> {
                    try {
                        val message = FileMetadataProtocol.decode(requireNotNull(payload.asBytes())) ?: return
                        val id = message.payloadId
                        val name = message.name
                        val size = message.size
                        val metadata = TransferFileMetadata(name, size)
                        if (sessions.get(session, id) == null && !sessions.register(session, id,
                            TransferDirection.Receiving, null, SystemClock.elapsedRealtime())) {
                            client.cancelPayload(id)
                            info(R.string.failed, error = true)
                            return
                        }
                        if (!sessions.attachMetadata(session, id, metadata)) return
                        incomingMetadata[id] = IncomingMetadata(name, size)
                        maybePersistReceived(id)
                    } catch (_: Exception) { info(R.string.invalid_metadata, error = true) }
                }
                Payload.Type.FILE -> {
                    val existing = sessions.get(session, payload.id)
                    if (existing == null && !sessions.register(session, payload.id,
                        TransferDirection.Receiving, null, SystemClock.elapsedRealtime())) {
                        client.cancelPayload(payload.id)
                        info(R.string.failed, error = true)
                        return
                    }
                    if (existing?.direction == TransferDirection.Sending || existing?.state in setOf(FileTransferState.Complete, FileTransferState.Failed, FileTransferState.Cancelled) ||
                        incomingPayloads.containsKey(payload.id)) return
                    incomingPeers[payload.id] = state.value.connectedName ?: context.getString(R.string.nearby_device)
                    incomingPayloads[payload.id] = payload
                    maybePersistReceived(payload.id)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (!current(generation) || connectedId != endpointId) return
            val id = update.payloadId
            val sent = activeOutboundId == id
            // Ignore progress callbacks belonging to tiny metadata messages.
            if (!sent && !incomingPayloads.containsKey(id) && !incomingMetadata.containsKey(id)) return
            val direction = if (sent) "Sending" else "Receiving"
            val name = if (sent) activeOutboundName else incomingMetadata[id]?.name ?: context.getString(R.string.ready_files)
            val total = update.totalBytes.coerceAtLeast(0L)
            val outcome = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> if (sent) TransferOutcome.Complete else TransferOutcome.Saving
                PayloadTransferUpdate.Status.FAILURE -> TransferOutcome.Failed
                PayloadTransferUpdate.Status.CANCELED -> TransferOutcome.Cancelled
                else -> TransferOutcome.Running
            }
            val timestamp = SystemClock.elapsedRealtime()
            val previous = sessions.get(session, id) ?: return
            val terminal = update.status != PayloadTransferUpdate.Status.IN_PROGRESS
            val bytes = if (terminal) update.bytesTransferred.coerceAtLeast(previous.bytes)
                else update.bytesTransferred.coerceAtLeast(0L)
            // Some terminal failures reset counters; never suppress them as regressing progress.
            if (!sessions.progress(session, id, bytes, timestamp.coerceAtLeast(previous.updatedMillis))) return
            val next = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> if (sent) FileTransferState.Complete else FileTransferState.Saving
                PayloadTransferUpdate.Status.FAILURE -> FileTransferState.Failed
                PayloadTransferUpdate.Status.CANCELED -> FileTransferState.Cancelled
                else -> null
            }
            if (next != null && !sessions.transition(session, id, next,
                when (next) {
                    FileTransferState.Failed -> TransferFailureCategory.Transport
                    FileTransferState.Cancelled -> TransferFailureCategory.Cancelled
                    else -> TransferFailureCategory.None
                })) return
            if (next != null) report(id)
            val measurement = measurements.sample(id, bytes, total, timestamp)
            // Never suppress a success, failure, or cancellation. Only throttle UI work.
            if (emissionGate.shouldEmit(id,bytes,timestamp,
                update.status != PayloadTransferUpdate.Status.IN_PROGRESS)) {
                val currentIndex = if (sent) outboundIndex else 0
                val fileCount = if (sent) outboundUris.size else 0
                state.update { it.copy(
                    page = if (it.page == "receive" || it.page == "discover") "transfer" else it.page,
                    progress = TransferProgress(name,direction,bytes,total,
                        finished = sent && update.status == PayloadTransferUpdate.Status.SUCCESS,
                        outcome = outcome, measurement = measurement, payloadId = id,
                        fileIndex = currentIndex, fileCount = fileCount))
                }
            }
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (sent) {
                        activeOutboundId = null
                        activeOutboundPayload?.close()
                        activeOutboundPayload = null
                        state.update { it.copy(history = listOf(
                            HistoryEntry(name, outboundPeer, "Sent", total, System.currentTimeMillis())
                        ) + it.history) }
                        sendNext()
                    } else {
                        incomingTransportLengths[id] = update.totalBytes
                        completed.add(id)
                        maybePersistReceived(id)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    if (sent) {
                        activeOutboundId = null
                        activeOutboundPayload?.close()
                        activeOutboundPayload = null
                        outboundUris = emptyList()
                    }
                    else discardPartialIncoming(id)
                    info(if (update.status == PayloadTransferUpdate.Status.CANCELED) R.string.cancelled else R.string.failed, error = true)
                }
            }
        }
    }

    override fun advertise() {
        if (dispatchToMain { advertise() } || closed) return
        disconnect()
        changeConnection(SessionState.Advertising)
        qrTarget = null
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = emptyList()
        val sessionToken = QrPairing.newToken()
        activeAdvertisedToken = sessionToken
        state.update { it.copy(page = "receive", status = UiMessage(R.string.making_visible), hasError = false, recoveryAction = NearbyRecoveryAction.None, diagnostics = null, progress = null, connectingId = null,
            peers = emptyList(), selected = emptyList(), selectedFiles = emptyList(), receiverQrPayload = null) }
        client.startAdvertising(QrPairing.advertisement(sessionToken,Build.MODEL), serviceId, connectionCallback(sessionGeneration.get()),
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener {
                if(activeAdvertisedToken == sessionToken && !closed && sessions.state() == SessionState.Advertising) {
                    state.update { s -> s.copy(isAdvertising = true,
                        receiverQrPayload = QrPairing.payload(sessionToken),
                        status = UiMessage(R.string.waiting_sender)) }
                }
            }
            .addOnFailureListener {
                if(activeAdvertisedToken == sessionToken && sessions.state() == SessionState.Advertising) {
                    activeAdvertisedToken = null
                    state.update { s -> s.copy(receiverQrPayload = null) }
                    radioStartFailed(R.string.cannot_receive, it)
                }
            }
    }

    override fun discover(uris: List<Uri>) {
        if (dispatchToMain { discover(uris) } || closed || uris.isEmpty()) return
        val target = qrTarget
        val selection = state.value.selectedFiles.filter { it.uri in uris }
        disconnect()
        qrTarget = target
        changeConnection(SessionState.Discovering)
        val generation = sessionGeneration.get()
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = uris
        outboundIndex = 0
        state.update { it.copy(page = "discover", hasError = false, diagnostics = null, progress = null, connectingId = null,
            peers = emptyList(), selected = uris, selectedFiles = selection, receiverQrPayload = null,
            status = UiMessage(if(qrTarget != null) R.string.qr_searching else R.string.searching)) }
        client.startDiscovery(serviceId, discoveryCallback(generation),
            DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { if (current(generation) && sessions.state() == SessionState.Discovering) state.update { s -> s.copy(isDiscovering = true) } }
            .addOnFailureListener { if (current(generation) && sessions.state() == SessionState.Discovering) radioStartFailed(R.string.cannot_search, it) }
    }

    override fun connect(peer: Peer) {
        if (dispatchToMain { connect(peer) } || closed || connectedId != null) return
        val generation = sessionGeneration.get()
        if (state.value.connectingId != null || state.value.pending != null) return
        if (!changeConnection(SessionState.Connecting)) return
        state.update { it.copy(connectingId = peer.id, hasError = false) }
        info(R.string.connecting, peer.name)
        client.requestConnection(Build.MODEL.take(26), peer.id, connectionCallback(generation))
            .addOnFailureListener {
                if (!current(generation) || state.value.connectingId != peer.id) return@addOnFailureListener
                changeConnection(SessionState.Failed)
                state.update { s -> s.copy(connectingId = null) }
                info(R.string.connection_request_failed, it.localizedMessage ?: it.javaClass.simpleName, error = true)
            }
    }

    override fun answerConnection(accept: Boolean) {
        if (dispatchToMain { answerConnection(accept) } || closed) return
        val generation = sessionGeneration.get()
        val request = state.value.pending ?: return
        state.update { it.copy(pending = null, connectedName = if (accept) request.peerName else null, connectingId = if (accept) request.endpointId else null) }
        if (accept) {
            approvedEndpoint = request.endpointId
            changeConnection(SessionState.Connecting)
            client.acceptConnection(request.endpointId, payloadCallback(generation))
                .addOnFailureListener {
                    if (current(generation) && state.value.connectingId == request.endpointId) {
                        changeConnection(SessionState.Failed)
                        approvedEndpoint = null
                        state.update { s -> s.copy(connectingId = null, connectedName = null) }
                        info(R.string.accept_failed, it.localizedMessage ?: it.javaClass.simpleName, error = true)
                    }
                }
        } else {
            approvedEndpoint = null
            changeConnection(SessionState.Cancelled)
            client.rejectConnection(request.endpointId)
            info(R.string.connection_declined)
        }
    }

    private fun sendNext() {
        if (dispatchToMain { sendNext() } || closed) return
        val target = connectedId ?: return
        if (activeOutboundId != null || preparingOutbound != null) return
        if (outboundIndex >= outboundUris.size) {
            outboundUris = emptyList()
            info(R.string.all_sent)
            return
        }
        val uri = outboundUris[outboundIndex++]
        val generation = sessionGeneration.get()
        val token = session
        preparingOutbound = token
        val selected = state.value.selectedFiles.find { it.uri == uri }
        state.update { it.copy(progress = TransferProgress(selected?.name ?: context.getString(R.string.ready_files),
            "Sending", 0, selected?.bytes ?: -1, fileIndex = outboundIndex, fileCount = outboundUris.size)) }
        io.execute {
            try {
                val meta = readMetadata(uri)
                val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
                val filePayload = try { Payload.fromFile(pfd) } catch (e: Exception) { pfd.close(); throw e }
                main.post {
                    if (!current(generation) || connectedId != target) {
                        filePayload.close()
                        return@post
                    }
                    preparingOutbound = null
                    if (!sessions.register(token, filePayload.id, TransferDirection.Sending,
                        TransferFileMetadata(meta.name, meta.size), SystemClock.elapsedRealtime())) {
                        filePayload.close()
                        outboundUris = emptyList()
                        state.update { it.copy(progress = it.progress?.copy(outcome = TransferOutcome.Failed)) }
                        info(R.string.failed, error = true)
                        return@post
                    }
                    activeOutboundId = filePayload.id
                    activeOutboundPayload = filePayload
                    activeOutboundName = meta.name
                    state.update { it.copy(progress = TransferProgress(meta.name, "Sending", 0, meta.size,
                        payloadId = filePayload.id, fileIndex = outboundIndex, fileCount = outboundUris.size),
                        status = UiMessage(R.string.sending), hasError = false) }
                    try {
                        val metadata = FileMetadataProtocol.encode(filePayload.id, meta.name, meta.size)
                        client.sendPayload(target, Payload.fromBytes(metadata))
                            .addOnSuccessListener {
                                if (!current(generation) || connectedId != target || activeOutboundId != filePayload.id ||
                                    sessions.get(token, filePayload.id)?.state != FileTransferState.Active) {
                                    filePayload.close()
                                    return@addOnSuccessListener
                                }
                                try {
                                    client.sendPayload(target, filePayload).addOnFailureListener { e ->
                                        failOutbound(generation, token, filePayload, R.string.send_failed, e, TransferFailureCategory.Transport)
                                    }
                                } catch (e: Exception) {
                                    failOutbound(generation, token, filePayload, R.string.send_failed, e, TransferFailureCategory.Transport)
                                }
                            }
                            .addOnFailureListener { e ->
                                failOutbound(generation, token, filePayload, R.string.metadata_failed, e, TransferFailureCategory.Metadata)
                            }
                    } catch (e: Exception) {
                        failOutbound(generation, token, filePayload, R.string.metadata_failed, e, TransferFailureCategory.Metadata)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (current(generation) && connectedId == target) {
                        preparingOutbound = null
                        state.update { it.copy(progress = TransferProgress(
                            it.selectedFiles.find { file -> file.uri == uri }?.name ?: context.getString(R.string.ready_files),
                            "Sending", 0, 0, outcome = TransferOutcome.Failed,
                            fileIndex = outboundIndex, fileCount = outboundUris.size)) }
                        outboundUris = emptyList()
                        failTransfer(R.string.read_failed, e)
                    }
                }
            }
        }
    }

    private fun failOutbound(generation: Long, token: SessionId, payload: Payload,
        resource: Int, error: Exception, category: TransferFailureCategory) {
        payload.close()
        if (!current(generation) || activeOutboundId != payload.id ||
            !sessions.transition(token, payload.id, FileTransferState.Failed, category)) return
        report(payload.id, token)
        activeOutboundId = null
        activeOutboundPayload = null
        preparingOutbound = null
        outboundUris = emptyList()
        failTransfer(resource, error)
    }

    private fun readMetadata(uri: Uri): IncomingMetadata {
        var name = uri.lastPathSegment ?: "shared-file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return IncomingMetadata(SafeFileName.normalize(name), size.coerceAtLeast(-1L))
    }

    private fun maybePersistReceived(id: Long) {
        if (closed || !completed.contains(id)) return
        val file = incomingPayloads[id] ?: return
        val meta = incomingMetadata[id] ?: return
        val token = session
        if (!sessions.canSave(token, id) || !persisting.add(id)) return
        val peerName = incomingPeers[id] ?: state.value.connectedName ?: context.getString(R.string.nearby_device)
        val generation = sessionGeneration.get()
        val transportLength = incomingTransportLengths[id] ?: -1L
        io.execute {
            try {
                val uri = requireNotNull(file.asFile()?.asUri())
                val written = saveIntoDownloads(uri, meta.name, meta.size, transportLength,
                    isSessionActive = { !closed && sessions.canSave(token, id) },
                    finalize = { bytes, publish ->
                        sessions.finalizeReceive(token, id) {
                            publish()
                            // History is committed with publication, even if navigation resets before the UI callback.
                            state.update { it.copy(history = listOf(HistoryEntry(meta.name, peerName, "Received",
                                bytes, System.currentTimeMillis())) + it.history) }
                        }
                    })
                main.post {
                    if (!current(generation)) return@post
                    report(id, token)
                    state.update { s ->
                        val current = s.progress?.payloadId == id
                        s.copy(status = if (current) UiMessage(R.string.saved_file, listOf(meta.name, written.location)) else s.status,
                            hasError = if (current) false else s.hasError,
                            progress = if (current) TransferProgress(meta.name, "Received", written.bytes, written.bytes, true, payloadId = id) else s.progress)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (!current(generation) || !sessions.transition(token, id, FileTransferState.Failed,
                        TransferFailureCategory.Storage)) return@post
                    report(id, token)
                    if (state.value.progress?.payloadId == id) failTransfer(R.string.save_failed, e)
                }
            } finally {
                // Delete only this task's captured Nearby temporary source, never a new session's entry.
                releaseIncoming(file)
                main.post {
                    if (!current(generation)) return@post
                    incomingPayloads.remove(id, file)
                    incomingMetadata.remove(id, meta)
                    incomingTransportLengths.remove(id)
                    completed.remove(id)
                    incomingPeers.remove(id)
                    persisting.remove(id)
                }
            }
        }
    }

    private data class SavedFile(val location: String, val bytes: Long)

    private fun saveIntoDownloads(source: Uri, untrustedName: String,
        metadataSize: Long, transportSize: Long, isSessionActive: () -> Boolean,
        finalize: (Long, () -> Unit) -> Unit): SavedFile {
        var writtenBytes = 0L
        var published = false
        val filename = SafeFileName.normalize(untrustedName)
        if (Build.VERSION.SDK_INT >= 29) {
            val extension = filename.substringAfterLast('.', "").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
            val content = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SwiftDrop")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val destination = requireNotNull(resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, content))
            try {
                resolver.openInputStream(source).use { input ->
                    requireNotNull(input)
                    resolver.openOutputStream(destination).use { output ->
                        requireNotNull(output)
                        writtenBytes = VerifiedFileCopy.copy(input,output,metadataSize,transportSize,isSessionActive)
                    }
                }
                finalize(writtenBytes) {
                    check(resolver.update(destination, ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }, null, null) == 1) { "Cannot publish received file" }
                    published = true
                }
            } catch (e: Exception) {
                if (!published) resolver.delete(destination, null, null)
                throw e
            }
            return SavedFile("Downloads/SwiftDrop", writtenBytes)
        }
        val appDownloads = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        val folder = File(appDownloads, "SwiftDrop")
        check(folder.exists() || folder.mkdirs()) { "Cannot create folder" }
        // Avoid overwriting an existing file with the same name.
        var dest = File(folder, filename)
        var index = 1
        while (dest.exists()) {
            dest = File(folder, "${filename.substringBeforeLast('.', filename)} ($index)" +
                if ('.' in filename) ".${filename.substringAfterLast('.')}" else "")
            index++
        }
        val staging = File.createTempFile(".swiftdrop-", ".partial", folder)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input)
                FileOutputStream(staging).use { output ->
                    writtenBytes = VerifiedFileCopy.copy(input,output,metadataSize,transportSize,isSessionActive)
                }
            }
            finalize(writtenBytes) {
                check(!dest.exists() && staging.renameTo(dest)) { "Cannot finalize received file" }
                published = true
            }
        } catch (e: Exception) {
            staging.delete()
            throw e
        }
        return SavedFile("App downloads/SwiftDrop", writtenBytes)
    }

    private fun releaseIncoming(payload: Payload) {
        val temporary = payload.asFile()?.asUri()
        runCatching { payload.close() }
        if (temporary != null) runCatching { context.contentResolver.delete(temporary, null, null) }
    }

    private fun discardPartialIncoming(id: Long) {
        val payload = incomingPayloads.remove(id)
        incomingMetadata.remove(id)
        incomingTransportLengths.remove(id)
        incomingPeers.remove(id)
        completed.remove(id)
        if (payload != null) io.execute { releaseIncoming(payload) }
    }

    override fun disconnect() {
        if (dispatchToMain { disconnect() }) return
        resetSearching()
        qrTarget = null
        client.stopAllEndpoints()
        connectedId = null
        approvedEndpoint = null
        activeOutboundId = null
        activeOutboundPayload?.close()
        activeOutboundPayload = null
        preparingOutbound = null
        outboundUris = emptyList()
        // The IO executor serializes deletes after any in-flight save has aborted.
        val partialPayloads = incomingPayloads.filterKeys { it !in persisting }.values.toList()
        if (partialPayloads.isNotEmpty()) io.execute { partialPayloads.forEach { releaseIncoming(it) } }
        incomingPayloads.clear()
        incomingMetadata.clear()
        incomingTransportLengths.clear()
        incomingPeers.clear()
        completed.clear()
        persisting.clear()
        state.update { it.copy(status = UiMessage(), hasError = false, recoveryAction = NearbyRecoveryAction.None, diagnostics = null, connectingId = null, isAdvertising = false,
            isDiscovering = false, receiverQrPayload = null, connectedName = null, pending = null, peers = emptyList(),
            selected = emptyList(), selectedFiles = emptyList(), loadingSelection = false, progress = null, page = "home", sessionState = SessionState.Idle) }
    }

    override fun selectFiles(uris: List<Uri>) {
        if (dispatchToMain { selectFiles(uris) } || closed) return
        val generation = sessionGeneration.get()
        if (uris.isEmpty()) return
        val selected = uris.distinct()
        state.update { it.copy(page = "selection", selected = selected, selectedFiles = emptyList(),
            loadingSelection = true, hasError = false) }
        io.execute {
            try {
                val files = selected.map { uri -> readMetadata(uri).let { SelectedFile(uri, it.name, it.size) } }
                main.post {
                    if (current(generation)) state.update {
                        if (it.selected == selected) it.copy(selectedFiles = files, loadingSelection = false) else it
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (current(generation) && state.value.selected == selected) {
                        state.update { it.copy(loadingSelection = false) }
                        info(R.string.file_unavailable, error = true)
                    }
                }
            }
        }
    }

    private fun failTransfer(resource: Int, exception: Exception) {
        state.update { it.copy(progress = it.progress?.copy(outcome = TransferOutcome.Failed, finished = false)) }
        info(resource, exception.localizedMessage ?: exception.javaClass.simpleName, error = true)
    }

    override fun cancelTransfer() {
        if (dispatchToMain { cancelTransfer() } || closed) return
        val progress = state.value.progress ?: return disconnect()
        if (preparingOutbound == null && !sessions.hasInFlight(session)) return
        changeConnection(SessionState.Cancelled)
        activeOutboundId?.let { report(it); client.cancelPayload(it) }
        (incomingPayloads.keys + incomingMetadata.keys).forEach { report(it); client.cancelPayload(it) }
        disconnect()
        state.update { it.copy(page = "transfer", progress = progress.copy(outcome = TransferOutcome.Cancelled, finished = false),
            status = UiMessage(R.string.cancelled)) }
    }

    private fun resetSearching() {
        val active = (incomingPayloads.keys + incomingMetadata.keys + listOfNotNull(activeOutboundId)).filter {
            sessions.get(session, it)?.state in setOf(FileTransferState.Active, FileTransferState.Saving)
        }
        changeConnection(SessionState.Closed)
        active.forEach { report(it) }
        sessionGeneration.incrementAndGet()
        session = sessions.begin()
        measurements.reset()
        emissionGate.reset()
        activeAdvertisedToken = null
        client.stopAdvertising()
        client.stopDiscovery()
        state.update { it.copy(isAdvertising = false, isDiscovering = false, receiverQrPayload = null) }
    }

    override fun close() {
        if (dispatchToMain { close() } || closed) return
        closed = true
        disconnect()
        changeConnection(SessionState.Closed)
        io.shutdown()
    }
}
