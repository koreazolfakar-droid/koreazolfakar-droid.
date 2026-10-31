package app.swiftdrop

import android.Manifest

/**
 * Permissions for user-initiated local-only hotspot hosting and joining.
 * On Android 13+ nearby Wi-Fi replaces location for this feature; older
 * Android versions require location access even though SwiftDrop never reads
 * or stores coordinates.
 */
fun requiredUltraWifiPermissions(sdk: Int): List<String> = when {
    sdk >= 33 -> listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    sdk >= 29 -> listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
