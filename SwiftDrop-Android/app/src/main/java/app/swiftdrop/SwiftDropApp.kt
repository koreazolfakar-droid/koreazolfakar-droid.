package app.swiftdrop

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.swiftdrop.ui.screens.settings.SettingsScreen
import app.swiftdrop.ui.screens.onboarding.WelcomeScreen
import java.util.Locale
import app.swiftdrop.ui.screens.files.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.screens.home.HomeScreen
import app.swiftdrop.ui.screens.discovery.*
import app.swiftdrop.ui.screens.transfer.TransferScreen
import app.swiftdrop.ui.screens.permissions.PermissionsScreen
import app.swiftdrop.ui.screens.history.HistoryScreen
import app.swiftdrop.ui.theme.*

@Composable
fun SwiftDropApp(manager: NearbyTransferManager,preferences: AppSettings,apps: InstalledAppsController,send: (Array<String>) -> Unit,
                 receive: () -> Unit,discover: () -> Unit,openSettings: () -> Unit,openLocationSettings: () -> Unit,scanQr: () -> Unit,
                 mediaPermissionRevision: Int = 0, requestMedia: (MediaKind) -> Unit = {}) {
    val settings by preferences.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when(settings.appearance) {
        Appearance.System -> systemDark
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val context = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val configuration = remember(systemConfiguration,settings.language) {
        Configuration(systemConfiguration).apply {
            val locale = when(settings.language) {
                AppLanguage.English -> Locale.ENGLISH
                AppLanguage.Arabic -> Locale.forLanguageTag("ar")
                AppLanguage.System -> systemConfiguration.locales[0]
            }
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localized = remember(context,configuration) { context.createConfigurationContext(configuration) }
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) { dark })
    }
    CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides configuration,
        LocalLayoutDirection provides if(configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        SwiftDropContent(manager,apps,settings,dark,preferences::setAppearance,preferences::setLanguage,
            {preferences.completeOnboarding();manager.setPage("home")},send,receive,discover,openSettings,openLocationSettings,scanQr,
            mediaPermissionRevision,requestMedia)
    }
}

