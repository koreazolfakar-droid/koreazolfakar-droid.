package app.swiftdrop

/** Legacy storage permission remains limited to FILE payload support on API <= 32. */
fun requiredNearbyPermissions(sdk: Int): List<String> = buildList {
    if(sdk >= 31) {
        add("android.permission.BLUETOOTH_SCAN")
        add("android.permission.BLUETOOTH_ADVERTISE")
        add("android.permission.BLUETOOTH_CONNECT")
    }
    if(sdk in 29..31) {
        add("android.permission.ACCESS_FINE_LOCATION")
        add("android.permission.ACCESS_COARSE_LOCATION")
    } else if(sdk <= 28) add("android.permission.ACCESS_COARSE_LOCATION")
    if(sdk >= 33) add("android.permission.NEARBY_WIFI_DEVICES")
    if(sdk <= 32) add("android.permission.READ_EXTERNAL_STORAGE")
}
