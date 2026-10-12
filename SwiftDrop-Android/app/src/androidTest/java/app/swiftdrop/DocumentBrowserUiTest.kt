package app.swiftdrop

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.files.DocumentBrowserScreen
import app.swiftdrop.ui.theme.SwiftDropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test for the former Docs tap crash: the screen must compose without storage grants. */
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
        compose.onAllNodesWithText("Choose folder").onFirst().assertExists()
    }
}
