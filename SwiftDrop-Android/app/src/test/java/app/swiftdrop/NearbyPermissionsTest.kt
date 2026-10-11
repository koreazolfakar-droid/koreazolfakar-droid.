package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class NearbyPermissionsTest {
    @Test fun androidEightUsesLegacyRadioAndFilePermissions() {
        assertEquals(setOf("android.permission.ACCESS_COARSE_LOCATION","android.permission.READ_EXTERNAL_STORAGE"),
            requiredNearbyPermissions(26).toSet())
    }
    @Test fun androidTenRequestsBothLocationPermissions() {
        val permissions=requiredNearbyPermissions(29)
        assertTrue(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertFalse(permissions.contains("android.permission.BLUETOOTH_SCAN"))
    }
    @Test fun androidTwelveCombinesBluetoothAndLocation() {
        val permissions=requiredNearbyPermissions(31)
        assertTrue(permissions.contains("android.permission.BLUETOOTH_SCAN"))
        assertTrue(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
    }
    @Test fun androidTwelveLDoesNotAskForDeprecatedLocation() {
        val permissions=requiredNearbyPermissions(32)
        assertTrue(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(permissions.any { "LOCATION" in it })
    }
    @Test fun modernAndroidNeverRequestsBroadStorageOrMediaAccess() {
        for(sdk in 33..36) {
            val permissions=requiredNearbyPermissions(sdk)
            assertEquals(4,permissions.size)
            assertTrue(permissions.contains("android.permission.NEARBY_WIFI_DEVICES"))
            assertFalse(permissions.any { "STORAGE" in it || "MEDIA" in it || "LOCATION" in it })
        }
    }
}
