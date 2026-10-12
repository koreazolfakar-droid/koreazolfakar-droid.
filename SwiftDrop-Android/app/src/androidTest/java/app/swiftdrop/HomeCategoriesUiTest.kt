package app.swiftdrop

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.home.HomeScreen
import app.swiftdrop.ui.theme.SwiftDropTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCategoriesUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun photosVideosMusicAndDocumentsDispatchDistinctActions() {
        var target:MediaKind?=null
        var documents=false
        var apps=false
        var generic=false
        compose.setContent {
            SwiftDropTheme(false) {
                HomeScreen(TransferUiState(),
                    send={generic=true},receive={},seeAll={},resume={},status="Ready",
                    openFiles={generic=true},openApps={apps=true},
                    openMedia={target=it},openDocuments={documents=true})
            }
        }
        compose.onNodeWithText("Photos").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MediaKind.Photos,target) }
        compose.onNodeWithText("Videos").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MediaKind.Videos,target) }
        compose.onNodeWithText("Music").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(MediaKind.Music,target) }
        compose.onNodeWithText("Apps").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(apps) }
        compose.onNodeWithText("Docs").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(documents); assertEquals(false,generic) }
    }

    @Test fun browseAllRemainsGenericFileLibraryShortcut() {
        var generic=false
        compose.setContent {
            SwiftDropTheme(false) {
                HomeScreen(TransferUiState(),send={},receive={},seeAll={},resume={},
                    status="Ready",openFiles={generic=true})
            }
        }
        compose.onNodeWithText("Browse all").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(generic) }
    }
}
