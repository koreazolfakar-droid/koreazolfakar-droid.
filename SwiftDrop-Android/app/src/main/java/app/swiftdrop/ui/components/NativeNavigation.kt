package app.swiftdrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.theme.swiftMotionDurationMillis

/** Compact, full-width navigation with no oversized floating pills.
 * Actual routes and tab semantics remain unchanged. */
@Composable
fun SwiftNavigationBar(current: String, navigate: (String) -> Unit) {
    val duration = swiftMotionDurationMillis()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 5.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple("home", R.string.home, R.drawable.ic_home),
                    Triple("history", R.string.history, R.drawable.ic_history),
                    Triple("settings", R.string.settings, R.drawable.ic_settings)
                ).forEach { (page, label, icon) ->
                    val selected = current == page
                    val ink by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(duration), label = "navigation state"
                    )
                    Column(
                        Modifier.weight(1f).heightIn(min = 55.dp)
                            .selectable(
                                selected = selected, role = Role.Tab,
                                onClick = { navigate(page) }
                            ).padding(vertical = 3.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier.width(22.dp).height(3.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent, CircleShape
                                )
                        )
                        AppIcon(icon, Modifier.size(21.dp), tint = ink)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelSmall,
                            color = ink,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
