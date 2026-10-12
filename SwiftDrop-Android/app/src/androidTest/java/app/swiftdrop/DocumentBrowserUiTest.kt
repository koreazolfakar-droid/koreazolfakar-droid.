package app.swiftdrop

import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    }
    @Test fun browserWithAllFilesAccessOpensInternalStorage() {
        if (Build.VERSION.SDK_INT < 30) return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun appOp(action: String) {
            val descriptor = automation.executeShellCommand(
                "appops set app.swiftdrop MANAGE_EXTERNAL_STORAGE $action")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
        }
        appOp("allow")
        try {
            compose.setContent {
                SwiftDropTheme(false) {
                    DocumentBrowserScreen(back = {}, onSend = {})
                }
            }
            compose.onNodeWithText("Internal storage").assertExists()
        } finally {
            appOp("default")
        }
    }

}
