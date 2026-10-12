package app.swiftdrop

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Checks the merged build artifact, including any library manifest contributions. */
class NearbyManifestTest {
    private val namespace = "http://schemas.android.com/apk/res/android"
    private val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(requireNotNull(System.getProperty("swiftdrop.mergedManifest"))))
    private val permissions = manifest.getElementsByTagName("uses-permission").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
            .associateBy { it.getAttributeNS(namespace, "name") }
    }

    @Test fun wifiStatePermissionsRemainAvailableOnModernAndroid() {
        listOf("ACCESS_WIFI_STATE", "CHANGE_WIFI_STATE").forEach { name ->
            val permission = requireNotNull(permissions["android.permission.$name"])
            assertFalse("$name must not disappear on API 32+", permission.hasAttributeNS(namespace, "maxSdkVersion"))
        }
    }

    @Test fun everyRuntimeRequestIsDeclaredForItsAndroidVersion() {
        for (sdk in 26..36) for (name in requiredNearbyPermissions(sdk)) {
            val declaration = requireNotNull(permissions[name]) { "Missing $name on API $sdk" }
            val max = declaration.getAttributeNS(namespace, "maxSdkVersion").toIntOrNull() ?: Int.MAX_VALUE
            assertTrue("$name removed on API $sdk", sdk <= max)
        }
    }

    @Test fun android12LLocationDeclaredButNewerAndroidHasNoLocationPrompt() {
        val coarse = requireNotNull(permissions["android.permission.ACCESS_COARSE_LOCATION"])
        val fine = requireNotNull(permissions["android.permission.ACCESS_FINE_LOCATION"])
        assertEquals(28,coarse.getAttributeNS(namespace,"maxSdkVersion").toInt())
        assertEquals(32,fine.getAttributeNS(namespace,"maxSdkVersion").toInt())
        for (sdk in 33..36) assertFalse(requiredNearbyPermissions(sdk).any { it.contains("LOCATION") })
    }

    @Test fun normalWifiPermissionsDoNotCreateRuntimePrompts() {
        for (sdk in 26..36) assertFalse(requiredNearbyPermissions(sdk).any { it.endsWith("WIFI_STATE") })
    }

    @Test fun manifestPreservesPackageAndAvoidsBroadAccess() {
        assertEquals("app.swiftdrop", manifest.documentElement.getAttribute("package"))
        listOf("INTERNET", "QUERY_ALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES")
            .forEach { assertFalse("Unexpected broad permission $it", permissions.containsKey("android.permission.$it")) }
    }
}
