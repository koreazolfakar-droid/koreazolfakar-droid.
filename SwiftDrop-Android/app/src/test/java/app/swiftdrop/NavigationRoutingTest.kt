package app.swiftdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRoutingTest {
    @Test fun returningFromInstalledAppsRespectsEntryScreen() {
        assertEquals("home",backDestination("apps","home"))
        assertEquals("files",backDestination("apps","files"))
        assertEquals("home",backDestination("apps","unexpected"))
    }

    @Test fun backFromFileSelectionReturnsToPicker() {
        assertEquals("files",backDestination("selection"))
    }

    @Test fun homeCategoriesHaveRealUniqueDestinations() {
        assertEquals("gallery_photos",galleryDestination(MediaKind.Photos))
        assertEquals("gallery_videos",galleryDestination(MediaKind.Videos))
        assertEquals("gallery_music",galleryDestination(MediaKind.Music))
        assertEquals(MediaKind.Photos,galleryCategory("gallery_photos"))
        assertEquals(MediaKind.Videos,galleryCategory("gallery_videos"))
        assertEquals(MediaKind.Music,galleryCategory("gallery_music"))
        assertEquals(null,galleryCategory("files"))
        assertEquals(null,galleryCategory("documents"))
    }

    @Test fun categoryBackNavigationNeverOpensWrongTab() {
        for(page in listOf("gallery_photos","gallery_videos","gallery_music","documents")) {
            assertEquals("home",backDestination(page))
        }
    }

    @Test fun standardScreensReturnHome() {
        for(screen in listOf("files","history","settings","receive","discover","transfer")) {
            assertEquals(screen, "home",backDestination(screen))
        }
    }
}
