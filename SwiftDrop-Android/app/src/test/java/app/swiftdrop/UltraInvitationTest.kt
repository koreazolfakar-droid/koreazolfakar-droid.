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
