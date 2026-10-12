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

    @Test fun androidEightNineRequestsOnlyCoarseLocation() {
        for (sdk in 26..28) assertEquals(
            setOf(coarse, "android.permission.READ_EXTERNAL_STORAGE"),
            requiredNearbyPermissions(sdk).toSet()
        )
    }

    @Test fun androidTenToTwelveLRequestsBothLocationPermissions() {
        for (sdk in 29..32) {
            val requested = requiredNearbyPermissions(sdk).toSet()
            assertTrue("API $sdk coarse missing", coarse in requested)
            assertTrue("API $sdk fine missing", fine in requested)
            assertEquals("API $sdk Bluetooth group",sdk >= 31,bluetooth.all { it in requested })
        }
    }

    @Test fun androidThirteenToSixteenRequestsBothLocationPermissionsAndNearbyRadios() {
        for (sdk in 33..36) {
            val requested = requiredNearbyPermissions(sdk).toSet()
            assertEquals("API $sdk", bluetooth + coarse + fine + wifi, requested)
        }
    }

    @Test fun noVersionAsksForBackgroundLocationOrUnnecessaryBroadStorage() {
        for (sdk in 26..36) {
            val requested = requiredNearbyPermissions(sdk)
            assertEquals("API $sdk duplicate permissions", requested.size, requested.distinct().size)
            assertFalse(requested.any { it.contains("BACKGROUND_LOCATION") ||
                it.contains("MANAGE_EXTERNAL_STORAGE") || it.contains("READ_MEDIA_") })
        }
    }
}
