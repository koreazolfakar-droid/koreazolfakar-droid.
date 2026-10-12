package app.swiftdrop

/**
 * Map Nearby Connections failures to actionable guidance instead of exposing
 * Google Play Services' internal error strings to the user.
 */
internal enum class NearbyFailure {
    LocationOff,
    LocationDependency,
    MissingPermission,
    RadioBusy,
    Other
}

internal fun classifyNearbyFailure(statusCode: Int?, locationEnabled: Boolean): NearbyFailure =
    when (statusCode) {
        8034, 8036, 8025 -> if (locationEnabled) NearbyFailure.LocationDependency else NearbyFailure.LocationOff
        8029, 8032, 8033 -> NearbyFailure.MissingPermission
        8050 -> NearbyFailure.RadioBusy
        else -> NearbyFailure.Other
    }
