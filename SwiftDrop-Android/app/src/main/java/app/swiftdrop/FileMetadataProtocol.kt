package app.swiftdrop

import org.json.JSONObject

// SwiftDrop @refactor Codex 13/10/2026 Preserve v1 metadata keys and UTF-8 encoding exactly.
data class LegacyFileMetadata(val payloadId: Long, val name: String, val size: Long)
object FileMetadataProtocol {
    fun encode(payloadId: Long, name: String, size: Long): ByteArray {
        require(size >= -1)
        return JSONObject().put("type", "file-metadata").put("id", payloadId)
            .put("name", SafeFileName.normalize(name)).put("size", size)
            .toString().toByteArray(Charsets.UTF_8)
    }
    fun decode(bytes: ByteArray): LegacyFileMetadata? {
        require(bytes.size <= 32 * 1024) { "Metadata exceeds Nearby bytes limit" }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        if (json.optString("type") != "file-metadata") return null
        val size = json.optLong("size", -1L)
        require(size >= -1) { "Invalid file size" }
        return LegacyFileMetadata(json.getLong("id"), SafeFileName.normalize(json.getString("name")), size)
    }
}
