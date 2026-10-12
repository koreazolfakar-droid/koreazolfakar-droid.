package app.swiftdrop

import java.security.SecureRandom

/**
 * QR is a short-lived rendezvous hint, NOT a trusted authentication token.
 * Nearby's matching authentication digits must still be compared on both phones.
 * No file names, file content, IP address or credentials are encoded.
 */
object QrPairing {
    private val tokenRegex = Regex("[0-9a-f]{32}")
    private const val URI_PREFIX = "swiftdrop://pair/v1/"
    private const val AD_PREFIX = "SD1:"

    fun newToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun payload(token: String): String {
        require(tokenRegex.matches(token)) { "Invalid pairing token" }
        return URI_PREFIX + token
    }

    fun parsePayload(value: String?): String? {
        if (value == null || value.length != URI_PREFIX.length + 32 ||
            !value.startsWith(URI_PREFIX)) return null
        val token = value.substring(URI_PREFIX.length)
        return token.takeIf(tokenRegex::matches)
    }

    fun advertisement(token: String, model: String): String {
        require(tokenRegex.matches(token))
        val cleanName = model.replace(':',' ').replace('\n',' ').trim().take(26)
        return "$AD_PREFIX$token:${cleanName.ifBlank { "Android" }}"
    }

    data class AdvertisedPeer(val token: String, val name: String)
    fun parseAdvertisement(value: String): AdvertisedPeer? {
        if (!value.startsWith(AD_PREFIX)) return null
        val pieces = value.removePrefix(AD_PREFIX).split(':',limit=2)
        if (pieces.size != 2 || !tokenRegex.matches(pieces[0]) || pieces[1].isBlank()) return null
        return AdvertisedPeer(pieces[0],pieces[1])
    }
}
