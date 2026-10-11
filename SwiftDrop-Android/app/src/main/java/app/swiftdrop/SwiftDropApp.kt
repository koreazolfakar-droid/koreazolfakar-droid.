package app.swiftdrop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.ui.components.AppIcon
import app.swiftdrop.ui.screens.home.HomeScreen
import app.swiftdrop.ui.theme.SwiftDropDesign
import app.swiftdrop.ui.theme.SwiftDropTheme

@Composable
fun SwiftDropApp(manager: NearbyTransferManager, send: (Array<String>) -> Unit, receive: () -> Unit) {
    val state by manager.state.collectAsState()
    BackHandler(state.page != "home" && state.pending == null) { manager.setPage("home") }
    SwiftDropTheme(state.darkTheme) {
        Scaffold(bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("home",R.string.home,R.drawable.ic_home),
                    Triple("history",R.string.history,R.drawable.ic_history),
                    Triple("settings",R.string.settings,R.drawable.ic_settings)).forEach { (page,label,icon) ->
                    NavigationBarItem(selected = state.page == page, onClick = { manager.setPage(page) },
                        icon = { AppIcon(icon, tint = if (state.page == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(stringResource(label)) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = SwiftDropDesign.ContentMaxWidth).fillMaxSize()) {
                    when(state.page) {
                        "home" -> HomeScreen(state,send,receive,{manager.setPage("history")},{
                            manager.setPage(if (state.connectedName != null || state.progress != null) "transfer"
                                else if (state.isAdvertising) "receive" else "discover")
                        },state.status)
                        "discover" -> DiscoveryPage(state,manager)
                        "receive" -> WaitingPage(state,manager)
                        "transfer" -> TransferPage(state,manager)
                        "history" -> HistoryPage(state)
                        "settings" -> SettingsPage(state,manager)
                        "permissions" -> PermissionPage({send(arrayOf("*/*"))},receive)
                    }
                }
            }
        }
        state.pending?.let { request ->
            AlertDialog(onDismissRequest = { manager.answerConnection(false) },
                icon = { AppIcon(R.drawable.ic_security) },
                title = { Text(stringResource(R.string.secure_connection)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.connect_to,request.peerName))
                        Text(stringResource(R.string.security_hint))
                        Text(request.code, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = { Button({manager.answerConnection(true)}) { Text(stringResource(R.string.accept_codes)) } },
                dismissButton = { TextButton({manager.answerConnection(false)}) { Text(stringResource(R.string.decline)) } })
        }
    }
}
