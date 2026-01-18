package su.kidoz.feature.settings

import su.kidoz.mvi.UiEffect

sealed interface SettingsEffect : UiEffect {
    data object SettingsSaved : SettingsEffect

    data object SettingsReset : SettingsEffect

    data class ShowError(
        val message: String,
    ) : SettingsEffect
}
