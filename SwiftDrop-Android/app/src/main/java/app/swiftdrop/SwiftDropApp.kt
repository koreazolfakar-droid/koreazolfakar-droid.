package app.swiftdrop

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.OnBackPressedDispatcherOwner
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
import app.swiftdrop.ui.screens.files.DocumentBrowserScreen
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
import app.swiftdrop.ui.screens.transfer.UltraTransferScreen
import app.swiftdrop.ui.screens.permissions.PermissionsScreen
import app.swiftdrop.ui.screens.history.HistoryScreen
import app.swiftdrop.ui.theme.*

/** Named destinations prevent Home categories from silently opening the generic file picker. */
fun galleryDestination(kind:MediaKind):String=when(kind) {
    MediaKind.Photos -> "gallery_photos"
    MediaKind.Videos -> "gallery_videos"
    MediaKind.Music -> "gallery_music"
}

fun galleryCategory(page:String):MediaKind?=when(page) {
    "gallery_photos" -> MediaKind.Photos
    "gallery_videos" -> MediaKind.Videos
    "gallery_music" -> MediaKind.Music
    else -> null
}

/** Navigation returns to the actual parent instead of jumping to Home from every screen. */
fun backDestination(page:String,appsParent:String="home"):String=when(page) {
    "apps" -> if(appsParent=="files") "files" else "home"
    "selection" -> "files"
    "gallery_photos", "gallery_videos", "gallery_music", "documents", "storage" -> "home"
    else -> "home"
}

@Composable
fun SwiftDropApp(manager: NearbyTransferManager,preferences: AppSettings,apps: InstalledAppsController,send: (Array<String>) -> Unit,
                 receive: () -> Unit,discover: () -> Unit,openSettings: () -> Unit,openLocationSettings: () -> Unit,scanQr: () -> Unit,scanUltra: () -> Unit = {}, startUltraHost: () -> Unit = {},
                 mediaPermissionRevision: Int = 0, requestMedia: (MediaKind) -> Unit = {},
                 overlay: @Composable () -> Unit = {}) {
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
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) { dark })
    }
    SwiftDropLocalizedProviders(configuration) {
        SwiftDropTheme(dark) {
        SwiftDropContent(manager,apps,settings,dark,preferences::setAppearance,preferences::setLanguage,
            {preferences.completeOnboarding();manager.setPage("home")},send,receive,discover,openSettings,openLocationSettings,scanQr,scanUltra,
            mediaPermissionRevision,requestMedia,startUltraHost)
        overlay()
        }
    }
}

/**
 * A localized Context is only a display/resources wrapper, not a ComponentActivity.
 * ActivityResult and BackHandler owners MUST be captured from the original composition
 * before LocalContext is replaced, otherwise opening Documents immediately crashes
 * with "No ActivityResultRegistryOwner was provided".
 *
 * Reuse this for all localized screens so music, gallery, files, and send pickers
 * retain their Activity integration without changing language or transfer behavior.
 */
