package app.swiftdrop

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned, authenticated encrypted LAN wire protocol. Does not depend on Play Services.
 *
 * QR carries a fresh random 256-bit session secret (never the Wi-Fi password alone).
 * Both peers prove possession via challenge/response before file names are exchanged.
 * Each direction derives a separate AES-256-GCM key and monotonically increasing IV.
 * A new QR secret and challenge must be generated for every hosting session.
 */
internal object UltraWire {
    const val MAGIC = 0x53445531 // "SDU1"
    const val CHUNK = 64 * 1024
    private const val MAX_FRAME = CHUNK + 64
    private val secureRandom = SecureRandom()

    fun newSecret(): ByteArray = ByteArray(32).also(secureRandom::nextBytes)

    private fun mac(secret: ByteArray, label: String, first: ByteArray, second: ByteArray): ByteArray {
        val h = Mac.getInstance("HmacSHA256")
        h.init(SecretKeySpec(secret, "HmacSHA256"))
        h.update(label.toByteArray(Charsets.US_ASCII))
        h.update(first)
        return h.doFinal(second)
    }

    private fun checkedSecret(secret: ByteArray) {
        require(secret.size == 32) { "Session secret must be 256-bit" }
    }

    fun host(input: InputStream, output: OutputStream, secret: ByteArray): Channel {
        checkedSecret(secret)
        val reader = DataInputStream(input)
        val writer = DataOutputStream(output)
        val nonceA = ByteArray(16).also(secureRandom::nextBytes)
        writer.writeInt(MAGIC)
        writer.write(nonceA)
        writer.flush()

        val nonceB = ByteArray(16)
        reader.readFully(nonceB)
        val proof = ByteArray(32)
        reader.readFully(proof)
        if (!MessageDigest.isEqual(proof, mac(secret,"client",nonceA,nonceB))) {
            throw SecurityException("Unauthenticated Ultra sender")
        }
        writer.write(mac(secret,"server",nonceA,nonceB))
        writer.flush()
        return Channel(reader, writer,
            readKey = mac(secret,"client-to-host",nonceA,nonceB),
            writeKey = mac(secret,"host-to-client",nonceA,nonceB))
    }

    fun client(input: InputStream, output: OutputStream, secret: ByteArray): Channel {
        checkedSecret(secret)
        val reader = DataInputStream(input)
        val writer = DataOutputStream(output)
        require(reader.readInt() == MAGIC) { "Unsupported Ultra protocol" }
        val nonceA = ByteArray(16)
        reader.readFully(nonceA)
        val nonceB = ByteArray(16).also(secureRandom::nextBytes)
        writer.write(nonceB)
        writer.write(mac(secret,"client",nonceA,nonceB))
        writer.flush()
        val proof = ByteArray(32)
        reader.readFully(proof)
        if (!MessageDigest.isEqual(proof, mac(secret,"server",nonceA,nonceB))) {
            throw SecurityException("Unauthenticated Ultra receiver")
        }
        return Channel(reader, writer,
            readKey = mac(secret,"host-to-client",nonceA,nonceB),
            writeKey = mac(secret,"client-to-host",nonceA,nonceB))
    }

    data class Frame(val type: Int, val data: ByteArray)

    class Channel internal constructor(
        private val input: DataInputStream,
        private val output: DataOutputStream,
        private val readKey: ByteArray,
        private val writeKey: ByteArray
    ) {
        private var readSequence = 0L
        private var writeSequence = 0L

        private fun iv(sequence: Long): ByteArray {
            require(sequence >= 0)
            return ByteArray(12).also { buffer ->
                for (i in 0 until 8) buffer[11-i] = (sequence ushr (i*8)).toByte()
            }
        }

        @Synchronized
        fun send(type: Int, payload: ByteArray = ByteArray(0)) {
            require(type in 1..255)
            require(payload.size <= CHUNK) { "Frame too large" }
            check(writeSequence != Long.MAX_VALUE) { "Sequence exhausted" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(writeKey,"AES"),
                GCMParameterSpec(128,iv(writeSequence++)))
            val plain = ByteArray(payload.size+1)
            plain[0] = type.toByte()
            payload.copyInto(plain,1)
            val encrypted = cipher.doFinal(plain)
            output.writeInt(encrypted.size)
            output.write(encrypted)
            output.flush()
        }

        @Synchronized
        fun read(): Frame {
            check(readSequence != Long.MAX_VALUE) { "Sequence exhausted" }
            val size = input.readInt()
            require(size in 17..MAX_FRAME) { "Invalid encrypted frame size" }
            val sealed = ByteArray(size)
            input.readFully(sealed)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(readKey,"AES"),
                GCMParameterSpec(128,iv(readSequence++)))
            val plain = cipher.doFinal(sealed)
            require(plain.isNotEmpty())
            return Frame(plain[0].toInt() and 255, plain.copyOfRange(1,plain.size))
        }
    }
}

