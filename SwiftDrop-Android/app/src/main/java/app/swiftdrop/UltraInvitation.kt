package app.swiftdrop

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * A QR rendezvous invitation. The randomly generated secret authenticates the file
 * transport; SSID/password are only for Wi-Fi association, never sufficient on their own.
 *
 * Treat this QR as a sensitive short-lived invitation, do not log or persist it.
 */
data class UltraInvitation(
    val ssid: String,
    val password: String,
    val port: Int,
    val secret: ByteArray
) {
    init {
        require(ssid.isNotEmpty() && ssid.toByteArray(Charsets.UTF_8).size <= 32)
        require(password.length in 8..63)
        require(port in 1024..65535)
        require(secret.size == 32)
    }
    fun encode(): String {
        fun param(v:String) = URLEncoder.encode(v,"UTF-8")
        return "swiftdrop://ultra/v1?ssid=${param(ssid)}&pw=${param(password)}" +
            "&port=$port&key=${Base64.getUrlEncoder().withoutPadding().encodeToString(secret)}"
    }
    companion object {
        fun parse(qr: String?): UltraInvitation? {
            if (qr == null || qr.length !in 70..2048) return null
            return runCatching {
                val uri = URI(qr)
                require(uri.scheme == "swiftdrop" && uri.host == "ultra" &&
                    uri.path == "/v1" && uri.rawFragment == null)
                val entries = requireNotNull(uri.rawQuery).split('&').map { item ->
                    val split = item.indexOf('=')
                    require(split > 0)
                    val key = item.substring(0,split)
                    val value = URLDecoder.decode(item.substring(split+1),"UTF-8")
                    key to value
                }
                val fields = entries.toMap()
                require(entries.size == 4 && fields.size == 4 &&
                    fields.keys == setOf("ssid","pw","port","key"))
                val secret = Base64.getUrlDecoder().decode(requireNotNull(fields["key"]))
                UltraInvitation(requireNotNull(fields["ssid"]),
                    requireNotNull(fields["pw"]),
                    requireNotNull(fields["port"]).toInt(),
                    secret)
            }.getOrNull()
        }
    }
}
