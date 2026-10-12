package app.swiftdrop

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Appearance { System, Light, Dark }
enum class AppLanguage { System, English, Arabic }
data class SettingsState(val appearance: Appearance = Appearance.System, val language: AppLanguage = AppLanguage.System,
    val onboardingComplete: Boolean = false)

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences("swiftdrop_settings",Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SettingsState(
        Appearance.entries.find { it.name == preferences.getString("appearance",null) } ?: Appearance.System,
        AppLanguage.entries.find { it.name == preferences.getString("language",null) } ?: AppLanguage.System,
        preferences.getBoolean("onboarding_complete",false)))
    val state = mutable.asStateFlow()

    fun completeOnboarding() {
        preferences.edit().putBoolean("onboarding_complete",true).apply()
        mutable.value = mutable.value.copy(onboardingComplete = true)
    }

    fun setAppearance(appearance: Appearance) {
        preferences.edit().putString("appearance",appearance.name).apply()
        mutable.value = mutable.value.copy(appearance = appearance)
    }
    fun setLanguage(language: AppLanguage) {
        preferences.edit().putString("language",language.name).apply()
        mutable.value = mutable.value.copy(language = language)
    }
}
