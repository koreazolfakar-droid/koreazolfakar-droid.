package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class QrPairingTest {
    @Test fun validQrRoundTripsAndAdvertisedNameMatches() {
        val token = QrPairing.newToken()
        assertEquals(32, token.length)
        val raw = QrPairing.payload(token)
        assertEquals(token, QrPairing.parsePayload(raw))
        val advertisement = QrPairing.advertisement(token,"Galaxy S24")
        assertEquals(token,QrPairing.parseAdvertisement(advertisement)?.token)
        assertEquals("Galaxy S24",QrPairing.parseAdvertisement(advertisement)?.name)
    }

    @Test fun invalidQrIsRejectedWithoutExternalLinks() {
        for (raw in listOf(null,"","https://example.com","swiftdrop://pair/v1/",
            "swiftdrop://pair/v1/"+"g".repeat(32),"swiftdrop://pair/v2/"+"a".repeat(32),
            "swiftdrop://pair/v1/"+"a".repeat(32)+"?extra=1",
            "SWIFTDROP://pair/v1/"+"a".repeat(32))) {
            assertNull(raw,QrPairing.parsePayload(raw))
        }
    }

    @Test fun spoofedOrMalformedAdvertisementDoesNotMatch() {
        val token = "a".repeat(32)
        assertNull(QrPairing.parseAdvertisement("Galaxy S24"))
        assertNull(QrPairing.parseAdvertisement("SD1:"+"z".repeat(32)+":phone"))
        assertNull(QrPairing.parseAdvertisement("SD1:"+token+":"))
        assertNotEquals(token,QrPairing.parseAdvertisement(
            QrPairing.advertisement("b".repeat(32),"Galaxy S24"))?.token)
    }

    @Test fun generatedTokensDifferAndNeverIncludeFiles() {
        val a=QrPairing.newToken(); val b=QrPairing.newToken()
        assertNotEquals(a,b)
        assertFalse(QrPairing.payload(a).contains("filename"))
    }
}
