package app.swiftdrop

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.files.DocumentBrowserScreen
import app.swiftdrop.ui.theme.SwiftDropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verify Storage UI without changing app-op permissions mid-instrumentation:
 * on Android 16 an all-files-access grant can kill the app/test process. */
@RunWith(AndroidJUnit4::class)
class DocumentBrowserUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fileBrowserOpensWithoutRequiringStoragePermission() {
        compose.setContent {
            SwiftDropTheme(false) {
                DocumentBrowserScreen(back = {}, onSend = {})
            }
        }
        compose.onNodeWithText("Choose files").assertExists()
    }

    @Test fun fileBrowserOffersExplicitStorageAccessInsteadOfCrashing() {
        compose.setContent {
            SwiftDropTheme(false) {
                DocumentBrowserScreen(back = {}, onSend = {})
            }
        }
        compose.onNodeWithText("Allow internal storage access").assertExists()
    }
}
