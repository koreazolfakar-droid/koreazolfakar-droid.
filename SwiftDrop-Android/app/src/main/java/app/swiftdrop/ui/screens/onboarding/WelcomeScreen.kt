package app.swiftdrop.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

/** SwiftMotion introduction, retaining existing skip/get-started entry behavior. */
@Composable
fun WelcomeScreen(continueSharing: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(continueSharing, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.skip))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BrandMark(Modifier.size(68.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    stringResource(R.string.welcome_caption),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Benefit(R.drawable.ic_send, R.string.direct_sharing, R.string.direct_caption)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Benefit(R.drawable.ic_security, R.string.private_sharing, R.string.security_hint)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Benefit(R.drawable.ic_no_ads, R.string.no_ads, R.string.no_ads_caption)
            }
        }
        Button(
            continueSharing, Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(stringResource(R.string.get_started))
        }
    }
}

@Composable
private fun Benefit(icon: Int, title: Int, caption: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
