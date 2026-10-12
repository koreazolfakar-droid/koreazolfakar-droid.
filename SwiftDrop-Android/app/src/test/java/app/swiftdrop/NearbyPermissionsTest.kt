package app.swiftdrop

import org.junit.Assert.*
import org.junit.Test

class NearbyPermissionsTest {
    private val coarse = "android.permission.ACCESS_COARSE_LOCATION"
    private val fine = "android.permission.ACCESS_FINE_LOCATION"
    private val wifi = "android.permission.NEARBY_WIFI_DEVICES"
    private val bluetooth = setOf(
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT"
    )

    @Test fun androidEightNineRequestsCoarseAndLegacyStorage() {
        for (sdk in 26..28) assertEquals(
            setOf(coarse,"android.permission.READ_EXTERNAL_STORAGE"),
            requiredNearbyPermissions(sdk).toSet())
    }

    @Test fun androidTenToTwelveLRequestsBothLocationPermissions() {
        for (sdk in 29..32) {
            val requested = requiredNearbyPermissions(sdk).toSet()
            assertTrue("missing coarse on API $sdk",coarse in requested)
            assertTrue("missing fine on API $sdk",fine in requested)
            assertEquals("API $sdk",sdk>=31,bluetooth.all { it in requested })
        }
    }

    @Test fun androidThirteenToSixteenRequestsApproximateLocationAndNearbyRadios() {
        for (sdk in 33..36) {
            val perms = requiredNearbyPermissions(sdk).toSet()
            assertEquals("API $sdk",bluetooth+coarse+wifi,perms)
            assertFalse("API $sdk must not demand precise location",fine in perms)
        }
    }

    @Test fun permissionListNeverRequestsBackgroundOrUnnecessaryBroadAccess() {
        for (sdk in 26..36) {
            val requested = requiredNearbyPermissions(sdk)
            assertEquals(requested.size,requested.distinct().size)
            assertFalse(requested.any { it.contains("BACKGROUND_LOCATION") ||
                it.contains("MANAGE_EXTERNAL_STORAGE") || it.contains("READ_MEDIA_") })
        }
    }
}
