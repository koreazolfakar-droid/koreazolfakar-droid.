package app.swiftdrop

/** Remove path traversal and filesystem-hostile characters from peer-provided names. */
object SafeFileName {
    fun normalize(name: String): String {
        val normalized = name
            .replace('\\', '/')
            .substringAfterLast('/')
            .replace(Regex("[\\u0000-\\u001f\\u007f<>:\"|?*]"), "_")
            .trim(' ', '.')
            .take(160)
            .trimEnd(' ', '.')
        return normalized.ifBlank { "received-file" }
    }
}
