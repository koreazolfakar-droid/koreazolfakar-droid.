package app.swiftdrop

import app.swiftdrop.music.cleanMusicTitle
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class BackgroundMusicConfigTest {
    @Test fun removesOnlyKnownAudioExtension() {
        assertEquals("song",cleanMusicTitle("song.mp3"))
        assertEquals("أغنية",cleanMusicTitle("أغنية.M4A"))
        assertEquals("song.mix",cleanMusicTitle("song.mix.flac"))
        assertEquals("recording.mp4",cleanMusicTitle("recording.mp4"))
    }

    @Test fun mergedManifestDeclaresMediaPlaybackForegroundService() {
        val path=System.getProperty("swiftdrop.mergedManifest")
            ?: error("Merged manifest missing: CI must build it first")
        val root=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }
            .newDocumentBuilder().parse(File(path)).documentElement
        val perms=root.getElementsByTagName("uses-permission")
        val android="http://schemas.android.com/apk/res/android"
        val permissionNames=(0 until perms.length).map {
            perms.item(it).attributes.getNamedItemNS(android,"name")?.nodeValue
        }
        assertTrue(permissionNames.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(permissionNames.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertFalse(permissionNames.contains("android.permission.POST_NOTIFICATIONS"))
        val services=root.getElementsByTagName("service")
        val found=(0 until services.length).any { index ->
            val service=services.item(index)
            service.attributes.getNamedItemNS(android,"name")?.nodeValue?.endsWith("SwiftDropMusicService")==true &&
                service.attributes.getNamedItemNS(android,"foregroundServiceType")?.nodeValue?.contains("mediaPlayback")==true
        }
        assertTrue("MediaSessionService must be in merged manifest",found)
    }
}
