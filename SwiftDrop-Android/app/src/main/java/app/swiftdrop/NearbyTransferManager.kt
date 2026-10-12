package app.swiftdrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.connection.AdvertisingOptions
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * First functional transport milestone: a single Nearby Connections link.
 * Actual Wi-Fi radio choice is delegated to Google Play Services and OS.
 */
class NearbyTransferManager(private val context: Context) {
    val state = MutableStateFlow(TransferUiState())
    @Volatile private var closed = false
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "app.swiftdrop.nearby"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val io = Executors.newSingleThreadExecutor()
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

    fun setPage(page: String) { state.update { it.copy(page = page) } }

    fun armQrTarget(token: String?) {
        require(token == null || QrPairing.parsePayload(QrPairing.payload(token)) == token)
        qrTarget = token
    }


    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, details: DiscoveredEndpointInfo) {
            val advertised = QrPairing.parseAdvertisement(details.endpointName)
            val target = qrTarget
            // QR pairing never connects to an unrelated or spoofed textual device name.
            if (target != null && advertised?.token != target) return
            val peer = Peer(endpointId,advertised?.name ?: details.endpointName)
            state.update { it.copy(peers = (it.peers.filterNot { p -> p.id == endpointId } + peer)) }
            if (target != null && advertised?.token == target) connect(peer)
        }
        override fun onEndpointLost(endpointId: String) {
            state.update { it.copy(peers = it.peers.filterNot { p -> p.id == endpointId }) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, details: ConnectionInfo) {
            // BOTH devices must compare authentication digits and explicitly accept.
            state.update {
                it.copy(pending = PendingConnection(endpointId, details.endpointName,
                    details.authenticationDigits), status = UiMessage(R.string.compare_codes), connectingId = endpointId)
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            state.update { it.copy(pending = null, connectingId = null) }
            if (result.status.statusCode == com.google.android.gms.nearby.connection.ConnectionsStatusCodes.STATUS_OK) {
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
                state.update { it.copy(connectedName = null) }
                info(R.string.connection_failed, result.status.statusCode.toString(), error = true)
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedId != endpointId) return
            // Keep fully received files available for saving, but discard incomplete ones.
            incomingPayloads.keys.toList().filterNot { completed.contains(it) }.forEach {
                discardPartialIncoming(it)
            }
            connectedId = null
            activeOutboundId = null
            outboundUris = emptyList()
            state.update { it.copy(connectedName = null, status = UiMessage(R.string.disconnected), pending = null,
                connectingId = null, progress = it.progress?.let { progress ->
                    if (progress.outcome == TransferOutcome.Running) progress.copy(outcome = TransferOutcome.Failed) else progress
                }) }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    try {
                        val json = JSONObject(String(payload.asBytes()!!, Charsets.UTF_8))
                        if (json.optString("type") != "file-metadata") return
                        val id = json.getLong("id")
                        val name = SafeFileName.normalize(json.getString("name"))
                        incomingMetadata[id] = IncomingMetadata(name, json.optLong("size", -1L))
                        maybePersistReceived(id)
                    } catch (_: Exception) { info(R.string.invalid_metadata, error = true) }
                }
                Payload.Type.FILE -> {
                    incomingPeers[payload.id] = state.value.connectedName ?: context.getString(R.string.nearby_device)
                    incomingPayloads[payload.id] = payload
                    maybePersistReceived(payload.id)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
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
            val measurement = measurements.sample(id, update.bytesTransferred, total, timestamp)
            // Never suppress a success, failure, or cancellation. Only throttle UI work.
            if (emissionGate.shouldEmit(id,update.bytesTransferred,timestamp,
                update.status != PayloadTransferUpdate.Status.IN_PROGRESS)) {
                val currentIndex = if (sent) outboundIndex else 0
                val fileCount = if (sent) outboundUris.size else 0
                state.update { it.copy(
                    page = if (it.page == "receive" || it.page == "discover") "transfer" else it.page,
                    progress = TransferProgress(name,direction,update.bytesTransferred.coerceAtLeast(0L),total,
                        finished = sent && update.status == PayloadTransferUpdate.Status.SUCCESS,
                        outcome = outcome, measurement = measurement, payloadId = id,
                        fileIndex = currentIndex, fileCount = fileCount))
                }
            }
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (sent) {
                        activeOutboundId = null
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
                    if (sent) activeOutboundId = null
                    else discardPartialIncoming(id)
                    info(if (update.status == PayloadTransferUpdate.Status.CANCELED) R.string.cancelled else R.string.failed, error = true)
                }
            }
        }
    }

    fun advertise() {
        resetSearching()
        qrTarget = null
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = emptyList()
        val sessionToken = QrPairing.newToken()
        activeAdvertisedToken = sessionToken
        state.update { it.copy(page = "receive", status = UiMessage(R.string.making_visible), hasError = false, recoveryAction = NearbyRecoveryAction.None, diagnostics = null, progress = null, connectingId = null,
            peers = emptyList(), selected = emptyList(), selectedFiles = emptyList(), receiverQrPayload = null) }
        client.startAdvertising(QrPairing.advertisement(sessionToken,Build.MODEL), serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener {
                if(activeAdvertisedToken == sessionToken && !closed) {
                    state.update { s -> s.copy(isAdvertising = true,
                        receiverQrPayload = QrPairing.payload(sessionToken),
                        status = UiMessage(R.string.waiting_sender)) }
                }
            }
            .addOnFailureListener {
                if(activeAdvertisedToken == sessionToken) {
                    activeAdvertisedToken = null
                    state.update { s -> s.copy(receiverQrPayload = null) }
                    radioStartFailed(R.string.cannot_receive, it)
                }
            }
    }

    fun discover(uris: List<Uri>) {
        if (uris.isEmpty()) return
        resetSearching()
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = uris
        outboundIndex = 0
        state.update { it.copy(page = "discover", hasError = false, diagnostics = null, progress = null, connectingId = null,
            peers = emptyList(), selected = uris, receiverQrPayload = null,
            status = UiMessage(if(qrTarget != null) R.string.qr_searching else R.string.searching)) }
        client.startDiscovery(serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { state.update { s -> s.copy(isDiscovering = true) } }
            .addOnFailureListener { radioStartFailed(R.string.cannot_search, it) }
    }

    fun connect(peer: Peer) {
        if (state.value.connectingId != null || state.value.pending != null) return
        state.update { it.copy(connectingId = peer.id, hasError = false) }
        info(R.string.connecting, peer.name)
        client.requestConnection(Build.MODEL.take(26), peer.id, connectionCallback)
            .addOnFailureListener {
                state.update { s -> s.copy(connectingId = null) }
                info(R.string.connection_request_failed, it.localizedMessage ?: it.javaClass.simpleName, error = true)
            }
    }

    fun answerConnection(accept: Boolean) {
        val request = state.value.pending ?: return
        state.update { it.copy(pending = null, connectedName = if (accept) request.peerName else null, connectingId = if (accept) request.endpointId else null) }
        if (accept) {
            client.acceptConnection(request.endpointId, payloadCallback)
                .addOnFailureListener { state.update { s -> s.copy(connectingId = null, connectedName = null) }; info(R.string.accept_failed, it.localizedMessage ?: it.javaClass.simpleName, error = true) }
        } else {
            client.rejectConnection(request.endpointId)
            info(R.string.connection_declined)
        }
    }

    private fun sendNext() {
        if (closed) return
        val target = connectedId ?: return
        if (activeOutboundId != null) return
        if (outboundIndex >= outboundUris.size) {
            outboundUris = emptyList()
            info(R.string.all_sent)
            return
        }
        val uri = outboundUris[outboundIndex++]
        val generation = sessionGeneration.get()
        io.execute {
            try {
                val meta = readMetadata(uri)
                val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
                val filePayload = Payload.fromFile(pfd)
                if (sessionGeneration.get() != generation || connectedId != target) {
                    filePayload.close()
                    return@execute
                }
                activeOutboundId = filePayload.id
                activeOutboundName = meta.name
                state.update { it.copy(progress = TransferProgress(meta.name, "Sending", 0, meta.size,
                    payloadId = filePayload.id,fileIndex=outboundIndex,fileCount=outboundUris.size),
                    status = UiMessage(R.string.sending), hasError = false) }
                val metadata = JSONObject().put("type", "file-metadata")
                    .put("id", filePayload.id).put("name", meta.name).put("size", meta.size)
                client.sendPayload(target, Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
                    .addOnSuccessListener {
                        if (sessionGeneration.get() != generation || connectedId != target) {
                            filePayload.close()
                            return@addOnSuccessListener
                        }
                        client.sendPayload(target, filePayload)
                            .addOnFailureListener { e ->
                                filePayload.close()
                                if (sessionGeneration.get() == generation) {
                                    activeOutboundId = null
                                    failTransfer(R.string.send_failed, e)
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        filePayload.close()
                        if (sessionGeneration.get() == generation) {
                            activeOutboundId = null
                            failTransfer(R.string.metadata_failed, e)
                        }
                    }
            } catch (e: Exception) {
                if (sessionGeneration.get() == generation) {
                    activeOutboundId = null
                    failTransfer(R.string.read_failed, e)
                }
            }
        }
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
        return IncomingMetadata(SafeFileName.normalize(name), size)
    }

    private fun maybePersistReceived(id: Long) {
        if (closed) return
        if (!completed.contains(id)) return
        val file = incomingPayloads[id] ?: return
        val meta = incomingMetadata[id] ?: return
        if (!persisting.add(id)) return // prevent metadata/transfer completion race
        val peerName = incomingPeers[id] ?: state.value.connectedName ?: context.getString(R.string.nearby_device)
        val generation = sessionGeneration.get()
        val transportLength = incomingTransportLengths[id] ?: -1L
        io.execute {
            try {
                val uri = requireNotNull(file.asFile()?.asUri())
                val written = saveIntoDownloads(uri, meta.name,meta.size,transportLength) {
                    !closed && sessionGeneration.get() == generation
                }
                if (closed || sessionGeneration.get() != generation) return@execute
                state.update { s ->
                    val current = s.progress?.payloadId == id
                    s.copy(status = if (current) UiMessage(R.string.saved_file, listOf(meta.name, written.location)) else s.status,
                        hasError = if (current) false else s.hasError,
                        progress = if (current) TransferProgress(meta.name, "Received", written.bytes, written.bytes, true, payloadId = id) else s.progress,
                        history = listOf(HistoryEntry(meta.name, peerName, "Received", written.bytes,
                            System.currentTimeMillis())) + s.history)
                }
            } catch (e: Exception) {
                if (!closed && sessionGeneration.get() == generation &&
                    state.value.progress?.payloadId == id) failTransfer(R.string.save_failed, e)
            } finally {
                val temporary = incomingPayloads.remove(id)?.asFile()?.asUri()
                if (temporary != null) runCatching { context.contentResolver.delete(temporary,null,null) }
                incomingMetadata.remove(id)
                incomingTransportLengths.remove(id)
                completed.remove(id)
                incomingPeers.remove(id)
                persisting.remove(id)
            }
        }
    }

    private data class SavedFile(val location: String, val bytes: Long)

    private fun saveIntoDownloads(source: Uri, untrustedName: String,
        metadataSize: Long, transportSize: Long, isSessionActive: () -> Boolean): SavedFile {
        var writtenBytes = 0L
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
                resolver.update(destination, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
            } catch (e: Exception) {
                resolver.delete(destination, null, null)
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
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input)
                FileOutputStream(dest).use { output ->
                    writtenBytes = VerifiedFileCopy.copy(input,output,metadataSize,transportSize,isSessionActive)
                }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        return SavedFile("App downloads/SwiftDrop", writtenBytes)
    }

    private fun discardPartialIncoming(id: Long) {
        val temporary = incomingPayloads.remove(id)?.asFile()?.asUri()
        incomingMetadata.remove(id)
        incomingTransportLengths.remove(id)
        incomingPeers.remove(id)
        completed.remove(id)
        if (temporary != null && !closed) {
            runCatching {
                io.execute { runCatching { context.contentResolver.delete(temporary,null,null) } }
            }
        }
    }

    fun disconnect() {
        resetSearching()
        qrTarget = null
        client.stopAllEndpoints()
        connectedId = null
        activeOutboundId = null
        outboundUris = emptyList()
        // The IO executor serializes deletes after any in-flight save has aborted.
        val tempUris = incomingPayloads.values.mapNotNull { it.asFile()?.asUri() }
        if (tempUris.isNotEmpty()) runCatching {
            io.execute { tempUris.forEach { uri ->
                runCatching { context.contentResolver.delete(uri,null,null) }
            } }
        }
        incomingPayloads.clear()
        incomingMetadata.clear()
        incomingTransportLengths.clear()
        incomingPeers.clear()
        completed.clear()
        state.update { it.copy(status = UiMessage(), hasError = false, recoveryAction = NearbyRecoveryAction.None, diagnostics = null, connectingId = null, isAdvertising = false,
            isDiscovering = false, receiverQrPayload = null, connectedName = null, pending = null, peers = emptyList(),
            selected = emptyList(), selectedFiles = emptyList(), loadingSelection = false, progress = null, page = "home") }
    }

    fun selectFiles(uris: List<Uri>) {
        if (closed) return
        if (uris.isEmpty()) return
        val selected = uris.distinct()
        state.update { it.copy(page = "selection", selected = selected, selectedFiles = emptyList(),
            loadingSelection = true, hasError = false) }
        io.execute {
            try {
                val files = selected.map { uri -> readMetadata(uri).let { SelectedFile(uri, it.name, it.size) } }
                state.update { if (it.selected == selected) it.copy(selectedFiles = files, loadingSelection = false) else it }
            } catch (e: Exception) {
                state.update { it.copy(loadingSelection = false) }
                info(R.string.file_unavailable, error = true)
            }
        }
    }

    private fun failTransfer(resource: Int, exception: Exception) {
        state.update { it.copy(progress = it.progress?.copy(outcome = TransferOutcome.Failed, finished = false)) }
        info(resource, exception.localizedMessage ?: exception.javaClass.simpleName, error = true)
    }

    fun cancelTransfer() {
        val progress = state.value.progress ?: return disconnect()
        activeOutboundId?.let { client.cancelPayload(it) }
        incomingPayloads.keys.forEach { client.cancelPayload(it) }
        disconnect()
        state.update { it.copy(page = "transfer", progress = progress.copy(outcome = TransferOutcome.Cancelled, finished = false),
            status = UiMessage(R.string.cancelled)) }
    }

    private fun resetSearching() {
        sessionGeneration.incrementAndGet()
        measurements.reset()
        emissionGate.reset()
        activeAdvertisedToken = null
        client.stopAdvertising()
        client.stopDiscovery()
        state.update { it.copy(isAdvertising = false, isDiscovering = false, receiverQrPayload = null) }
    }

    fun close() {
        closed = true
        disconnect()
        io.shutdown()
    }
}
