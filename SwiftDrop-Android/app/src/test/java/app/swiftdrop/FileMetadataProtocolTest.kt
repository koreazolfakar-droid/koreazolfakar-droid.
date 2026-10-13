package app.swiftdrop

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FileMetadataProtocolTest {
    @Test fun encoderPreservesExactLegacyKeysAndLongLengths() {
        val json = JSONObject(String(FileMetadataProtocol.encode(123, "ملف.txt", 3_000_000_000), Charsets.UTF_8))
        assertEquals(setOf("type", "id", "name", "size"), json.keys().asSequence().toSet())
        assertEquals("file-metadata", json.getString("type"))
        assertEquals(123, json.getLong("id"))
        assertEquals("ملف.txt", json.getString("name"))
        assertEquals(3_000_000_000, json.getLong("size"))
    }
    @Test fun decoderAcceptsLegacyMissingSizeAndUnknownFields() {
        val raw = """{"type":"file-metadata","id":1,"name":"old.txt","extension":true}"""
        assertEquals(LegacyFileMetadata(1, "old.txt", -1), FileMetadataProtocol.decode(raw.toByteArray()))
    }
    @Test fun unrelatedMessagesAreIgnoredAndNamesRemainSafe() {
        assertNull(FileMetadataProtocol.decode("""{"type":"future"}""".toByteArray()))
        val result = FileMetadataProtocol.decode("""{"type":"file-metadata","id":1,"name":"../bad.txt","size":0}""".toByteArray())
        assertEquals(SafeFileName.normalize("../bad.txt"), result?.name)
    }
    @Test fun invalidOrOversizedMetadataIsRejected() {
        for (raw in listOf("{}", "{", """{"type":"file-metadata","id":1,"name":"a","size":-2}""")) {
            try {
                val result = FileMetadataProtocol.decode(raw.toByteArray())
                assertNull(result)
            } catch (_: Exception) { /* Malformed known messages must not create a transfer. */ }
        }
        try { FileMetadataProtocol.decode(ByteArray(32769)); fail() } catch (_: IllegalArgumentException) {}
    }
}
