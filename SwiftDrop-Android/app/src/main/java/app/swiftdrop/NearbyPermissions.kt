package app.swiftdrop

/**
 * Explicit Nearby Connections permission matrix, matching the platform rules:
 * API 26-28: coarse location, API 29-30: fine location,
 * API 31-32: both coarse and fine location (Android 12 restriction),
 * API 31+: nearby Bluetooth, API 33+: nearby Wi-Fi.
 *
 * Never request location permission on Android 13+ solely for Nearby Connections.
 * A device's global Location setting is distinct from an app permission; some
 * Google Play Services versions still require the switch enabled for discovery.
 */
fun requiredNearbyPermissions(sdk: Int): List<String> = buildList {
    if (sdk <= 28) add("android.permission.ACCESS_COARSE_LOCATION")
    if (sdk in 29..32) add("android.permission.ACCESS_FINE_LOCATION")
    if (sdk in 31..32) add("android.permission.ACCESS_COARSE_LOCATION")
    if (sdk >= 31) {
        add("android.permission.BLUETOOTH_SCAN")
        add("android.permission.BLUETOOTH_ADVERTISE")
        add("android.permission.BLUETOOTH_CONNECT")
    }
    if (sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
    if (sdk <= 32) add("android.permission.READ_EXTERNAL_STORAGE")
}
