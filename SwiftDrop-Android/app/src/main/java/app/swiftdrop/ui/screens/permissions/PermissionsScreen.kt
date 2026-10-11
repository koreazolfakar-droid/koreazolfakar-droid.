package app.swiftdrop.ui.screens.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

@Composable
fun PermissionsScreen(send: () -> Unit,receive: () -> Unit,openSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconBadge(R.drawable.ic_security)
        PageHeading(stringResource(R.string.permissions_title),stringResource(R.string.permissions_caption))
        Button(send,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.retry_send)) }
        OutlinedButton(receive,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.receive_files)) }
        TextButton(openSettings,Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.open_settings)) }
    }
}
