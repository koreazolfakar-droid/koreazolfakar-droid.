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

    @Test fun approximateAndPreciseLocationAvailableThroughAndroid16() {
        val coarse = requireNotNull(permissions["android.permission.ACCESS_COARSE_LOCATION"])
        val fine = requireNotNull(permissions["android.permission.ACCESS_FINE_LOCATION"])
        assertFalse(coarse.hasAttributeNS(namespace,"maxSdkVersion"))
        assertFalse(fine.hasAttributeNS(namespace,"maxSdkVersion"))
        for (sdk in 33..36) {
            assertTrue(requiredNearbyPermissions(sdk).contains("android.permission.ACCESS_COARSE_LOCATION"))
            assertTrue(requiredNearbyPermissions(sdk).contains("android.permission.ACCESS_FINE_LOCATION"))
        }
    }

    @Test fun normalWifiPermissionsDoNotCreateRuntimePrompts() {
        for (sdk in 26..36) assertFalse(requiredNearbyPermissions(sdk).any { it.endsWith("WIFI_STATE") })
    }

    @Test fun scopedMediaRequestsAreDeclaredOnlyForSupportedApiLevels() {
        for (sdk in 26..36) for (kind in MediaKind.entries) {
            for (name in requestedMediaPermissions(kind,sdk)) {
                val declaration = requireNotNull(permissions[name]) { "Missing $name on API $sdk" }
                val max = declaration.getAttributeNS(namespace,"maxSdkVersion").toIntOrNull() ?: Int.MAX_VALUE
                assertTrue("$name expires before API $sdk", sdk <= max)
            }
        }
        assertEquals("32",permissions["android.permission.READ_EXTERNAL_STORAGE"]
            ?.getAttributeNS(namespace,"maxSdkVersion"))
    }

    @Test fun manifestPreservesPackageAndRestrictsUnrelatedSensitiveAccess() {
        assertEquals("app.swiftdrop", manifest.documentElement.getAttribute("package"))
        // Internal storage browsing is explicitly opt-in via Android Settings. Other sensitive
        // permissions still stay absent; document-tree browsing remains available without it.
        assertTrue("Storage browser needs optional all-files permission",
            permissions.containsKey("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertTrue("Encrypted LAN sockets require INTERNET even without cloud access",
            permissions.containsKey("android.permission.INTERNET"))
        assertTrue(permissions.containsKey("android.permission.CHANGE_NETWORK_STATE"))
        assertTrue(permissions.containsKey("android.permission.ACCESS_NETWORK_STATE"))
        listOf("QUERY_ALL_PACKAGES", "REQUEST_INSTALL_PACKAGES",
            "WRITE_EXTERNAL_STORAGE")
            .forEach { assertFalse("Unexpected permission $it",
                permissions.containsKey("android.permission.$it")) }
    }
}
