package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class NearbyPermissionsTest {
    @Test fun androidEightUsesCoarseLocation() {
        assertEquals(setOf("android.permission.ACCESS_COARSE_LOCATION","android.permission.READ_EXTERNAL_STORAGE"),
            requiredNearbyPermissions(26).toSet())
    }
    @Test fun androidTenAndElevenRequireFineLocationOnly() {
        for (sdk in 29..30) {
            val permissions = requiredNearbyPermissions(sdk)
            assertTrue(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
            assertFalse(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
            assertFalse(permissions.contains("android.permission.BLUETOOTH_SCAN"))
        }
    }
    @Test fun androidTwelveAndTwelveLRequireBluetoothAndFineLocation() {
        for (sdk in 31..32) {
            val permissions = requiredNearbyPermissions(sdk)
            assertTrue("API $sdk",permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
            assertFalse(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
            assertTrue(permissions.contains("android.permission.BLUETOOTH_SCAN"))
            assertTrue(permissions.contains("android.permission.BLUETOOTH_ADVERTISE"))
            assertTrue(permissions.contains("android.permission.BLUETOOTH_CONNECT"))
        }
    }
    @Test fun androidThirteenThroughSixteenUseOnlyNearbyRadios() {
        for (sdk in 33..36) {
            val permissions = requiredNearbyPermissions(sdk)
            assertEquals("API $sdk",4,permissions.size)
            assertTrue(permissions.contains("android.permission.NEARBY_WIFI_DEVICES"))
            assertFalse(permissions.any { "STORAGE" in it || "MEDIA" in it || "LOCATION" in it })
        }
    }
    @Test fun everyAndroidVersionHasNoDuplicates() {
        for (sdk in 26..36) assertEquals(requiredNearbyPermissions(sdk).size,requiredNearbyPermissions(sdk).distinct().size)
    }
}
