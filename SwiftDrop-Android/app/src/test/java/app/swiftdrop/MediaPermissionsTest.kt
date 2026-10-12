package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class MediaPermissionsTest {
    @Test fun eachModernSectionRequestsOnlyItsOwnMediaType() {
        for (sdk in 33..36) for (kind in MediaKind.entries) {
            val requested = requestedMediaPermissions(kind, sdk).toSet()
            assertTrue(kind.permission in requested)
            assertEquals(1, requested.count { it.startsWith("android.permission.READ_MEDIA_") &&
                it != "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" })
            assertEquals(sdk >= 34 && kind != MediaKind.Music,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" in requested)
        }
    }

    @Test fun visualPartialGrantDoesNotUnlockAudioOrOtherFullAccess() {
        val partial = setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        assertEquals(MediaAccess(true, true), mediaAccess(MediaKind.Photos, 34) { it in partial })
        assertEquals(MediaAccess(true, true), mediaAccess(MediaKind.Videos, 34) { it in partial })
        assertEquals(MediaAccess(false), mediaAccess(MediaKind.Music, 34) { it in partial })
    }

    @Test fun deniedAndRevokedAccessAlwaysReturnToLockedState() {
        val granted = mutableSetOf(MediaKind.Photos.permission)
        assertTrue(mediaAccess(MediaKind.Photos, 35) { it in granted }.allowed)
        granted.clear()
        assertFalse(mediaAccess(MediaKind.Photos, 35) { it in granted }.allowed)
        assertFalse(mediaAccess(MediaKind.Videos, 35) { it in granted }.allowed)
    }

    @Test fun legacyStorageIsRequestedOnlyWhenBrowsingMedia() {
        for (sdk in 26..32) {
            assertArrayEquals(arrayOf("android.permission.READ_EXTERNAL_STORAGE"),
                requestedMediaPermissions(MediaKind.Music, sdk))
            assertFalse(requiredNearbyPermissions(sdk).contains("android.permission.READ_EXTERNAL_STORAGE"))
        }
    }
}
