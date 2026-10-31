package app.swiftdrop

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.SoftApConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * User-initiated local-only Wi-Fi AP, independent from Nearby Connections.
 * This only establishes a LAN; there is deliberately no fake "Ultra" payload path.
 * No internet tethering, hidden system-setting writes, or fixed SSID/password.
 */
enum class UltraHotspotPhase { Idle, Starting, Active, Failed }

data class UltraHotspotState(
    val phase: UltraHotspotPhase = UltraHotspotPhase.Idle,
    val ssid: String = "",
    val passphrase: String = "",
    val security: UltraWifiSecurity = UltraWifiSecurity.WPA2,
    val failureReason: Int? = null
)

class UltraHotspotController(context: Context) {
    val state = MutableStateFlow(UltraHotspotState())
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var generation = 0L

    fun start() {
        if (state.value.phase == UltraHotspotPhase.Starting ||
            state.value.phase == UltraHotspotPhase.Active) return
        val request = ++generation
        state.value = UltraHotspotState(phase = UltraHotspotPhase.Starting)
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(lease: WifiManager.LocalOnlyHotspotReservation) {
                    if (request != generation) {
                        lease.close()
                        return
                    }
                    reservation = lease
                    val credentials = credentials(lease)
                    if (credentials == null) {
                        // Never show or retain an AP that a peer cannot join.
                        stop()
                        state.value = UltraHotspotState(phase = UltraHotspotPhase.Failed)
                        return
                    }
                    state.value = UltraHotspotState(
                        phase = UltraHotspotPhase.Active,
                        ssid = credentials.first,
                        passphrase = credentials.second,
                        security = credentials.third
                    )
                }

                override fun onStopped() {
                    if (request != generation) return
                    reservation = null
                    state.value = UltraHotspotState()
                }

                override fun onFailed(reason: Int) {
                    if (request != generation) return
                    reservation = null
                    state.value = UltraHotspotState(
                        phase = UltraHotspotPhase.Failed, failureReason = reason
                    )
                }
            }, mainHandler)
        } catch (_: SecurityException) {
            if (request == generation) state.value =
                UltraHotspotState(phase = UltraHotspotPhase.Failed)
        } catch (_: IllegalStateException) {
            if (request == generation) state.value =
                UltraHotspotState(phase = UltraHotspotPhase.Failed)
        } catch (_: UnsupportedOperationException) {
            if (request == generation) state.value =
                UltraHotspotState(phase = UltraHotspotPhase.Failed)
        }
    }

    @Suppress("DEPRECATION")
    private fun credentials(lease: WifiManager.LocalOnlyHotspotReservation): Triple<String, String, UltraWifiSecurity>? {
        val ssid: String?
        val password: String?
        val security: UltraWifiSecurity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ap = lease.softApConfiguration
            ssid = ap.ssid
            password = ap.passphrase
            security = when (ap.securityType) {
                SoftApConfiguration.SECURITY_TYPE_WPA2_PSK,
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION -> UltraWifiSecurity.WPA2
                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> UltraWifiSecurity.WPA3
                else -> return null // Unsupported or unauthenticated hotspot
            }
        } else {
            ssid = lease.wifiConfiguration?.SSID?.trim('"')
            password = lease.wifiConfiguration?.preSharedKey?.trim('"')
            security = UltraWifiSecurity.WPA2
        }
        if (ssid.isNullOrBlank() || password.isNullOrBlank()) return null
        return Triple(ssid, password, security)
    }

    fun stop() {
        generation++
        val old = reservation
        reservation = null
        state.value = UltraHotspotState() // zero out join credentials immediately
        runCatching { old?.close() }
    }
}
