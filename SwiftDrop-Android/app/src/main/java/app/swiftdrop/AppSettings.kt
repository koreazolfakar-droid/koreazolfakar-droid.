package app.swiftdrop

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Appearance { System, Light, Dark }
enum class AppLanguage { System, English, Arabic }
data class SettingsState(val appearance: Appearance = Appearance.System, val language: AppLanguage = AppLanguage.System)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("swiftdrop_settings",Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SettingsState(
        Appearance.entries.find { it.name == preferences.getString("appearance",null) } ?: Appearance.System,
        AppLanguage.entries.find { it.name == preferences.getString("language",null) } ?: AppLanguage.System))
    val state = mutable.asStateFlow()

    fun setAppearance(appearance: Appearance) {
        preferences.edit().putString("appearance",appearance.name).apply()
        mutable.value = mutable.value.copy(appearance = appearance)
    }
    fun setLanguage(language: AppLanguage) {
        preferences.edit().putString("language",language.name).apply()
        mutable.value = mutable.value.copy(language = language)
    }
}
