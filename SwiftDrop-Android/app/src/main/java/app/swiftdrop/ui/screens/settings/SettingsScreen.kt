package app.swiftdrop.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.swiftdrop.*
import app.swiftdrop.R
import app.swiftdrop.ui.components.*

@Composable
fun SettingsScreen(state: SettingsState, appearance: (Appearance) -> Unit, language: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageHeading(stringResource(R.string.settings),stringResource(R.string.settings_caption))
        SettingsGroup(R.drawable.ic_appearance,stringResource(R.string.appearance)) {
            Column(Modifier.selectableGroup()) {
                Appearance.entries.forEach { value ->
                    Choice(stringResource(when(value) { Appearance.System -> R.string.system; Appearance.Light -> R.string.light; Appearance.Dark -> R.string.dark }),
                        state.appearance==value) { appearance(value) }
                }
            }
        }
        SettingsGroup(R.drawable.ic_language,stringResource(R.string.language)) {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { value ->
                    Choice(stringResource(when(value) { AppLanguage.System -> R.string.system; AppLanguage.English -> R.string.english; AppLanguage.Arabic -> R.string.arabic }),
                        state.language==value) { language(value) }
                }
            }
        }
        Information(R.drawable.ic_folder,R.string.download_folder,stringResource(if(Build.VERSION.SDK_INT>=29) R.string.storage_modern else R.string.storage_legacy))
        Information(R.drawable.ic_security,R.string.privacy,stringResource(R.string.privacy_value))
        Information(R.drawable.ic_nearby,R.string.engine,stringResource(R.string.engine_value))
        Information(R.drawable.ic_info,R.string.about,stringResource(R.string.version,BuildConfig.VERSION_NAME))
    }
}

@Composable
private fun SettingsGroup(icon: Int,title: String,content: @Composable () -> Unit) {
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                AppIcon(icon);Text(title,style=MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun Choice(label: String,selected: Boolean,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=selected,role=Role.RadioButton,onClick=onClick)
        .padding(horizontal=4.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        RadioButton(selected,onClick=null)
        Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
    }
}

@Composable
private fun Information(icon: Int,title: Int,value: String) {
    SettingsGroup(icon,stringResource(title)) {
        Text(value,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
