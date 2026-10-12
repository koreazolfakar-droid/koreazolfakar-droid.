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

    @Test fun standardScreensReturnHome() {
        for(screen in listOf("files","history","settings","receive","discover","transfer")) {
            assertEquals(screen, "home",backDestination(screen))
        }
    }
}
