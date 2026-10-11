package app.swiftdrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri

private val Blue = Color(0xFF1668F5)
private val Cyan = Color(0xFF03C1DA)
private val PaleBlue = Color(0xFFEAF3FF)
private val Midnight = Color(0xFF101A35)

class MainActivity : ComponentActivity() {
    private lateinit var manager: NearbyTransferManager
    private var permissionAction: (() -> Unit)? = null
    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val action = permissionAction
        permissionAction = null
        if (result.values.all { it }) action?.invoke() else manager.setPage("permissions")
    }
    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) withNearbyPermissions { manager.discover(uris) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = NearbyTransferManager(applicationContext)
        setContent {
            SwiftDropApp(
                manager = manager,
                send = { pickFiles.launch(arrayOf("*/*")) },
                receive = { withNearbyPermissions { manager.advertise() } }
            )
        }
    }

    private fun withNearbyPermissions(action: () -> Unit) {
        val names = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT == 31) add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else if (Build.VERSION.SDK_INT >= 29) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = names.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else {
            permissionAction = action
            permissions.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        manager.close()
        super.onDestroy()
    }
}

@Composable
private fun SwiftDropApp(manager: NearbyTransferManager, send: () -> Unit, receive: () -> Unit) {
    val state by manager.state.collectAsState()
    val dark = state.darkTheme
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF8BB5FF), background = Color(0xFF0D1424),
        surface = Color(0xFF182238), onSurface = Color(0xFFF1F6FF),
        onBackground = Color.White
    ) else lightColorScheme(
        primary = Blue, background = Color(0xFFF6F9FF), surface = Color.White,
        onSurface = Midnight, onBackground = Midnight
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Triple("home", "Home", "⌂"),
                        Triple("history", "History", "◷"),
                        Triple("settings", "Settings", "⚙")).forEach { tab ->
                        NavigationBarItem(
                            selected = state.page == tab.first,
                            onClick = { manager.setPage(tab.first) },
                            icon = { Text(tab.third, fontSize = 21.sp) },
                            label = { Text(tab.second) }, alwaysShowLabel = true
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.page) {
                    "home" -> HomePage(state, send, receive) { manager.setPage("history") }
                    "discover" -> DiscoveryPage(state, manager)
                    "receive" -> WaitingPage(state, manager)
                    "transfer" -> TransferPage(state, manager)
                    "history" -> HistoryPage(state)
                    "settings" -> SettingsPage(state, manager)
                    "permissions" -> PermissionPage(send, receive)
                    else -> HomePage(state, send, receive) { manager.setPage("history") }
                }
                state.pending?.let { request ->
                    AlertDialog(
                        onDismissRequest = { manager.answerConnection(false) },
                        title = { Text("Secure connection") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Connect to ${request.peerName}?")
                                Text("Check that BOTH phones show this same code before accepting.")
                                Text(request.code, fontSize = 33.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        confirmButton = { Button(onClick = { manager.answerConnection(true) }) {
                            Text("Codes match · Accept")
                        } },
                        dismissButton = { TextButton(onClick = { manager.answerConnection(false) }) {
                            Text("Decline")
                        } }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String = "Share everything. Instantly.") {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(Cyan, Blue))), contentAlignment = Alignment.Center) {
            Text("➤", color = Color.White, fontSize = 27.sp)
        }
        Column {
            Text("SwiftDrop", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomePage(state: TransferUiState, send: () -> Unit, receive: () -> Unit, seeAll: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(4.dp))
        BrandHeader()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Blue, Color(0xFF04AADC))))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fast. Private. Ad-free.", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 23.sp)
            Text("Send files directly to nearby devices, no Internet needed.",
                color = Color.White.copy(alpha = .87f), fontSize = 13.sp)
            Text("⚡  High-speed peer-to-peer sharing", color = Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("➤", "Send", "Choose files", Blue, Modifier.weight(1f), send)
            ActionTile("↓", "Receive", "Make visible", Cyan, Modifier.weight(1f), receive)
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♢", color = MaterialTheme.colorScheme.primary, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connection status", fontWeight = FontWeight.SemiBold)
                    Text(state.status, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("●", color = Color(0xFF22B982), fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Recent activity", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = seeAll) { Text("See all") }
        }
        if (state.history.isEmpty()) EmptyHistory()
        else state.history.take(3).forEach { HistoryRow(it) }
        Text("No ads · No account · No upload server", fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionTile(symbol: String, title: String, caption: String,
                       color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.height(155.dp).clip(RoundedCornerShape(23.dp)).background(color)
        .clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Text(symbol, color = Color.White, fontSize = 29.sp)
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            Text(caption, color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun DiscoveryPage(state: TransferUiState, manager: NearbyTransferManager) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Nearby devices", "Select the phone you want to send files to")
        RadarMark()
        Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (state.peers.isEmpty()) {
            Text("No devices found yet. On the other phone, open SwiftDrop and tap Receive.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.peers.forEach { peer ->
            Card(modifier = Modifier.fillMaxWidth().clickable { manager.connect(peer) },
                shape = RoundedCornerShape(17.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("▣", color = MaterialTheme.colorScheme.primary, fontSize = 23.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(peer.name, fontWeight = FontWeight.SemiBold)
                        Text("Tap to connect", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = manager::disconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun RadarMark() {
    Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(180.dp).clip(CircleShape).background(PaleBlue),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(130.dp).clip(CircleShape).background(Color(0xFFCFE5FF)),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(77.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Cyan, Blue))),
                    contentAlignment = Alignment.Center) {
                    Text("▣", fontSize = 31.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WaitingPage(state: TransferUiState, manager: NearbyTransferManager) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(20.dp))
        PageTitle("Receive files", "Other devices can now find your phone")
        RadarMark()
        Text("Waiting for sender", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("Compare the security code when a device requests to connect.",
            fontSize = 13.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = manager::disconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Stop receiving")
        }
    }
}

@Composable
private fun TransferPage(state: TransferUiState, manager: NearbyTransferManager) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PageTitle("File transfer", state.connectedName ?: "Nearby device")
        Spacer(Modifier.height(14.dp))
        val progress = state.progress
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { (progress?.percent ?: 0) / 100f },
                modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${progress?.percent ?: 0}%", fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(progress?.direction ?: "Connected", fontSize = 14.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(progress?.name ?: "Ready for files", fontWeight = FontWeight.Bold,
                fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Transfer details", fontWeight = FontWeight.Bold)
                DetailRow("Transferred", "${formatSize(progress?.bytes ?: 0)} / ${formatSize(progress?.total ?: 0)}")
                DetailRow("Connection", "Nearby point-to-point")
                DetailRow("Status", if (progress?.finished == true) "Complete" else "Active / waiting")
                Text("Speed depends on Wi-Fi hardware and Android's selected radio.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = manager::disconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Disconnect")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HistoryPage(state: TransferUiState) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle("Transfer history", "Files sent and received in this session")
        if (state.history.isEmpty()) EmptyHistory()
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(state.history) { entry -> HistoryRow(entry) }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Card(shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(47.dp).clip(RoundedCornerShape(13.dp))
                .background(PaleBlue), contentAlignment = Alignment.Center) {
                Text(if (entry.direction == "Sent") "↗" else "↓", color = Blue, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold)
                Text("${entry.direction} · ${entry.peer}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatSize(entry.bytes), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyHistory() {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(27.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("↗", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
            Text("No transfers yet", fontWeight = FontWeight.Bold)
            Text("Your completed transfers will appear here.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsPage(state: TransferUiState, manager: NearbyTransferManager) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        PageTitle("Settings", "Customize SwiftDrop")
        Card(shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◑", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dark theme", fontWeight = FontWeight.SemiBold)
                    Text("Switch between light and dark", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.darkTheme, onCheckedChange = { manager.toggleTheme() })
            }
        }
        SettingTile("⌑", "Download folder", "Downloads / SwiftDrop (Android 10+)")
        SettingTile("♢", "Privacy", "No accounts, ads, analytics, or cloud")
        SettingTile("⚡", "Transfer engine", "Nearby Connections · High bandwidth")
        SettingTile("ⓘ", "About SwiftDrop", "Version 0.1.0-alpha")
    }
}

@Composable
private fun SettingTile(icon: String, title: String, value: String) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionPage(send: () -> Unit, receive: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle("Nearby permissions", "Required to discover and connect to other phones")
        Text("SwiftDrop needs nearby-device permissions. Grant them in system settings if you denied them permanently.")
        Button(onClick = send, modifier = Modifier.fillMaxWidth()) { Text("Try send again") }
        OutlinedButton(onClick = receive, modifier = Modifier.fillMaxWidth()) {
            Text("Try receive again")
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "Unknown"
    val kb = 1024.0
    return when {
        bytes >= kb * kb * kb -> "%.1f GB".format(bytes / (kb * kb * kb))
        bytes >= kb * kb -> "%.1f MB".format(bytes / (kb * kb))
        bytes >= kb -> "%.1f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
