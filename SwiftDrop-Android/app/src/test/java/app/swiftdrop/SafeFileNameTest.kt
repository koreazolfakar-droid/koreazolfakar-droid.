package app.swiftdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeFileNameTest {
    @Test fun stripsTraversalAndControlCharacters() {
        assertEquals("photo.png", SafeFileName.normalize("../../photo.png"))
        assertEquals("note_txt", SafeFileName.normalize("\\docs\\note:txt"))
        assertEquals("received-file", SafeFileName.normalize("../../.."))
        assertEquals("bad_name", SafeFileName.normalize("bad\u0000name"))
    }

    @Test fun respectsLengthBound() {
        val result = SafeFileName.normalize("a".repeat(1000))
        assertEquals(160, result.length)
        assertFalse(result.contains('/'))
    }
}
