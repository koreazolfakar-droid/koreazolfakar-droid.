package app.swiftdrop

/**
 * Nearby Connections requires the foreground coarse permission on some Android 13+
 * devices (8034) and the foreground fine permission on some devices as well (8036).
 * Both are requested together on Android 12+ to support the Precise/Approximate UI.
 * This app does not access coordinates or request background location.
 */
fun requiredNearbyPermissions(sdk: Int): List<String> = buildList {
    add("android.permission.ACCESS_COARSE_LOCATION")
    if (sdk >= 29) add("android.permission.ACCESS_FINE_LOCATION")
    if (sdk >= 31) {
        add("android.permission.BLUETOOTH_SCAN")
        add("android.permission.BLUETOOTH_ADVERTISE")
        add("android.permission.BLUETOOTH_CONNECT")
    }
    if (sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
    if (sdk <= 32) add("android.permission.READ_EXTERNAL_STORAGE")
}
