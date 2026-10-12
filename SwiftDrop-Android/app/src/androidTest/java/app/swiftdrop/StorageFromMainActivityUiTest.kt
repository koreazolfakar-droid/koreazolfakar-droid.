package app.swiftdrop

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regress the user-reported fatal crash:
 * Opening Storage from MainActivity, after SwiftDropApp creates the localized Context,
 * must keep ActivityResultRegistryOwner available for OpenDocumentTree/OpenMultipleDocuments.
 * Isolated DocumentBrowserScreen tests do not cover this navigation/context boundary.
 */
@RunWith(AndroidJUnit4::class)
class StorageFromMainActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openStorage(language: AppLanguage, tile: String, chooser: String) {
        compose.runOnUiThread {
            val model = ViewModelProvider(compose.activity)[SwiftDropViewModel::class.java]
            model.settings.setLanguage(language)
            model.manager.setPage("home")
        }
        compose.waitForIdle()
        compose.onNodeWithText(tile).performScrollTo().performClick()
        compose.onNodeWithText(chooser).assertExists()
    }

    @Test fun englishStorageOpensFromRealLauncher() {
        openStorage(AppLanguage.English, "Storage", "Choose files")
    }

    @Test fun arabicStorageOpensFromRealLauncher() {
        openStorage(AppLanguage.Arabic, "الذاكرة", "اختيار ملفات")
    }
}
