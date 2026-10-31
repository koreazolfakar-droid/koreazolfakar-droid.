package app.swiftdrop

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class UltraPermissionsTest {
    @Test fun requiresFineLocationForLegacyLocalHotspot() {
        for (sdk in 26..28) assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requiredUltraWifiPermissions(sdk)
        )
    }

    @Test fun combinesCoarseAndFineForAndroidTenThroughTwelve() {
        for (sdk in 29..32) assertEquals(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION),
            requiredUltraWifiPermissions(sdk)
        )
    }

    @Test fun usesNearbyWifiRuntimePermissionForAndroidThirteenAndNewer() {
        for (sdk in 33..36) assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            requiredUltraWifiPermissions(sdk)
        )
    }
}