/**
 * One-file-at-a-time bounded-memory protocol; host decides whether to accept each file.
 * The writer only publishes a completed file after length and SHA-256 are verified.
 */
internal object UltraFileWire {
    const val OFFER = 1
    const val ACCEPT = 2
    const val REJECT = 3
    const val BLOCK = 4
    const val END = 5
    const val COMPLETE = 6
    const val FAILURE = 7
    const val MAX_FILE_BYTES = 1L shl 40 // protocol sanity bound, not guaranteed disk capacity

    data class Offer(val name: String, val bytes: Long)
    private fun header(offer: Offer): ByteArray {
        val name = offer.name.toByteArray(Charsets.UTF_8)
        require(name.isNotEmpty() && name.size <= 1024)
        require(offer.bytes in 0..MAX_FILE_BYTES)
        return java.io.ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { d ->
                d.writeShort(name.size)
                d.write(name)
                d.writeLong(offer.bytes)
            }
        }.toByteArray()
    }

    private fun parseHeader(value: ByteArray): Offer {
        val inp = DataInputStream(value.inputStream())
        val nameSize = inp.readUnsignedShort()
        require(nameSize in 1..1024)
        val name = ByteArray(nameSize).also(inp::readFully).toString(Charsets.UTF_8)
        val size = inp.readLong()
        require(inp.available() == 0)
        require(size in 0..MAX_FILE_BYTES)
        return Offer(SafeFileName.normalize(name),size)
    }

    private fun requireType(frame: UltraWire.Frame, expected: Int) {
        require(frame.type == expected) { "Unexpected Ultra frame: ${frame.type}" }
    }

    fun send(
        channel: UltraWire.Channel,
        offer: Offer,
        input: InputStream,
        onProgress: (Long) -> Unit = {}
    ): Boolean {
        channel.send(OFFER,header(offer))
        val response = channel.read()
        if (response.type == REJECT) return false
        requireType(response,ACCEPT)
        val digest = MessageDigest.getInstance("SHA-256")
        var remaining = offer.bytes
        var done = 0L
        val buffer = ByteArray(UltraWire.CHUNK)
        while (remaining > 0) {
            val read = input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
            require(read > 0) { "Source file ended before its declared size" }
            digest.update(buffer,0,read)
            channel.send(BLOCK,buffer.copyOf(read))
            remaining -= read
            done += read
            onProgress(done)
        }
        require(input.read() == -1) { "Source file grew while transferring" }
        channel.send(END,digest.digest())
        return when(channel.read().type) {
            COMPLETE -> true
            FAILURE -> false
            else -> throw IllegalArgumentException("Invalid final acknowledgement")
        }
    }

    /**
     * The caller must create a private/pending destination and delete it on failure.
     * Approval callback may wait for an explicit local UI decision before returning.
     */
    fun receive(
        channel: UltraWire.Channel,
        allow: (Offer) -> Boolean,
        createOutput: (Offer) -> OutputStream,
        commit: (Offer) -> Unit,
        rollback: (Offer) -> Unit,
        onProgress: (Long, Long) -> Unit = { _,_ -> }
    ): Boolean {
        val first = channel.read()
        requireType(first,OFFER)
        val offer = parseHeader(first.data)
        if (!allow(offer)) {
            channel.send(REJECT)
            return false
        }
        var created = false
        try {
            val target = createOutput(offer)
            created = true
            target.use { output ->
                channel.send(ACCEPT)
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                while (true) {
                    val frame = channel.read()
                    when(frame.type) {
                        BLOCK -> {
                            require(frame.data.isNotEmpty())
                            require(frame.data.size <= offer.bytes - received) {
                                "File exceeded advertised length"
                            }
                            output.write(frame.data)
                            digest.update(frame.data)
                            received += frame.data.size
                            onProgress(received,offer.bytes)
                        }
                        END -> {
                            require(received == offer.bytes) { "Truncated Ultra file" }
                            require(frame.data.size == 32 &&
                                MessageDigest.isEqual(frame.data,digest.digest())) { "Checksum mismatch" }
                            break
                        }
                        else -> error("Unexpected file transfer frame")
                    }
                }
                output.flush()
            }
            commit(offer)
            channel.send(COMPLETE)
            return true
        } catch (e: Exception) {
            if (created) rollback(offer)
            runCatching { channel.send(FAILURE) }
            throw e
        }
    }
}
