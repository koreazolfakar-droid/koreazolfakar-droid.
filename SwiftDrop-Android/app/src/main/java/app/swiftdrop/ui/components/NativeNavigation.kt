package app.swiftdrop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R

// SwiftDrop @feature Codex 12/10/2026 Compact navigation with native insets and tab semantics.
@Composable
fun SwiftNavigationBar(current: String, navigate: (String) -> Unit) {
    Surface(color=MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
                listOf(Triple("home",R.string.home,R.drawable.ic_home),
                    Triple("history",R.string.history,R.drawable.ic_history),
                    Triple("settings",R.string.settings,R.drawable.ic_settings)).forEach { (page,label,icon) ->
                    val selected=current==page
                    val ink=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(Modifier.weight(1f).heightIn(min=64.dp)
                        .selectable(selected,role=Role.Tab,onClick={navigate(page)}).padding(vertical=8.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        AppIcon(icon,tint=ink)
                        Text(stringResource(label),style=MaterialTheme.typography.labelSmall,color=ink)
                    }
                }
            }
        }
    }
}
