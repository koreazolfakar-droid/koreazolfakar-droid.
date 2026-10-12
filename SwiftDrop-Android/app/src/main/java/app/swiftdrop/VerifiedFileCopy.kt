package app.swiftdrop

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Streams to an unpublished destination with bounded memory and strict byte-count checks.
 * Unknown lengths (-1) are allowed. We do not claim cryptographic integrity here.
 */
object VerifiedFileCopy {
    @Throws(IOException::class)
    fun copy(
        input: InputStream,
        output: OutputStream,
        metadataLength: Long = -1L,
        transportLength: Long = -1L,
        isSessionActive: () -> Boolean = { true }
    ): Long {
        require(metadataLength >= -1 && transportLength >= -1) { "Invalid expected size" }
        var total = 0L
        var noProgressReads = 0
        val buffer = ByteArray(256 * 1024)
        while (true) {
            if (!isSessionActive()) throw IOException("Transfer session cancelled")
            val count = input.read(buffer)
            if (count == -1) break
            if (count == 0) {
                if (++noProgressReads > 16) throw IOException("File input made no progress")
                continue
            }
            noProgressReads = 0
            if (Long.MAX_VALUE - total < count) throw IOException("File too large")
            total += count
            if ((metadataLength >= 0 && total > metadataLength) ||
                (transportLength >= 0 && total > transportLength)) {
                throw IOException("Received file size does not match the sender's declaration")
            }
            output.write(buffer,0,count)
        }
        if (!isSessionActive()) throw IOException("Transfer session cancelled")
        if ((metadataLength >= 0 && total != metadataLength) ||
            (transportLength >= 0 && total != transportLength)) {
            throw IOException("Received file size does not match the sender's declaration")
        }
        output.flush()
        return total
    }
}
