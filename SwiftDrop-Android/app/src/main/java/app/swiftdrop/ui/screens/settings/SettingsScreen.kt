package app.swiftdrop.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*
import app.swiftdrop.ui.theme.SwiftDropDesign

@Composable
fun SettingsScreen(state: SettingsState,appearance: (Appearance) -> Unit,language: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Surface(
            shape=MaterialTheme.shapes.extraLarge,
            color=MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                IconBadge(R.drawable.ic_appearance)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings),
                        style=MaterialTheme.typography.headlineSmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.settings_caption),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surface) {
            Column {
                ExpandableSetting(R.drawable.ic_appearance,stringResource(R.string.appearance),appearanceLabel(state.appearance)) {
                    Column(Modifier.selectableGroup()) {
                        Appearance.entries.forEach { value ->
                            Choice(appearanceLabel(value),state.appearance==value) { appearance(value) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start=68.dp),color=MaterialTheme.colorScheme.outlineVariant)
                ExpandableSetting(R.drawable.ic_language,stringResource(R.string.language),languageLabel(state.language)) {
                    Column(Modifier.selectableGroup()) {
                        AppLanguage.entries.forEach { value ->
                            Choice(languageLabel(value),state.language==value) { language(value) }
                        }
                    }
                }
            }
        }
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Information(R.drawable.ic_security,R.string.privacy,stringResource(R.string.privacy_value))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Information(R.drawable.ic_folder,R.string.download_folder,stringResource(if(Build.VERSION.SDK_INT>=29) R.string.storage_modern else R.string.storage_legacy))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Information(R.drawable.ic_nearby,R.string.engine,stringResource(R.string.engine_value))
            }
        }
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                BrandMark(Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text("SwiftDrop",style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.version,BuildConfig.VERSION_NAME),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun appearanceLabel(value: Appearance): String = stringResource(when(value) {
    Appearance.System -> R.string.system;Appearance.Light -> R.string.light;Appearance.Dark -> R.string.dark
})

@Composable
private fun languageLabel(value: AppLanguage): String = stringResource(when(value) {
    AppLanguage.System -> R.string.system;AppLanguage.English -> R.string.english;AppLanguage.Arabic -> R.string.arabic
})

@Composable
private fun ExpandableSetting(icon: Int,title: String,summary: String,content: @Composable () -> Unit) {
    var expanded by rememberSaveable {mutableStateOf(false)}
    val rotation by animateFloatAsState(if(expanded) 180f else 0f,label="setting disclosure")
    Column(Modifier.animateContentSize(animationSpec=tween(SwiftDropDesign.MotionMillis))) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min=72.dp).clickable(role=Role.Button) {expanded=!expanded}.padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconBadge(icon)
                Column(Modifier.weight(1f)) {
                    Text(title,style=MaterialTheme.typography.titleMedium)
                    Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIcon(R.drawable.ic_expand,Modifier.graphicsLayer {rotationZ=rotation})
            }
            AnimatedVisibility(expanded) {Box(Modifier.padding(horizontal=16.dp).padding(bottom=12.dp)) {content()}}
        }
    }
}

@Composable
private fun Choice(label: String,selected: Boolean,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp).selectable(selected=selected,role=Role.RadioButton,onClick=onClick)
        .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        RadioButton(selected,onClick=null)
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Information(icon: Int,title: Int,value: String) {
    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AppIcon(icon)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium)
            Text(value,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
