package app.swiftdrop

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class VerifiedFileCopyTest {
    @Test fun copiesValidPayloadWithoutModifyingBytes() {
        val src=ByteArray(650_000) { (it % 251).toByte() }
        val target=ByteArrayOutputStream()
        val count=VerifiedFileCopy.copy(ByteArrayInputStream(src),target,src.size.toLong(),src.size.toLong())
        assertEquals(src.size.toLong(),count)
        assertArrayEquals(src,target.toByteArray())
    }
    @Test fun rejectsTooShortAndTooLongPayloads() {
        for(expected in listOf(2L,6L)) {
            try {
                VerifiedFileCopy.copy(ByteArrayInputStream(byteArrayOf(1,2,3,4)),ByteArrayOutputStream(),expected)
                fail("Should reject $expected")
            } catch (_: IOException) {}
        }
    }
    @Test fun mismatchingTransportSizeIsRejectedEvenWhenMetadataMatches() {
        try {
            VerifiedFileCopy.copy(ByteArrayInputStream(ByteArray(10)),ByteArrayOutputStream(),10,11)
            fail("Expected mismatch")
        } catch (_: IOException) {}
    }
    @Test fun noKnownSizeStillCopiesAndCounts() {
        assertEquals(11L,VerifiedFileCopy.copy(
            ByteArrayInputStream(ByteArray(11)),ByteArrayOutputStream()))
    }
    @Test fun emptyFilesAreSupported() {
        assertEquals(0L,VerifiedFileCopy.copy(
            ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream(),0,0))
    }
    @Test fun rejectsInvalidExpectedLength() {
        try {
            VerifiedFileCopy.copy(ByteArrayInputStream(byteArrayOf()),ByteArrayOutputStream(),-2)
            fail("Expected invalid length")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun cancelledSessionStopsCopyBeforePublishing() {
        try {
            VerifiedFileCopy.copy(ByteArrayInputStream(ByteArray(4096)),ByteArrayOutputStream(),
                isSessionActive={false})
            fail("Expected cancellation")
        } catch (_: IOException) {}
    }
}