@Composable
private fun SwiftDropContent(manager: NearbyTransferManager,apps: InstalledAppsController,settings: SettingsState,dark: Boolean,
    appearance: (Appearance) -> Unit,language: (AppLanguage) -> Unit,welcomeDone: () -> Unit,send: (Array<String>) -> Unit,
    receive: () -> Unit,discover: () -> Unit,openSettings: () -> Unit,openLocationSettings: () -> Unit,scanQr: () -> Unit,
    mediaPermissionRevision: Int,requestMedia: (MediaKind) -> Unit) {
    val state by manager.state.collectAsState()
    val appsState by apps.state.collectAsState()
    var chosenFileTab by rememberSaveable { mutableIntStateOf(0) }
    var leaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sessionActive = state.connectedName != null || state.isAdvertising || state.isDiscovering || state.connectingId != null
    val guardedSend: (Array<String>) -> Unit = { mime ->
        if(sessionActive) leaveAction = { manager.disconnect();send(mime) } else send(mime)
    }
    val guardedMediaSelect: (List<android.net.Uri>) -> Unit = { uris ->
        if(sessionActive) leaveAction = { manager.disconnect();manager.selectFiles(uris) }
        else manager.selectFiles(uris)
    }
    val guardedAppExport: (List<ShareableApp>) -> Unit = { selection ->
        if(sessionActive) leaveAction = { manager.disconnect();apps.export(selection) }
        else apps.export(selection)
    }
    val openRecoverySettings: () -> Unit = {
        if(state.recoveryAction == NearbyRecoveryAction.LocationSettings) openLocationSettings() else openSettings()
    }
    val guardedReceive: () -> Unit = {
        if(sessionActive) leaveAction = { manager.disconnect();receive() } else receive()
    }
    BackHandler(state.page != "home" && state.pending == null && leaveAction == null) {
        when(state.page) {
            "welcome" -> welcomeDone()
            "apps" -> manager.setPage("files")
            else -> manager.setPage("home")
        }
    }
    SwiftDropTheme(dark) {
        CompositionLocalProvider(LocalPageBack provides {
            manager.setPage(if(state.page=="apps") "files" else "home")
        }) {
        Scaffold(modifier=Modifier.imePadding(),bottomBar = {
            if(state.page in setOf("home","history","settings")) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("home",R.string.home,R.drawable.ic_home),
                    Triple("history",R.string.history,R.drawable.ic_history),
                    Triple("settings",R.string.settings,R.drawable.ic_settings)).forEach { (page,label,icon) ->
                    NavigationBarItem(selected=state.page==page,onClick={manager.setPage(page)},
                        icon={AppIcon(icon,tint=if(state.page==page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)},
                        label={Text(stringResource(label))})
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
                Box(Modifier.widthIn(max=SwiftDropDesign.ContentMaxWidth).fillMaxSize()) {
                    when(state.page) {
                        "welcome" -> WelcomeScreen(welcomeDone)
                        "home" -> HomeScreen(state,guardedSend,guardedReceive,{manager.setPage("history")},{
                            manager.setPage(if(state.connectedName!=null || state.progress!=null) "transfer"
                                else if(state.isAdvertising) "receive" else if(state.selectedFiles.isNotEmpty() && !state.isDiscovering) "selection" else "discover")
                        },messageText(state.status),openFiles={chosenFileTab=0;manager.setPage("files")},openApps={apps.load();manager.setPage("apps")},onSettings={manager.setPage("settings")},
                            openMedia={ kind ->
                                chosenFileTab=kind.ordinal
                                manager.setPage("files")
                            })
                        "files" -> FileLibraryScreen(guardedSend,{apps.load();manager.setPage("apps")},
                            appsState,{apps.load(true)},guardedAppExport,guardedMediaSelect,
                            mediaPermissionRevision,requestMedia,chosenFileTab)
                        "apps" -> InstalledAppsScreen(appsState,{apps.load(true)},guardedAppExport,
                            {guardedSend(arrayOf("application/vnd.android.package-archive"))},{manager.setPage("files")})
                        "selection" -> SelectionScreen(state,{manager.armQrTarget(null);discover()},{guardedSend(arrayOf("*/*"))},scanQr)
                        "discover" -> DiscoveryScreen(state,manager::connect,discover,manager::disconnect,openRecoverySettings)
                        "receive" -> ReceiveScreen(state,receive,manager::disconnect,openRecoverySettings)
                        "transfer" -> TransferScreen(state,{
                            if(state.progress?.outcome==TransferOutcome.Running) manager.cancelTransfer() else manager.disconnect()
                        },sendMore={
                            if(sessionActive) leaveAction={manager.disconnect();manager.setPage("files")} else manager.setPage("files")
                        },history={manager.setPage("history")})
                        "history" -> HistoryScreen(state.history)
                        "settings" -> SettingsScreen(settings,appearance,language)
                        "permissions" -> PermissionsScreen({if(state.selected.isNotEmpty()) discover() else guardedSend(arrayOf("*/*"))},guardedReceive,openSettings)
                    }
                }
            }
        }
        state.pending?.let { request ->
            AlertDialog(onDismissRequest={manager.answerConnection(false)},icon={AppIcon(R.drawable.ic_security)},
                title={Text(stringResource(R.string.secure_connection))},text={
                    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.connect_to,request.peerName))
                        Text(stringResource(R.string.security_hint))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(request.code,style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                },confirmButton={Button({manager.answerConnection(true)}) {Text(stringResource(R.string.accept_codes))}},
                dismissButton={TextButton({manager.answerConnection(false)}) {Text(stringResource(R.string.decline))}})
        }
        leaveAction?.let { action ->
            AlertDialog(onDismissRequest={leaveAction=null},title={Text(stringResource(R.string.confirm_leave))},
                text={Text(stringResource(R.string.leave_caption))},
                confirmButton={Button({leaveAction=null;action()}) {Text(stringResource(R.string.disconnect))}},
                dismissButton={TextButton({leaveAction=null}) {Text(stringResource(R.string.keep_session))}})
        }
        }
    }
}
