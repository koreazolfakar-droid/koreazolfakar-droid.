package app.swiftdrop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.theme.swiftMotionDurationMillis

/** SwiftMotion's compact floating dock, retaining existing routes and tab semantics. */
@Composable
fun SwiftNavigationBar(current: String, navigate: (String) -> Unit) {
    val duration = swiftMotionDurationMillis()
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("home", R.string.home, R.drawable.ic_home),
                Triple("history", R.string.history, R.drawable.ic_history),
                Triple("settings", R.string.settings, R.drawable.ic_settings)
            ).forEach { (page, label, icon) ->
                val selected = current == page
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    tween(duration), label = "dock selection"
                )
                val ink = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    color = background, shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { navigate(page) }
                            ).padding(horizontal = 5.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppIcon(icon, tint = ink)
                        Text(
                            stringResource(label), style = MaterialTheme.typography.labelSmall,
                            color = ink, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
