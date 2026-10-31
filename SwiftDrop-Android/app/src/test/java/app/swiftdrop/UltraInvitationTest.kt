package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class UltraInvitationTest {
    @Test fun qrRoundTripPreservesUnicodeAndRandomKey() {
        val original = UltraInvitation("SwiftDrop-شبكة","1234abc!23",49111,UltraWire.newSecret())
        val restored = requireNotNull(UltraInvitation.parse(original.encode()))
        assertEquals(original.ssid,restored.ssid)
        assertEquals(original.password,restored.password)
        assertEquals(original.port,restored.port)
        assertArrayEquals(original.secret,restored.secret)
    }

    @Test fun wpa3RoundTripAndLegacyWpa2AreBothSupported() {
        val original = UltraInvitation(
            "Galaxy-Receiver", "correcthorse123", 53021,
            UltraWire.newSecret(), UltraWifiSecurity.WPA3
        )
        val qr = original.encode()
        assertTrue(qr.contains("security=wpa3"))
        val restored = requireNotNull(UltraInvitation.parse(qr))
        assertEquals(UltraWifiSecurity.WPA3, restored.security)
        assertArrayEquals(original.secret, restored.secret)
        val legacy = UltraInvitation(
            "Old-Receiver", "abcdefgh", 50001, UltraWire.newSecret()
        )
        assertFalse(legacy.encode().contains("security="))
        assertEquals(UltraWifiSecurity.WPA2, UltraInvitation.parse(legacy.encode())?.security)
        assertNull(UltraInvitation.parse(qr.replace("security=wpa3", "security=unknown")))
        assertNull(UltraInvitation.parse(qr + "&security=wpa2"))
    }

    @Test fun nearbyQrAndMalformedUltraLinksAreRejected() {
        assertNull(UltraInvitation.parse("swiftdrop://pair/v1/0123456789abcdef0123456789abcdef"))
        assertNull(UltraInvitation.parse("https://example.com"))
        assertNull(UltraInvitation.parse("swiftdrop://ultra/v1?ssid=X"))
    }

    @Test fun rejectsTamperedCredentialsAndDuplicatedParameters() {
        val valid = UltraInvitation("test","abcdefgh",59400,UltraWire.newSecret()).encode()
        assertNull(UltraInvitation.parse(valid.replace("port=59400","port=0")))
        assertNull(UltraInvitation.parse(valid.replace("pw=abcdefgh","pw=x")))
        assertNull(UltraInvitation.parse(valid+"&ssid=other"))
        assertNull(UltraInvitation.parse(valid.replace("/v1?","/v9?")))
    }
}