@Composable
internal fun SwiftDropLocalizedProviders(
    configuration: Configuration,
    content: @Composable () -> Unit
) {
    val activityContext = LocalContext.current
    val activityResultOwner = requireNotNull(
        LocalActivityResultRegistryOwner.current ?: (activityContext as? ActivityResultRegistryOwner)
    ) { "An ActivityResultRegistryOwner is required for SwiftDrop file pickers" }
    val backPressedOwner = requireNotNull(
        LocalOnBackPressedDispatcherOwner.current ?: (activityContext as? OnBackPressedDispatcherOwner)
    ) { "An OnBackPressedDispatcherOwner is required for SwiftDrop navigation" }
    val localized = remember(activityContext, configuration) {
        activityContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalActivityResultRegistryOwner provides activityResultOwner,
        LocalOnBackPressedDispatcherOwner provides backPressedOwner,
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides
            if(configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
                LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        content()
    }
}

@Composable
private fun SwiftDropContent(manager: NearbyTransferManager,apps: InstalledAppsController,settings: SettingsState,dark: Boolean,
    appearance: (Appearance) -> Unit,language: (AppLanguage) -> Unit,welcomeDone: () -> Unit,send: (Array<String>) -> Unit,
    receive: () -> Unit,discover: () -> Unit,openSettings: () -> Unit,openLocationSettings: () -> Unit,scanQr: () -> Unit,
    scanUltra: () -> Unit,mediaPermissionRevision: Int,requestMedia: (MediaKind) -> Unit,
    startUltraHost: () -> Unit) {
    val state by manager.state.collectAsState()
    val ultra by manager.ultraHotspot.state.collectAsState()
    val lan by manager.ultraLan.state.collectAsState()
    val appsState by apps.state.collectAsState()
    var chosenFileTab by rememberSaveable { mutableIntStateOf(0) }
    var appsParent by rememberSaveable { mutableStateOf("home") }
    var docsParent by rememberSaveable { mutableStateOf("home") }
    var leaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sessionActive = state.connectedName != null || state.isAdvertising || state.isDiscovering ||
        state.connectingId != null || ultra.phase == UltraHotspotPhase.Starting || ultra.phase == UltraHotspotPhase.Active
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
            "documents","storage" -> manager.setPage(docsParent)
            else -> manager.setPage(backDestination(state.page,appsParent))
        }
    }
    SwiftDropTheme(dark) {
        CompositionLocalProvider(LocalPageBack provides {
            manager.setPage(if(state.page=="documents" || state.page=="storage")
                docsParent else backDestination(state.page,appsParent))
        }) {
        Scaffold(modifier=Modifier.imePadding(),bottomBar = {
            Column {
                if(state.page!="welcome") app.swiftdrop.music.PersistentMusicPlayer()
                if(state.page in setOf("home","history","settings")) SwiftNavigationBar(state.page,manager::setPage)
                else Spacer(Modifier.navigationBarsPadding())
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter) {
                Box(Modifier.widthIn(max=SwiftDropDesign.ContentMaxWidth).fillMaxSize()) {
                    when(state.page) {
                        "welcome" -> WelcomeScreen(welcomeDone)
                        "home" -> HomeScreen(state,guardedSend,guardedReceive,{manager.setPage("history")},{
                            manager.setPage(if(state.connectedName!=null || state.progress!=null) "transfer"
                                else if(state.isAdvertising) "receive" else if(state.selectedFiles.isNotEmpty() && !state.isDiscovering) "selection" else "discover")
                        },messageText(state.status),openFiles={chosenFileTab=0;manager.setPage("files")},openApps={appsParent="home";apps.load();manager.setPage("apps")},onSettings={manager.setPage("settings")},
                            openMedia={kind->manager.setPage(galleryDestination(kind))},
                            openDocuments={docsParent="home";manager.setPage("documents")},
                            openStorage={docsParent="home";manager.setPage("storage")})
                        "gallery_photos","gallery_videos","gallery_music" -> {
                            val kind=galleryCategory(state.page) ?: MediaKind.Photos
                            FileLibraryScreen(guardedSend,{
                                appsParent="files";apps.load();manager.setPage("apps")
                            },appsState,{apps.load(true)},guardedAppExport,guardedMediaSelect,
                                mediaPermissionRevision,requestMedia,kind.ordinal,
                                standaloneCategory=true,
                                openDocuments={docsParent="files";manager.setPage("documents")},
                                openStorage={docsParent="files";manager.setPage("storage")})
                        }
                        "documents","storage" -> DocumentBrowserScreen(
                            back={manager.setPage(docsParent)},
                            onSend=guardedMediaSelect,documentsOnly=state.page=="documents")
                        "files" -> FileLibraryScreen(guardedSend,{
                            appsParent="files";apps.load();manager.setPage("apps")
                        },
                            appsState,{apps.load(true)},guardedAppExport,guardedMediaSelect,
                            mediaPermissionRevision,requestMedia,chosenFileTab,
                            openDocuments={docsParent="files";manager.setPage("documents")},
                            openStorage={docsParent="files";manager.setPage("storage")})
                        "apps" -> InstalledAppsScreen(appsState,{apps.load(true)},guardedAppExport,
                            {guardedSend(arrayOf("application/vnd.android.package-archive"))},{manager.setPage("files")})
                        "selection" -> SelectionScreen(state,{manager.armQrTarget(null);discover()},{guardedSend(arrayOf("*/*"))},scanQr,scanUltra)
                        "discover" -> DiscoveryScreen(state,manager::connect,discover,manager::disconnect,openRecoverySettings)
                        "receive" -> ReceiveScreen(state,receive,manager::disconnect,openRecoverySettings,
                            ultra,startUltraHost,manager::stopUltraHotspot,
                            lan,if(ultra.phase==UltraHotspotPhase.Active)
                                manager.ultraLan.invitation(ultra.ssid,ultra.passphrase,ultra.security) else null)
                        "ultra_transfer" -> UltraTransferScreen(lan,manager::disconnect)
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
        lan.pending?.let { offer ->
            AlertDialog(
                onDismissRequest={manager.ultraLan.decide(false)},
                title={Text(stringResource(R.string.ultra_incoming_title))},
                text={Text(stringResource(R.string.ultra_incoming_message,offer.name,offer.bytes))},
                confirmButton={Button({manager.ultraLan.decide(true)}) {
                    Text(stringResource(R.string.accept_codes))
                }},
                dismissButton={TextButton({manager.ultraLan.decide(false)}) {
                    Text(stringResource(R.string.decline))
                }}
            )
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
