package app.swiftdrop

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.google.android.gms.nearby.Nearby
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

/**
 * First functional transport milestone: a single Nearby Connections link.
 * Actual Wi-Fi radio choice is delegated to Google Play Services and OS.
 */
class NearbyTransferManager(private val context: Context) {
    val state = MutableStateFlow(TransferUiState())
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "app.swiftdrop.nearby"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    private val io = Executors.newSingleThreadExecutor()
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingMetadata = ConcurrentHashMap<Long, IncomingMetadata>()
    private val completed = ConcurrentHashMap.newKeySet<Long>()
    private val persisting = ConcurrentHashMap.newKeySet<Long>()
    private var connectedId: String? = null
    private var outboundUris: List<Uri> = emptyList()
    private var outboundIndex = 0
    private var activeOutboundId: Long? = null
    private var activeOutboundName = ""
    private var outboundPeer = ""

    private data class IncomingMetadata(val name: String, val size: Long)
    private fun info(message: String) { state.update { it.copy(status = message) } }

    fun setPage(page: String) { state.update { it.copy(page = page) } }
    fun toggleTheme() { state.update { it.copy(darkTheme = !it.darkTheme) } }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, details: DiscoveredEndpointInfo) {
            state.update {
                it.copy(peers = (it.peers.filterNot { p -> p.id == endpointId } +
                    Peer(endpointId, details.endpointName)))
            }
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
                    details.authenticationDigits), status = "Compare the code on both phones")
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            state.update { it.copy(pending = null) }
            if (result.status.statusCode == com.google.android.gms.nearby.connection.ConnectionsStatusCodes.STATUS_OK) {
                connectedId = endpointId
                client.stopAdvertising()
                client.stopDiscovery()
                val peerName = state.value.peers.find { it.id == endpointId }?.name
                    ?: state.value.connectedName ?: "Nearby device"
                outboundPeer = peerName
                state.update { it.copy(connectedName = peerName, isAdvertising = false,
                    isDiscovering = false, page = "transfer", status = "Connected securely") }
                if (outboundUris.isNotEmpty()) sendNext()
            } else {
                info("Connection rejected or failed (${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedId = null
            activeOutboundId = null
            state.update { it.copy(connectedName = null, status = "Disconnected", pending = null) }
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
                    } catch (_: Exception) { info("Invalid file metadata ignored") }
                }
                Payload.Type.FILE -> {
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
            val name = if (sent) activeOutboundName else incomingMetadata[id]?.name ?: "Incoming file"
            val total = update.totalBytes.coerceAtLeast(0L)
            state.update { it.copy(page = "transfer", progress = TransferProgress(name,
                direction, update.bytesTransferred.coerceAtLeast(0L), total,
                finished = update.status == PayloadTransferUpdate.Status.SUCCESS)) }
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (sent) {
                        activeOutboundId = null
                        state.update { it.copy(history = listOf(
                            HistoryEntry(name, outboundPeer, "Sent", total, System.currentTimeMillis())
                        ) + it.history) }
                        sendNext()
                    } else {
                        completed.add(id)
                        maybePersistReceived(id)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    if (sent) activeOutboundId = null
                    info("Transfer stopped (${update.status})")
                }
            }
        }
    }

    fun advertise() {
        resetSearching()
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = emptyList()
        state.update { it.copy(page = "receive", status = "Making your phone visible…",
            peers = emptyList(), selected = emptyList()) }
        client.startAdvertising(Build.MODEL.take(26), serviceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { state.update { s -> s.copy(isAdvertising = true,
                status = "Waiting for sender — keep this screen open") } }
            .addOnFailureListener { info("Cannot receive: ${it.localizedMessage ?: it.javaClass.simpleName}") }
    }

    fun discover(uris: List<Uri>) {
        if (uris.isEmpty()) return
        resetSearching()
        client.stopAllEndpoints()
        connectedId = null
        outboundUris = uris
        outboundIndex = 0
        state.update { it.copy(page = "discover", status = "Searching for nearby devices…",
            peers = emptyList(), selected = uris) }
        client.startDiscovery(serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener { state.update { s -> s.copy(isDiscovering = true) } }
            .addOnFailureListener { info("Cannot search: ${it.localizedMessage ?: it.javaClass.simpleName}") }
    }

    fun connect(peer: Peer) {
        info("Connecting to ${peer.name}…")
        client.requestConnection(Build.MODEL.take(26), peer.id, connectionCallback)
            .addOnFailureListener { info("Connection request failed: ${it.localizedMessage}") }
    }

    fun answerConnection(accept: Boolean) {
        val request = state.value.pending ?: return
        state.update { it.copy(pending = null, connectedName = request.peerName) }
        if (accept) {
            client.acceptConnection(request.endpointId, payloadCallback)
                .addOnFailureListener { info("Accept failed: ${it.localizedMessage}") }
        } else {
            client.rejectConnection(request.endpointId)
            info("Connection declined")
        }
    }

    private fun sendNext() {
        val target = connectedId ?: return
        if (activeOutboundId != null) return
        if (outboundIndex >= outboundUris.size) {
            outboundUris = emptyList()
            info("All selected files sent")
            return
        }
        val uri = outboundUris[outboundIndex++]
        io.execute {
            try {
                val meta = readMetadata(uri)
                val pfd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
                val filePayload = Payload.fromFile(pfd)
                activeOutboundId = filePayload.id
                activeOutboundName = meta.name
                val metadata = JSONObject().put("type", "file-metadata")
                    .put("id", filePayload.id).put("name", meta.name).put("size", meta.size)
                client.sendPayload(target, Payload.fromBytes(metadata.toString().toByteArray(Charsets.UTF_8)))
                    .addOnSuccessListener {
                        client.sendPayload(target, filePayload)
                            .addOnFailureListener { e ->
                                filePayload.close()
                                activeOutboundId = null
                                info("Send failed: ${e.localizedMessage}")
                            }
                    }
                    .addOnFailureListener { e ->
                        filePayload.close()
                        activeOutboundId = null
                        info("Metadata failed: ${e.localizedMessage}")
                    }
            } catch (e: Exception) {
                activeOutboundId = null
                info("Could not read file: ${e.localizedMessage}")
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
        if (!completed.contains(id)) return
        val file = incomingPayloads[id] ?: return
        val meta = incomingMetadata[id] ?: return
        if (!persisting.add(id)) return // prevent metadata/transfer completion race
        io.execute {
            try {
                val uri = requireNotNull(file.asFile()?.asUri())
                val written = saveIntoDownloads(uri, meta.name)
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                state.update { s -> s.copy(status = "Saved ${meta.name} to $written",
                    progress = TransferProgress(meta.name, "Received", meta.size, meta.size, true),
                    history = listOf(HistoryEntry(meta.name,
                        s.connectedName ?: "Nearby device", "Received", meta.size,
                        System.currentTimeMillis())) + s.history) }
            } catch (e: Exception) {
                info("Saving failed: ${e.localizedMessage}")
            } finally {
                incomingPayloads.remove(id)
                incomingMetadata.remove(id)
                completed.remove(id)
            }
        }
    }

    private fun saveIntoDownloads(source: Uri, untrustedName: String): String {
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
                        input.copyTo(output, 64 * 1024)
                    }
                }
                resolver.update(destination, ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }, null, null)
            } catch (e: Exception) {
                resolver.delete(destination, null, null)
                throw e
            }
            return "Downloads/SwiftDrop"
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
                FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) }
            }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        return "App downloads/SwiftDrop"
    }

    fun disconnect() {
        resetSearching()
        client.stopAllEndpoints()
        connectedId = null
        activeOutboundId = null
        outboundUris = emptyList()
        state.update { it.copy(status = "Ready to share", isAdvertising = false,
            isDiscovering = false, connectedName = null, pending = null, peers = emptyList(),
            selected = emptyList(), progress = null, page = "home") }
    }

    private fun resetSearching() {
        client.stopAdvertising()
        client.stopDiscovery()
        state.update { it.copy(isAdvertising = false, isDiscovering = false) }
    }

    fun close() {
        disconnect()
        io.shutdown()
    }
}
