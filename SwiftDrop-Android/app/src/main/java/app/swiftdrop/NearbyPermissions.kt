package app.swiftdrop

/**
 * Nearby Connections compatibility (API 26-36).
 *
 * Some Google Play Services implementations still reject discovery with 8034
 * (MISSING_PERMISSION_ACCESS_COARSE_LOCATION) on Android 13-16 even if all
 * Nearby Devices permissions are granted. Request foreground approximate
 * location explicitly only at the point of Send / Receive, never background.
 * We do not use a location provider or collect coordinates.
 */
fun requiredNearbyPermissions(sdk: Int): List<String> = buildList {
    add("android.permission.ACCESS_COARSE_LOCATION")
    if (sdk in 29..32) add("android.permission.ACCESS_FINE_LOCATION")
    if (sdk >= 31) {
        add("android.permission.BLUETOOTH_SCAN")
        add("android.permission.BLUETOOTH_ADVERTISE")
        add("android.permission.BLUETOOTH_CONNECT")
    }
    if (sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
    if (sdk <= 32) add("android.permission.READ_EXTERNAL_STORAGE")
}
