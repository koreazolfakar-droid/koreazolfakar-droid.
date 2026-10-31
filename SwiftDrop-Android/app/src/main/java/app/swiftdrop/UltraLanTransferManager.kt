package app.swiftdrop

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class UltraLanPhase { Idle, Waiting, Joining, Connecting, Sending, Receiving, Complete, Failed }
data class UltraPendingFile(val name: String, val bytes: Long)
data class UltraLanState(
    val phase: UltraLanPhase = UltraLanPhase.Idle,
    val hostPort: Int = 0,
    val pending: UltraPendingFile? = null,
    val progress: TransferProgress? = null,
    val error: String? = null
)

/**
 * Native offline LAN transfer. Host advertises its presence only to the QR-key holder
 * over authenticated UDP discovery; client joins via the OS Wi-Fi network dialog.
 *
 * Still requires two *real* phones for hardware validation. No claimed Wi-Fi band or speed.
 */
class UltraLanTransferManager(
    private val context: Context,
    private val hotspot: UltraHotspotController,
    private val onHistory: (HistoryEntry) -> Unit
) {
    val state = MutableStateFlow(UltraLanState())
    private val io = Executors.newCachedThreadPool()
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val version = AtomicLong()
    private val measurements = TransferMeasurements()
    private val progressGate = ProgressEmissionGate()
    @Volatile private var secret: ByteArray? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    @Volatile private var transfer: Socket? = null
    @Volatile private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var pendingApproval: CompletableFuture<Boolean>? = null

    fun invitation(ssid: String, password: String,
                   security: UltraWifiSecurity = UltraWifiSecurity.WPA2): String? {
        val key = secret ?: return null
        val port = state.value.hostPort
        if (port == 0 || key.size != 32) return null
        return runCatching { UltraInvitation(ssid,password,port,key,security).encode() }.getOrNull()
    }

    fun host() {
        stop()
        val session = version.incrementAndGet()
        try {
            val socket = ServerSocket(0,1).apply { soTimeout = 1000 }
            val discovery = DatagramSocket(socket.localPort).apply { soTimeout = 1000 }
            val key = UltraWire.newSecret()
            server = socket
            udp = discovery
            secret = key
            state.value = UltraLanState(UltraLanPhase.Waiting,hostPort = socket.localPort)
            io.execute { discoveryLoop(session,discovery,key) }
            io.execute { receiveLoop(session,socket,key) }
            hotspot.start()
        } catch (e: Exception) {
            stop()
            state.value = UltraLanState(UltraLanPhase.Failed,error=e.javaClass.simpleName)
        }
    }

    fun decide(accept: Boolean) {
        pendingApproval?.complete(accept)
    }

    private fun discoveryLoop(session: Long, socket: DatagramSocket, key: ByteArray) {
        while (version.get() == session && !socket.isClosed) {
            try {
                val bytes = ByteArray(128)
                val request = DatagramPacket(bytes, bytes.size)
                socket.receive(request)
                if (request.length != 53 || !bytes.copyOfRange(0,5).contentEquals("SDUP1".toByteArray())) continue
                val nonce = bytes.copyOfRange(5,21)
                val proof = bytes.copyOfRange(21,53)
                if (!MessageDigest.isEqual(proof, UltraWire.discoveryProof(key,"probe",nonce))) continue
                val response = "SDUA1".toByteArray() + nonce +
                    UltraWire.discoveryProof(key,"reply",nonce)
                socket.send(DatagramPacket(response,response.size,request.address,request.port))
            } catch (_: SocketTimeoutException) {
                // Periodic lifecycle check, not an error.
            } catch (_: Exception) {
                if (version.get() == session) state.update { it.copy(
                    error = "Wi-Fi discovery unavailable",phase = UltraLanPhase.Failed) }
                return
            }
        }
    }

    private fun receiveLoop(session: Long, listener: ServerSocket, key: ByteArray) {
        while (version.get() == session && !listener.isClosed) {
            try {
                listener.accept().use { remote ->
                    transfer = remote
                    remote.soTimeout = 75_000
                    remote.sendBufferSize = 512 * 1024
                    remote.receiveBufferSize = 512 * 1024
                    try {
                        val channel=UltraWire.host(remote.getInputStream(),remote.getOutputStream(),key)
                        var sink: PendingSink? = null
                        val accepted = UltraFileWire.receive(
                            channel,
                            allow = { file ->
                                val future = CompletableFuture<Boolean>()
                                pendingApproval = future
                                state.update { it.copy(
                                    pending = UltraPendingFile(file.name,file.bytes), error = null
                                ) }
                                try { future.get(60,TimeUnit.SECONDS) }
                                catch (_: Exception) { false }
                                finally {
                                    pendingApproval = null
                                    state.update { it.copy(pending = null) }
                                }
                            },
                            createOutput = { file ->
                                sink = createSink(file)
                                state.update { it.copy(phase = UltraLanPhase.Receiving,
                                    progress = TransferProgress(file.name,"Receiving",0,file.bytes)) }
                                requireNotNull(sink).output
                            },
                            commit = { file ->
                                requireNotNull(sink).commit()
                                state.update { it.copy(phase=UltraLanPhase.Complete,
                                    progress=TransferProgress(file.name,"Received",file.bytes,file.bytes,true)) }
                                onHistory(HistoryEntry(file.name,"Ultra Wi-Fi","Received",file.bytes,
                                    System.currentTimeMillis()))
                            },
                            rollback = { _ -> sink?.rollback() },
                            onProgress = { bytes,total ->
                                updateProgress(session,bytes,total,"Receiving")
                            }
                        )
                        if (!accepted) state.update { it.copy(phase = UltraLanPhase.Waiting) }
                    } catch (e: Exception) {
                        // Surface the actual receive failure without stopping the
                        // hotspot listener. Failed partial sinks are rolled back
                        // inside UltraFileWire.receive; a new peer may retry.
                        if (version.get() == session) state.update {
                            it.copy(
                                phase = UltraLanPhase.Waiting,
                                pending = null,
                                error = "Ultra receive failed: " + e.javaClass.simpleName,
                                progress = it.progress?.copy(outcome = TransferOutcome.Failed)
                            )
                        }
                    } finally { if (transfer === remote) transfer = null }
                }
            } catch (_: SocketTimeoutException) {
                // Await another client.
            } catch (_: Exception) {
                if (version.get() == session) state.update { it.copy(phase=UltraLanPhase.Failed,
                    error="Local receiver unavailable") }
                return
            }
        }
    }

    private fun updateProgress(session: Long, bytes: Long, total: Long, direction: String) {
        if (session != version.get()) return
        val stamp=SystemClock.elapsedRealtime()
        val id=session
        val measurement=measurements.sample(id,bytes,total,stamp)
        if (progressGate.shouldEmit(id,bytes,stamp,bytes==total)) {
            state.update { s ->
                val p=s.progress ?: return@update s
                s.copy(progress=p.copy(bytes=bytes,total=total,measurement=measurement,
                    direction=direction))
            }
        }
    }

    fun send(invite: UltraInvitation, files: List<SelectedFile>) {
        if (files.isEmpty() || files.size > 200) return
        stop()
        val session=version.incrementAndGet()
        state.value=UltraLanState(UltraLanPhase.Joining)
        if (Build.VERSION.SDK_INT < 29) {
            io.execute {
                val wifi=cm.allNetworks.firstOrNull { net ->
                    cm.getNetworkCapabilities(net)?.hasTransport(
                        NetworkCapabilities.TRANSPORT_WIFI) == true
                }
                if (wifi == null) fail(session,"Connect to the receiver Wi-Fi network first")
                else transferFiles(session,wifi,invite,files)
            }
            return
        }
        try {
            val builder=WifiNetworkSpecifier.Builder().setSsid(invite.ssid)
            val specifier=when (invite.security) {
                UltraWifiSecurity.WPA2 -> builder.setWpa2Passphrase(invite.password).build()
                UltraWifiSecurity.WPA3 -> builder.setWpa3Passphrase(invite.password).build()
            }
            val request=NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier).build()
            val launched = AtomicBoolean(false)
            val cb=object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (version.get() == session && launched.compareAndSet(false,true)) io.execute {
                        transferFiles(session,network,invite,files)
                    }
                }
                override fun onUnavailable() { fail(session,"Wi-Fi connection not approved or unavailable") }
                override fun onLost(network:Network) {
                    if (version.get() == session && state.value.phase in setOf(
                            UltraLanPhase.Joining,UltraLanPhase.Connecting,
                            UltraLanPhase.Sending)) {
                        transfer?.close()
                        fail(session,"Wi-Fi connection lost")
                    }
                }
            }
            callback=cb
            cm.requestNetwork(request,cb,40_000)
        } catch (e: Exception) {
            fail(session,e.localizedMessage ?: "Could not request local Wi-Fi connection")
        }
    }

    private fun transferFiles(
        session: Long, network: Network, invite: UltraInvitation, files: List<SelectedFile>
    ) {
        if (session!=version.get()) return
        try {
            state.update { it.copy(phase=UltraLanPhase.Connecting) }
            val host=findHost(session,network,invite)
            for (file in files) {
                if (session != version.get()) return
                require(file.bytes in 0..UltraFileWire.MAX_FILE_BYTES) { "Unsupported file size" }
                network.socketFactory.createSocket(host,invite.port).use { socket ->
                    transfer=socket
                    socket.soTimeout=75_000
                    socket.sendBufferSize = 512 * 1024
                    socket.receiveBufferSize = 512 * 1024
                    val channel=UltraWire.client(socket.getInputStream(),socket.getOutputStream(),invite.secret)
                    state.update { it.copy(phase=UltraLanPhase.Sending,
                        progress=TransferProgress(file.name,"Sending",0,file.bytes)) }
                    context.contentResolver.openInputStream(file.uri).use { source ->
                        val accepted=UltraFileWire.send(channel,
                            UltraFileWire.Offer(file.name,file.bytes),
                            requireNotNull(source) { "Cannot read selected file" }) { done ->
                            updateProgress(session,done,file.bytes,"Sending")
                        }
                        if (!accepted) throw IllegalStateException("File declined or not saved")
                    }
                    onHistory(HistoryEntry(file.name,"Ultra Wi-Fi","Sent",file.bytes,System.currentTimeMillis()))
                    transfer=null
                }
            }
            state.update { it.copy(phase=UltraLanPhase.Complete,
                progress=it.progress?.copy(finished=true,outcome=TransferOutcome.Complete)) }
        } catch (e: Exception) {
            fail(session,e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            transfer=null
            callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
            callback=null
        }
    }

    private fun findHost(session:Long,net:Network,invite:UltraInvitation): InetAddress {
        val nonce=ByteArray(16).also(SecureRandom()::nextBytes)
        val request="SDUP1".toByteArray()+nonce+
            UltraWire.discoveryProof(invite.secret,"probe",nonce)
        // Android may deliver onAvailable before DHCP routes/link addresses.
        // Wait briefly on the worker thread so the hotspot gateway can be
        // contacted directly; broadcast-only discovery fails on some phones.
        var link = cm.getLinkProperties(net)
        for (attempt in 0 until 12) {
            if (link?.routes?.any { it.gateway is Inet4Address } == true) break
            if (session != version.get()) throw IllegalStateException("Transfer cancelled")
            Thread.sleep(150)
            link = cm.getLinkProperties(net)
        }
        val destinations = UltraDiscoveryTargets.candidates(
            link?.routes?.mapNotNull { it.gateway as? Inet4Address } ?: emptyList(),
            link?.linkAddresses?.map { it.address to it.prefixLength } ?: emptyList()
        )
        DatagramSocket().use { udpClient ->
            net.bindSocket(udpClient)
            udpClient.broadcast=true
            udpClient.soTimeout=900
            for (attempt in 0 until 5) {
                require(session==version.get()) { "Transfer cancelled" }
                for (ip in destinations) runCatching {
                    udpClient.send(DatagramPacket(request,request.size,ip,invite.port))
                }
                val stop=SystemClock.elapsedRealtime()+900
                while (SystemClock.elapsedRealtime()<stop) {
                    try {
                        val received=ByteArray(128)
                        val reply=DatagramPacket(received,received.size)
                        udpClient.receive(reply)
                        if (reply.length!=53 ||
                            !received.copyOfRange(0,5).contentEquals("SDUA1".toByteArray()) ||
                            !received.copyOfRange(5,21).contentEquals(nonce)) continue
                        val proof=received.copyOfRange(21,53)
                        if (MessageDigest.isEqual(proof,
                                UltraWire.discoveryProof(invite.secret,"reply",nonce))) return reply.address
                    } catch (_: SocketTimeoutException) { break }
                }
            }
        }
        throw IllegalStateException("Receiver not found on local Wi-Fi")
    }

    private data class PendingSink(
        val output: OutputStream,
        val commit: () -> Unit,
        val rollback: () -> Unit
    )

    private fun createSink(file: UltraFileWire.Offer): PendingSink {
        // Reserve disk capacity before creating an output and never expose partial downloads.
        // MediaStore Downloads is on shared storage, which may not have the
        // same free space as app-private internal storage on every device.
        val external = context.getExternalFilesDir(null)
        val space = android.os.StatFs(
            (external ?: context.filesDir).absolutePath
        ).availableBytes
        require(file.bytes <= space - minOf(64L*1024*1024,space/10)) { "Insufficient storage" }
        val name=SafeFileName.normalize(file.name)
        if (Build.VERSION.SDK_INT>=29) {
            val extension=name.substringAfterLast('.', "").lowercase()
            val type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
            val meta=ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,name)
                put(MediaStore.Downloads.MIME_TYPE,type)
                put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/SwiftDrop")
                put(MediaStore.Downloads.IS_PENDING,1)
            }
            val resolver=context.contentResolver
            val target=requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,meta))
            return try {
                val stream=requireNotNull(resolver.openOutputStream(target))
                PendingSink(stream,
                    commit = {
                        check(resolver.update(target,ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING,0)
                        },null,null)>0) { "Failed to publish file" }
                    },
                    rollback = { resolver.delete(target,null,null) })
            } catch (e: Exception) {
                resolver.delete(target,null,null)
                throw e
            }
        }
        val folder=File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),"SwiftDrop")
        check(folder.isDirectory || folder.mkdirs())
        var dest=File(folder,name)
        var idx=1
        while (dest.exists()) {
            dest=File(folder,"${name.substringBeforeLast('.',name)} (${idx++})" +
                if ('.' in name) ".${name.substringAfterLast('.')}" else "")
        }
        val temp=File.createTempFile("ultra-", ".part", folder)
        return PendingSink(FileOutputStream(temp),
            commit={ check(temp.renameTo(dest)) { "Cannot publish verified file" } },
            rollback={ temp.delete() })
    }

    private fun fail(session: Long, text: String) {
        if (session==version.get()) state.update {
            it.copy(phase=UltraLanPhase.Failed,error=text,pending=null,
                progress=it.progress?.copy(outcome=TransferOutcome.Failed))
        }
    }

    fun stop() {
        version.incrementAndGet()
        pendingApproval?.complete(false)
        pendingApproval=null
        transfer?.let { runCatching { it.close() } }
        transfer=null
        udp?.close()
        udp=null
        server?.let { runCatching { it.close() } }
        server=null
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback=null
        secret=null
        hotspot.stop()
        measurements.reset()
        progressGate.reset()
        state.value=UltraLanState()
    }

    fun close() { stop();io.shutdownNow() }
}
