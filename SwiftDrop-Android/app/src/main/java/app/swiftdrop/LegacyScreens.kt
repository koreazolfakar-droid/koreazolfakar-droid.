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

@Composable
internal fun SettingsPage(state: TransferUiState, manager: NearbyTransferManager) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        app.swiftdrop.ui.components.PageHeading("Settings", "Customize SwiftDrop")
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
internal fun SettingTile(icon: String, title: String, value: String) {
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

