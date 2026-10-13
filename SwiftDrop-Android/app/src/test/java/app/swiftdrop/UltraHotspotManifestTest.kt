package app.swiftdrop

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Local hotspot relies on already declared user-granted Nearby/Wi-Fi permissions. */
class UltraHotspotManifestTest {
    @Test fun hotspotPermissionsRemainAvailable() {
        val ns = "http://schemas.android.com/apk/res/android"
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(requireNotNull(System.getProperty("swiftdrop.mergedManifest"))))
        val nodes = manifest.getElementsByTagName("uses-permission")
        val attrs = (0 until nodes.length).map { nodes.item(it) as Element }
            .associateBy { it.getAttributeNS(ns, "name") }
        for (permission in listOf("android.permission.CHANGE_WIFI_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.ACCESS_FINE_LOCATION")) {
            val found = requireNotNull(attrs[permission]) { permission }
            assertFalse("$permission is capped on newer Android", found.hasAttributeNS(ns, "maxSdkVersion"))
        }
    }

    @Test fun disabledHotspotDoesNotClaimConnectivity() {
        val initial = UltraHotspotState()
        assertEquals(UltraHotspotPhase.Idle, initial.phase)
        assertTrue(initial.ssid.isEmpty())
        assertTrue(initial.passphrase.isEmpty())
    }
}
