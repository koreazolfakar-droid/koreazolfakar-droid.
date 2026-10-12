package app.swiftdrop.ui.screens.onboarding

import androidx.compose.foundation.Canvas
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

@Composable
fun WelcomeScreen(continueSharing: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            TextButton(continueSharing,Modifier.heightIn(min=48.dp)) {Text(stringResource(R.string.skip))}
        }
        BrandHeader()
        val primary=MaterialTheme.colorScheme.primary
        Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                drawCircle(primary.copy(alpha=.04f))
                drawCircle(primary.copy(alpha=.07f),size.minDimension*.38f)
                drawCircle(primary.copy(alpha=.1f),size.minDimension*.26f)
            }
            BrandMark(Modifier.size(72.dp))
            CategoryBadge(R.drawable.ic_photos,Modifier.align(Alignment.TopStart).padding(start=28.dp,top=16.dp))
            CategoryBadge(R.drawable.ic_music,Modifier.align(Alignment.TopEnd).padding(end=28.dp,top=16.dp))
            CategoryBadge(R.drawable.ic_documents,Modifier.align(Alignment.BottomStart).padding(start=42.dp,bottom=16.dp))
            CategoryBadge(R.drawable.ic_videos,Modifier.align(Alignment.BottomEnd).padding(end=42.dp,bottom=16.dp))
        }
        Text(stringResource(R.string.welcome_title),style=MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.welcome_caption),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Benefit(R.drawable.ic_send,R.string.direct_sharing,R.string.direct_caption)
        Benefit(R.drawable.ic_security,R.string.private_sharing,R.string.security_hint)
        Benefit(R.drawable.ic_no_ads,R.string.no_ads,R.string.no_ads_caption)
        Button(continueSharing,Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(stringResource(R.string.get_started))}
    }
}

@Composable
private fun Benefit(icon: Int,title: Int,caption: Int) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconBadge(icon)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium)
            Text(stringResource(caption),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
