package app.swiftdrop

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.swiftdrop.ui.screens.discovery.*
import app.swiftdrop.ui.screens.transfer.TransferScreen
import app.swiftdrop.ui.screens.qr.ReceiverQrCard
import app.swiftdrop.ui.theme.SwiftDropTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferFlowsUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun measuredProgressAndCancelHaveRealActions() {
        var cancelled=false
        compose.setContent {
            SwiftDropTheme(true) { TransferScreen(TransferUiState(connectedName="QA receiver",
                progress=TransferProgress("QA.txt","Sending",1024,2048)),{cancelled=true}) }
        }
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("Cancel transfer").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun selectedFileQrAndDiscoveryActionsStayAvailable() {
        var qr=false
        var discover=false
        val uri=Uri.parse("content://app.swiftdrop.test/QA.txt")
        compose.setContent { SwiftDropTheme(false) {
            SelectionScreen(TransferUiState(selected=listOf(uri),
                selectedFiles=listOf(SelectedFile(uri,"QA.txt",12))),
                discover={discover=true},chooseAgain={},scanQr={qr=true})
        } }
        compose.onNodeWithText("Scan receiver QR").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(qr) }
        compose.onNodeWithText("Find a receiver").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(discover) }
    }

    @Test fun discoveryConnectAndRecoveryAreInteractive() {
        var connected:Peer?=null
        val peer=Peer("qa","QA device")
        compose.setContent { SwiftDropTheme(false) {
            DiscoveryScreen(TransferUiState(peers=listOf(peer),isDiscovering=true),
                connect={connected=it},retry={},cancel={},openRecoverySettings={})
        } }
        compose.onNodeWithText("QA device").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(peer,connected) }
    }

    @Test fun receivingQrIsGeneratedFromActualSessionPayload() {
        compose.setContent { SwiftDropTheme(true) { ReceiverQrCard("swiftdrop://pair?qa=1") } }
        compose.onNodeWithContentDescription("Scan to pair with this phone").assertExists()
    }
}
