package app.swiftdrop

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.files.DocumentBrowserScreen
import app.swiftdrop.ui.theme.SwiftDropTheme
import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for 0.4.9-alpha crash on Android 16:
 * the production locale wrapper used to hide ActivityResultRegistryOwner.
 * Do not test DocumentBrowserScreen in an unwrapped composition only.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedContextUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun documentsOpensThroughLocalizedAppProviders() {
        compose.setContent {
            val context = LocalContext.current
            val config = remember(context) {
                Configuration(context.resources.configuration).apply {
                    val locale = Locale.forLanguageTag("ar")
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }
            SwiftDropLocalizedProviders(config) {
                assertNotNull("Picker owner must survive localization",
                    LocalActivityResultRegistryOwner.current)
                SwiftDropTheme(false) {
                    DocumentBrowserScreen(back = {}, onSend = {})
                }
            }
        }
        compose.onNodeWithText("اختيار ملفات").assertExists()
    }
}
