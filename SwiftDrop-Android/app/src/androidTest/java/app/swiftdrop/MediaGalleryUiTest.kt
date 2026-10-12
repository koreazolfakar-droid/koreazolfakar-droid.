package app.swiftdrop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.files.MediaGalleryScreen
import app.swiftdrop.ui.theme.SwiftDropTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaGalleryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deniedGalleryCanRequestOnlyTheOpenSection() {
        var requested: MediaKind? = null
        compose.setContent {
            SwiftDropTheme(false) {
                MediaGalleryScreen({}, {}, AppsState(), {}, {}, {}, 0, { requested = it })
            }
        }
        compose.onNodeWithText("Allow access").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(MediaKind.Photos, requested) }
        compose.onNodeWithText("Videos").performClick()
        compose.onNodeWithText("Allow access").performClick()
        compose.runOnIdle { assertEquals(MediaKind.Videos, requested) }
    }

    @Test fun tabsSwitchAndKeepPermissionActionsResponsive() {
        var requested: MediaKind? = null
        compose.setContent {
            SwiftDropTheme(false) {
                MediaGalleryScreen({}, {}, AppsState(), {}, {}, {}, 0,
                    {requested=it},initialTab=2)
            }
        }
        compose.onNodeWithText("Allow access").performClick()
        compose.runOnIdle { assertEquals(MediaKind.Music,requested) }
        compose.onNodeWithText("Photos").performClick()
        compose.onNodeWithText("Allow access").performClick()
        compose.runOnIdle { assertEquals(MediaKind.Photos,requested) }
        compose.onNodeWithText("Videos").performClick()
        compose.onNodeWithText("Allow access").performClick()
        compose.runOnIdle { assertEquals(MediaKind.Videos,requested) }
    }

    @Test fun standalonePhotosDoesNotShowGenericCategoryTabs() {
        compose.setContent {
            SwiftDropTheme(false) {
                MediaGalleryScreen({}, {}, AppsState(), {}, {}, {}, 0, {},
                    initialTab=0,standaloneCategory=true)
            }
        }
        compose.onNodeWithText("Photos").assertIsDisplayed()
        compose.onNodeWithText("Allow access").assertIsDisplayed()
        compose.onNodeWithText("Videos").assertDoesNotExist()
        compose.onNodeWithText("Applications").assertDoesNotExist()
    }

    @Test fun standaloneMusicOpensItsOwnPermissionAndTitle() {
        var requested:MediaKind?=null
        compose.setContent {
            SwiftDropTheme(false) {
                MediaGalleryScreen({}, {}, AppsState(), {}, {}, {}, 0,
                    {requested=it},initialTab=2,standaloneCategory=true)
            }
        }
        compose.onNodeWithText("Music").assertIsDisplayed()
        compose.onNodeWithText("Allow access").performClick()
        compose.runOnIdle { assertEquals(MediaKind.Music,requested) }
        compose.onNodeWithText("Photos").assertDoesNotExist()
    }

    @Test fun appSectionRemainsAvailableWithoutMediaPermissionInRtl() {
        compose.setContent {
            SwiftDropTheme(false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MediaGalleryScreen({}, {}, AppsState(), {}, {}, {}, 0, {})
                }
            }
        }
        compose.onNodeWithText("Applications").performScrollTo().performClick()
        compose.onNodeWithText("Choose an APK file instead").assertIsDisplayed()
    }
}
