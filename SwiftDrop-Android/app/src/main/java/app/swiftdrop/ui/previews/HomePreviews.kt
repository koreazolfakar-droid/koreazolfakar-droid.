package app.swiftdrop.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.swiftdrop.R
import app.swiftdrop.TransferUiState
import app.swiftdrop.ui.screens.home.HomeScreen
import app.swiftdrop.ui.theme.SwiftDropTheme

// Design-time previews contain an empty session, never simulated devices or speeds.
@Preview(name="Small phone",widthDp=320,heightDp=640,showBackground=true)
@Preview(name="Large text",widthDp=360,heightDp=800,fontScale=2f,showBackground=true)
@Preview(name="Arabic RTL",widthDp=360,heightDp=800,locale="ar",showBackground=true)
@Preview(name="Tablet",widthDp=720,heightDp=1024,showBackground=true)
@Composable
private fun HomePreview() {
    SwiftDropTheme(false) {
        HomeScreen(TransferUiState(),{},{},{},{},stringResource(R.string.ready))
    }
}

@Preview(name="Dark phone",widthDp=360,heightDp=800,showBackground=true)
@Composable
private fun DarkHomePreview() {
    SwiftDropTheme(true) {
        HomeScreen(TransferUiState(),{},{},{},{},stringResource(R.string.ready))
    }
}
